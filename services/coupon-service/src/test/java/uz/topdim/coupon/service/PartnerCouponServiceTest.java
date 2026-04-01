package uz.topdim.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.dto.CreateCouponOfferRequest;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerCouponServiceTest {

    @Mock private CouponOfferRepository couponOfferRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private CategoryRepository categoryRepository;

    @InjectMocks
    private PartnerCouponService partnerCouponService;

    private Merchant createMerchant() {
        return Merchant.builder().id(1L).name("Тест мерчант").userId(10L).active(true).build();
    }

    private Category createCategory() {
        return Category.builder().id(1L).name("Еда").slug("eda").active(true).build();
    }

    private CouponOffer createOffer(Merchant merchant, CouponStatus status) {
        return CouponOffer.builder()
                .id(100L).title("Тест купон").merchant(merchant)
                .category(createCategory()).fromPrice(BigDecimal.valueOf(50000))
                .status(status).build();
    }

    private CreateCouponOfferRequest createRequest() {
        CreateCouponOfferRequest req = new CreateCouponOfferRequest();
        req.setTitle("Новый купон");
        req.setMerchantId(1L);
        req.setCategoryId(1L);
        req.setFromPrice(BigDecimal.valueOf(30000));
        req.setBuyUntil(LocalDateTime.now().plusDays(30));
        req.setUseUntil(LocalDateTime.now().plusDays(60));
        return req;
    }

    // ==================== getMyCoupons ====================

    @Test
    @DisplayName("getMyCoupons: возвращает купоны партнёра")
    void getMyCoupons_returnsPartnerCoupons() {
        Merchant merchant = createMerchant();
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findByMerchantId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(createOffer(merchant, CouponStatus.ACTIVE))));

        Page<CouponOfferResponse> result = partnerCouponService.getMyCoupons(10L, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Тест купон");
    }

    @Test
    @DisplayName("getMyCoupons: фильтр по статусу")
    void getMyCoupons_filterByStatus() {
        Merchant merchant = createMerchant();
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findByMerchantIdAndStatus(eq(1L), eq(CouponStatus.PENDING_REVIEW), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(createOffer(merchant, CouponStatus.PENDING_REVIEW))));

        Page<CouponOfferResponse> result = partnerCouponService.getMyCoupons(10L, "PENDING_REVIEW", 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getMyCoupons: нет мерчанта → ResourceNotFoundException")
    void getMyCoupons_noMerchant_throws() {
        when(merchantRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerCouponService.getMyCoupons(99L, null, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== getMyCouponById ====================

    @Test
    @DisplayName("getMyCouponById: свой купон → OK")
    void getMyCouponById_ownCoupon_returnsIt() {
        Merchant merchant = createMerchant();
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(createOffer(merchant, CouponStatus.ACTIVE)));

        CouponOfferResponse result = partnerCouponService.getMyCouponById(10L, 100L);

        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getMyCouponById: чужой купон → IllegalStateException")
    void getMyCouponById_otherPartner_throws() {
        Merchant myMerchant = createMerchant();
        Merchant otherMerchant = Merchant.builder().id(2L).name("Другой").userId(20L).build();
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(myMerchant));
        when(couponOfferRepository.findById(200L)).thenReturn(Optional.of(createOffer(otherMerchant, CouponStatus.ACTIVE)));

        assertThatThrownBy(() -> partnerCouponService.getMyCouponById(10L, 200L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("другому партнёру");
    }

    // ==================== createCouponOffer ====================

    @Test
    @DisplayName("createCouponOffer: статус = PENDING_REVIEW")
    void createCouponOffer_setsPendingReview() {
        Merchant merchant = createMerchant();
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
        when(couponOfferRepository.save(any())).thenAnswer(inv -> {
            CouponOffer offer = inv.getArgument(0);
            offer.setId(101L);
            return offer;
        });

        CouponOfferResponse result = partnerCouponService.createCouponOffer(10L, createRequest());

        assertThat(result.getStatus()).isEqualTo("PENDING_REVIEW");
    }

    // ==================== updateMyCoupon ====================

    @Test
    @DisplayName("updateMyCoupon: PENDING_REVIEW → обновляется")
    void updateMyCoupon_pendingReview_updates() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.PENDING_REVIEW);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
        when(couponOfferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateCouponOfferRequest req = createRequest();
        req.setTitle("Обновлённый");

        CouponOfferResponse result = partnerCouponService.updateMyCoupon(10L, 100L, req);

        assertThat(result.getTitle()).isEqualTo("Обновлённый");
    }

    @Test
    @DisplayName("updateMyCoupon: REJECTED → ставит PENDING_REVIEW")
    void updateMyCoupon_rejected_resubmits() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.REJECTED);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
        when(couponOfferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CouponOfferResponse result = partnerCouponService.updateMyCoupon(10L, 100L, createRequest());

        assertThat(result.getStatus()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @DisplayName("updateMyCoupon: ACTIVE → IllegalStateException")
    void updateMyCoupon_active_throws() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.ACTIVE);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> partnerCouponService.updateMyCoupon(10L, 100L, createRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Нельзя редактировать");
    }
}
