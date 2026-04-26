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
    @DisplayName("registerSaleOnce: первая регистрация — инкрементирует счётчики")
    void registerSaleOnce_firstSale_incrementsCounters() {
        CouponOffer offer = buildOffer(5L, 0, BigDecimal.ZERO);
        when(couponOfferRepository.findById(5L)).thenReturn(Optional.of(offer));
        when(couponSaleRepository.findByOrderIdAndCouponOfferIdAndCouponOptionId(100L, 5L, 3L))
                .thenReturn(Optional.empty());
        when(couponSaleRepository.save(any(CouponSale.class))).thenAnswer(inv -> inv.getArgument(0));
        when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(inv -> inv.getArgument(0));

        couponOfferService.registerSaleOnce(100L, 5L, 3L, 2, BigDecimal.valueOf(198000));

        assertThat(offer.getTotalSold()).isEqualTo(2);
        assertThat(offer.getTotalTurnover()).isEqualByComparingTo(BigDecimal.valueOf(198000));
        assertThat(offer.getOptions().get(0).getQuantitySold()).isEqualTo(2);
        verify(couponSaleRepository).save(any(CouponSale.class));
        verify(couponOfferRepository).save(offer);
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
        verify(couponOfferRepository, never()).save(any());
        verify(couponSaleRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerSaleOnce: достижение лимита → SOLD_OUT")
    void registerSaleOnce_reachesLimit_setsOptionSoldOut() {
        CouponOffer offer = buildOffer(5L, 8, BigDecimal.valueOf(792000));
        when(couponOfferRepository.findById(5L)).thenReturn(Optional.of(offer));
        when(couponSaleRepository.findByOrderIdAndCouponOfferIdAndCouponOptionId(101L, 5L, 3L))
                .thenReturn(Optional.empty());
        when(couponSaleRepository.save(any(CouponSale.class))).thenAnswer(inv -> inv.getArgument(0));
        when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(inv -> inv.getArgument(0));

        couponOfferService.registerSaleOnce(101L, 5L, 3L, 2, BigDecimal.valueOf(198000));

        assertThat(offer.getTotalSold()).isEqualTo(10);
        assertThat(offer.getOptions().get(0).getQuantitySold()).isEqualTo(10);
        // Option reached quantityLimit=10, total also reached limit
        assertThat(offer.getStatus()).isEqualTo(CouponStatus.SOLD_OUT);
    }
}
