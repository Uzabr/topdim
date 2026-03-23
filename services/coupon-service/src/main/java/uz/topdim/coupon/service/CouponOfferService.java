package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.*;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponOfferService {

    private final CouponOfferRepository couponOfferRepository;
    private final CouponOptionRepository couponOptionRepository;
    private final MerchantRepository merchantRepository;
    private final CategoryRepository categoryRepository;

    // ==================== Public API ====================

    @Cacheable(value = "catalog", key = "#categoryId + '-' + #search + '-' + #sortBy + '-' + #page + '-' + #size")
    @Transactional(readOnly = true)
    public Page<CouponOfferResponse> getCatalog(Long categoryId, String search, String sortBy, int page, int size) {
        Pageable pageable = createPageable(sortBy, page, size);

        Page<CouponOffer> offers;

        if (search != null && !search.isBlank()) {
            offers = couponOfferRepository.searchByTitleOrDescription(CouponStatus.ACTIVE, search, pageable);
        } else if (categoryId != null) {
            offers = couponOfferRepository.findByStatusAndCategoryId(CouponStatus.ACTIVE, categoryId, pageable);
        } else {
            offers = couponOfferRepository.findByStatus(CouponStatus.ACTIVE, pageable);
        }

        return offers.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public CouponOfferResponse getById(Long id) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        // Increment view count
        offer.setViewCount(offer.getViewCount() + 1);
        couponOfferRepository.save(offer);

        return mapToResponse(offer);
    }

    @Cacheable(value = "topSelling", key = "#limit")
    @Transactional(readOnly = true)
    public List<CouponOfferResponse> getTopSelling(int limit) {
        return couponOfferRepository.findTopSelling(PageRequest.of(0, limit))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ==================== Admin API ====================

    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "topSelling", allEntries = true)
    })
    @Transactional
    public CouponOfferResponse create(CreateCouponOfferRequest request) {
        Merchant merchant = merchantRepository.findById(request.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Партнёр не найден"));

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
                .status(CouponStatus.DRAFT)
                .totalSold(0)
                .viewCount(0)
                .build();

        offer = couponOfferRepository.save(offer);

        // Create options
        if (request.getOptions() != null) {
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
                couponOptionRepository.save(option);
            }
        }

        return mapToResponse(couponOfferRepository.findById(offer.getId()).orElseThrow());
    }

    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "topSelling", allEntries = true),
            @CacheEvict(value = "couponDetail", key = "#id")
    })
    @Transactional
    public CouponOfferResponse updateStatus(Long id, CouponStatus status) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));
        offer.setStatus(status);
        return mapToResponse(couponOfferRepository.save(offer));
    }

    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "topSelling", allEntries = true),
            @CacheEvict(value = "couponDetail", key = "#id")
    })
    @Transactional
    public void delete(Long id) {
        if (!couponOfferRepository.existsById(id)) {
            throw new ResourceNotFoundException("Купон не найден");
        }
        couponOfferRepository.deleteById(id);
    }

    // ==================== Admin: All offers ====================

    @Transactional(readOnly = true)
    public Page<CouponOfferResponse> getAllForAdmin(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return couponOfferRepository.findAll(pageable).map(this::mapToResponse);
    }

    // ==================== Mapping ====================

    private CouponOfferResponse mapToResponse(CouponOffer offer) {
        return CouponOfferResponse.builder()
                .id(offer.getId())
                .title(offer.getTitle())
                .shortDescription(offer.getShortDescription())
                .fullDescription(offer.getFullDescription())
                .merchant(CouponOfferResponse.MerchantSummary.builder()
                        .id(offer.getMerchant().getId())
                        .name(offer.getMerchant().getName())
                        .logoUrl(offer.getMerchant().getLogoUrl())
                        .build())
                .category(CouponOfferResponse.CategorySummary.builder()
                        .id(offer.getCategory().getId())
                        .name(offer.getCategory().getName())
                        .slug(offer.getCategory().getSlug())
                        .iconUrl(offer.getCategory().getIconUrl())
                        .build())
                .oldPrice(offer.getOldPrice())
                .fromPrice(offer.getFromPrice())
                .discountPercent(offer.getDiscountPercent())
                .coverImageUrl(offer.getCoverImageUrl())
                .buyUntil(offer.getBuyUntil())
                .useUntil(offer.getUseUntil())
                .terms(offer.getTerms())
                .usageRules(offer.getUsageRules())
                .howToUse(offer.getHowToUse())
                .address(offer.getAddress())
                .contactPhone(offer.getContactPhone())
                .workingHours(offer.getWorkingHours())
                .giftAvailable(offer.isGiftAvailable())
                .status(offer.getStatus().name())
                .totalSold(offer.getTotalSold())
                .viewCount(offer.getViewCount())
                .options(offer.getOptions().stream()
                        .map(opt -> CouponOptionResponse.builder()
                                .id(opt.getId())
                                .title(opt.getTitle())
                                .regularPrice(opt.getRegularPrice())
                                .couponPrice(opt.getCouponPrice())
                                .quantityLimit(opt.getQuantityLimit())
                                .quantitySold(opt.getQuantitySold())
                                .status(opt.getStatus().name())
                                .build())
                        .collect(Collectors.toList()))
                .images(offer.getImages().stream()
                        .map(CouponImage::getImageUrl)
                        .collect(Collectors.toList()))
                .createdAt(offer.getCreatedAt())
                .build();
    }

    private Pageable createPageable(String sortBy, int page, int size) {
        Sort sort = switch (sortBy != null ? sortBy : "popular") {
            case "new" -> Sort.by("createdAt").descending();
            case "price_asc" -> Sort.by("fromPrice").ascending();
            case "price_desc" -> Sort.by("fromPrice").descending();
            case "discount" -> Sort.by("discountPercent").descending();
            default -> Sort.by("totalSold").descending();
        };
        return PageRequest.of(page, size, sort);
    }
}
