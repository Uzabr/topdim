package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.topdim.coupon.dto.CreateCategoryRequest;
import uz.topdim.coupon.dto.ExcelImportResponse;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.repository.CategoryRepository;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public Long createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Категория с таким названием уже существует: " + request.getName());
        }

        String actualSlug = request.getSlug() != null && !request.getSlug().isBlank()
                ? request.getSlug()
                : generateSlug(request.getName());

        Category category = Category.builder()
                .name(request.getName())
                .nameUz(request.getNameUz())
                .slug(actualSlug)
                .iconUrl(request.getIconUrl())
                .sortOrder(request.getSortOrder())
                .active(request.isActive())
                .build();

        return categoryRepository.save(category).getId();
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
                if (categoryRepository.existsByName(nameRu)) {
                    skippedCategories.add(nameRu);
                    continue; // Пропускаем дубликат
                }

                // Создаем категорию
                Category category = Category.builder()
                        .name(nameRu)
                        .nameUz(nameUz)
                        .slug(generateSlug(nameRu))
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
    private String generateSlug(String src) {
        char[] abcCyr = {'а','б','в','г','д','е','ё', 'ж','з','и','й','к','л','м','н','о','п','р','с','т','у','ф','х', 'ц','ч', 'ш','щ','ъ','ы','ь','э', 'ю','я', ' ', '-'};
        String[] abcLat = {"a","b","v","g","d","e","jo","zh","z","i","j","k","l","m","n","o","p","r","s","t","u","f","h","ts","ch","sh","shh","","y","","e","yu","ya", "-", "-"};

        StringBuilder builder = new StringBuilder();
        String text = src.toLowerCase();

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
}
