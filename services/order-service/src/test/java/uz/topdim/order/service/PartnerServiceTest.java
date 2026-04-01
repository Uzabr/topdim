package uz.topdim.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uz.topdim.order.dto.PartnerStatsResponse;
import uz.topdim.order.dto.RedemptionResponse;
import uz.topdim.order.entity.PurchasedCoupon;
import uz.topdim.order.entity.PurchasedCouponStatus;
import uz.topdim.order.entity.Redemption;
import uz.topdim.order.repository.PurchasedCouponRepository;
import uz.topdim.order.repository.RedemptionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerServiceTest {

    @Mock
    private PurchasedCouponRepository purchasedCouponRepository;

    @Mock
    private RedemptionRepository redemptionRepository;

    @InjectMocks
    private PartnerService partnerService;

    @Test
    @DisplayName("getStats: пустой список couponOfferIds возвращает нули")
    void getStats_emptyIds_returnsZeros() {
        PartnerStatsResponse stats = partnerService.getStats(1L, Set.of());

        assertThat(stats.getTotalCoupons()).isEqualTo(0);
        assertThat(stats.getTotalSold()).isEqualTo(0);
        assertThat(stats.getTotalRedeemed()).isEqualTo(0);
        assertThat(stats.getTotalRevenue()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getStats: возвращает правильную статистику")
    void getStats_withIds_returnsCalculatedStats() {
        Set<Long> ids = Set.of(10L, 11L);
        when(purchasedCouponRepository.countByCouponOfferIdIn(ids)).thenReturn(100L);
        when(purchasedCouponRepository.countByCouponOfferIdInAndStatus(ids, PurchasedCouponStatus.USED)).thenReturn(40L);
        when(purchasedCouponRepository.sumRevenueByCouponOfferIds(ids)).thenReturn(BigDecimal.valueOf(500000));

        PartnerStatsResponse stats = partnerService.getStats(1L, ids);

        assertThat(stats.getTotalCoupons()).isEqualTo(2);
        assertThat(stats.getTotalSold()).isEqualTo(100L);
        assertThat(stats.getTotalRedeemed()).isEqualTo(40L);
        assertThat(stats.getTotalRevenue()).isEqualTo(BigDecimal.valueOf(500000));
    }

    @Test
    @DisplayName("getRedemptions: возвращает разбитый на страницы список")
    void getRedemptions_returnsPagedList() {
        PurchasedCoupon pc = PurchasedCoupon.builder()
                .couponTitle("Test Coupon")
                .couponCode("CODE123")
                .build();
        Redemption r = Redemption.builder()
                .id(55L)
                .merchantId(1L)
                .purchasedCoupon(pc)
                .redeemedByStaff("Cashier 1")
                .redeemedAt(LocalDateTime.now())
                .build();

        when(redemptionRepository.findByMerchantId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r)));

        Page<RedemptionResponse> redemptions = partnerService.getRedemptions(1L, 0, 20);

        assertThat(redemptions.getContent()).hasSize(1);
        assertThat(redemptions.getContent().get(0).getCouponTitle()).isEqualTo("Test Coupon");
        assertThat(redemptions.getContent().get(0).getCouponCode()).isEqualTo("CODE123");
        assertThat(redemptions.getContent().get(0).getRedeemedByStaff()).isEqualTo("Cashier 1");
    }
}
