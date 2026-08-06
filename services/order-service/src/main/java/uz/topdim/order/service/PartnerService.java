package uz.topdim.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.order.dto.PartnerDashboardResponse;
import uz.topdim.order.dto.PartnerStatsResponse;
import uz.topdim.order.dto.RedemptionResponse;
import uz.topdim.order.entity.PurchasedCouponStatus;
import uz.topdim.order.entity.Redemption;
import uz.topdim.order.repository.PurchasedCouponRepository;
import uz.topdim.order.repository.RedemptionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Сервис статистики и погашений для партнёра.
 * merchantId резолвится контроллером из доверенного X-User-Id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PurchasedCouponRepository purchasedCouponRepository;
    private final RedemptionRepository redemptionRepository;

    /**
     * Статистика партнёра — безопасная версия, использует только merchantId.
     * Не принимает couponOfferIds от клиента.
     */
    @Transactional(readOnly = true)
    public PartnerStatsResponse getStats(Long merchantId) {
        long totalCoupons = purchasedCouponRepository.countDistinctCouponOfferIdsByMerchantId(merchantId);
        long totalSold = purchasedCouponRepository.countByMerchantId(merchantId);
        long totalRedeemed = purchasedCouponRepository.countByMerchantIdAndStatus(
                merchantId, PurchasedCouponStatus.USED);
        BigDecimal revenue = purchasedCouponRepository.sumRevenueByMerchantId(merchantId);

        return PartnerStatsResponse.builder()
                .totalCoupons((int) totalCoupons)
                .totalSold(totalSold)
                .totalRedeemed(totalRedeemed)
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .build();
    }

    /**
     * Дашборд партнёра — полная версия с KPI и последними погашениями.
     */
    @Transactional(readOnly = true)
    public PartnerDashboardResponse getDashboard(Long merchantId) {
        long totalCoupons = purchasedCouponRepository.countDistinctCouponOfferIdsByMerchantId(merchantId);
        long totalSold = purchasedCouponRepository.countByMerchantId(merchantId);
        long totalRedeemed = purchasedCouponRepository.countByMerchantIdAndStatus(
                merchantId, PurchasedCouponStatus.USED);
        long pendingRedemption = purchasedCouponRepository.countByMerchantIdAndStatus(
                merchantId, PurchasedCouponStatus.ACTIVE);
        long expired = purchasedCouponRepository.countByMerchantIdAndStatus(
                merchantId, PurchasedCouponStatus.EXPIRED);
        BigDecimal revenue = purchasedCouponRepository.sumRevenueByMerchantId(merchantId);

        // Recent 10 redemptions
        PageRequest recentPageable = PageRequest.of(0, 10, Sort.by("redeemedAt").descending());
        List<PartnerDashboardResponse.RecentRedemption> recentRedemptions =
                redemptionRepository.findByMerchantId(merchantId, recentPageable)
                        .getContent().stream()
                        .map(r -> PartnerDashboardResponse.RecentRedemption.builder()
                                .couponTitle(r.getPurchasedCoupon() != null ? r.getPurchasedCoupon().getCouponTitle() : null)
                                .couponCode(r.getPurchasedCoupon() != null ? r.getPurchasedCoupon().getCouponCode() : null)
                                .merchantLocationId(r.getMerchantLocationId())
                                .staffName(r.getRedeemedByStaff())
                                .redeemMethod(r.getRedeemMethod())
                                .redeemedAt(r.getRedeemedAt())
                                .build())
                        .collect(Collectors.toList());

        return PartnerDashboardResponse.builder()
                .totalCoupons(totalCoupons)
                .activeCoupons(pendingRedemption)
                .totalSold(totalSold)
                .totalRedeemed(totalRedeemed)
                .pendingRedemption(pendingRedemption)
                .expired(expired)
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .recentRedemptions(recentRedemptions)
                .build();
    }

    /**
     * История погашений с доверенным merchant/staff scope и optional filters.
     */
    @Transactional(readOnly = true)
    public Page<RedemptionResponse> getRedemptions(
            Long merchantId, Long staffId, String couponCode,
            LocalDate dateFrom, LocalDate dateTo, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("redeemedAt"), Sort.Order.desc("id")));
        String couponFragment = normalizeCouponFragment(couponCode);
        LocalDateTime fromInclusive = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime toExclusive = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;
        return redemptionRepository.findHistory(
                        merchantId, staffId, couponFragment, fromInclusive, toExclusive, pageable)
                .map(this::mapToResponse);
    }

    // Compatibility overloads until PartnerController switches to filtered history.
    @Transactional(readOnly = true)
    public Page<RedemptionResponse> getRedemptions(Long merchantId, int page, int size) {
        return getRedemptions(merchantId, null, null, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public Page<RedemptionResponse> getRedemptionsByStaff(Long merchantId, Long staffId, int page, int size) {
        return getRedemptions(merchantId, staffId, null, null, null, page, size);
    }

    private String normalizeCouponFragment(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) return null;
        return couponCode.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private RedemptionResponse mapToResponse(Redemption r) {
        return RedemptionResponse.builder()
                .id(r.getId())
                .couponTitle(r.getPurchasedCoupon() != null ? r.getPurchasedCoupon().getCouponTitle() : null)
                .optionTitle(r.getPurchasedCoupon() != null ? r.getPurchasedCoupon().getOptionTitle() : null)
                .couponCode(r.getPurchasedCoupon() != null ? r.getPurchasedCoupon().getCouponCode() : null)
                .redeemedByStaff(r.getRedeemedByStaff())
                .merchantLocationId(r.getMerchantLocationId())
                .staffId(r.getStaffId())
                .redeemMethod(r.getRedeemMethod())
                .note(r.getNote())
                .redeemedAt(r.getRedeemedAt())
                .build();
    }
}
