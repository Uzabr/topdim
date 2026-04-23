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
import uz.topdim.coupon.dto.CreateCouponOfferRequest;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.*;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponOfferServiceTest {

    @Mock private CouponOfferRepository couponOfferRepository;
    @Mock private CouponOptionRepository couponOptionRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private MerchantLocationRepository merchantLocationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private CouponImageRepository couponImageRepository;
    @Mock private EntityManager entityManager;
    @Mock private TelegramPreviewService telegramPreviewService;

    @InjectMocks
    private CouponOfferService couponOfferService;

    private CouponOffer createTestOffer() {
        Merchant merchant = Merchant.builder().id(1L).name("SPA Oasis").logoUrl("/logo.jpg").build();
        Category category = Category.builder().id(1L).name("Красота").slug("beauty").iconUrl("/icon.svg").build();
        return CouponOffer.builder()
                .id(1L).title("SPA массаж 50%")
                .offerDescription("Релакс\n\nДетали")
                .merchant(merchant).category(category)
                .oldPrice(BigDecimal.valueOf(300000)).fromPrice(BigDecimal.valueOf(150000))
                .discountPercent(50).coverImageUrl("/cover.jpg")
                .status(CouponStatus.ACTIVE).totalSold(45).viewCount(100)
                .giftAvailable(false)
                .options(new ArrayList<>()).images(new ArrayList<>())
                .build();
    }

    // ==================== Catalog ====================

    @Test
    @DisplayName("Каталог: без фильтра — возвращает все ACTIVE")
    void getCatalog_noFilter_returnsAllActive() {
        CouponOffer offer = createTestOffer();
        Page<CouponOffer> page = new PageImpl<>(List.of(offer));
        when(couponOfferRepository.findByStatus(eq(CouponStatus.ACTIVE), any(Pageable.class))).thenReturn(page);

        Page<CouponOfferResponse> result = couponOfferService.getCatalog(null, null, "popular", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("SPA массаж 50%");
    }

    @Test
    @DisplayName("Каталог: с фильтром по категории")
    void getCatalog_withCategory_filtersCorrectly() {
        CouponOffer offer = createTestOffer();
        Page<CouponOffer> page = new PageImpl<>(List.of(offer));
        when(couponOfferRepository.findByStatusAndCategoryId(eq(CouponStatus.ACTIVE), eq(1L), any(Pageable.class))).thenReturn(page);

        Page<CouponOfferResponse> result = couponOfferService.getCatalog(1L, null, "new", 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(couponOfferRepository).findByStatusAndCategoryId(eq(CouponStatus.ACTIVE), eq(1L), any());
    }

    // ==================== GetById ====================

    @Test
    @DisplayName("Получение по ID: существующий — возвращает и вызывает incrementViewCount")
    void getById_existingId_returnsOfferAndIncrementsViewCount() {
        CouponOffer offer = createTestOffer();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

        CouponOfferResponse result = couponOfferService.getById(1L);

        assertThat(result.getTitle()).isEqualTo("SPA массаж 50%");
        verify(couponOfferRepository).incrementViewCount(1L);
    }

    @Test
    @DisplayName("Получение по ID: несуществующий → ResourceNotFoundException")
    void getById_notFound_throwsException() {
        when(couponOfferRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponOfferService.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("не найден");
    }

    // ==================== Admin ====================

    @Test
    @DisplayName("Создание купона: успешное → статус LEAD")
    void create_success_statusLead() {
        Merchant merchant = Merchant.builder().id(1L).name("SPA").logoUrl("/l.jpg").build();
        Category category = Category.builder().id(1L).name("Красота").slug("beauty").iconUrl("/i.svg").build();

        CreateCouponOfferRequest request = new CreateCouponOfferRequest();
        request.setTitle("Новый купон");
        request.setOfferDescription("Краткое описание оффера");
        request.setMerchantId(1L);
        request.setCategoryId(1L);
        request.setOldPrice(BigDecimal.valueOf(200000));
        request.setFromPrice(BigDecimal.valueOf(100000));
        request.setDiscountPercent(50);
        request.setCoverImageUrl("/cover.jpg");
        request.setBuyUntil(java.time.LocalDateTime.now().plusDays(30));
        request.setUseUntil(java.time.LocalDateTime.now().plusDays(60));

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        CouponOffer savedOffer = CouponOffer.builder()
                .id(10L).title("Новый купон").merchant(merchant).category(category)
                .status(CouponStatus.LEAD).options(new ArrayList<>()).images(new ArrayList<>())
                .oldPrice(BigDecimal.valueOf(200000)).fromPrice(BigDecimal.valueOf(100000))
                .discountPercent(50).totalSold(0).viewCount(0).giftAvailable(false).build();

        when(couponOfferRepository.save(any(CouponOffer.class))).thenReturn(savedOffer);
        when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(savedOffer));

        CouponOfferResponse result = couponOfferService.create(request);

        assertThat(result.getStatus()).isEqualTo("LEAD");
        verify(couponOfferRepository, atLeastOnce()).save(any());
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @DisplayName("Удаление: существующий DRAFT — удаляет")
    void delete_existingDraft_deletes() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.DRAFT);
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

        couponOfferService.delete(1L);

        verify(couponOfferRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Удаление: ACTIVE — запрещено")
    void delete_activeCoupon_throwsException() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.ACTIVE);
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> couponOfferService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Удаление запрещено");
    }

    @Test
    @DisplayName("Удаление: несуществующий → ResourceNotFoundException")
    void delete_nonExistent_throwsException() {
        when(couponOfferRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponOfferService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== State Machine ====================

    @Test
    @DisplayName("State Machine: takeToWork LEAD → DRAFT")
    void takeToWork_fromLead_setsDraft() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.LEAD);
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(couponOfferRepository.save(any())).thenReturn(offer);

        CouponOfferResponse result = couponOfferService.takeToWork(1L, 100L, "mod@test.uz");

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getAssignedModeratorId()).isEqualTo(100L);
        assertThat(result.getAssignedModeratorName()).isEqualTo("mod@test.uz");
    }

    @Test
    @DisplayName("State Machine: takeToWork из DRAFT → IllegalStateException")
    void takeToWork_fromDraft_throws() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.DRAFT);
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> couponOfferService.takeToWork(1L, 100L, "mod@test.uz"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("State Machine: sendToApproval DRAFT → WAITING_FOR_MERCHANT")
    void sendToApproval_fromDraft_setsWaiting() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.DRAFT);
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(couponOfferRepository.save(any())).thenReturn(offer);

        CouponOfferResponse result = couponOfferService.sendToApproval(1L);

        assertThat(result.getStatus()).isEqualTo("WAITING_FOR_MERCHANT");
    }

    @Test
    @DisplayName("State Machine: sendToApproval из ACTIVE → IllegalStateException")
    void sendToApproval_fromActive_throws() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.ACTIVE);
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> couponOfferService.sendToApproval(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("State Machine: approveByMerchant WAITING → ACTIVE")
    void approve_fromWaiting_setsActive() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.WAITING_FOR_MERCHANT);
        MerchantLocation location = MerchantLocation.builder()
                .id(10L)
                .merchant(offer.getMerchant())
                .address("Ташкент, ул. Амира Темура, 10")
                .primary(true)
                .active(true)
                .build();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(merchantLocationRepository.findByMerchantIdAndPrimaryTrue(1L)).thenReturn(Optional.of(location));
        when(couponOfferRepository.save(any())).thenReturn(offer);

        CouponOfferResponse result = couponOfferService.approveByMerchant(1L);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("State Machine: approveByMerchant WAITING без active primary location → IllegalStateException")
    void approve_fromWaiting_withoutPrimaryLocation_throws() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.WAITING_FOR_MERCHANT);
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(merchantLocationRepository.findByMerchantIdAndPrimaryTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponOfferService.approveByMerchant(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primary location");
    }

    @Test
    @DisplayName("State Machine: approveByMerchant WAITING с пустым адресом primary location → IllegalStateException")
    void approve_fromWaiting_withBlankPrimaryLocationAddress_throws() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.WAITING_FOR_MERCHANT);
        MerchantLocation location = MerchantLocation.builder()
                .id(10L)
                .merchant(offer.getMerchant())
                .address(" ")
                .primary(true)
                .active(true)
                .build();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(merchantLocationRepository.findByMerchantIdAndPrimaryTrue(1L)).thenReturn(Optional.of(location));

        assertThatThrownBy(() -> couponOfferService.approveByMerchant(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("адрес");
    }

    @Test
    @DisplayName("State Machine: approveByMerchant из DRAFT → IllegalStateException")
    void approve_fromDraft_throws() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.DRAFT);
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> couponOfferService.approveByMerchant(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("State Machine: requestRevisionByMerchant WAITING → REVISION_REQUESTED")
    void reject_fromWaiting_setsRevision() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.WAITING_FOR_MERCHANT);
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(couponOfferRepository.save(any())).thenReturn(offer);

        CouponOfferResponse result = couponOfferService.requestRevisionByMerchant(1L, "Цена неверна");

        assertThat(result.getStatus()).isEqualTo("REVISION_REQUESTED");
        assertThat(result.getRevisionComment()).isEqualTo("Цена неверна");
    }

    // ==================== Update restrictions ====================

    @Test
    @DisplayName("Update: из DRAFT — разрешено (не бросает)")
    void update_fromDraft_allowed() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.DRAFT);
        Merchant merchant = offer.getMerchant();
        Category category = offer.getCategory();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(categoryRepository.findById(anyLong())).thenReturn(Optional.of(category));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        CreateCouponOfferRequest req = new CreateCouponOfferRequest();
        req.setTitle("Updated");
        req.setOfferDescription("Updated description");
        req.setMerchantId(1L);
        req.setCategoryId(1L);
        req.setFromPrice(offer.getFromPrice());

        assertThatCode(() -> couponOfferService.update(1L, req, 100L, "ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Update: из ACTIVE — разрешено (Variant B)")
    void update_fromActive_allowed() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.ACTIVE);
        Merchant merchant = offer.getMerchant();
        Category category = offer.getCategory();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(categoryRepository.findById(anyLong())).thenReturn(Optional.of(category));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        CreateCouponOfferRequest req = new CreateCouponOfferRequest();
        req.setTitle("Updated");
        req.setOfferDescription("Updated description");
        req.setMerchantId(1L);
        req.setCategoryId(1L);
        req.setFromPrice(offer.getFromPrice());

        assertThatCode(() -> couponOfferService.update(1L, req, 100L, "ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Update: из WAITING_FOR_MERCHANT — запрещено")
    void update_fromWaiting_throws() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.WAITING_FOR_MERCHANT);
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

        CreateCouponOfferRequest req = new CreateCouponOfferRequest();

        assertThatThrownBy(() -> couponOfferService.update(1L, req, 100L, "ADMIN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Редактирование запрещено");
    }

    @Test
    @DisplayName("Update: MODERATOR редактирует чужой купон — запрещено")
    void update_otherModeratorCoupon_throws() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.DRAFT);
        offer.setAssignedModeratorId(100L);
        offer.setAssignedModeratorName("mod1@test.uz");
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

        CreateCouponOfferRequest req = new CreateCouponOfferRequest();

        // Другой MODERATOR (другой userId) пытается редактировать
        assertThatThrownBy(() -> couponOfferService.update(1L, req, 200L, "MODERATOR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("закреплён за другим");
    }

    @Test
    @DisplayName("Delete: WAITING_FOR_MERCHANT — запрещено")
    void delete_waitingForMerchant_throws() {
        CouponOffer offer = createTestOffer();
        offer.setStatus(CouponStatus.WAITING_FOR_MERCHANT);
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> couponOfferService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Удаление запрещено");
    }

    // ==================== Release 1: offerDescription + primaryLocation ====================

    @Test
    @DisplayName("Создание: пишет offerDescription")
    void create_writesOfferDescription() {
        Merchant merchant = Merchant.builder().id(1L).name("SPA").logoUrl("/l.jpg").build();
        Category category = Category.builder().id(1L).name("Красота").slug("beauty").iconUrl("/i.svg").build();

        CreateCouponOfferRequest request = new CreateCouponOfferRequest();
        request.setTitle("Новый купон");
        request.setOfferDescription("Полное описание оффера");
        request.setMerchantId(1L);
        request.setCategoryId(1L);
        request.setFromPrice(BigDecimal.valueOf(100000));
        request.setCoverImageUrl("/cover.jpg");
        request.setBuyUntil(java.time.LocalDateTime.now().plusDays(30));
        request.setUseUntil(java.time.LocalDateTime.now().plusDays(60));

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        CouponOffer savedOffer = CouponOffer.builder()
                .id(10L).title("Новый купон")
                .offerDescription("Полное описание оффера")
                .merchant(merchant).category(category)
                .status(CouponStatus.LEAD).options(new ArrayList<>()).images(new ArrayList<>())
                .fromPrice(BigDecimal.valueOf(100000))
                .totalSold(0).viewCount(0).giftAvailable(false).build();

        when(couponOfferRepository.save(any(CouponOffer.class))).thenReturn(savedOffer);
        when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(savedOffer));

        CouponOfferResponse result = couponOfferService.create(request);

        assertThat(result.getOfferDescription()).isEqualTo("Полное описание оффера");
    }

    @Test
    @DisplayName("mapToResponse: включает merchant primaryLocation")
    void mapToResponse_includesMerchantPrimaryLocation() {
        CouponOffer offer = createTestOffer();
        MerchantLocation loc = MerchantLocation.builder()
                .id(1L).merchant(offer.getMerchant())
                .address("Ташкент, ул. Амира Темура")
                .phone("+998901234567")
                .workingHours("09:00-22:00")
                .primary(true).active(true)
                .build();

        when(merchantLocationRepository.findByMerchantIdAndPrimaryTrue(1L))
                .thenReturn(Optional.of(loc));

        CouponOfferResponse result = couponOfferService.mapToResponse(offer);

        assertThat(result.getMerchant().getPrimaryLocation()).isNotNull();
        assertThat(result.getMerchant().getPrimaryLocation().getAddress())
                .isEqualTo("Ташкент, ул. Амира Темура");
        assertThat(result.getMerchant().getPrimaryLocation().getPhone())
                .isEqualTo("+998901234567");
    }

    @Test
    @DisplayName("mapToResponse: null offerDescription сохраняет null canonical field")
    void mapToResponse_nullOfferDescription_returnsNullOfferDescription() {
        CouponOffer offer = createTestOffer();
        offer.setOfferDescription(null);

        CouponOfferResponse result = couponOfferService.mapToResponse(offer);

        assertThat(result.getOfferDescription()).isNull();
    }
}
