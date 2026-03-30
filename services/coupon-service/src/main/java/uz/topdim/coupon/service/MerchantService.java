package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.CategoryResponse;
import uz.topdim.coupon.dto.CreateCategoryRequest;
import uz.topdim.coupon.dto.CreateMerchantRequest;
import uz.topdim.coupon.dto.MerchantResponse;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис управления партнёрами (merchants).
 * CRUD операции, получение категорий.
 * Кэширование списка категорий.
 */
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
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
     *
     * @param request name, description, logoUrl, address, phone
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
                .address(request.getAddress())
                .phone(request.getPhone())
                .email(request.getEmail())
                .website(request.getWebsite())
                .workingHours(request.getWorkingHours())
                .contactPerson(request.getContactPerson())
                .active(true)
                .build();
        return mapMerchant(merchantRepository.save(merchant));
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
        merchant.setAddress(request.getAddress());
        merchant.setPhone(request.getPhone());
        merchant.setEmail(request.getEmail());
        merchant.setWebsite(request.getWebsite());
        merchant.setWorkingHours(request.getWorkingHours());
        merchant.setContactPerson(request.getContactPerson());
        return mapMerchant(merchantRepository.save(merchant));
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
        return MerchantResponse.builder()
                .id(merchant.getId())
                .name(merchant.getName())
                .description(merchant.getDescription())
                .logoUrl(merchant.getLogoUrl())
                .coverUrl(merchant.getCoverUrl())
                .address(merchant.getAddress())
                .phone(merchant.getPhone())
                .email(merchant.getEmail())
                .website(merchant.getWebsite())
                .workingHours(merchant.getWorkingHours())
                .contactPerson(merchant.getContactPerson())
                .active(merchant.isActive())
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
