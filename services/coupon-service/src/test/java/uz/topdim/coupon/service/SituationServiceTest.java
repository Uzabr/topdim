package uz.topdim.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.mapper.SituationMapper;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.SituationCouponRepository;
import uz.topdim.coupon.repository.SituationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SituationServiceTest {

    @Mock private SituationRepository situationRepository;
    @Mock private SituationCouponRepository situationCouponRepository;
    @Mock private CouponOfferRepository couponOfferRepository;
    @Mock private SituationMapper situationMapper;

    @InjectMocks
    private SituationService situationService;

    // ==================== getActiveSituations ====================

    @Test
    @DisplayName("getActiveSituations: 3 ситуации с разным couponCount")
    void getActiveSituations_happyPath_returnsCorrectCounts() {
        Situation s1 = Situation.builder().id(1L).slug("kids").title("Дети").sortOrder(0).active(true).featured(true).build();
        Situation s2 = Situation.builder().id(2L).slug("beauty").title("Красота").sortOrder(1).active(true).build();
        Situation s3 = Situation.builder().id(3L).slug("health").title("Здоровье").sortOrder(2).active(true).build();

        when(situationRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(s1, s2, s3));
        when(situationCouponRepository.countActiveCouponsBySituation(eq(CouponStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new Object[]{1L, 10L},
                        new Object[]{3L, 5L}
                        // s2 (id=2) отсутствует → couponCount = 0
                ));

        // Маппер: slug → key, couponCount ignored (set by service)
        when(situationMapper.toResponse(any(Situation.class))).thenAnswer(inv -> {
            Situation s = inv.getArgument(0);
            return SituationResponse.builder()
                    .key(s.getSlug())
                    .title(s.getTitle())
                    .featured(s.isFeatured())
                    .sortOrder(s.getSortOrder())
                    .build();
        });

        List<SituationResponse> result = situationService.getActiveSituations();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getKey()).isEqualTo("kids");
        assertThat(result.get(0).getCouponCount()).isEqualTo(10);
        assertThat(result.get(0).isFeatured()).isTrue();
        assertThat(result.get(1).getKey()).isEqualTo("beauty");
        assertThat(result.get(1).getCouponCount()).isEqualTo(0); // отсутствует в агрегате → 0
        assertThat(result.get(2).getKey()).isEqualTo("health");
        assertThat(result.get(2).getCouponCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("getActiveSituations: нет активных ситуаций → пустой список")
    void getActiveSituations_noActive_returnsEmpty() {
        when(situationRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());

        List<SituationResponse> result = situationService.getActiveSituations();

        assertThat(result).isEmpty();
        // Агрегирующий запрос не вызывается при пустом списке
        verifyNoInteractions(situationCouponRepository);
    }

    @Test
    @DisplayName("getActiveSituations: все ситуации без купонов → couponCount = 0 у каждой")
    void getActiveSituations_allZeroCoupons() {
        Situation s1 = Situation.builder().id(1L).slug("kids").title("Дети").sortOrder(0).active(true).build();
        when(situationRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(s1));
        when(situationCouponRepository.countActiveCouponsBySituation(eq(CouponStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of()); // INNER JOIN → пусто

        when(situationMapper.toResponse(any(Situation.class))).thenAnswer(inv -> {
            Situation s = inv.getArgument(0);
            return SituationResponse.builder().key(s.getSlug()).title(s.getTitle()).build();
        });

        List<SituationResponse> result = situationService.getActiveSituations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCouponCount()).isEqualTo(0);
    }

    // ==================== createSituation ====================

    @Test
    @DisplayName("getAllSituationsForAdmin: возвращает active и inactive с id и couponCount")
    void getAllSituationsForAdmin_returnsAllWithCounts() {
        Situation active = Situation.builder().id(1L).slug("kids").title("Дети").sortOrder(0).active(true).build();
        Situation inactive = Situation.builder().id(2L).slug("hidden").title("Скрыто").sortOrder(1).active(false).build();
        when(situationRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(active, inactive));
        when(situationCouponRepository.countActiveCouponsBySituation(eq(CouponStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(new Object[]{1L, 3L}, new Object[]{2L, 1L}));

        List<AdminSituationResponse> result = situationService.getAllSituationsForAdmin();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getKey()).isEqualTo("kids");
        assertThat(result.get(0).getCouponCount()).isEqualTo(3);
        assertThat(result.get(0).isActive()).isTrue();
        assertThat(result.get(1).getKey()).isEqualTo("hidden");
        assertThat(result.get(1).getCouponCount()).isEqualTo(1);
        assertThat(result.get(1).isActive()).isFalse();
    }

    @Test
    @DisplayName("getSituationForAdmin: не найдена → ResourceNotFoundException")
    void getSituationForAdmin_notFound_throws() {
        when(situationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> situationService.getSituationForAdmin(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("createSituation: happy path")
    void createSituation_happyPath_returnsId() {
        CreateSituationRequest req = new CreateSituationRequest();
        req.setKey("dinner");
        req.setTitle("Ужин");
        req.setActive(true);

        when(situationRepository.save(any(Situation.class))).thenAnswer(inv -> {
            Situation s = inv.getArgument(0);
            s.setId(42L);
            return s;
        });

        Long id = situationService.createSituation(req);

        assertThat(id).isEqualTo(42L);
        verify(situationRepository).save(argThat(s -> s.getSlug().equals("dinner")));
    }

    @Test
    @DisplayName("createSituation: дубликат slug → DataIntegrityViolation → IllegalArgumentException")
    void createSituation_duplicateSlug_throwsIllegalArgument() {
        CreateSituationRequest req = new CreateSituationRequest();
        req.setKey("kids");
        req.setTitle("Дети");

        when(situationRepository.save(any(Situation.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        assertThatThrownBy(() -> situationService.createSituation(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kids");
    }

    // ==================== updateSituation ====================

    @Test
    @DisplayName("updateSituation: существующая → обновляется")
    void updateSituation_existing_updates() {
        Situation existing = Situation.builder().id(1L).slug("kids").title("Старое").active(true).build();
        when(situationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(situationRepository.save(any(Situation.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateSituationRequest req = new UpdateSituationRequest();
        req.setTitle("Новое");
        req.setActive(true);

        situationService.updateSituation(1L, req);

        verify(situationRepository).save(argThat(s -> s.getTitle().equals("Новое")));
    }

    @Test
    @DisplayName("updateSituation: не найдена → ResourceNotFoundException")
    void updateSituation_notFound_throws() {
        when(situationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> situationService.updateSituation(99L, new UpdateSituationRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== deleteSituation ====================

    @Test
    @DisplayName("deleteSituation: не найдена → ResourceNotFoundException")
    void deleteSituation_notFound_throws() {
        when(situationRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> situationService.deleteSituation(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deleteSituation: существующая → удаляется")
    void deleteSituation_existing_deletes() {
        when(situationRepository.existsById(1L)).thenReturn(true);

        situationService.deleteSituation(1L);

        verify(situationRepository).deleteById(1L);
    }

    // ==================== assignCoupons ====================

    @Test
    @DisplayName("assignCoupons: купон не найден → ResourceNotFoundException")
    void assignCoupons_couponNotFound_throws() {
        Situation s = Situation.builder().id(1L).slug("kids").build();
        when(situationRepository.findById(1L)).thenReturn(Optional.of(s));
        when(couponOfferRepository.existsById(999L)).thenReturn(false);

        SituationCouponRequest req = new SituationCouponRequest(List.of(999L));

        assertThatThrownBy(() -> situationService.assignCoupons(1L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("assignCoupons: happy path — атомарная замена")
    void assignCoupons_happyPath_replacesAll() {
        Situation s = Situation.builder().id(1L).slug("kids").build();
        when(situationRepository.findById(1L)).thenReturn(Optional.of(s));
        when(couponOfferRepository.existsById(anyLong())).thenReturn(true);
        when(couponOfferRepository.getReferenceById(anyLong())).thenAnswer(inv ->
                CouponOffer.builder().id(inv.getArgument(0)).build());

        SituationCouponRequest req = new SituationCouponRequest(List.of(10L, 20L));

        situationService.assignCoupons(1L, req);

        verify(situationCouponRepository).deleteBySituationId(1L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SituationCoupon>> captor = ArgumentCaptor.forClass(List.class);
        verify(situationCouponRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .extracting(link -> link.getCoupon().getId(), SituationCoupon::getSortOrder)
                .containsExactly(tuple(10L, 0), tuple(20L, 1));
    }

    @Test
    @DisplayName("assignCoupons: дубли couponId запрещены до удаления текущих связей")
    void assignCoupons_duplicateCouponIds_throwsBeforeDeletingExistingLinks() {
        Situation s = Situation.builder().id(1L).slug("kids").build();
        when(situationRepository.findById(1L)).thenReturn(Optional.of(s));

        SituationCouponRequest req = new SituationCouponRequest(List.of(10L, 10L));

        assertThatThrownBy(() -> situationService.assignCoupons(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("дубликаты");

        verify(situationCouponRepository, never()).deleteBySituationId(anyLong());
        verifyNoInteractions(couponOfferRepository);
    }

    @Test
    @DisplayName("assignCoupons: пустой список запрещён до удаления текущих связей")
    void assignCoupons_emptyCouponIds_throwsBeforeDeletingExistingLinks() {
        Situation s = Situation.builder().id(1L).slug("kids").build();
        when(situationRepository.findById(1L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> situationService.assignCoupons(1L, new SituationCouponRequest(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");

        verify(situationCouponRepository, never()).deleteBySituationId(anyLong());
        verifyNoInteractions(couponOfferRepository);
    }

    @Test
    @DisplayName("assignCoupons: ситуация не найдена → ResourceNotFoundException")
    void assignCoupons_situationNotFound_throws() {
        when(situationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> situationService.assignCoupons(99L, new SituationCouponRequest(List.of(1L))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== getCouponIdsBySlug ====================

    @Test
    @DisplayName("getCouponIdsBySlug: несуществующий slug → пустой список")
    void getCouponIdsBySlug_nonexistent_returnsEmpty() {
        when(situationRepository.findBySlugAndActiveTrue("nonexistent")).thenReturn(Optional.empty());

        List<Long> result = situationService.getCouponIdsBySlug("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCouponIdsBySlug: ситуация без привязанных купонов → пустой список")
    void getCouponIdsBySlug_noCoupons_returnsEmpty() {
        Situation s = Situation.builder().id(1L).slug("kids").build();
        when(situationRepository.findBySlugAndActiveTrue("kids")).thenReturn(Optional.of(s));
        when(situationCouponRepository.findCouponIdsBySituationId(1L)).thenReturn(List.of());

        List<Long> result = situationService.getCouponIdsBySlug("kids");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCouponIdsBySlug: happy path → список ID")
    void getCouponIdsBySlug_happyPath_returnsIds() {
        Situation s = Situation.builder().id(1L).slug("kids").build();
        when(situationRepository.findBySlugAndActiveTrue("kids")).thenReturn(Optional.of(s));
        when(situationCouponRepository.findCouponIdsBySituationId(1L)).thenReturn(List.of(10L, 20L, 30L));

        List<Long> result = situationService.getCouponIdsBySlug("kids");

        assertThat(result).containsExactly(10L, 20L, 30L);
    }

    @Test
    @DisplayName("getCouponIdsBySlug: неактивная ситуация трактуется как отсутствующая")
    void getCouponIdsBySlug_inactive_returnsEmpty() {
        when(situationRepository.findBySlugAndActiveTrue("kids")).thenReturn(Optional.empty());

        List<Long> result = situationService.getCouponIdsBySlug(" kids ");

        assertThat(result).isEmpty();
        verify(situationCouponRepository, never()).findCouponIdsBySituationId(anyLong());
    }

    // ==================== clearCoupons ====================

    @Test
    @DisplayName("clearCoupons: ситуация не найдена → ResourceNotFoundException")
    void clearCoupons_notFound_throws() {
        when(situationRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> situationService.clearCoupons(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
