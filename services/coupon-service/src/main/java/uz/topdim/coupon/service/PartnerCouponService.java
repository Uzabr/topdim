package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для партнёров — управление купонами.
 * Партнёр видит только СВОИ купоны (через Merchant.userId).
 * Новые купоны создаются как лиды (LEAD) для дальнейшей обработки менеджером.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerCouponService {

    private final CouponOfferRepository couponOfferRepository;
    private final MerchantRepository merchantRepository;
    private final CategoryRepository categoryRepository;

    private static final Set<CouponStatus> EDITABLE_STATUSES = Set.of(
            CouponStatus.LEAD, CouponStatus.DRAFT, CouponStatus.REVISION_REQUESTED
    );

    /**
     * Получает мерчанта текущего партнёра.
     * Кассиры не имеют привязки Merchant.userId — им вернётся 404.
     */
    private Merchant getMerchantForOwner(Long userId) {
        return merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "У вас нет привязанного мерчанта. Создание заявок доступно только владельцам и менеджерам."));
    }

    /**
     * Список купонов партнёра (все статусы).
     */
    @Transactional(readOnly = true)
    public Page<CouponOfferResponse> getMyCoupons(Long userId, String status, int page, int size) {
        Merchant merchant = getMerchantForOwner(userId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<CouponOffer> offers;
        if (status != null && !status.isBlank()) {
            CouponStatus couponStatus = CouponStatus.valueOf(status.toUpperCase());
            offers = couponOfferRepository.findByMerchantIdAndStatus(merchant.getId(), couponStatus, pageable);
        } else {
            offers = couponOfferRepository.findByMerchantId(merchant.getId(), pageable);
        }

        return offers.map(this::mapToResponse);
    }

    /**
     * Детали конкретного купона (проверка владения).
     */
    @Transactional(readOnly = true)
    public CouponOfferResponse getMyCouponById(Long userId, Long couponId) {
        Merchant merchant = getMerchantForOwner(userId);
        CouponOffer offer = couponOfferRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        if (!offer.getMerchant().getId().equals(merchant.getId())) {
            throw new IllegalStateException("Купон принадлежит другому партнёру");
        }

        return mapToResponse(offer);
    }

    /**
     * Создать заявку на акцию (статус = LEAD).
     * Партнёр не может опубликовать купон напрямую.
     * Доступно только для owner/manager — кассиры не имеют Merchant.userId.
     */
    @Transactional
    public CouponOfferResponse createPartnerRequest(Long userId, CreatePartnerCouponRequest request) {
        Merchant merchant = getMerchantForOwner(userId);

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Категория не найдена"));

        // Business validation: fromPrice must be less than oldPrice
        if (request.getFromPrice().compareTo(request.getOldPrice()) >= 0) {
            throw new IllegalArgumentException("Цена по акции должна быть ниже старой цены");
        }

        // Business validation: useUntil >= buyUntil
        if (request.getUseUntil().isBefore(request.getBuyUntil())) {
            throw new IllegalArgumentException("Срок использования не может быть раньше срока покупки");
        }

        // Determine cover image: explicit cover > first from imageUrls > null (admin will set later)
        String coverImageUrl = request.getCoverImageUrl();
        if ((coverImageUrl == null || coverImageUrl.isBlank()) &&
                request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            coverImageUrl = request.getImageUrls().get(0);
        }

        CouponOffer offer = CouponOffer.builder()
                .title(request.getTitle())
                .offerDescription(request.getOfferDescription())
                .merchant(merchant)
                .category(category)
                .oldPrice(request.getOldPrice())
                .fromPrice(request.getFromPrice())
                .discountPercent(request.getDiscountPercent())
                .coverImageUrl(coverImageUrl)
                .buyUntil(request.getBuyUntil())
                .useUntil(request.getUseUntil())
                .giftAvailable(request.isGiftAvailable())
                .status(CouponStatus.LEAD)
                .options(new ArrayList<>())
                .images(new ArrayList<>())
                .build();

        // Persist options
        for (CreateCouponOptionRequest optReq : request.getOptions()) {
            CouponOption option = CouponOption.builder()
                    .couponOffer(offer)
                    .title(optReq.getTitle())
                    .regularPrice(optReq.getRegularPrice())
                    .couponPrice(optReq.getCouponPrice())
                    .quantityLimit(optReq.getQuantityLimit())
                    .quantitySold(0)
                    .status(CouponOptionStatus.ACTIVE)
                    .build();
            offer.getOptions().add(option);
        }

        // Persist images (if any)
        if (request.getImageUrls() != null) {
            int sortOrder = 0;
            for (String imageUrl : request.getImageUrls()) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    CouponImage image = CouponImage.builder()
                            .couponOffer(offer)
                            .imageUrl(imageUrl)
                            .sortOrder(sortOrder++)
                            .build();
                    offer.getImages().add(image);
                }
            }
        }

        offer = couponOfferRepository.save(offer);
        log.info("PARTNER: Пользователь {} создал заявку на акцию {} (LEAD)", userId, offer.getId());

        return mapToResponse(offer);
    }

    /**
     * Обновить свой купон (только если LEAD / DRAFT / REVISION_REQUESTED).
     */
    @Transactional
    public CouponOfferResponse updateMyCoupon(Long userId, Long couponId, CreatePartnerCouponRequest request) {
        Merchant merchant = getMerchantForOwner(userId);
        CouponOffer offer = couponOfferRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        if (!offer.getMerchant().getId().equals(merchant.getId())) {
            throw new IllegalStateException("Купон принадлежит другому партнёру");
        }

        if (!EDITABLE_STATUSES.contains(offer.getStatus())) {
            throw new IllegalStateException("Нельзя редактировать купон в статусе " + offer.getStatus());
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Категория не найдена"));

        // Business validation
        if (request.getFromPrice().compareTo(request.getOldPrice()) >= 0) {
            throw new IllegalArgumentException("Цена по акции должна быть ниже старой цены");
        }
        if (request.getUseUntil().isBefore(request.getBuyUntil())) {
            throw new IllegalArgumentException("Срок использования не может быть раньше срока покупки");
        }

        // Determine cover
        String coverImageUrl = request.getCoverImageUrl();
        if ((coverImageUrl == null || coverImageUrl.isBlank()) &&
                request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            coverImageUrl = request.getImageUrls().get(0);
        }

        offer.setTitle(request.getTitle());
        offer.setOfferDescription(request.getOfferDescription());
        offer.setCategory(category);
        offer.setOldPrice(request.getOldPrice());
        offer.setFromPrice(request.getFromPrice());
        offer.setDiscountPercent(request.getDiscountPercent());
        offer.setCoverImageUrl(coverImageUrl);
        offer.setBuyUntil(request.getBuyUntil());
        offer.setUseUntil(request.getUseUntil());
        offer.setGiftAvailable(request.isGiftAvailable());

        // Replace options
        offer.getOptions().clear();
        for (CreateCouponOptionRequest optReq : request.getOptions()) {
            CouponOption option = CouponOption.builder()
                    .couponOffer(offer)
                    .title(optReq.getTitle())
                    .regularPrice(optReq.getRegularPrice())
                    .couponPrice(optReq.getCouponPrice())
                    .quantityLimit(optReq.getQuantityLimit())
                    .quantitySold(0)
                    .status(CouponOptionStatus.ACTIVE)
                    .build();
            offer.getOptions().add(option);
        }

        // Replace images
        offer.getImages().clear();
        if (request.getImageUrls() != null) {
            int sortOrder = 0;
            for (String imageUrl : request.getImageUrls()) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    CouponImage image = CouponImage.builder()
                            .couponOffer(offer)
                            .imageUrl(imageUrl)
                            .sortOrder(sortOrder++)
                            .build();
                    offer.getImages().add(image);
                }
            }
        }

        // After editing revision-requested coupon — reset to LEAD for re-review
        if (offer.getStatus() == CouponStatus.REVISION_REQUESTED) {
            offer.setStatus(CouponStatus.LEAD);
        }

        offer = couponOfferRepository.save(offer);
        return mapToResponse(offer);
    }

    private CouponOfferResponse mapToResponse(CouponOffer offer) {
        List<CouponOptionResponse> optionResponses = offer.getOptions() != null
                ? offer.getOptions().stream().map(o -> CouponOptionResponse.builder()
                        .id(o.getId())
                        .title(o.getTitle())
                        .regularPrice(o.getRegularPrice())
                        .couponPrice(o.getCouponPrice())
                        .quantityLimit(o.getQuantityLimit())
                        .quantitySold(o.getQuantitySold())
                        .status(o.getStatus().name())
                        .build())
                .collect(Collectors.toList())
                : List.of();

        List<String> imageUrls = offer.getImages() != null
                ? offer.getImages().stream().map(CouponImage::getImageUrl).collect(Collectors.toList())
                : List.of();

        return CouponOfferResponse.builder()
                .id(offer.getId())
                .title(offer.getTitle())
                .offerDescription(offer.getOfferDescription())
                .oldPrice(offer.getOldPrice())
                .fromPrice(offer.getFromPrice())
                .discountPercent(offer.getDiscountPercent())
                .coverImageUrl(offer.getCoverImageUrl())
                .status(offer.getStatus().name())
                .revisionComment(offer.getRevisionComment())
                .totalSold(offer.getTotalSold())
                .options(optionResponses)
                .images(imageUrls)
                .createdAt(offer.getCreatedAt())
                .buyUntil(offer.getBuyUntil())
                .useUntil(offer.getUseUntil())
                .giftAvailable(offer.isGiftAvailable())
                .build();
    }
}
