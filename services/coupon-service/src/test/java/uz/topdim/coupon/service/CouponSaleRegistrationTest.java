package uz.topdim.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.repository.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponSaleRegistrationTest {

    @Mock private CouponOfferRepository couponOfferRepository;
    @Mock private CouponOptionRepository couponOptionRepository;
    @Mock private CouponSaleRepository couponSaleRepository;
    @Mock private CouponImageRepository couponImageRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private MerchantLocationRepository merchantLocationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private jakarta.persistence.EntityManager entityManager;
    @Mock private TelegramPreviewService telegramPreviewService;
    @Mock private CouponRedemptionLedgerRepository couponRedemptionLedgerRepository;

    @InjectMocks
    private CouponOfferService couponOfferService;

    private CouponOffer buildOffer(Long id, int totalSold, BigDecimal turnover) {
        CouponOption option = CouponOption.builder()
                .id(3L).quantityLimit(10).quantitySold(totalSold)
                .title("Standard").couponPrice(BigDecimal.valueOf(99000))
                .status(CouponOptionStatus.ACTIVE)
                .build();
        CouponOffer offer = CouponOffer.builder()
                .id(id).title("SPA").status(CouponStatus.ACTIVE)
                .totalSold(totalSold).redeemedCount(0)
                .totalTurnover(turnover)
                .options(new ArrayList<>(List.of(option)))
                .build();
        option.setCouponOffer(offer);
        return offer;
    }

    @Test
    @DisplayName("registerSaleOnce: первая регистрация — вызывает atomic increment")
    void registerSaleOnce_firstSale_callsAtomicIncrement() {
        CouponOffer offer = buildOffer(5L, 2, BigDecimal.valueOf(198000));
        when(couponSaleRepository.findByOrderIdAndCouponOfferIdAndCouponOptionId(100L, 5L, 3L))
                .thenReturn(Optional.empty());
        when(couponSaleRepository.save(any(CouponSale.class))).thenAnswer(inv -> inv.getArgument(0));
        // Atomic increment succeeds (returns 1 row updated)
        when(couponOptionRepository.atomicIncrementSold(3L, 2)).thenReturn(1);
        when(couponOfferRepository.atomicIncrementSoldAndTurnover(eq(5L), eq(2), any(BigDecimal.class))).thenReturn(1);
        // After atomic updates, re-read offer for SOLD_OUT check
        when(couponOfferRepository.findById(5L)).thenReturn(Optional.of(offer));

        couponOfferService.registerSaleOnce(100L, 5L, 3L, 2, BigDecimal.valueOf(198000));

        verify(couponSaleRepository).save(any(CouponSale.class));
        verify(couponOptionRepository).atomicIncrementSold(3L, 2);
        verify(couponOfferRepository).atomicIncrementSoldAndTurnover(eq(5L), eq(2), eq(BigDecimal.valueOf(198000)));
    }

    @Test
    @DisplayName("registerSaleOnce: дублирующий вызов — не инкрементирует")
    void registerSaleOnce_duplicateSale_doesNotIncrement() {
        CouponSale existing = CouponSale.builder()
                .id(1L).orderId(100L).couponOfferId(5L).couponOptionId(3L)
                .quantity(2).amount(BigDecimal.valueOf(198000)).build();
        when(couponSaleRepository.findByOrderIdAndCouponOfferIdAndCouponOptionId(100L, 5L, 3L))
                .thenReturn(Optional.of(existing));

        couponOfferService.registerSaleOnce(100L, 5L, 3L, 2, BigDecimal.valueOf(198000));

        verify(couponOfferRepository, never()).findById(anyLong());
        verify(couponOptionRepository, never()).atomicIncrementSold(anyLong(), anyInt());
        verify(couponOfferRepository, never()).atomicIncrementSoldAndTurnover(anyLong(), anyInt(), any());
        verify(couponSaleRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerSaleOnce: достижение лимита → SOLD_OUT")
    void registerSaleOnce_reachesLimit_setsOfferSoldOut() {
        // Offer has totalSold=10 (after atomic increment), limit=10
        CouponOffer offer = buildOffer(5L, 10, BigDecimal.valueOf(990000));
        when(couponSaleRepository.findByOrderIdAndCouponOfferIdAndCouponOptionId(101L, 5L, 3L))
                .thenReturn(Optional.empty());
        when(couponSaleRepository.save(any(CouponSale.class))).thenAnswer(inv -> inv.getArgument(0));
        when(couponOptionRepository.atomicIncrementSold(3L, 2)).thenReturn(1);
        when(couponOfferRepository.atomicIncrementSoldAndTurnover(eq(5L), eq(2), any(BigDecimal.class))).thenReturn(1);
        // After atomic updates, offer re-read shows totalSold >= totalLimit
        when(couponOfferRepository.findById(5L)).thenReturn(Optional.of(offer));
        when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(inv -> inv.getArgument(0));

        couponOfferService.registerSaleOnce(101L, 5L, 3L, 2, BigDecimal.valueOf(198000));

        assertThat(offer.getStatus()).isEqualTo(CouponStatus.SOLD_OUT);
        verify(couponOfferRepository).save(offer);
    }

    @Test
    @DisplayName("registerSaleOnce: лимит исчерпан — atomic increment возвращает 0, выбрасывает исключение")
    void registerSaleOnce_limitExceeded_throwsException() {
        when(couponSaleRepository.findByOrderIdAndCouponOfferIdAndCouponOptionId(102L, 5L, 3L))
                .thenReturn(Optional.empty());
        when(couponSaleRepository.save(any(CouponSale.class))).thenAnswer(inv -> inv.getArgument(0));
        // Atomic increment fails (returns 0 — limit exceeded)
        when(couponOptionRepository.atomicIncrementSold(3L, 2)).thenReturn(0);

        assertThatThrownBy(() ->
                couponOfferService.registerSaleOnce(102L, 5L, 3L, 2, BigDecimal.valueOf(198000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("лимит исчерпан");

        // Should NOT increment offer counters
        verify(couponOfferRepository, never()).atomicIncrementSoldAndTurnover(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("registerSale: atomic increment вызывается, лимит не превышен")
    void registerSale_callsAtomicIncrement() {
        CouponOffer offer = buildOffer(5L, 2, BigDecimal.valueOf(198000));
        when(couponOptionRepository.atomicIncrementSold(3L, 1)).thenReturn(1);
        when(couponOfferRepository.atomicIncrementSoldAndTurnover(eq(5L), eq(1), any(BigDecimal.class))).thenReturn(1);
        when(couponOfferRepository.findById(5L)).thenReturn(Optional.of(offer));

        couponOfferService.registerSale(5L, 3L, 1, BigDecimal.valueOf(99000));

        verify(couponOptionRepository).atomicIncrementSold(3L, 1);
        verify(couponOfferRepository).atomicIncrementSoldAndTurnover(eq(5L), eq(1), eq(BigDecimal.valueOf(99000)));
    }

    @Test
    @DisplayName("registerSale: лимит исчерпан — atomic increment возвращает 0, выбрасывает исключение")
    void registerSale_limitExceeded_throwsException() {
        when(couponOptionRepository.atomicIncrementSold(3L, 1)).thenReturn(0);

        assertThatThrownBy(() ->
                couponOfferService.registerSale(5L, 3L, 1, BigDecimal.valueOf(99000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("лимит исчерпан");

        verify(couponOfferRepository, never()).atomicIncrementSoldAndTurnover(anyLong(), anyInt(), any());
    }
}
