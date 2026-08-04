package uz.topdim.coupon.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import uz.topdim.coupon.dto.AdminCategoryResponse;
import uz.topdim.coupon.dto.CreateCategoryRequest;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.CouponOfferRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private CouponOfferRepository couponOfferRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void adminList_returnsActiveAndInactiveCategoriesInRepositoryOrder() {
        Category active = category(1L, "Еда", "food", 1, true);
        Category inactive = category(2L, "Архив", "archive", 2, false);
        when(categoryRepository.findAllByOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(active, inactive));

        List<AdminCategoryResponse> result = categoryService.getAllCategoriesForAdmin();

        assertThat(result).extracting(AdminCategoryResponse::getName)
                .containsExactly("Еда", "Архив");
        assertThat(result).extracting(AdminCategoryResponse::isActive)
                .containsExactly(true, false);
    }

    @Test
    void adminDetail_missingCategory_returnsNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryForAdmin(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Категория не найдена");
    }

    @Test
    void create_trimsValuesAndNormalizesProvidedSlug() {
        CreateCategoryRequest request = request(
                "  Летние скидки  ", "  Yozgi chegirmalar  ",
                "  Summer Deals!!  ", "  /api/v1/media/icon.png  ", 7, true);
        when(categoryRepository.existsByNameIgnoreCase("Летние скидки")).thenReturn(false);
        when(categoryRepository.existsBySlugIgnoreCase("summer-deals")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        Long id = categoryService.createCategory(request);

        assertThat(id).isEqualTo(42L);
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue())
                .extracting(Category::getName, Category::getNameUz, Category::getSlug,
                        Category::getIconUrl, Category::getSortOrder, Category::isActive)
                .containsExactly("Летние скидки", "Yozgi chegirmalar", "summer-deals",
                        "/api/v1/media/icon.png", 7, true);
    }

    @Test
    void create_blankSlug_generatesNormalizedSlugFromName() {
        CreateCategoryRequest request = request(" Здоровье ", "   ", "  ", "   ", 0, true);
        when(categoryRepository.existsByNameIgnoreCase("Здоровье")).thenReturn(false);
        when(categoryRepository.existsBySlugIgnoreCase("zdorove")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        categoryService.createCategory(request);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("zdorove");
        assertThat(captor.getValue().getNameUz()).isNull();
        assertThat(captor.getValue().getIconUrl()).isNull();
    }

    @Test
    void create_caseInsensitiveDuplicateName_isRejected() {
        CreateCategoryRequest request = request(" beauty ", null, "new-beauty", null, 0, true);
        when(categoryRepository.existsByNameIgnoreCase("beauty")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("названием");
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_caseInsensitiveDuplicateSlug_isRejected() {
        CreateCategoryRequest request = request("Красота", null, " BEAUTY ", null, 0, true);
        when(categoryRepository.existsByNameIgnoreCase("Красота")).thenReturn(false);
        when(categoryRepository.existsBySlugIgnoreCase("beauty")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_concurrentDuplicateDetectedByDatabase_isReturnedAsBusinessError() {
        CreateCategoryRequest request = request("Красота", null, "beauty", null, 0, true);
        when(categoryRepository.existsByNameIgnoreCase("Красота")).thenReturn(false);
        when(categoryRepository.existsBySlugIgnoreCase("beauty")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("unique index"));

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("названием или slug");
    }

    @Test
    void create_generatedSlugLongerThanDatabaseColumn_isRejectedBeforeSave() {
        String expandingName = "щ".repeat(100);
        CreateCategoryRequest request = request(expandingName, null, null, null, 0, true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Slug")
                .hasMessageContaining("100");
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_canDeactivateCategoryAndNormalizesAllValues() {
        Category existing = category(5L, "Старое", "old", 1, true);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Новое", 5L)).thenReturn(false);
        when(categoryRepository.existsBySlugIgnoreCaseAndIdNot("new-name", 5L)).thenReturn(false);
        when(categoryRepository.saveAndFlush(existing)).thenReturn(existing);
        CreateCategoryRequest request = request(
                "  Новое ", " Yangi ", " New Name ", " /new.png ", 10, false);

        AdminCategoryResponse response = categoryService.updateCategory(5L, request);

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getName()).isEqualTo("Новое");
        assertThat(response.getSlug()).isEqualTo("new-name");
        assertThat(response.isActive()).isFalse();
        assertThat(existing.getNameUz()).isEqualTo("Yangi");
        assertThat(existing.getIconUrl()).isEqualTo("/new.png");
        assertThat(existing.getSortOrder()).isEqualTo(10);
    }

    @Test
    void delete_referencedCategory_returnsConflictWithoutDeleting() {
        Category existing = category(8L, "Еда", "food", 1, false);
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(couponOfferRepository.existsByCategoryId(8L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(8L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("используется купонами");
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void delete_unusedCategory_deletesIt() {
        Category existing = category(9L, "Пустая", "empty", 9, false);
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(couponOfferRepository.existsByCategoryId(9L)).thenReturn(false);

        categoryService.deleteCategory(9L);

        verify(categoryRepository).delete(existing);
        verify(categoryRepository).flush();
    }

    @Test
    void delete_referenceAddedConcurrently_isReturnedAsConflict() {
        Category existing = category(10L, "Пустая", "empty", 9, false);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(couponOfferRepository.existsByCategoryId(10L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("foreign key"))
                .when(categoryRepository).flush();

        assertThatThrownBy(() -> categoryService.deleteCategory(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("используется купонами");
    }

    private static Category category(
            Long id, String name, String slug, int sortOrder, boolean active
    ) {
        return Category.builder()
                .id(id)
                .name(name)
                .nameUz(name + " uz")
                .slug(slug)
                .iconUrl("/" + slug + ".png")
                .sortOrder(sortOrder)
                .active(active)
                .build();
    }

    private static CreateCategoryRequest request(
            String name,
            String nameUz,
            String slug,
            String iconUrl,
            int sortOrder,
            boolean active
    ) {
        return new CreateCategoryRequest(name, nameUz, slug, iconUrl, sortOrder, active);
    }
}
