package uz.topdim.coupon.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static uz.topdim.coupon.util.PhoneUtils.normalize;

/**
 * Сервис управления купонами.
 * CRUD операции, каталог с фильтрами и пагинацией.
 * Кэширование через Redis (@Cacheable).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponOfferService {

    private final CouponOfferRepository couponOfferRepository;
    private final CouponOptionRepository couponOptionRepository;
    private final CouponImageRepository couponImageRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantLocationRepository merchantLocationRepository;
    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;
    private final CouponSaleRepository couponSaleRepository;
    private final CouponRedemptionLedgerRepository couponRedemptionLedgerRepository;
    private final EntityManager entityManager;
    private final TelegramPreviewService telegramPreviewService;
    private final CouponCoverFallbackService couponCoverFallbackService;

    // ==================== Public API ====================

    /**
     * Получает каталог купонов с фильтрацией, поиском и пагинацией.
     * Результат кэшируется в Redis (TTL: 3 мин).
     *
     * @param categoryId   фильтр по категории (null = все)
     * @param search       поисковый запрос (null = без поиска)
     * @param situationKey фильтр по ситуации/подборке (null = без фильтра)
     * @param sortBy       сортировка: popular, new, priceAsc, priceDesc, discount
     * @param page         номер страницы (0-based)
     * @param size         размер страницы
     * @return страница купонов
     */
    // TODO: восстановить кэширование после настройки Redis serializer
    // @Cacheable(value = "catalog", key = "...")
    @Transactional(readOnly = true)
    public Page<CouponOfferResponse> getCatalog(Long categoryId, String search, String situationKey,
                                                 String sortBy, int page, int size) {
        Pageable pageable = createPageable(sortBy, page, size);
        LocalDateTime now = LocalDateTime.now();

        Page<CouponOffer> offers;

        if (search != null && !search.isBlank()) {
            offers = couponOfferRepository.searchPublicByTitleOrDescription(CouponStatus.ACTIVE, search, now, pageable);
        } else if (situationKey != null && !situationKey.isBlank()) {
            offers = couponOfferRepository.findPublicBySituationSlug(
                    CouponStatus.ACTIVE, situationKey.trim(), now, pageable);
        } else if (categoryId != null) {
            offers = couponOfferRepository.findPublicByStatusAndCategoryId(CouponStatus.ACTIVE, categoryId, now, pageable);
        } else {
            offers = couponOfferRepository.findPublicByStatus(CouponStatus.ACTIVE, now, pageable);
        }

        return offers.map(this::mapToResponse);
    }

    /**
     * Получает детальную информацию о купоне (публичный).
     * Инкрементит viewCount для аналитики.
     *
     * @param id идентификатор купона
     * @return полная информация с опциями и изображениями
     * @throws ResourceNotFoundException если купон не найден
     */
    @Transactional
    public CouponOfferResponse getById(Long id) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        if (offer.getStatus() != CouponStatus.ACTIVE) {
            throw new ResourceNotFoundException("Купон не найден");
        }

        // Hide coupons with expired purchase deadline from public view
        if (offer.getBuyUntil() != null && offer.getBuyUntil().isBefore(LocalDateTime.now())) {
            throw new ResourceNotFoundException("Купон не найден");
        }

        // Атомарный инкремент view count (без race condition)
        couponOfferRepository.incrementViewCount(id);

        return mapToResponse(offer);
    }

    /**
     * Получает snapshot для покупки (internal).
     * НЕ инкрементит viewCount — для валидации заказа.
     *
     * @param couponId ID купона
     * @param optionId ID опции
     * @return snapshot с каноническими данными
     */
    @Transactional(readOnly = true)
    public CouponPurchaseSnapshotResponse getPurchaseSnapshot(Long couponId, Long optionId) {
        CouponOffer offer = couponOfferRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        Merchant merchant = offer.getMerchant();
        MerchantLocation primaryLocation = merchant != null
                ? merchantLocationRepository.findByMerchantIdAndPrimaryTrue(merchant.getId()).orElse(null)
                : null;

        // optionId == 0 — фолбэк: купон без явных опций, покупка по базовой цене
        if (optionId == 0L || offer.getOptions().isEmpty()) {
            return CouponPurchaseSnapshotResponse.builder()
                    .couponOfferId(offer.getId())
                    .couponOptionId(0L)
                    .couponTitle(offer.getTitle())
                    .optionTitle(offer.getTitle())
                    .couponStatus(offer.getStatus().name())
                    .optionStatus("ACTIVE")
                    .couponPrice(offer.getFromPrice())
                    .quantityLimit(0)
                    .quantitySold(offer.getTotalSold())
                    .merchantId(merchant != null ? merchant.getId() : null)
                    .merchantName(merchant != null ? merchant.getName() : null)
                    .merchantAddress(primaryLocation != null ? primaryLocation.getAddress() : null)
                    .merchantPhone(primaryLocation != null ? primaryLocation.getPhone() : null)
                    .merchantWorkingHours(primaryLocation != null ? primaryLocation.getWorkingHours() : null)
                    .buyUntil(offer.getBuyUntil())
                    .useUntil(offer.getUseUntil())
                    .build();
        }

        CouponOption option = offer.getOptions().stream()
                .filter(opt -> opt.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Опция купона не найдена"));

        return CouponPurchaseSnapshotResponse.builder()
                .couponOfferId(offer.getId())
                .couponOptionId(option.getId())
                .couponTitle(offer.getTitle())
                .optionTitle(option.getTitle())
                .couponStatus(offer.getStatus().name())
                .optionStatus(option.getStatus().name())
                .couponPrice(option.getCouponPrice())
                .quantityLimit(option.getQuantityLimit())
                .quantitySold(option.getQuantitySold())
                .merchantId(merchant != null ? merchant.getId() : null)
                .merchantName(merchant != null ? merchant.getName() : null)
                .merchantAddress(primaryLocation != null ? primaryLocation.getAddress() : null)
                .merchantPhone(primaryLocation != null ? primaryLocation.getPhone() : null)
                .merchantWorkingHours(primaryLocation != null ? primaryLocation.getWorkingHours() : null)
                .buyUntil(offer.getBuyUntil())
                .useUntil(offer.getUseUntil())
                .build();
    }

    /**
     * Получает детальную информацию о купоне (Admin).
     * НЕ инкрементит viewCount — для внутреннего просмотра/редактирования.
     *
     * @param id идентификатор купона
     * @return полная информация с опциями и изображениями
     * @throws ResourceNotFoundException если купон не найден
     */
    @Transactional(readOnly = true)
    public CouponOfferResponse getByIdAdmin(Long id) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));
        return mapToResponse(offer);
    }

    /**
     * Получает топ продаваемых купонов.
     * Кэшируется в Redis (TTL: 15 мин).
     *
     * @param limit максимальное количество результатов
     * @return список топ купонов
     */
    // TODO: восстановить кэширование после настройки Redis serializer
    // @Cacheable(value = "topSelling", key = "#limit")
    @Transactional(readOnly = true)
    public List<CouponOfferResponse> getTopSelling(int limit) {
        return couponOfferRepository.findPublicTopSelling(LocalDateTime.now(), PageRequest.of(0, limit))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ==================== Admin API ====================

    /**
     * Создаёт новый купон (Admin) со статусом LEAD.
     * Сбрасывает Redis кэш каталога.
     *
     * @param request данные купона (title, описание, merchantId, опции)
     * @return созданный купон
     */
    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "topSelling", allEntries = true)
    })
    @Transactional
    public CouponOfferResponse create(CreateCouponOfferRequest request) {
        Merchant merchant = merchantRepository.findById(request.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Партнёр не найден"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Категория не найдена"));

        String offerDesc = request.getOfferDescription();

        CouponOffer offer = CouponOffer.builder()
                .title(request.getTitle())
                .offerDescription(offerDesc)
                // Legacy text fields no longer written — canonical offerDescription is source of truth
                .merchant(merchant)
                .category(category)
                .oldPrice(request.getOldPrice())
                .fromPrice(request.getFromPrice())
                .discountPercent(request.getDiscountPercent())
                .coverImageUrl(request.getCoverImageUrl())
                .buyUntil(request.getBuyUntil())
                .useUntil(request.getUseUntil())
                // Contact fields live in merchant_locations
                .giftAvailable(request.isGiftAvailable())
                .status(CouponStatus.LEAD)
                .totalSold(0)
                .viewCount(0)
                .build();

        offer = couponOfferRepository.save(offer);

        // Create options
        if (request.getOptions() != null) {
            for (CreateCouponOptionRequest optReq : request.getOptions()) {
                CouponOption option = CouponOption.builder()
                        .couponOffer(offer)
                        .title(optReq.getTitle())
                        .regularPrice(optReq.getRegularPrice())
                        .couponPrice(optReq.getCouponPrice())
                        .quantityLimit(optReq.getQuantityLimit())
                        .quantitySold(0)
                        .status(CouponOptionStatus.ACTIVE)
                        .build();
                couponOptionRepository.save(option);
            }
        }

        // Create gallery images
        if (request.getImages() != null) {
            for (int i = 0; i < request.getImages().size(); i++) {
                CouponImage image = CouponImage.builder()
                        .couponOffer(offer)
                        .imageUrl(request.getImages().get(i))
                        .sortOrder(i)
                        .build();
                couponImageRepository.save(image);
            }
        }

        // Flush + clear чтобы re-fetch вернул актуальные options/images
        entityManager.flush();
        entityManager.clear();

        return mapToResponse(couponOfferRepository.findById(offer.getId()).orElseThrow());
    }

    // ==================== State Machine ====================

    /**
     * Отправляет купон на согласование партнёру.
     * Допустимые текущие статусы: DRAFT, REVISION_REQUESTED.
     * Результат: статус → WAITING_FOR_MERCHANT.
     *
     * @param id идентификатор купона
     * @param currentUserId ID сотрудника, выполняющего переход
     * @param currentUserRole роль сотрудника для ownership-проверки
     * @return обновлённый купон
     */
    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "couponDetail", key = "#id")
    })
    @Transactional
    public CouponOfferResponse sendToApproval(Long id, Long currentUserId, String currentUserRole) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        if (offer.getStatus() != CouponStatus.DRAFT && offer.getStatus() != CouponStatus.REVISION_REQUESTED) {
            throw new IllegalStateException(
                    "Нельзя отправить на согласование из статуса " + offer.getStatus()
                    + ". Допустимые: DRAFT, REVISION_REQUESTED");
        }

        assertModeratorOwnership(offer, currentUserId, currentUserRole);

        // Ensure cover image is set before approval — apply category fallback if missing
        if (offer.getCoverImageUrl() == null || offer.getCoverImageUrl().isBlank()) {
            String categorySlug = offer.getCategory() != null ? offer.getCategory().getSlug() : null;
            String fallback = couponCoverFallbackService.getFallbackCover(categorySlug);
            offer.setCoverImageUrl(fallback);
            log.info("Купон #{}: применена fallback обложка '{}' (категория: {})", id, fallback, categorySlug);
        }

        offer.setStatus(CouponStatus.WAITING_FOR_MERCHANT);
        offer.setRevisionComment(null);
        couponOfferRepository.save(offer);

        log.info("Купон #{} отправлен на согласование мерчанту #{}", id, offer.getMerchant().getId());

        // Отправляем превью в Telegram мерчанту
        telegramPreviewService.sendPreview(offer);

        return mapToResponse(offer);
    }

    /**
     * Модератор берёт лид в работу.
     * Текущий статус должен быть LEAD.
     * Результат: статус → DRAFT, assignedModeratorId/Name записывается.
     *
     * @param id идентификатор купона
     * @param moderatorId ID модератора из X-User-Id
     * @param moderatorName имя/email модератора
     * @return обновлённый купон
     */
    @Transactional
    public CouponOfferResponse takeToWork(Long id, Long moderatorId, String moderatorName) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        if (offer.getStatus() != CouponStatus.LEAD) {
            throw new IllegalStateException(
                    "Взять в работу можно только из статуса LEAD. Текущий: " + offer.getStatus());
        }

        offer.setStatus(CouponStatus.DRAFT);
        offer.setAssignedModeratorId(moderatorId);
        offer.setAssignedModeratorName(moderatorName);
        couponOfferRepository.save(offer);

        log.info("Купон #{} взят в работу модератором {} ({})", id, moderatorName, moderatorId);

        // Отправляем пуш мерчанту
        if (offer.getMerchant() != null && offer.getMerchant().getTelegramChatId() != null) {
            telegramPreviewService.sendPushMessage(
                    offer.getMerchant().getTelegramChatId(),
                    "⚡️ Модератор взял вашу заявку в работу. Ожидайте звонка!"
            );
        }

        return mapToResponse(offer);
    }

    /**
     * Отклонить заявку партнёра на акцию.
     * Допустимые статусы: LEAD, DRAFT.
     * Результат: статус → ARCHIVED, archiveReason записывается.
     */
    @Transactional
    public CouponOfferResponse rejectPartnerRequest(Long id, String reason) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        if (offer.getStatus() != CouponStatus.LEAD && offer.getStatus() != CouponStatus.DRAFT) {
            throw new IllegalStateException(
                    "Отклонить можно только заявки в статусе LEAD или DRAFT. Текущий: " + offer.getStatus());
        }

        offer.setStatus(CouponStatus.ARCHIVED);
        offer.setArchiveReason(reason);
        offer.setArchivedAt(LocalDateTime.now());
        couponOfferRepository.save(offer);

        log.info("Заявка #{} отклонена. Причина: {}", id, reason);
        return mapToResponse(offer);
    }

    /**
     * Партнёр одобряет купон → публикация.
     * Текущий статус должен быть WAITING_FOR_MERCHANT.
     * Результат: статус → ACTIVE.
     *
     * @param id идентификатор купона
     * @return обновлённый купон
     */
    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "topSelling", allEntries = true),
            @CacheEvict(value = "couponDetail", key = "#id")
    })
    @Transactional
    public CouponOfferResponse approveByMerchant(Long id) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        if (offer.getStatus() != CouponStatus.WAITING_FOR_MERCHANT) {
            throw new IllegalStateException(
                    "Нельзя одобрить купон из статуса " + offer.getStatus()
                    + ". Допустимый: WAITING_FOR_MERCHANT");
        }

        ensureMerchantReadyForPublication(offer);
        offer.setStatus(CouponStatus.ACTIVE);
        couponOfferRepository.save(offer);

        log.info("Купон #{} одобрен мерчантом #{} и опубликован", id, offer.getMerchant().getId());

        // Отправляем пуш мерчанту
        if (offer.getMerchant() != null && offer.getMerchant().getTelegramChatId() != null) {
            // Считаем общий лимит по опциям
            int limit = offer.getOptions().stream()
                    .mapToInt(opt -> opt.getQuantityLimit() != null ? opt.getQuantityLimit() : 0)
                    .sum();
            telegramPreviewService.sendPushMessage(
                    offer.getMerchant().getTelegramChatId(),
                    "🎉 Ура! Предложение запущено. Установлен лимит: " + limit + " сертификатов. Следить за продажами можно в разделе «📊 Статистика»."
            );
        }

        return mapToResponse(offer);
    }

    /**
     * Партнёр запрашивает правки → возврат менеджеру.
     * Текущий статус должен быть WAITING_FOR_MERCHANT.
     * Результат: статус → REVISION_REQUESTED, записывается комментарий.
     *
     * @param id идентификатор купона
     * @param comment комментарий партнёра с описанием правок
     * @return обновлённый купон
     */
    @Caching(evict = {
            @CacheEvict(value = "couponDetail", key = "#id")
    })
    @Transactional
    public CouponOfferResponse requestRevisionByMerchant(Long id, String comment) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        if (offer.getStatus() != CouponStatus.WAITING_FOR_MERCHANT) {
            throw new IllegalStateException(
                    "Нельзя запросить правки из статуса " + offer.getStatus()
                    + ". Допустимый: WAITING_FOR_MERCHANT");
        }

        offer.setStatus(CouponStatus.REVISION_REQUESTED);
        offer.setRevisionComment(comment);
        couponOfferRepository.save(offer);

        log.info("Купон #{} возвращён на доработку мерчантом #{}. Причина: {}",
                id, offer.getMerchant().getId(), comment);

        return mapToResponse(offer);
    }

    /**
     * Обновляет существующий купон (Admin).
     * Разрешено из статусов DRAFT, REVISION_REQUESTED.
     * ACTIVE, SOLD_OUT, ARCHIVED — immutable в MVP.
     * Модераторы могут редактировать только свои купоны (assignedModeratorId).
     * ADMIN/SUPER_ADMIN могут редактировать любые.
     *
     * @param id идентификатор купона
     * @param request обновлённые данные
     * @param currentUserId ID текущего пользователя (для ownership check)
     * @param currentUserRole роль текущего пользователя
     * @return обновлённый купон
     * @throws IllegalStateException если купон в неред. статусе или чужой
     */
    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "topSelling", allEntries = true),
            @CacheEvict(value = "couponDetail", key = "#id")
    })
    @Transactional
    public CouponOfferResponse update(Long id, CreateCouponOfferRequest request, Long currentUserId, String currentUserRole) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        // State Machine: редактирование только из DRAFT, REVISION_REQUESTED
        if (offer.getStatus() != CouponStatus.DRAFT
                && offer.getStatus() != CouponStatus.REVISION_REQUESTED) {
            throw new IllegalStateException(
                    "Редактирование запрещено из статуса " + offer.getStatus()
                    + ". Допустимые: DRAFT, REVISION_REQUESTED");
        }

        // Ownership check: MODERATOR может редактировать только свои
        assertModeratorOwnership(offer, currentUserId, currentUserRole);

        // Обновляем мерчанта
        // merchant_id обязательно (NOT NULL) — обновляем если передан, иначе оставляем текущего
        if (request.getMerchantId() != null) {
            Merchant merchant = merchantRepository.findById(request.getMerchantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Партнёр не найден"));
            offer.setMerchant(merchant);
        }

        // Обновляем категорию
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Категория не найдена"));
        offer.setCategory(category);

        String offerDesc = request.getOfferDescription();

        // Обновляем скалярные поля
        offer.setTitle(request.getTitle());
        offer.setOfferDescription(offerDesc);
        // Legacy text fields no longer written — canonical offerDescription is source of truth
        offer.setOldPrice(request.getOldPrice());
        offer.setFromPrice(request.getFromPrice());
        offer.setDiscountPercent(request.getDiscountPercent());
        offer.setCoverImageUrl(request.getCoverImageUrl());
        offer.setBuyUntil(request.getBuyUntil());
        offer.setUseUntil(request.getUseUntil());
        // Contact fields live in merchant_locations
        offer.setGiftAvailable(request.isGiftAvailable());

        // Мержим Options
        if (request.getOptions() != null) {
            var requestTitles = request.getOptions().stream()
                    .map(CreateCouponOptionRequest::getTitle)
                    .collect(Collectors.toSet());

            // Деактивируем варианты, которых нет в запросе
            for (CouponOption existing : offer.getOptions()) {
                if (!requestTitles.contains(existing.getTitle())) {
                    existing.setStatus(CouponOptionStatus.DISABLED);
                    couponOptionRepository.save(existing);
                }
            }

            // Обновляем существующие / добавляем новые
            for (CreateCouponOptionRequest optReq : request.getOptions()) {
                CouponOption existingOpt = offer.getOptions().stream()
                        .filter(o -> o.getTitle().equals(optReq.getTitle()))
                        .findFirst()
                        .orElse(null);

                if (existingOpt != null) {
                    existingOpt.setRegularPrice(optReq.getRegularPrice());
                    existingOpt.setCouponPrice(optReq.getCouponPrice());
                    existingOpt.setQuantityLimit(optReq.getQuantityLimit());
                    existingOpt.setStatus(CouponOptionStatus.ACTIVE);
                    couponOptionRepository.save(existingOpt);
                } else {
                    CouponOption newOpt = CouponOption.builder()
                            .couponOffer(offer)
                            .title(optReq.getTitle())
                            .regularPrice(optReq.getRegularPrice())
                            .couponPrice(optReq.getCouponPrice())
                            .quantityLimit(optReq.getQuantityLimit())
                            .quantitySold(0)
                            .status(CouponOptionStatus.ACTIVE)
                            .build();
                    couponOptionRepository.save(newOpt);
                }
            }
        }

        // Обновляем галерею изображений: удаляем старые, добавляем новые
        couponImageRepository.deleteAllByCouponOfferId(offer.getId());
        offer.getImages().clear();
        if (request.getImages() != null) {
            for (int i = 0; i < request.getImages().size(); i++) {
                CouponImage image = CouponImage.builder()
                        .couponOffer(offer)
                        .imageUrl(request.getImages().get(i))
                        .sortOrder(i)
                        .build();
                couponImageRepository.save(image);
            }
        }

        return mapToResponse(couponOfferRepository.findById(offer.getId()).orElseThrow());
    }

    private void assertModeratorOwnership(
            CouponOffer offer,
            Long currentUserId,
            String currentUserRole
    ) {
        if (!"MODERATOR".equals(currentUserRole)) {
            return;
        }

        if (offer.getAssignedModeratorId() == null) {
            throw new IllegalStateException("Купон не закреплён за текущим модератором");
        }
        if (!offer.getAssignedModeratorId().equals(currentUserId)) {
            throw new IllegalStateException(
                    "Купон закреплён за другим модератором ("
                    + offer.getAssignedModeratorName() + ")");
        }
    }

    /**
     * Управляет публикацией купона (Admin) через узкий generic endpoint.
     * Допустимы только ACTIVE→PAUSED и PAUSED→ACTIVE; workflow-переходы принадлежат
     * специализированным take/send/partner/bot/support операциям.
     *
     * @param id идентификатор купона
     * @param newStatus новый статус
     * @return обновлённый купон
     * @throws IllegalStateException если переход запрещён
     */
    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "topSelling", allEntries = true),
            @CacheEvict(value = "couponDetail", key = "#id")
    })
    @Transactional
    public CouponOfferResponse updateStatus(Long id, CouponStatus newStatus) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        CouponStatus current = offer.getStatus();
        boolean allowed = (current == CouponStatus.ACTIVE && newStatus == CouponStatus.PAUSED)
                || (current == CouponStatus.PAUSED && newStatus == CouponStatus.ACTIVE);

        if (!allowed) {
            throw new IllegalStateException(
                    "Переход " + current + " → " + newStatus + " запрещён. "
                    + "Generic status endpoint допускает только ACTIVE→PAUSED и PAUSED→ACTIVE. "
                    + "Для остальных переходов используйте специализированный endpoint");
        }

        if (newStatus == CouponStatus.ACTIVE) {
            ensureMerchantReadyForPublication(offer);
        }

        offer.setStatus(newStatus);
        return mapToResponse(couponOfferRepository.save(offer));
    }

    private void ensureMerchantReadyForPublication(CouponOffer offer) {
        Merchant merchant = offer.getMerchant();
        if (merchant == null) {
            throw new IllegalStateException("Нельзя публиковать купон без мерчанта");
        }

        MerchantLocation primaryLocation = merchantLocationRepository.findByMerchantIdAndPrimaryTrue(merchant.getId())
                .filter(MerchantLocation::isActive)
                .orElseThrow(() -> new IllegalStateException(
                        "Нельзя публиковать купон без active primary location у мерчанта"));

        if (primaryLocation.getAddress() == null || primaryLocation.getAddress().isBlank()) {
            throw new IllegalStateException(
                    "Нельзя публиковать купон без адреса в primary location мерчанта");
        }
    }

    /**
     * Архивирует опубликованный, приостановленный или распроданный купон.
     * Останавливает будущие продажи, но не меняет уже купленные купоны.
     */
    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "topSelling", allEntries = true),
            @CacheEvict(value = "couponDetail", key = "#id")
    })
    @Transactional
    public CouponOfferResponse archive(Long id, String reason) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isBlank()) {
            throw new IllegalArgumentException("Причина архивирования обязательна");
        }

        if (offer.getStatus() != CouponStatus.ACTIVE
                && offer.getStatus() != CouponStatus.PAUSED
                && offer.getStatus() != CouponStatus.SOLD_OUT) {
            throw new IllegalStateException(
                    "Архивирование запрещено из статуса " + offer.getStatus()
                    + ". Допустимые: ACTIVE, PAUSED, SOLD_OUT");
        }

        offer.setStatus(CouponStatus.ARCHIVED);
        offer.setArchiveReason(trimmedReason);
        offer.setArchivedAt(LocalDateTime.now());

        log.info("Купон #{} архивирован. Причина: {}", id, trimmedReason);
        return mapToResponse(couponOfferRepository.save(offer));
    }

    /**
     * Удаляет купон (Admin).
     * Разрешено только из статусов LEAD, DRAFT, REVISION_REQUESTED.
     * ACTIVE, WAITING_FOR_MERCHANT, SOLD_OUT, ARCHIVED защищены от удаления.
     * Сбрасывает Redis кэш.
     *
     * @param id идентификатор купона
     * @throws ResourceNotFoundException если купон не найден
     * @throws IllegalStateException если купон в защищённом статусе
     */
    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "topSelling", allEntries = true),
            @CacheEvict(value = "couponDetail", key = "#id")
    })
    @Transactional
    public void delete(Long id) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        if (offer.getStatus() != CouponStatus.LEAD
                && offer.getStatus() != CouponStatus.DRAFT
                && offer.getStatus() != CouponStatus.REVISION_REQUESTED) {
            throw new IllegalStateException(
                    "Удаление запрещено из статуса " + offer.getStatus()
                    + ". Допустимые для удаления: LEAD, DRAFT, REVISION_REQUESTED");
        }

        couponOfferRepository.deleteById(id);
    }

    // ==================== Admin: All offers ====================

    @Transactional(readOnly = true)
    public Page<CouponOfferResponse> getAllForAdmin(CouponStatus status, int page, int size) {
        Set<CouponStatus> statuses = status == null ? Set.of() : Set.of(status);
        return getAllForAdmin(new AdminCouponFilter(statuses, null, null, null), page, size);
    }

    @Transactional(readOnly = true)
    public Page<CouponOfferResponse> getAllForAdmin(
            AdminCouponFilter filter,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return couponOfferRepository.findAll(CouponOfferSpecifications.forAdmin(filter), pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<CouponAssigneeResponse> getCouponAssignees() {
        return couponOfferRepository.findDistinctAssignees();
    }

    /**
     * Возвращает купоны мерчанта для admin detail page.
     * Проверяет существование мерчанта.
     */
    @Transactional(readOnly = true)
    public Page<CouponOfferResponse> getMerchantCouponsForAdmin(Long merchantId, CouponStatus status, int page, int size) {
        if (!merchantRepository.existsById(merchantId)) {
            throw new ResourceNotFoundException("Мерчант не найден");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (status != null) {
            return couponOfferRepository.findByMerchantIdAndStatus(merchantId, status, pageable).map(this::mapToResponse);
        }
        return couponOfferRepository.findByMerchantId(merchantId, pageable).map(this::mapToResponse);
    }

    // ==================== Mapping ====================

    public CouponOfferResponse mapToResponse(CouponOffer offer) {
        // Resolve merchant primary location for contacts
        MerchantLocationResponse primaryLoc = null;
        if (offer.getMerchant() != null) {
            primaryLoc = merchantLocationRepository
                    .findByMerchantIdAndPrimaryTrue(offer.getMerchant().getId())
                    .map(loc -> MerchantLocationResponse.builder()
                            .id(loc.getId())
                            .title(loc.getTitle())
                            .address(loc.getAddress())
                            .phone(loc.getPhone())
                            .workingHours(loc.getWorkingHours())
                            .latitude(loc.getLatitude())
                            .longitude(loc.getLongitude())
                            .primary(loc.isPrimary())
                            .active(loc.isActive())
                            .build())
                    .orElse(null);
        }

        String offerDesc = offer.getOfferDescription();

        return CouponOfferResponse.builder()
                .id(offer.getId())
                .title(offer.getTitle())
                .offerDescription(offerDesc)
                .merchant(offer.getMerchant() != null ? CouponOfferResponse.MerchantSummary.builder()
                        .id(offer.getMerchant().getId())
                        .name(offer.getMerchant().getName())
                        .logoUrl(offer.getMerchant().getLogoUrl())
                        .description(offer.getMerchant().getDescription())
                        .primaryLocation(primaryLoc)
                        .build() : null)
                .category(offer.getCategory() != null ? CouponOfferResponse.CategorySummary.builder()
                        .id(offer.getCategory().getId())
                        .name(offer.getCategory().getName())
                        .slug(offer.getCategory().getSlug())
                        .iconUrl(offer.getCategory().getIconUrl())
                        .build() : null)
                .oldPrice(offer.getOldPrice())
                .fromPrice(offer.getFromPrice())
                .discountPercent(offer.getDiscountPercent())
                .coverImageUrl(offer.getCoverImageUrl())
                .buyUntil(offer.getBuyUntil())
                .useUntil(offer.getUseUntil())
                .giftAvailable(offer.isGiftAvailable())
                .status(offer.getStatus().name())
                .assignedModeratorId(offer.getAssignedModeratorId())
                .assignedModeratorName(offer.getAssignedModeratorName())
                .revisionComment(offer.getRevisionComment())
                .archiveReason(offer.getArchiveReason())
                .archivedAt(offer.getArchivedAt())
                .totalSold(offer.getTotalSold())
                .redeemedCount(offer.getRedeemedCount())
                .totalTurnover(offer.getTotalTurnover())
                .viewCount(offer.getViewCount())
                .averageRating(reviewRepository.getAverageRatingByCouponId(offer.getId()))
                .reviewCount(reviewRepository.countApprovedByCouponId(offer.getId()))
                .options(offer.getOptions().stream()
                        .map(opt -> CouponOptionResponse.builder()
                                .id(opt.getId())
                                .title(opt.getTitle())
                                .regularPrice(opt.getRegularPrice())
                                .couponPrice(opt.getCouponPrice())
                                .quantityLimit(opt.getQuantityLimit())
                                .quantitySold(opt.getQuantitySold())
                                .status(opt.getStatus().name())
                                .build())
                        .collect(Collectors.toList()))
                .images(offer.getImages().stream()
                        .map(CouponImage::getImageUrl)
                        .collect(Collectors.toList()))
                .createdAt(offer.getCreatedAt())
                .build();
    }

    private Pageable createPageable(String sortBy, int page, int size) {
        Sort sort = switch (sortBy != null ? sortBy : "popular") {
            case "new" -> Sort.by("createdAt").descending();
            case "price_asc" -> Sort.by("fromPrice").ascending();
            case "price_desc" -> Sort.by("fromPrice").descending();
            case "discount" -> Sort.by("discountPercent").descending();
            default -> Sort.by("totalSold").descending();
        };
        return PageRequest.of(page, size, sort);
    }

    @Transactional
    public void createLeadFromBot(BotLeadRequest request) {
        Merchant merchant = null;

        // Step 1: lookup by telegramChatId
        if (request.getTelegramChatId() != null && !request.getTelegramChatId().isEmpty()) {
            merchant = merchantRepository.findByTelegramChatId(request.getTelegramChatId()).orElse(null);
        }

        // Step 2: lookup by phone in merchant_locations (canonical — only source of phone data)
        String normalizedPhone = normalize(request.getPhone());
        if (merchant == null && normalizedPhone != null) {
            merchant = merchantLocationRepository.findFirstByPhoneAndActiveTrue(normalizedPhone)
                    .map(MerchantLocation::getMerchant)
                    .orElse(null);
        }

        // Step 3: lookup by exact name (only if unique match)
        if (merchant == null && request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            String name = request.getCompanyName().trim();
            long nameCount = merchantRepository.countByNameIgnoreCase(name);
            if (nameCount == 1) {
                merchant = merchantRepository.findFirstByNameIgnoreCase(name).orElse(null);
            } else if (nameCount > 1) {
                log.warn("Bot lead: ambiguous name match for '{}' ({} merchants found), creating new merchant lead",
                        name, nameCount);
            }
        }

        // Create new merchant if not found
        if (merchant == null) {
            merchant = Merchant.builder()
                    .name(request.getCompanyName() != null ? request.getCompanyName() : "Unknown Lead")
                    // phone now lives in merchant_locations (created below)
                    .contactPerson((request.getFirstName() != null ? request.getFirstName() : "") + " " +
                                   (request.getLastName() != null ? request.getLastName() : ""))
                    .telegramChatId(request.getTelegramChatId())
                    .website(request.getSourceLink())
                    .active(false)
                    .build();
            merchant = merchantRepository.save(merchant);

            // Create primary location if contact data is present
            boolean hasContactData = normalizedPhone != null
                    || (request.getCompanyName() != null && !request.getCompanyName().isBlank());
            if (hasContactData) {
                MerchantLocation primaryLoc = MerchantLocation.builder()
                        .merchant(merchant)
                        .title("Основной адрес")
                        .phone(normalizedPhone)
                        .primary(true)
                        .active(true)
                        .build();
                merchantLocationRepository.save(primaryLoc);
            }
        }

        String fullDesc = request.getPromoDescription() != null ? request.getPromoDescription() : "";
        if (request.getVoiceFileId() != null && !request.getVoiceFileId().isEmpty()) {
            fullDesc = fullDesc + "\n\n[TG Voice File ID]: " + request.getVoiceFileId();
        }

        CouponOffer lead = CouponOffer.builder()
                .title("Лид от: " + merchant.getName())
                .offerDescription(fullDesc)
                // Legacy fields no longer written — phone lives in merchant_locations
                .merchant(merchant)
                .status(CouponStatus.LEAD)
                .build();
        
        couponOfferRepository.save(lead);
    }


    // ==================== Bot API (Stat & List) ====================

    @Transactional(readOnly = true)
    public List<CouponOfferResponse> getMyCouponsByTelegramId(String chatId) {
        return merchantRepository.findByTelegramChatId(chatId)
                .map(merchant -> couponOfferRepository.findByMerchantId(
                        merchant.getId(),
                        PageRequest.of(0, 50, Sort.by("createdAt").descending()))
                        .stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public CouponStatsResponse getCouponStats(Long id) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        int totalLimit = offer.getOptions().stream()
                .mapToInt(opt -> opt.getQuantityLimit() != null ? opt.getQuantityLimit() : 0)
                .sum();

        return CouponStatsResponse.builder()
                .id(offer.getId())
                .title(offer.getTitle())
                .status(offer.getStatus().name())
                .quantityLimit(totalLimit)
                .viewCount(offer.getViewCount())
                .totalSold(offer.getTotalSold())
                .redeemedCount(offer.getRedeemedCount())
                .totalTurnover(offer.getTotalTurnover())
                .averageRating(reviewRepository.getAverageRatingByCouponId(offer.getId()))
                .reviewCount(reviewRepository.countApprovedByCouponId(offer.getId()))
                .build();
    }

    /**
     * Регистрирует продажу купона.
     * Использует atomic SQL для предотвращения oversell при конкурентных вызовах.
     * Если лимит распродан, автоматически останавливает публикацию.
     */
    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "topSelling", allEntries = true),
            @CacheEvict(value = "couponDetail", key = "#id")
    })
    @Transactional
    public void registerSale(Long id, Long optionId, int quantity, BigDecimal amount) {
        // Atomic increment option quantitySold с проверкой лимита
        int updated = couponOptionRepository.atomicIncrementSold(optionId, quantity);
        if (updated == 0) {
            throw new IllegalStateException("Купон распродан — лимит исчерпан для опции #" + optionId);
        }

        // Atomic increment offer totalSold и totalTurnover
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        couponOfferRepository.atomicIncrementSoldAndTurnover(id, quantity, safeAmount);

        // Перечитываем offer из БД после atomic updates для проверки SOLD_OUT
        checkAndSetSoldOut(id);
    }

    /**
     * Идемпотентная регистрация продажи купона.
     * Использует coupon_sales ledger для предотвращения дублирования.
     * Если (orderId, couponId, optionId) уже зарегистрирован — пропускает без инкремента.
     *
     * @param orderId ID заказа
     * @param couponId ID купонного предложения
     * @param optionId ID опции купона
     * @param quantity количество проданных единиц
     * @param amount сумма продажи
     */
    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "topSelling", allEntries = true),
            @CacheEvict(value = "couponDetail", key = "#couponId")
    })
    @Transactional
    public void registerSaleOnce(Long orderId, Long couponId, Long optionId, int quantity, BigDecimal amount) {
        // Idempotency check
        if (couponSaleRepository.findByOrderIdAndCouponOfferIdAndCouponOptionId(orderId, couponId, optionId).isPresent()) {
            log.info("Продажа для заказа {} купона {} опции {} уже зарегистрирована, пропускаем", orderId, couponId, optionId);
            return;
        }

        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;

        // Записываем в ledger
        CouponSale sale = CouponSale.builder()
                .orderId(orderId)
                .couponOfferId(couponId)
                .couponOptionId(optionId)
                .quantity(quantity)
                .amount(safeAmount)
                .build();
        couponSaleRepository.save(sale);

        // Atomic increment option quantitySold с проверкой лимита
        if (optionId != 0L) {
            int updated = couponOptionRepository.atomicIncrementSold(optionId, quantity);
            if (updated == 0) {
                log.warn("Лимит исчерпан для опции #{} при регистрации продажи заказа #{}", optionId, orderId);
                throw new IllegalStateException("Купон распродан — лимит исчерпан для опции #" + optionId);
            }
        }

        // Atomic increment offer totalSold и totalTurnover
        couponOfferRepository.atomicIncrementSoldAndTurnover(couponId, quantity, safeAmount);

        // Перечитываем offer из БД после atomic updates для проверки SOLD_OUT
        checkAndSetSoldOut(couponId);

        log.info("Зарегистрирована продажа: orderId={}, couponId={}, optionId={}, qty={}, amount={}",
                orderId, couponId, optionId, quantity, amount);
    }

    /**
     * Идемпотентный инкремент redeemedCount по событию погашения.
     * Использует coupon_redemption_ledger для предотвращения дублирования.
     *
     * @param purchasedCouponId ID купленного купона (уникальный ключ идемпотентности)
     * @param couponOfferId ID купонного предложения
     * @param couponOptionId ID опции купона
     * @param merchantId ID мерчанта
     */
    @Transactional
    public void incrementRedeemedOnce(Long purchasedCouponId, Long couponOfferId, Long couponOptionId, Long merchantId) {
        if (couponRedemptionLedgerRepository.findByPurchasedCouponId(purchasedCouponId).isPresent()) {
            log.info("Погашение purchasedCouponId={} уже учтено, пропускаем", purchasedCouponId);
            return;
        }

        CouponOffer offer = couponOfferRepository.findById(couponOfferId)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        // Record in ledger
        CouponRedemptionLedger ledger = CouponRedemptionLedger.builder()
                .purchasedCouponId(purchasedCouponId)
                .couponOfferId(couponOfferId)
                .couponOptionId(couponOptionId)
                .merchantId(merchantId)
                .build();
        couponRedemptionLedgerRepository.save(ledger);

        // Increment counter
        offer.setRedeemedCount(offer.getRedeemedCount() + 1);
        couponOfferRepository.save(offer);

        log.info("Инкремент redeemedCount для couponOfferId={}, purchasedCouponId={}, new count={}",
                couponOfferId, purchasedCouponId, offer.getRedeemedCount());
    }

    // ==================== Stock/SOLD_OUT ====================

    /**
     * Перечитывает offer из БД после atomic updates и проверяет,
     * не исчерпан ли общий лимит. Если да — переводит в SOLD_OUT.
     */
    private void checkAndSetSoldOut(Long couponId) {
        CouponOffer offer = couponOfferRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        int totalSold = offer.getTotalSold();
        int totalLimit = offer.getOptions().stream()
                .mapToInt(opt -> opt.getQuantityLimit() != null ? opt.getQuantityLimit() : 0)
                .sum();

        if (totalLimit > 0 && totalSold >= totalLimit && offer.getStatus() == CouponStatus.ACTIVE) {
            offer.setStatus(CouponStatus.SOLD_OUT);
            couponOfferRepository.save(offer);
            log.info("Купон #{} автоматически переведен в SOLD_OUT", couponId);

            if (offer.getMerchant() != null && offer.getMerchant().getTelegramChatId() != null) {
                telegramPreviewService.sendPushMessage(
                        offer.getMerchant().getTelegramChatId(),
                        "🛑 Сертификаты по предложению распроданы! Публикация автоматически приостановлена. " +
                        "Чтобы запустить новое, подайте купонное предложение."
                );
            }
        }
    }

    // ==================== Helpers ====================

    /**
     * Internal preview helper used by Telegram preview payloads.
     */
    static String derivePreview(String offerDescription, int maxLength) {
        if (offerDescription == null || offerDescription.isBlank()) return null;
        String firstLine = offerDescription.lines()
                .filter(line -> !line.isBlank() && !line.startsWith("##"))
                .findFirst()
                .orElse(offerDescription.substring(0, Math.min(offerDescription.length(), maxLength)));
        firstLine = firstLine.trim();
        if (firstLine.length() > maxLength) {
            firstLine = firstLine.substring(0, maxLength - 1).trim() + "…";
        }
        return firstLine;
    }

}
