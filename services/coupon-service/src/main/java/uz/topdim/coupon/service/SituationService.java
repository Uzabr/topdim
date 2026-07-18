package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.mapper.SituationMapper;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.SituationCouponRepository;
import uz.topdim.coupon.repository.SituationRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис управления ситуациями (подборками купонов).
 *
 * <p>Кэширование: @Cacheable("situations"), TTL = глобальный (5 мин, RedisCacheConfig).
 * Счётчик couponCount eventually-consistent: при смене статуса купона
 * кэш situations НЕ сбрасывается — рассинхрон ≤ TTL (5 мин).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SituationService {

    private final SituationRepository situationRepository;
    private final SituationCouponRepository situationCouponRepository;
    private final CouponOfferRepository couponOfferRepository;
    private final SituationMapper situationMapper;

    // ==================== Public API ====================

    /**
     * Возвращает список активных ситуаций с couponCount.
     * Один агрегирующий запрос для счётчика — без N+1.
     * Ситуации без активных купонов получают couponCount = 0 (не выпадают из результата).
     */
    @Cacheable("situations")
    @Transactional(readOnly = true)
    public List<SituationResponse> getActiveSituations() {
        List<Situation> situations = situationRepository.findByActiveTrueOrderBySortOrderAsc();
        if (situations.isEmpty()) {
            return List.of();
        }

        // Один агрегирующий запрос на все ситуации (INNER JOIN → ситуации без купонов отсутствуют)
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> counts = situationCouponRepository.countActiveCouponsBySituation(
                CouponStatus.ACTIVE, now);

        // Map: situationId → count; отсутствующие = 0
        Map<Long, Long> countMap = counts.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        return situations.stream()
                .map(s -> {
                    SituationResponse resp = situationMapper.toResponse(s);
                    resp.setCouponCount(countMap.getOrDefault(s.getId(), 0L));
                    return resp;
                })
                .collect(Collectors.toList());
    }

    // ==================== Admin API ====================

    /**
     * Создаёт новую ситуацию.
     * Уникальность slug: проверяется UNIQUE-ограничением в БД + catch DataIntegrityViolationException.
     */
    @CacheEvict(value = "situations", allEntries = true)
    @Transactional
    public Long createSituation(CreateSituationRequest request) {
        try {
            Situation situation = Situation.builder()
                    .slug(request.getKey().trim())
                    .title(request.getTitle())
                    .titleUz(request.getTitleUz())
                    .imageUrl(request.getImageUrl())
                    .featured(request.isFeatured())
                    .sortOrder(request.getSortOrder())
                    .active(request.isActive())
                    .build();
            return situationRepository.save(situation).getId();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "Ситуация с ключом '" + request.getKey() + "' уже существует");
        }
    }

    /**
     * Обновляет существующую ситуацию.
     * Slug неизменяем после создания.
     */
    @CacheEvict(value = "situations", allEntries = true)
    @Transactional
    public void updateSituation(Long id, UpdateSituationRequest request) {
        Situation situation = situationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ситуация не найдена"));

        situation.setTitle(request.getTitle());
        situation.setTitleUz(request.getTitleUz());
        situation.setImageUrl(request.getImageUrl());
        situation.setFeatured(request.isFeatured());
        situation.setSortOrder(request.getSortOrder());
        situation.setActive(request.isActive());

        situationRepository.save(situation);
    }

    /**
     * Удаляет ситуацию. Каскад удалит связи в situation_coupons.
     */
    @CacheEvict(value = "situations", allEntries = true)
    @Transactional
    public void deleteSituation(Long id) {
        if (!situationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Ситуация не найдена");
        }
        situationRepository.deleteById(id);
    }

    /**
     * Атомарная замена набора купонов ситуации.
     * Удаляет все текущие привязки, вставляет новые.
     */
    @CacheEvict(value = "situations", allEntries = true)
    @Transactional
    public void assignCoupons(Long situationId, SituationCouponRequest request) {
        Situation situation = situationRepository.findById(situationId)
                .orElseThrow(() -> new ResourceNotFoundException("Ситуация не найдена"));

        List<Long> couponIds = request.getCouponIds();
        if (couponIds == null || couponIds.isEmpty()) {
            throw new IllegalArgumentException("Список купонов не может быть пустым");
        }

        Set<Long> uniqueCouponIds = new LinkedHashSet<>(couponIds);
        if (uniqueCouponIds.size() != couponIds.size()) {
            throw new IllegalArgumentException("Список купонов содержит дубликаты");
        }

        // Валидируем все couponId до удаления текущих связей.
        for (Long couponId : couponIds) {
            if (!couponOfferRepository.existsById(couponId)) {
                throw new ResourceNotFoundException("Купон не найден: " + couponId);
            }
        }

        // Атомарная замена: удалить старые, вставить новые
        situationCouponRepository.deleteBySituationId(situationId);

        List<SituationCoupon> links = new ArrayList<>();
        int sortOrder = 0;
        for (Long couponId : couponIds) {
            links.add(SituationCoupon.builder()
                        .situation(situation)
                        .coupon(couponOfferRepository.getReferenceById(couponId))
                        .sortOrder(sortOrder++)
                        .build());
        }

        situationCouponRepository.saveAll(links);
    }

    /**
     * Снять все купоны с ситуации (очистка).
     */
    @CacheEvict(value = "situations", allEntries = true)
    @Transactional
    public void clearCoupons(Long situationId) {
        if (!situationRepository.existsById(situationId)) {
            throw new ResourceNotFoundException("Ситуация не найдена");
        }
        situationCouponRepository.deleteBySituationId(situationId);
    }

    // ==================== Catalog filter support ====================

    /**
     * Возвращает ID купонов, привязанных к ситуации по slug.
     * Пустой список — если slug не найден или ситуация без купонов.
     * Вызывающий код обязан закоротить на Page.empty() при пустом результате.
     */
    @Transactional(readOnly = true)
    public List<Long> getCouponIdsBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return List.of();
        }
        return situationRepository.findBySlugAndActiveTrue(slug.trim())
                .map(s -> situationCouponRepository.findCouponIdsBySituationId(s.getId()))
                .orElse(List.of());
    }
}
