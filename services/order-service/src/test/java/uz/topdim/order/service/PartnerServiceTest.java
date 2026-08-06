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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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

        when(redemptionRepository.findHistory(
                eq(1L), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r)));

        Page<RedemptionResponse> redemptions = partnerService.getRedemptions(
                1L, null, null, null, null, 0, 20);

        assertThat(redemptions.getContent()).hasSize(1);
        assertThat(redemptions.getContent().get(0).getCouponTitle()).isEqualTo("Test Coupon");
        assertThat(redemptions.getContent().get(0).getCouponCode()).isEqualTo("CODE123");
        assertThat(redemptions.getContent().get(0).getRedeemedByStaff()).isEqualTo("Cashier 1");
    }

    @Test
    @DisplayName("getRedemptions: normalizes filters and uses stable newest-first sorting")
    void getRedemptions_normalizesFiltersAndBoundaries() {
        when(redemptionRepository.findHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        partnerService.getRedemptions(
                77L, 5L, " CP_%! ",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 6), 2, 20);

        verify(redemptionRepository).findHistory(
                eq(77L), eq(5L), eq("cp!_!%!!"),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 8, 7, 0, 0)),
                argThat(pageable -> pageable.getPageNumber() == 2
                        && pageable.getPageSize() == 20
                        && pageable.getSort().getOrderFor("redeemedAt").isDescending()
                        && pageable.getSort().getOrderFor("id").isDescending()));
    }

    @Test
    @DisplayName("getRedemptions: blank code and absent dates remain unfiltered")
    void getRedemptions_blankFiltersBecomeNull() {
        when(redemptionRepository.findHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        partnerService.getRedemptions(77L, null, "   ", null, null, 0, 20);

        verify(redemptionRepository).findHistory(
                eq(77L), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("getStats: calculates stats by merchantId without client-provided coupon ids")
    void getStats_byMerchantId_returnsCalculatedStats() {
        when(purchasedCouponRepository.countByMerchantId(77L)).thenReturn(100L);
        when(purchasedCouponRepository.countByMerchantIdAndStatus(77L, PurchasedCouponStatus.USED)).thenReturn(40L);
        when(purchasedCouponRepository.countDistinctCouponOfferIdsByMerchantId(77L)).thenReturn(2L);
        when(purchasedCouponRepository.sumRevenueByMerchantId(77L)).thenReturn(BigDecimal.valueOf(500000));

        PartnerStatsResponse stats = partnerService.getStats(77L);

        assertThat(stats.getTotalCoupons()).isEqualTo(2);
        assertThat(stats.getTotalSold()).isEqualTo(100);
        assertThat(stats.getTotalRedeemed()).isEqualTo(40);
        assertThat(stats.getTotalRevenue()).isEqualTo(BigDecimal.valueOf(500000));
    }
}
