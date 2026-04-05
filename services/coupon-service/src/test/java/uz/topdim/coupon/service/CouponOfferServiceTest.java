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
import uz.topdim.coupon.repository.*;

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
    @Mock private CategoryRepository categoryRepository;
    @Mock private ReviewRepository reviewRepository;

    @InjectMocks
    private CouponOfferService couponOfferService;

    private CouponOffer createTestOffer() {
        Merchant merchant = Merchant.builder().id(1L).name("SPA Oasis").logoUrl("/logo.jpg").build();
        Category category = Category.builder().id(1L).name("Красота").slug("beauty").iconUrl("/icon.svg").build();
        return CouponOffer.builder()
                .id(1L).title("SPA массаж 50%").shortDescription("Релакс")
                .fullDescription("Детали").merchant(merchant).category(category)
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
    @DisplayName("Получение по ID: существующий — возвращает и увеличивает viewCount")
    void getById_existingId_returnsOfferAndIncrementsViewCount() {
        CouponOffer offer = createTestOffer();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(couponOfferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CouponOfferResponse result = couponOfferService.getById(1L);

        assertThat(result.getTitle()).isEqualTo("SPA массаж 50%");
        assertThat(offer.getViewCount()).isEqualTo(101); // was 100, incremented
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
    @DisplayName("Создание купона: успешное → статус DRAFT")
    void create_success_statusDraft() {
        Merchant merchant = Merchant.builder().id(1L).name("SPA").logoUrl("/l.jpg").build();
        Category category = Category.builder().id(1L).name("Красота").slug("beauty").iconUrl("/i.svg").build();

        CreateCouponOfferRequest request = new CreateCouponOfferRequest();
        request.setTitle("Новый купон");
        request.setShortDescription("Краткое");
        request.setMerchantId(1L);
        request.setCategoryId(1L);
        request.setOldPrice(BigDecimal.valueOf(200000));
        request.setFromPrice(BigDecimal.valueOf(100000));
        request.setDiscountPercent(50);

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        CouponOffer savedOffer = CouponOffer.builder()
                .id(10L).title("Новый купон").merchant(merchant).category(category)
                .status(CouponStatus.DRAFT).options(new ArrayList<>()).images(new ArrayList<>())
                .oldPrice(BigDecimal.valueOf(200000)).fromPrice(BigDecimal.valueOf(100000))
                .discountPercent(50).totalSold(0).viewCount(0).giftAvailable(false).build();

        when(couponOfferRepository.save(any(CouponOffer.class))).thenReturn(savedOffer);
        when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(savedOffer));

        CouponOfferResponse result = couponOfferService.create(request);

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(couponOfferRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("Удаление: существующий — удаляет")
    void delete_existingCoupon_deletes() {
        when(couponOfferRepository.existsById(1L)).thenReturn(true);

        couponOfferService.delete(1L);

        verify(couponOfferRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Удаление: несуществующий → ResourceNotFoundException")
    void delete_nonExistent_throwsException() {
        when(couponOfferRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> couponOfferService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
