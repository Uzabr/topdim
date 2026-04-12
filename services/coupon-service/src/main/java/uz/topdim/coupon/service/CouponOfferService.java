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

import java.util.List;
import java.util.stream.Collectors;

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
    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;
    private final EntityManager entityManager;

    // ==================== Public API ====================

    /**
     * Получает каталог купонов с фильтрацией, поиском и пагинацией.
     * Результат кэшируется в Redis (TTL: 3 мин).
     *
     * @param categoryId фильтр по категории (null = все)
     * @param search поисковый запрос (null = без поиска)
     * @param sortBy сортировка: popular, new, priceAsc, priceDesc, discount
     * @param page номер страницы (0-based)
     * @param size размер страницы
     * @return страница купонов
     */
    // TODO: восстановить кэширование после настройки Redis serializer
    // @Cacheable(value = "catalog", key = "#categoryId + '-' + #search + '-' + #sortBy + '-' + #page + '-' + #size")
    @Transactional(readOnly = true)
    public Page<CouponOfferResponse> getCatalog(Long categoryId, String search, String sortBy, int page, int size) {
        Pageable pageable = createPageable(sortBy, page, size);

        Page<CouponOffer> offers;

        if (search != null && !search.isBlank()) {
            offers = couponOfferRepository.searchByTitleOrDescription(CouponStatus.ACTIVE, search, pageable);
        } else if (categoryId != null) {
            offers = couponOfferRepository.findByStatusAndCategoryId(CouponStatus.ACTIVE, categoryId, pageable);
        } else {
            offers = couponOfferRepository.findByStatus(CouponStatus.ACTIVE, pageable);
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

        // Атомарный инкремент view count (без race condition)
        couponOfferRepository.incrementViewCount(id);

        return mapToResponse(offer);
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
        return couponOfferRepository.findTopSelling(PageRequest.of(0, limit))
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

        CouponOffer offer = CouponOffer.builder()
                .title(request.getTitle())
                .shortDescription(request.getShortDescription())
                .fullDescription(request.getFullDescription())
                .merchant(merchant)
                .category(category)
                .oldPrice(request.getOldPrice())
                .fromPrice(request.getFromPrice())
                .discountPercent(request.getDiscountPercent())
                .coverImageUrl(request.getCoverImageUrl())
                .buyUntil(request.getBuyUntil())
                .useUntil(request.getUseUntil())
                .terms(request.getTerms())
                .usageRules(request.getUsageRules())
                .howToUse(request.getHowToUse())
                .address(request.getAddress())
                .contactPhone(request.getContactPhone())
                .workingHours(request.getWorkingHours())
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
     * @return обновлённый купон
     */
    @Caching(evict = {
            @CacheEvict(value = "catalog", allEntries = true),
            @CacheEvict(value = "couponDetail", key = "#id")
    })
    @Transactional
    public CouponOfferResponse sendToApproval(Long id) {
        CouponOffer offer = couponOfferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        if (offer.getStatus() != CouponStatus.DRAFT && offer.getStatus() != CouponStatus.REVISION_REQUESTED) {
            throw new IllegalStateException(
                    "Нельзя отправить на согласование из статуса " + offer.getStatus()
                    + ". Допустимые: DRAFT, REVISION_REQUESTED");
        }

        offer.setStatus(CouponStatus.WAITING_FOR_MERCHANT);
        offer.setRevisionComment(null);
        couponOfferRepository.save(offer);

        log.info("Купон #{} отправлен на согласование мерчанту #{}", id, offer.getMerchant().getId());

        // TODO: Здесь будет вызов сервиса отправки сообщения в Telegram

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

        offer.setStatus(CouponStatus.ACTIVE);
        couponOfferRepository.save(offer);

        log.info("Купон #{} одобрен мерчантом #{} и опубликован", id, offer.getMerchant().getId());

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
     * Разрешено из статусов DRAFT, REVISION_REQUESTED, ACTIVE.
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

        // State Machine: редактирование из DRAFT, REVISION_REQUESTED, ACTIVE
        if (offer.getStatus() != CouponStatus.DRAFT
                && offer.getStatus() != CouponStatus.REVISION_REQUESTED
                && offer.getStatus() != CouponStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Редактирование запрещено из статуса " + offer.getStatus()
                    + ". Допустимые: DRAFT, REVISION_REQUESTED, ACTIVE");
        }

        // Ownership check: MODERATOR может редактировать только свои
        if ("MODERATOR".equals(currentUserRole)
                && offer.getAssignedModeratorId() != null
                && !offer.getAssignedModeratorId().equals(currentUserId)) {
            throw new IllegalStateException(
                    "Купон закреплён за другим модератором (" + offer.getAssignedModeratorName() + ")");
        }

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

        // Обновляем скалярные поля
        offer.setTitle(request.getTitle());
        offer.setShortDescription(request.getShortDescription());
        offer.setFullDescription(request.getFullDescription());
        offer.setOldPrice(request.getOldPrice());
        offer.setFromPrice(request.getFromPrice());
        offer.setDiscountPercent(request.getDiscountPercent());
        offer.setCoverImageUrl(request.getCoverImageUrl());
        offer.setBuyUntil(request.getBuyUntil());
        offer.setUseUntil(request.getUseUntil());
        offer.setTerms(request.getTerms());
        offer.setUsageRules(request.getUsageRules());
        offer.setHowToUse(request.getHowToUse());
        offer.setAddress(request.getAddress());
        offer.setContactPhone(request.getContactPhone());
        offer.setWorkingHours(request.getWorkingHours());
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

    /**
     * Обновляет статус купона (Admin) — только разрешённые переходы State Machine.
     * Допустимые: LEAD→DRAFT, DRAFT/REVISION→WAITING, WAITING→ACTIVE/REVISION.
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
        boolean allowed = switch (newStatus) {
            case DRAFT -> current == CouponStatus.LEAD;
            case WAITING_FOR_MERCHANT -> current == CouponStatus.DRAFT
                    || current == CouponStatus.REVISION_REQUESTED;
            case ACTIVE -> current == CouponStatus.WAITING_FOR_MERCHANT;
            case REVISION_REQUESTED -> current == CouponStatus.WAITING_FOR_MERCHANT;
            case LEAD -> false;
        };

        if (!allowed) {
            throw new IllegalStateException(
                    "Переход " + current + " → " + newStatus + " запрещён. "
                    + "Допустимые: LEAD→DRAFT, DRAFT/REVISION→WAITING, WAITING→ACTIVE/REVISION");
        }

        offer.setStatus(newStatus);
        return mapToResponse(couponOfferRepository.save(offer));
    }

    /**
     * Удаляет купон (Admin).
     * Разрешено только из статусов LEAD, DRAFT, REVISION_REQUESTED.
     * ACTIVE и WAITING_FOR_MERCHANT защищены от удаления.
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

        if (offer.getStatus() == CouponStatus.ACTIVE || offer.getStatus() == CouponStatus.WAITING_FOR_MERCHANT) {
            throw new IllegalStateException(
                    "Удаление запрещено из статуса " + offer.getStatus()
                    + ". Допустимые для удаления: LEAD, DRAFT, REVISION_REQUESTED");
        }

        couponOfferRepository.deleteById(id);
    }

    // ==================== Admin: All offers ====================

    @Transactional(readOnly = true)
    public Page<CouponOfferResponse> getAllForAdmin(CouponStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (status != null) {
            return couponOfferRepository.findAllByStatus(status, pageable).map(this::mapToResponse);
        }
        return couponOfferRepository.findAll(pageable).map(this::mapToResponse);
    }

    // ==================== Mapping ====================

    public CouponOfferResponse mapToResponse(CouponOffer offer) {
        return CouponOfferResponse.builder()
                .id(offer.getId())
                .title(offer.getTitle())
                .shortDescription(offer.getShortDescription())
                .fullDescription(offer.getFullDescription())
                .merchant(offer.getMerchant() != null ? CouponOfferResponse.MerchantSummary.builder()
                        .id(offer.getMerchant().getId())
                        .name(offer.getMerchant().getName())
                        .logoUrl(offer.getMerchant().getLogoUrl())
                        .build() : null)
                .category(CouponOfferResponse.CategorySummary.builder()
                        .id(offer.getCategory().getId())
                        .name(offer.getCategory().getName())
                        .slug(offer.getCategory().getSlug())
                        .iconUrl(offer.getCategory().getIconUrl())
                        .build())
                .oldPrice(offer.getOldPrice())
                .fromPrice(offer.getFromPrice())
                .discountPercent(offer.getDiscountPercent())
                .coverImageUrl(offer.getCoverImageUrl())
                .buyUntil(offer.getBuyUntil())
                .useUntil(offer.getUseUntil())
                .terms(offer.getTerms())
                .usageRules(offer.getUsageRules())
                .howToUse(offer.getHowToUse())
                .address(offer.getAddress())
                .contactPhone(offer.getContactPhone())
                .workingHours(offer.getWorkingHours())
                .giftAvailable(offer.isGiftAvailable())
                .status(offer.getStatus().name())
                .assignedModeratorId(offer.getAssignedModeratorId())
                .assignedModeratorName(offer.getAssignedModeratorName())
                .revisionComment(offer.getRevisionComment())
                .totalSold(offer.getTotalSold())
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
}
