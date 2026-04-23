package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
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

    // ==================== Merchants ====================

    /**
     * Получает список всех партнёров.
     *
     * @return список MerchantResponse
     */
    @Transactional(readOnly = true)
    public List<MerchantResponse> getAllMerchants() {
        return merchantRepository.findByActiveTrue().stream()
                .map(this::mapMerchant)
                .collect(Collectors.toList());
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
     * Если locations переданы — создаёт их. Иначе auto-creates primary из legacy fields.
     *
     * @param request name, description, logoUrl, locations or address/phone
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

        // Update locations: replace all
        saveLocations(merchant, request);

        return mapMerchant(merchantRepository.findById(id).orElseThrow());
    }

    // ==================== Location Helpers ====================

    /**
     * Saves locations for a merchant.
     * If request.locations is provided, uses them (replacing existing).
     * Otherwise, auto-creates a primary location from legacy fields if they exist.
     */
    private void saveLocations(Merchant merchant, CreateMerchantRequest request) {
        // Remove existing locations
        merchantLocationRepository.deleteAllByMerchantId(merchant.getId());
        merchant.getLocations().clear();

        if (request.getLocations() != null && !request.getLocations().isEmpty()) {
            // Use explicit locations from request
            boolean hasPrimary = false;
            for (CreateMerchantRequest.LocationRequest locReq : request.getLocations()) {
                MerchantLocation loc = MerchantLocation.builder()
                        .merchant(merchant)
                        .title(locReq.getTitle())
                        .address(locReq.getAddress())
                        .phone(normalize(locReq.getPhone()))
                        .workingHours(locReq.getWorkingHours())
                        .latitude(locReq.getLatitude())
                        .longitude(locReq.getLongitude())
                        .primary(locReq.isPrimary())
                        .active(true)
                        .build();
                if (locReq.isPrimary()) hasPrimary = true;
                merchantLocationRepository.save(loc);
            }
            // If no primary was set, promote the first one
            if (!hasPrimary) {
                List<MerchantLocation> saved = merchantLocationRepository.findByMerchantId(merchant.getId());
                if (!saved.isEmpty()) {
                    MerchantLocation first = saved.get(0);
                    first.setPrimary(true);
                    merchantLocationRepository.save(first);
                }
            }
        } else {
            // Auto-create primary from legacy fields
            boolean hasLegacy = (request.getAddress() != null && !request.getAddress().isBlank())
                    || (request.getPhone() != null && !request.getPhone().isBlank())
                    || (request.getWorkingHours() != null && !request.getWorkingHours().isBlank());

            if (hasLegacy) {
                MerchantLocation loc = MerchantLocation.builder()
                        .merchant(merchant)
                        .title("Основной адрес")
                        .address(request.getAddress())
                        .phone(normalize(request.getPhone()))
                        .workingHours(request.getWorkingHours())
                        .primary(true)
                        .active(true)
                        .build();
                merchantLocationRepository.save(loc);
            }
        }
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

        return MerchantResponse.builder()
                .id(merchant.getId())
                .name(merchant.getName())
                .description(merchant.getDescription())
                .logoUrl(merchant.getLogoUrl())
                .coverUrl(merchant.getCoverUrl())
                // Legacy contact fields no longer read from entity — data lives in locations
                .email(merchant.getEmail())
                .website(merchant.getWebsite())
                .contactPerson(merchant.getContactPerson())
                .active(merchant.isActive())
                .primaryLocation(primaryLoc)
                .locations(locResponses)
                .build();
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
