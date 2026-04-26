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
