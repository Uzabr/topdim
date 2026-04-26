package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static uz.topdim.coupon.util.PhoneUtils.normalize;

/**
 * Сервис управления партнёрами (merchants) и их локациями.
 * CRUD операции, получение категорий.
 * Кэширование списка категорий.
 */
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantLocationRepository merchantLocationRepository;
    private final CategoryRepository categoryRepository;
    private final CouponOfferRepository couponOfferRepository;

    // ==================== Internal Context ====================

    /**
     * Возвращает контекст мерчанта по userId (для межсервисных вызовов).
     */
    @Transactional(readOnly = true)
    public MerchantContextResponse getMerchantContextByUserId(Long userId) {
        Merchant merchant = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Мерчант для пользователя не найден"));

        return MerchantContextResponse.builder()
                .merchantId(merchant.getId())
                .userId(merchant.getUserId())
                .name(merchant.getName())
                .active(merchant.isActive())
                .build();
    }

    /**
     * Создаёт мерчанта при partner onboarding (idempotent by userId).
     */
    @CacheEvict(value = "catalog", allEntries = true)
    @Transactional
    public MerchantResponse createFromOnboarding(CreateMerchantOnboardingRequest request) {
        Merchant existing = merchantRepository.findByUserId(request.getUserId()).orElse(null);
        if (existing != null) {
            return mapMerchant(existing);
        }

        Merchant merchant = Merchant.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .email(request.getEmail())
                .website(request.getWebsite())
                .contactPerson(request.getContactPerson())
                .userId(request.getUserId())
                .active(true)
                .build();
        merchant = merchantRepository.save(merchant);

        MerchantLocation location = MerchantLocation.builder()
                .merchant(merchant)
                .title(request.getLocation().getTitle())
                .address(request.getLocation().getAddress())
                .phone(normalize(request.getLocation().getPhone()))
                .workingHours(request.getLocation().getWorkingHours())
                .latitude(request.getLocation().getLatitude())
                .longitude(request.getLocation().getLongitude())
                .primary(true)
                .active(true)
                .build();
        merchantLocationRepository.save(location);

        return mapMerchant(merchantRepository.findById(merchant.getId()).orElseThrow());
    }

    // ==================== Merchants ====================

    /**
     * Получает список всех партнёров (для coupon form selector — НЕ МЕНЯТЬ).
     */
    @Transactional(readOnly = true)
    public List<MerchantResponse> getAllMerchants() {
        return merchantRepository.findByActiveTrue().stream()
                .map(this::mapMerchant)
                .collect(Collectors.toList());
    }

    /**
     * Admin paginated merchant search с readiness и счётчиками купонов.
     */
    @Transactional(readOnly = true)
    public Page<AdminMerchantSummaryResponse> getAdminMerchantPage(String search, Boolean active, Pageable pageable) {
        Page<Merchant> merchants = merchantRepository.searchMerchants(search, active, pageable);
        return merchants.map(this::mapToAdminSummary);
    }

    /**
     * Активация/деактивация мерчанта (Admin).
     * Деактивация блокируется при наличии ACTIVE/WAITING_FOR_MERCHANT купонов.
     */
    @CacheEvict(value = "catalog", allEntries = true)
    @Transactional
    public MerchantResponse setMerchantActiveStatus(Long id, boolean active) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Партнёр не найден"));

        if (!active && hasPublicationDependentCoupons(merchant.getId())) {
            throw new IllegalStateException(
                    "Нельзя деактивировать мерчанта с активными или ожидающими подтверждения купонами");
        }

        merchant.setActive(active);
        merchantRepository.save(merchant);
        return mapMerchant(merchantRepository.findById(id).orElseThrow());
    }

    /**
     * Получает партнёра по ID.
     *
     * @param id идентификатор партнёра
     * @return данные партнёра
     * @throws ResourceNotFoundException если не найден
     */
    @Transactional(readOnly = true)
    public MerchantResponse getMerchantById(Long id) {
        return mapMerchant(merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Партнёр не найден")));
    }

    /**
     * Создаёт нового партнёра (Admin).
     * Locations are managed only through normalized locations[].
     *
     * @param request name, description, logoUrl, normalized locations
     * @return созданный партнёр
     */
    @CacheEvict(value = "catalog", allEntries = true)
    @Transactional
    public MerchantResponse createMerchant(CreateMerchantRequest request) {
        Merchant merchant = Merchant.builder()
                .name(request.getName())
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .coverUrl(request.getCoverUrl())
                // Legacy contact fields no longer written to merchant entity — live in merchant_locations
                .email(request.getEmail())
                .website(request.getWebsite())
                .contactPerson(request.getContactPerson())
                .active(true)
                .build();
        merchant = merchantRepository.save(merchant);

        // Create locations
        saveLocations(merchant, request);

        return mapMerchant(merchantRepository.findById(merchant.getId()).orElseThrow());
    }

    /**
     * Обновляет данные партнёра (Admin).
     *
     * @param id идентификатор партнёра
     * @param request обновлённые данные
     * @return обновлённый партнёр
     */
    @CacheEvict(value = "catalog", allEntries = true)
    @Transactional
    public MerchantResponse updateMerchant(Long id, CreateMerchantRequest request) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Партнёр не найден"));
        merchant.setName(request.getName());
        merchant.setDescription(request.getDescription());
        merchant.setLogoUrl(request.getLogoUrl());
        merchant.setCoverUrl(request.getCoverUrl());
        // Legacy contact fields no longer written to merchant entity — live in merchant_locations
        merchant.setEmail(request.getEmail());
        merchant.setWebsite(request.getWebsite());
        merchant.setContactPerson(request.getContactPerson());
        merchantRepository.save(merchant);

        // Update locations: safe path (null = preserve, empty = check dependents)
        saveLocationsForUpdate(merchant, request);

        return mapMerchant(merchantRepository.findById(id).orElseThrow());
    }

    // ==================== Location Helpers ====================

    /**
     * Saves normalized locations for a merchant, replacing all existing ones.
     * Used on CREATE path only.
     */
    private void saveLocations(Merchant merchant, CreateMerchantRequest request) {
        List<MerchantLocation> normalizedLocations = buildLocations(merchant, request);

        // Remove existing locations
        merchantLocationRepository.deleteAllByMerchantId(merchant.getId());
        merchant.getLocations().clear();

        for (MerchantLocation location : normalizedLocations) {
            merchantLocationRepository.save(location);
        }
    }

    /**
     * Safe location update for UPDATE path:
     * - null locations = preserve existing (no-op)
     * - empty locations = check for dependent coupons before clearing
     */
    private void saveLocationsForUpdate(Merchant merchant, CreateMerchantRequest request) {
        if (request.getLocations() == null) {
            return;
        }

        List<MerchantLocation> normalizedLocations = buildLocations(merchant, request);
        if (normalizedLocations.isEmpty() && hasPublicationDependentCoupons(merchant.getId())) {
            throw new IllegalStateException(
                    "Нельзя удалить все locations у мерчанта с WAITING_FOR_MERCHANT или ACTIVE купонами");
        }

        merchantLocationRepository.deleteAllByMerchantId(merchant.getId());
        merchant.getLocations().clear();
        normalizedLocations.forEach(merchantLocationRepository::save);
    }

    private boolean hasPublicationDependentCoupons(Long merchantId) {
        return couponOfferRepository.existsByMerchantIdAndStatusIn(
                merchantId,
                List.of(CouponStatus.WAITING_FOR_MERCHANT, CouponStatus.ACTIVE)
        );
    }

    private List<MerchantLocation> buildLocations(Merchant merchant, CreateMerchantRequest request) {
        if (request.getLocations() != null && !request.getLocations().isEmpty()) {
            List<CreateMerchantRequest.LocationRequest> nonEmptyLocations = request.getLocations().stream()
                    .filter(this::hasLocationData)
                    .toList();

            if (!nonEmptyLocations.isEmpty()) {
                long primaryCount = nonEmptyLocations.stream()
                        .filter(CreateMerchantRequest.LocationRequest::isPrimary)
                        .count();
                if (primaryCount > 1) {
                    throw new IllegalArgumentException("У мерчанта может быть только одна primary location");
                }

                List<MerchantLocation> normalizedLocations = new ArrayList<>();
                for (CreateMerchantRequest.LocationRequest locReq : nonEmptyLocations) {
                    normalizedLocations.add(MerchantLocation.builder()
                            .merchant(merchant)
                            .title(locReq.getTitle())
                            .address(locReq.getAddress())
                            .phone(normalize(locReq.getPhone()))
                            .workingHours(locReq.getWorkingHours())
                            .latitude(locReq.getLatitude())
                            .longitude(locReq.getLongitude())
                            .primary(locReq.isPrimary())
                            .active(true)
                            .build());
                }

                if (primaryCount == 0) {
                    normalizedLocations.get(0).setPrimary(true);
                }

                return normalizedLocations;
            }
        }

        return List.of();
    }

    private boolean hasLocationData(CreateMerchantRequest.LocationRequest request) {
        return (request.getTitle() != null && !request.getTitle().isBlank())
                || (request.getAddress() != null && !request.getAddress().isBlank())
                || (request.getPhone() != null && !request.getPhone().isBlank())
                || (request.getWorkingHours() != null && !request.getWorkingHours().isBlank())
                || request.getLatitude() != null
                || request.getLongitude() != null;
    }

    // ==================== Categories ====================

    /**
     * Получает все активные категории.
     * Кэшируется в Redis (TTL: 1 час).
     *
     * @return список категорий
     */
    @Cacheable(value = "categories")
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findByActiveTrueOrderBySortOrder().stream()
                .map(this::mapCategory)
                .collect(Collectors.toList());
    }

    /**
     * Получить категорию по ID (Admin).
     */
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        return mapCategory(categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Категория не найдена")));
    }

    /**
     * Создаёт новую категорию (Admin).
     * Если slug не указан — генерируется из name.
     */
    @CacheEvict(value = {"categories", "catalog"}, allEntries = true)
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Категория с таким названием уже существует");
        }
        Category category = Category.builder()
                .name(request.getName())
                .nameUz(request.getNameUz())
                .slug(request.getSlug() != null ? request.getSlug() : generateSlug(request.getName()))
                .iconUrl(request.getIconUrl())
                .sortOrder(request.getSortOrder())
                .active(request.isActive())
                .build();
        return mapCategory(categoryRepository.save(category));
    }

    /**
     * Обновляет категорию (Admin).
     */
    @CacheEvict(value = {"categories", "catalog"}, allEntries = true)
    @Transactional
    public CategoryResponse updateCategory(Long id, CreateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Категория не найдена"));
        category.setName(request.getName());
        category.setNameUz(request.getNameUz());
        category.setSlug(request.getSlug() != null ? request.getSlug() : generateSlug(request.getName()));
        category.setIconUrl(request.getIconUrl());
        category.setSortOrder(request.getSortOrder());
        category.setActive(request.isActive());
        return mapCategory(categoryRepository.save(category));
    }

    /**
     * Удаляет категорию (soft delete — active=false).
     */
    @CacheEvict(value = {"categories", "catalog"}, allEntries = true)
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Категория не найдена"));
        category.setActive(false);
        categoryRepository.save(category);
    }

    // ==================== Mappers ====================

    private CategoryResponse mapCategory(Category cat) {
        return CategoryResponse.builder()
                .id(cat.getId())
                .name(cat.getName())
                .nameUz(cat.getNameUz())
                .slug(cat.getSlug())
                .iconUrl(cat.getIconUrl())
                .sortOrder(cat.getSortOrder())
                .build();
    }

    private MerchantResponse mapMerchant(Merchant merchant) {
        List<MerchantLocation> locations = merchantLocationRepository
                .findByMerchantIdAndActiveTrue(merchant.getId());

        MerchantLocationResponse primaryLoc = locations.stream()
                .filter(MerchantLocation::isPrimary)
                .findFirst()
                .map(this::mapLocation)
                .orElse(null);

        List<MerchantLocationResponse> locResponses = locations.stream()
                .map(this::mapLocation)
                .collect(Collectors.toList());

        MerchantLocation primaryRaw = locations.stream()
                .filter(MerchantLocation::isPrimary)
                .findFirst().orElse(null);
        String[] readiness = computePublicationReadiness(merchant, primaryRaw);

        return MerchantResponse.builder()
                .id(merchant.getId())
                .name(merchant.getName())
                .description(merchant.getDescription())
                .logoUrl(merchant.getLogoUrl())
                .coverUrl(merchant.getCoverUrl())
                .email(merchant.getEmail())
                .website(merchant.getWebsite())
                .contactPerson(merchant.getContactPerson())
                .userId(merchant.getUserId())
                .active(merchant.isActive())
                .publicationReady(readiness[0] == null)
                .publicationBlockReason(readiness[0])
                .primaryLocation(primaryLoc)
                .locations(locResponses)
                .build();
    }

    private AdminMerchantSummaryResponse mapToAdminSummary(Merchant merchant) {
        List<MerchantLocation> locations = merchantLocationRepository
                .findByMerchantIdAndActiveTrue(merchant.getId());

        MerchantLocationResponse primaryLoc = locations.stream()
                .filter(MerchantLocation::isPrimary)
                .findFirst()
                .map(this::mapLocation)
                .orElse(null);

        MerchantLocation primaryRaw = locations.stream()
                .filter(MerchantLocation::isPrimary)
                .findFirst().orElse(null);
        String[] readiness = computePublicationReadiness(merchant, primaryRaw);

        return AdminMerchantSummaryResponse.builder()
                .id(merchant.getId())
                .name(merchant.getName())
                .logoUrl(merchant.getLogoUrl())
                .contactPerson(merchant.getContactPerson())
                .email(merchant.getEmail())
                .userId(merchant.getUserId())
                .active(merchant.isActive())
                .primaryLocation(primaryLoc)
                .publicationReady(readiness[0] == null)
                .publicationBlockReason(readiness[0])
                .activeCouponsCount(couponOfferRepository.countByMerchantIdAndStatus(merchant.getId(), CouponStatus.ACTIVE))
                .waitingCouponsCount(couponOfferRepository.countByMerchantIdAndStatus(merchant.getId(), CouponStatus.WAITING_FOR_MERCHANT))
                .totalCouponsCount(couponOfferRepository.countByMerchantId(merchant.getId()))
                .build();
    }

    /**
     * Вычисляет причину блокировки публикации.
     * @return String[1] where [0] is null if ready, or block reason string
     */
    private String[] computePublicationReadiness(Merchant merchant, MerchantLocation primary) {
        if (!merchant.isActive()) return new String[]{"Мерчант не активен"};
        if (primary == null) return new String[]{"Нет primary location"};
        if (!primary.isActive()) return new String[]{"Primary location не активна"};
        if (primary.getAddress() == null || primary.getAddress().isBlank()) return new String[]{"Не указан адрес"};
        if (primary.getPhone() == null || primary.getPhone().isBlank()) return new String[]{"Не указан телефон"};
        return new String[]{null}; // ready
    }

    private MerchantLocationResponse mapLocation(MerchantLocation loc) {
        return MerchantLocationResponse.builder()
                .id(loc.getId())
                .title(loc.getTitle())
                .address(loc.getAddress())
                .phone(loc.getPhone())
                .workingHours(loc.getWorkingHours())
                .latitude(loc.getLatitude())
                .longitude(loc.getLongitude())
                .primary(loc.isPrimary())
                .active(loc.isActive())
                .build();
    }

    /**
     * Генерирует slug из названия (транслитерация кириллицы → латиница).
     */
    private String generateSlug(String name) {
        String lower = name.toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (char c : lower.toCharArray()) {
            String mapped = switch (c) {
                case 'а' -> "a"; case 'б' -> "b"; case 'в' -> "v"; case 'г' -> "g";
                case 'д' -> "d"; case 'е' -> "e"; case 'ё' -> "yo"; case 'ж' -> "zh";
                case 'з' -> "z"; case 'и' -> "i"; case 'й' -> "y"; case 'к' -> "k";
                case 'л' -> "l"; case 'м' -> "m"; case 'н' -> "n"; case 'о' -> "o";
                case 'п' -> "p"; case 'р' -> "r"; case 'с' -> "s"; case 'т' -> "t";
                case 'у' -> "u"; case 'ф' -> "f"; case 'х' -> "kh"; case 'ц' -> "ts";
                case 'ч' -> "ch"; case 'ш' -> "sh"; case 'щ' -> "shch"; case 'ъ' -> "";
                case 'ы' -> "y"; case 'ь' -> ""; case 'э' -> "e"; case 'ю' -> "yu";
                case 'я' -> "ya"; case ' ' -> "-";
                default -> Character.isLetterOrDigit(c) ? String.valueOf(c) : "-";
            };
            sb.append(mapped);
        }
        return sb.toString()
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
