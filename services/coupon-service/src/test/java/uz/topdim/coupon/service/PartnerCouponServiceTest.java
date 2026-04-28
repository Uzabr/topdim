package uz.topdim.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.dto.CreateCouponOptionRequest;
import uz.topdim.coupon.dto.CreatePartnerCouponRequest;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    @Mock private CouponOfferService couponOfferService;

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
                .oldPrice(BigDecimal.valueOf(100000))
                .status(status)
                .options(new ArrayList<>())
                .images(new ArrayList<>())
                .build();
    }

    private CreatePartnerCouponRequest createRequest() {
        CreatePartnerCouponRequest req = new CreatePartnerCouponRequest();
        req.setTitle("Новый купон");
        req.setOfferDescription("Описание оффера");
        req.setCategoryId(1L);
        req.setOldPrice(BigDecimal.valueOf(100000));
        req.setFromPrice(BigDecimal.valueOf(30000));
        req.setBuyUntil(LocalDateTime.now().plusDays(30));
        req.setUseUntil(LocalDateTime.now().plusDays(60));

        CreateCouponOptionRequest optionReq = new CreateCouponOptionRequest();
        optionReq.setTitle("Базовый");
        optionReq.setRegularPrice(BigDecimal.valueOf(100000));
        optionReq.setCouponPrice(BigDecimal.valueOf(30000));
        optionReq.setQuantityLimit(50);
        req.setOptions(List.of(optionReq));

        return req;
    }

    private CouponOfferResponse response(Long id, String status) {
        return CouponOfferResponse.builder()
                .id(id)
                .title("Тест купон")
                .status(status)
                .build();
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
        when(couponOfferRepository.findByMerchantIdAndStatus(eq(1L), eq(CouponStatus.DRAFT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(createOffer(merchant, CouponStatus.DRAFT))));

        Page<CouponOfferResponse> result = partnerCouponService.getMyCoupons(10L, "DRAFT", 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getMyCoupons: нет мерчанта (кассир) → ResourceNotFoundException")
    void getMyCoupons_noMerchant_throws() {
        when(merchantRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerCouponService.getMyCoupons(99L, null, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("нет привязанного мерчанта");
    }

    // ==================== getMyCouponById ====================

    @Test
    @DisplayName("getMyCouponById: свой купон → OK")
    void getMyCouponById_ownCoupon_returnsIt() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.ACTIVE);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));
        when(couponOfferService.mapToResponse(offer)).thenReturn(response(100L, "ACTIVE"));

        CouponOfferResponse result = partnerCouponService.getMyCouponById(10L, 100L);

        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getMyCouponById: detail response использует canonical mapper с merchant/category")
    void getMyCouponById_usesCanonicalMapperForMerchantPreviewData() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.WAITING_FOR_MERCHANT);
        CouponOfferResponse canonicalResponse = response(100L, "WAITING_FOR_MERCHANT");
        canonicalResponse.setMerchant(CouponOfferResponse.MerchantSummary.builder()
                .id(1L)
                .name("Тест мерчант")
                .description("Описание бизнеса")
                .build());
        canonicalResponse.setCategory(CouponOfferResponse.CategorySummary.builder()
                .id(1L)
                .name("Еда")
                .slug("eda")
                .build());

        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));
        when(couponOfferService.mapToResponse(offer)).thenReturn(canonicalResponse);

        CouponOfferResponse result = partnerCouponService.getMyCouponById(10L, 100L);

        assertThat(result.getMerchant()).isNotNull();
        assertThat(result.getMerchant().getName()).isEqualTo("Тест мерчант");
        assertThat(result.getCategory()).isNotNull();
        assertThat(result.getCategory().getSlug()).isEqualTo("eda");
        verify(couponOfferService).mapToResponse(offer);
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

    // ==================== approveMyCoupon ====================

    @Test
    @DisplayName("approveMyCoupon: свой WAITING_FOR_MERCHANT → делегирует approveByMerchant")
    void approveMyCoupon_ownWaitingCoupon_delegatesToCanonicalService() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.WAITING_FOR_MERCHANT);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));
        when(couponOfferService.approveByMerchant(100L)).thenReturn(response(100L, "ACTIVE"));

        CouponOfferResponse result = partnerCouponService.approveMyCoupon(10L, 100L);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(couponOfferService).approveByMerchant(100L);
    }

    @Test
    @DisplayName("approveMyCoupon: чужой купон → IllegalStateException и не публикует")
    void approveMyCoupon_otherMerchant_throwsBeforeDelegation() {
        Merchant myMerchant = createMerchant();
        Merchant other = Merchant.builder().id(2L).name("Другой").userId(20L).build();
        CouponOffer offer = createOffer(other, CouponStatus.WAITING_FOR_MERCHANT);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(myMerchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> partnerCouponService.approveMyCoupon(10L, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("другому партнёру");

        verifyNoInteractions(couponOfferService);
    }

    @Test
    @DisplayName("approveMyCoupon: DRAFT → IllegalStateException")
    void approveMyCoupon_draft_throws() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.DRAFT);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> partnerCouponService.approveMyCoupon(10L, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WAITING_FOR_MERCHANT");

        verifyNoInteractions(couponOfferService);
    }

    // ==================== requestRevisionForMyCoupon ====================

    @Test
    @DisplayName("requestRevisionForMyCoupon: свой WAITING_FOR_MERCHANT → сохраняет trimmed comment")
    void requestRevisionForMyCoupon_ownWaitingCoupon_delegatesWithTrimmedComment() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.WAITING_FOR_MERCHANT);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));
        when(couponOfferService.requestRevisionByMerchant(100L, "Исправить цену"))
                .thenReturn(response(100L, "REVISION_REQUESTED"));

        CouponOfferResponse result = partnerCouponService.requestRevisionForMyCoupon(
                10L, 100L, "  Исправить цену  ");

        assertThat(result.getStatus()).isEqualTo("REVISION_REQUESTED");
        verify(couponOfferService).requestRevisionByMerchant(100L, "Исправить цену");
    }

    @Test
    @DisplayName("requestRevisionForMyCoupon: пустой комментарий → IllegalArgumentException")
    void requestRevisionForMyCoupon_blankComment_throws() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.WAITING_FOR_MERCHANT);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> partnerCouponService.requestRevisionForMyCoupon(10L, 100L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Комментарий");

        verifyNoInteractions(couponOfferService);
    }

    // ==================== createPartnerRequest ====================

    @Test
    @DisplayName("createPartnerRequest: создаёт LEAD с options и images")
    void createPartnerRequest_createsLeadWithOptionsAndImages() {
        Merchant merchant = createMerchant();
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
        when(couponOfferRepository.save(any())).thenAnswer(inv -> {
            CouponOffer offer = inv.getArgument(0);
            offer.setId(101L);
            return offer;
        });

        CreatePartnerCouponRequest req = createRequest();
        req.setImageUrls(List.of("https://cdn.example.com/photo1.jpg", "https://cdn.example.com/photo2.jpg"));

        CouponOfferResponse result = partnerCouponService.createPartnerRequest(10L, req);

        assertThat(result.getStatus()).isEqualTo("LEAD");
        assertThat(result.getOptions()).hasSize(1);
        assertThat(result.getImages()).hasSize(2);

        ArgumentCaptor<CouponOffer> captor = ArgumentCaptor.forClass(CouponOffer.class);
        verify(couponOfferRepository).save(captor.capture());
        CouponOffer saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CouponStatus.LEAD);
        assertThat(saved.getOptions()).hasSize(1);
        assertThat(saved.getImages()).hasSize(2);
    }

    @Test
    @DisplayName("createPartnerRequest: первое фото → coverImageUrl")
    void createPartnerRequest_firstImageBecomesCover() {
        Merchant merchant = createMerchant();
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
        when(couponOfferRepository.save(any())).thenAnswer(inv -> {
            CouponOffer offer = inv.getArgument(0);
            offer.setId(102L);
            return offer;
        });

        CreatePartnerCouponRequest req = createRequest();
        req.setCoverImageUrl(null); // no explicit cover
        req.setImageUrls(List.of("https://cdn.example.com/auto-cover.jpg"));

        CouponOfferResponse result = partnerCouponService.createPartnerRequest(10L, req);

        assertThat(result.getCoverImageUrl()).isEqualTo("https://cdn.example.com/auto-cover.jpg");
    }

    @Test
    @DisplayName("createPartnerRequest: без фото → coverImageUrl = null (OK)")
    void createPartnerRequest_noImages_noCover() {
        Merchant merchant = createMerchant();
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
        when(couponOfferRepository.save(any())).thenAnswer(inv -> {
            CouponOffer offer = inv.getArgument(0);
            offer.setId(103L);
            return offer;
        });

        CreatePartnerCouponRequest req = createRequest();
        req.setCoverImageUrl(null);
        req.setImageUrls(null);

        CouponOfferResponse result = partnerCouponService.createPartnerRequest(10L, req);

        assertThat(result.getCoverImageUrl()).isNull();
        assertThat(result.getImages()).isEmpty();
    }

    @Test
    @DisplayName("createPartnerRequest: fromPrice >= oldPrice → IllegalArgumentException")
    void createPartnerRequest_invalidPrice_throws() {
        Merchant merchant = createMerchant();
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));

        CreatePartnerCouponRequest req = createRequest();
        req.setFromPrice(BigDecimal.valueOf(150000)); // more than oldPrice=100000

        assertThatThrownBy(() -> partnerCouponService.createPartnerRequest(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ниже старой цены");
    }

    @Test
    @DisplayName("createPartnerRequest: useUntil < buyUntil → IllegalArgumentException")
    void createPartnerRequest_invalidDates_throws() {
        Merchant merchant = createMerchant();
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));

        CreatePartnerCouponRequest req = createRequest();
        req.setUseUntil(LocalDateTime.now().plusDays(5));
        req.setBuyUntil(LocalDateTime.now().plusDays(30));

        assertThatThrownBy(() -> partnerCouponService.createPartnerRequest(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("раньше срока покупки");
    }

    @Test
    @DisplayName("createPartnerRequest: неизвестная категория → ResourceNotFoundException")
    void createPartnerRequest_unknownCategory_throws() {
        Merchant merchant = createMerchant();
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        CreatePartnerCouponRequest req = createRequest();
        req.setCategoryId(999L);

        assertThatThrownBy(() -> partnerCouponService.createPartnerRequest(10L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Категория не найдена");
    }

    @Test
    @DisplayName("createPartnerRequest: кассир (нет мерчанта) → ResourceNotFoundException")
    void createPartnerRequest_cashier_throws() {
        when(merchantRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerCouponService.createPartnerRequest(99L, createRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("владельцам и менеджерам");
    }

    // ==================== updateMyCoupon ====================

    @Test
    @DisplayName("updateMyCoupon: LEAD → обновляется")
    void updateMyCoupon_lead_updates() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.LEAD);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
        when(couponOfferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreatePartnerCouponRequest req = createRequest();
        req.setTitle("Обновлённый");

        CouponOfferResponse result = partnerCouponService.updateMyCoupon(10L, 100L, req);

        assertThat(result.getTitle()).isEqualTo("Обновлённый");
    }

    @Test
    @DisplayName("updateMyCoupon: DRAFT → обновляется")
    void updateMyCoupon_draft_updates() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.DRAFT);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
        when(couponOfferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreatePartnerCouponRequest req = createRequest();
        req.setTitle("Обновлённый");

        CouponOfferResponse result = partnerCouponService.updateMyCoupon(10L, 100L, req);

        assertThat(result.getTitle()).isEqualTo("Обновлённый");
    }

    @Test
    @DisplayName("updateMyCoupon: REVISION_REQUESTED → ставит LEAD")
    void updateMyCoupon_revisionRequested_resetsToLead() {
        Merchant merchant = createMerchant();
        CouponOffer offer = createOffer(merchant, CouponStatus.REVISION_REQUESTED);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
        when(couponOfferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CouponOfferResponse result = partnerCouponService.updateMyCoupon(10L, 100L, createRequest());

        assertThat(result.getStatus()).isEqualTo("LEAD");
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

    @Test
    @DisplayName("updateMyCoupon: чужой купон → IllegalStateException")
    void updateMyCoupon_otherMerchant_throws() {
        Merchant myMerchant = createMerchant();
        Merchant other = Merchant.builder().id(2L).name("Другой").userId(20L).build();
        CouponOffer offer = createOffer(other, CouponStatus.LEAD);
        when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(myMerchant));
        when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> partnerCouponService.updateMyCoupon(10L, 100L, createRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("другому партнёру");
    }
}
