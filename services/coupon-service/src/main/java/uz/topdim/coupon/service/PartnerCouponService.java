package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.dto.CreateCouponOfferRequest;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.util.Set;

/**
 * Сервис для партнёров — управление купонами.
 * Партнёр видит только СВОИ купоны (через Merchant.userId).
 * Новые купоны создаются со статусом DRAFT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerCouponService {

    private final CouponOfferRepository couponOfferRepository;
    private final MerchantRepository merchantRepository;
    private final CategoryRepository categoryRepository;

    private static final Set<CouponStatus> EDITABLE_STATUSES = Set.of(
            CouponStatus.DRAFT, CouponStatus.REVISION_REQUESTED
    );

    /**
     * Получает мерчанта текущего партнёра.
     */
    private Merchant getMerchant(Long userId) {
        return merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("У вас нет привязанного мерчанта"));
    }

    /**
     * Список купонов партнёра (все статусы).
     */
    @Transactional(readOnly = true)
    public Page<CouponOfferResponse> getMyCoupons(Long userId, String status, int page, int size) {
        Merchant merchant = getMerchant(userId);
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
        Merchant merchant = getMerchant(userId);
        CouponOffer offer = couponOfferRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        if (!offer.getMerchant().getId().equals(merchant.getId())) {
            throw new IllegalStateException("Купон принадлежит другому партнёру");
        }

        return mapToResponse(offer);
    }

    /**
     * Создать купон (статус = DRAFT).
     * Партнёр не может создать ACTIVE купон напрямую.
     */
    @Transactional
    public CouponOfferResponse createCouponOffer(Long userId, CreateCouponOfferRequest request) {
        Merchant merchant = getMerchant(userId);

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Категория не найдена"));

        CouponOffer offer = CouponOffer.builder()
                .title(request.getTitle())
                .shortDescription(request.getShortDescription())
                .fullDescription(request.getFullDescription())
                .merchant(merchant)
                .category(category)
                .oldPrice(request.getOldPrice())
                .fromPrice(request.getFromPrice())
                .discountPercent(request.getDiscountPercent())
                .coverImageUrl(request.getCoverImageUrl())
                .buyUntil(request.getBuyUntil())
                .useUntil(request.getUseUntil())
                .terms(request.getTerms())
                .usageRules(request.getUsageRules())
                .howToUse(request.getHowToUse())
                .address(request.getAddress())
                .contactPhone(request.getContactPhone())
                .workingHours(request.getWorkingHours())
                .giftAvailable(request.isGiftAvailable())
                .status(CouponStatus.LEAD)
                .build();

        offer = couponOfferRepository.save(offer);
        log.info("PARTNER: Пользователь {} создал купон {} (LEAD)", userId, offer.getId());

        return mapToResponse(offer);
    }

    /**
     * Обновить свой купон (только если DRAFT / REVISION_REQUESTED).
     */
    @Transactional
    public CouponOfferResponse updateMyCoupon(Long userId, Long couponId, CreateCouponOfferRequest request) {
        Merchant merchant = getMerchant(userId);
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

        offer.setTitle(request.getTitle());
        offer.setShortDescription(request.getShortDescription());
        offer.setFullDescription(request.getFullDescription());
        offer.setCategory(category);
        offer.setOldPrice(request.getOldPrice());
        offer.setFromPrice(request.getFromPrice());
        offer.setDiscountPercent(request.getDiscountPercent());
        offer.setCoverImageUrl(request.getCoverImageUrl());
        offer.setBuyUntil(request.getBuyUntil());
        offer.setUseUntil(request.getUseUntil());
        offer.setTerms(request.getTerms());
        offer.setUsageRules(request.getUsageRules());
        offer.setHowToUse(request.getHowToUse());
        offer.setAddress(request.getAddress());
        offer.setContactPhone(request.getContactPhone());
        offer.setWorkingHours(request.getWorkingHours());
        offer.setGiftAvailable(request.isGiftAvailable());
        // После редактирования отклонённого — снова на модерацию
        if (offer.getStatus() == CouponStatus.REVISION_REQUESTED) {
            offer.setStatus(CouponStatus.DRAFT);
        }

        offer = couponOfferRepository.save(offer);
        return mapToResponse(offer);
    }

    private CouponOfferResponse mapToResponse(CouponOffer offer) {
        return CouponOfferResponse.builder()
                .id(offer.getId())
                .title(offer.getTitle())
                .shortDescription(offer.getShortDescription())
                .oldPrice(offer.getOldPrice())
                .fromPrice(offer.getFromPrice())
                .discountPercent(offer.getDiscountPercent())
                .coverImageUrl(offer.getCoverImageUrl())
                .status(offer.getStatus().name())
                .totalSold(offer.getTotalSold())
                .build();
    }
}
