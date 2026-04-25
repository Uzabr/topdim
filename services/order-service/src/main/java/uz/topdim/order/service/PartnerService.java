package uz.topdim.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.order.dto.PartnerStatsResponse;
import uz.topdim.order.dto.RedemptionResponse;
import uz.topdim.order.entity.PurchasedCouponStatus;
import uz.topdim.order.entity.Redemption;
import uz.topdim.order.repository.PurchasedCouponRepository;
import uz.topdim.order.repository.RedemptionRepository;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Сервис статистики и погашений для партнёра.
 * merchantId и couponOfferIds передаются из контроллера.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PurchasedCouponRepository purchasedCouponRepository;
    private final RedemptionRepository redemptionRepository;

    /**
     * Статистика партнёра: продажи, погашения, выручка.
     *
     * @param merchantId ID мерчанта
     * @param couponOfferIds ID купонов этого мерчанта
     */
    @Transactional(readOnly = true)
    public PartnerStatsResponse getStats(Long merchantId, Collection<Long> couponOfferIds) {
        if (couponOfferIds.isEmpty()) {
            return PartnerStatsResponse.builder()
                    .totalCoupons(0).totalSold(0).totalRedeemed(0)
                    .totalRevenue(BigDecimal.ZERO).build();
        }

        long totalSold = purchasedCouponRepository.countByCouponOfferIdIn(couponOfferIds);
        long totalRedeemed = purchasedCouponRepository.countByCouponOfferIdInAndStatus(
                couponOfferIds, PurchasedCouponStatus.USED);
        BigDecimal revenue = purchasedCouponRepository.sumRevenueByCouponOfferIds(couponOfferIds);

        return PartnerStatsResponse.builder()
                .totalCoupons(couponOfferIds.size())
                .totalSold(totalSold)
                .totalRedeemed(totalRedeemed)
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .build();
    }

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
     * История погашений по мерчанту.
     */
    @Transactional(readOnly = true)
    public Page<RedemptionResponse> getRedemptions(Long merchantId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("redeemedAt").descending());
        return redemptionRepository.findByMerchantId(merchantId, pageable)
                .map(this::mapToResponse);
    }

    private RedemptionResponse mapToResponse(Redemption r) {
        return RedemptionResponse.builder()
                .id(r.getId())
                .couponTitle(r.getPurchasedCoupon() != null ? r.getPurchasedCoupon().getCouponTitle() : null)
                .optionTitle(r.getPurchasedCoupon() != null ? r.getPurchasedCoupon().getOptionTitle() : null)
                .couponCode(r.getPurchasedCoupon() != null ? r.getPurchasedCoupon().getCouponCode() : null)
                .redeemedByStaff(r.getRedeemedByStaff())
                .note(r.getNote())
                .redeemedAt(r.getRedeemedAt())
                .build();
    }
}
