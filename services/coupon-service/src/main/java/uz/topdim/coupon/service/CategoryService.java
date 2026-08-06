package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.topdim.coupon.dto.AdminCategoryResponse;
import uz.topdim.coupon.dto.CreateCategoryRequest;
import uz.topdim.coupon.dto.ExcelImportResponse;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.CouponOfferRepository;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CouponOfferRepository couponOfferRepository;

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> getAllCategoriesForAdmin() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminCategoryResponse getCategoryForAdmin(Long id) {
        return toAdminResponse(findCategory(id));
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public Long createCategory(CreateCategoryRequest request) {
        String name = request.getName().trim();
        String actualSlug = normalizeSlug(request.getSlug(), name);
        ensureUnique(name, actualSlug, null);

        Category category = Category.builder()
                .name(name)
                .nameUz(trimToNull(request.getNameUz()))
                .slug(actualSlug)
                .iconUrl(trimToNull(request.getIconUrl()))
                .sortOrder(request.getSortOrder())
                .active(request.isActive())
                .build();

        try {
            return categoryRepository.saveAndFlush(category).getId();
        } catch (DataIntegrityViolationException ex) {
            throw duplicateConstraintError();
        }
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public AdminCategoryResponse updateCategory(Long id, CreateCategoryRequest request) {
        Category category = findCategory(id);
        String name = request.getName().trim();
        String actualSlug = normalizeSlug(request.getSlug(), name);
        ensureUnique(name, actualSlug, id);

        category.setName(name);
        category.setNameUz(trimToNull(request.getNameUz()));
        category.setSlug(actualSlug);
        category.setIconUrl(trimToNull(request.getIconUrl()));
        category.setSortOrder(request.getSortOrder());
        category.setActive(request.isActive());

        try {
            return toAdminResponse(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            throw duplicateConstraintError();
        }
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public void deleteCategory(Long id) {
        Category category = findCategory(id);
        if (couponOfferRepository.existsByCategoryId(id)) {
            throw new IllegalStateException(
                    "Категория используется купонами. Деактивируйте её вместо удаления");
        }
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException(
                    "Категория используется купонами. Деактивируйте её вместо удаления");
        }
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public ExcelImportResponse importCategoriesFromExcel(MultipartFile file) {
        List<String> skippedCategories = new ArrayList<>();
        int processed = 0;
        int added = 0;

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                // Пропускаем шапку (первая строка)
                if (row.getRowNum() == 0) continue;

                // Читаем название (Рус) - колонка 0
                if (row.getCell(0) == null) continue;
                String nameRu = row.getCell(0).getStringCellValue().trim();
                if (nameRu.isEmpty()) continue;

                processed++;

                // Читаем название (Узб) - колонка 1 (может быть пустой)
                String nameUz = null;
                if (row.getCell(1) != null) {
                    nameUz = row.getCell(1).getStringCellValue().trim();
                }

                // Проверка на дубликат в базе
                String slug = generateSlug(nameRu);
                if (categoryRepository.existsByNameIgnoreCase(nameRu)
                        || categoryRepository.existsBySlugIgnoreCase(slug)) {
                    skippedCategories.add(nameRu);
                    continue; // Пропускаем дубликат
                }

                // Создаем категорию
                Category category = Category.builder()
                        .name(nameRu)
                        .nameUz(trimToNull(nameUz))
                        .slug(slug)
                        .iconUrl(null) // Иконки добавляются Админом вручную позже
                        .sortOrder(added + 1) // Автоматический порядок
                        .active(true)
                        .build();

                categoryRepository.save(category);
                added++;
            }
        } catch (Exception e) {
            log.error("Ошибка при разборе Excel файла", e);
            throw new RuntimeException("Не удалось обработать Excel файл: " + e.getMessage());
        }

        return ExcelImportResponse.builder()
                .processed(processed)
                .added(added)
                .skipped(skippedCategories.size())
                .skippedCategories(skippedCategories)
                .build();
    }

    /**
     * Создает безопасный "slug" (URL) из текста (транслитерация + удаление спецсимволов).
     */
    private String normalizeSlug(String requestedSlug, String fallbackName) {
        String source = requestedSlug == null || requestedSlug.isBlank()
                ? fallbackName
                : requestedSlug;
        String slug = generateSlug(source);
        if (slug.isBlank()) {
            throw new IllegalArgumentException("Slug категории не может быть пустым");
        }
        if (slug.length() > 100) {
            throw new IllegalArgumentException("Slug категории не может быть длиннее 100 символов");
        }
        return slug;
    }

    private String generateSlug(String src) {
        char[] abcCyr = {'а','б','в','г','д','е','ё', 'ж','з','и','й','к','л','м','н','о','п','р','с','т','у','ф','х', 'ц','ч', 'ш','щ','ъ','ы','ь','э', 'ю','я', ' ', '-'};
        String[] abcLat = {"a","b","v","g","d","e","jo","zh","z","i","j","k","l","m","n","o","p","r","s","t","u","f","h","ts","ch","sh","shh","","y","","e","yu","ya", "-", "-"};

        StringBuilder builder = new StringBuilder();
        String text = src.toLowerCase(Locale.ROOT).trim();

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean found = false;
            for (int j = 0; j < abcCyr.length; j++) {
                if (ch == abcCyr[j]) {
                    builder.append(abcLat[j]);
                    found = true;
                    break;
                }
            }
            if (!found) {
                // Если символ - английская буква или цифра, оставляем как есть
                if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                    builder.append(ch);
                }
            }
        }
        
        String slug = builder.toString();
        // Убираем двойные дефисы
        slug = slug.replaceAll("-+", "-");
        // Убираем дефисы по краям
        slug = slug.replaceAll("^-|-$", "");

        return slug;
    }

    private void ensureUnique(String name, String slug, Long currentId) {
        boolean duplicateName = currentId == null
                ? categoryRepository.existsByNameIgnoreCase(name)
                : categoryRepository.existsByNameIgnoreCaseAndIdNot(name, currentId);
        if (duplicateName) {
            throw new IllegalArgumentException(
                    "Категория с таким названием уже существует: " + name);
        }

        boolean duplicateSlug = currentId == null
                ? categoryRepository.existsBySlugIgnoreCase(slug)
                : categoryRepository.existsBySlugIgnoreCaseAndIdNot(slug, currentId);
        if (duplicateSlug) {
            throw new IllegalArgumentException("Категория с таким slug уже существует: " + slug);
        }
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Категория не найдена"));
    }

    private AdminCategoryResponse toAdminResponse(Category category) {
        return AdminCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .nameUz(category.getNameUz())
                .slug(category.getSlug())
                .iconUrl(category.getIconUrl())
                .sortOrder(category.getSortOrder())
                .active(category.isActive())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private IllegalArgumentException duplicateConstraintError() {
        return new IllegalArgumentException(
                "Категория с таким названием или slug уже существует");
    }
}
