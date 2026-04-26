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
class CouponRedeemedCountTest {

    @Mock private CouponOfferRepository couponOfferRepository;
    @Mock private CouponOptionRepository couponOptionRepository;
    @Mock private CouponImageRepository couponImageRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private MerchantLocationRepository merchantLocationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private CouponSaleRepository couponSaleRepository;
    @Mock private CouponRedemptionLedgerRepository couponRedemptionLedgerRepository;
    @Mock private jakarta.persistence.EntityManager entityManager;
    @Mock private TelegramPreviewService telegramPreviewService;

    @InjectMocks
    private CouponOfferService couponOfferService;

    private CouponOffer buildOffer(Long id, int redeemedCount) {
        CouponOption option = CouponOption.builder()
                .id(3L).quantityLimit(10).quantitySold(5)
                .title("Standard").couponPrice(BigDecimal.valueOf(99000))
                .status(CouponOptionStatus.ACTIVE)
                .build();
        CouponOffer offer = CouponOffer.builder()
                .id(id).title("SPA").status(CouponStatus.ACTIVE)
                .totalSold(5).redeemedCount(redeemedCount)
                .totalTurnover(BigDecimal.valueOf(495000))
                .options(new ArrayList<>(List.of(option)))
                .build();
        option.setCouponOffer(offer);
        return offer;
    }

    @Test
    @DisplayName("incrementRedeemedOnce: первый инкремент — увеличивает redeemedCount")
    void incrementRedeemedOnce_firstEvent_incrementsCounter() {
        CouponOffer offer = buildOffer(5L, 0);
        when(couponRedemptionLedgerRepository.findByPurchasedCouponId(100L)).thenReturn(Optional.empty());
        when(couponOfferRepository.findById(5L)).thenReturn(Optional.of(offer));
        when(couponRedemptionLedgerRepository.save(any(CouponRedemptionLedger.class))).thenAnswer(inv -> inv.getArgument(0));
        when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(inv -> inv.getArgument(0));

        couponOfferService.incrementRedeemedOnce(100L, 5L, 3L, 77L);

        assertThat(offer.getRedeemedCount()).isEqualTo(1);
        verify(couponRedemptionLedgerRepository).save(any(CouponRedemptionLedger.class));
        verify(couponOfferRepository).save(offer);
    }

    @Test
    @DisplayName("incrementRedeemedOnce: дубликат — не инкрементирует")
    void incrementRedeemedOnce_duplicateEvent_doesNotIncrement() {
        CouponRedemptionLedger existing = CouponRedemptionLedger.builder()
                .id(1L).purchasedCouponId(100L).couponOfferId(5L).couponOptionId(3L).merchantId(77L)
                .build();
        when(couponRedemptionLedgerRepository.findByPurchasedCouponId(100L)).thenReturn(Optional.of(existing));

        couponOfferService.incrementRedeemedOnce(100L, 5L, 3L, 77L);

        verify(couponOfferRepository, never()).findById(anyLong());
        verify(couponOfferRepository, never()).save(any());
        verify(couponRedemptionLedgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("incrementRedeemedOnce: множественные погашения — каждый уникальный инкрементирует")
    void incrementRedeemedOnce_multipleUnique_incrementsEach() {
        CouponOffer offer = buildOffer(5L, 2);
        when(couponRedemptionLedgerRepository.findByPurchasedCouponId(anyLong())).thenReturn(Optional.empty());
        when(couponOfferRepository.findById(5L)).thenReturn(Optional.of(offer));
        when(couponRedemptionLedgerRepository.save(any(CouponRedemptionLedger.class))).thenAnswer(inv -> inv.getArgument(0));
        when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(inv -> inv.getArgument(0));

        couponOfferService.incrementRedeemedOnce(200L, 5L, 3L, 77L);
        couponOfferService.incrementRedeemedOnce(201L, 5L, 3L, 77L);

        assertThat(offer.getRedeemedCount()).isEqualTo(4);
        verify(couponRedemptionLedgerRepository, times(2)).save(any(CouponRedemptionLedger.class));
    }
}
