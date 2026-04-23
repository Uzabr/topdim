package uz.topdim.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.coupon.dto.CategoryResponse;
import uz.topdim.coupon.dto.CreateCategoryRequest;
import uz.topdim.coupon.dto.CreateMerchantRequest;
import uz.topdim.coupon.dto.MerchantResponse;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private MerchantLocationRepository merchantLocationRepository;
    @Mock private CategoryRepository categoryRepository;

    @InjectMocks
    private MerchantService merchantService;

    // ==================== Merchants ====================

    @Nested
    @DisplayName("Merchants")
    class MerchantTests {

        private Merchant createTestMerchant() {
            return Merchant.builder()
                    .id(1L).name("SPA Oasis").description("Лучший СПА")
                    .logoUrl("/logo.jpg").coverUrl("/cover.jpg")
                    .email("spa@test.com").website("https://spa.com")
                    .contactPerson("Алишер")
                    .active(true).build();
        }

        @Test
        @DisplayName("Список: возвращает только активных")
        void getAllMerchants_returnsActiveOnly() {
            when(merchantRepository.findByActiveTrue()).thenReturn(List.of(createTestMerchant()));

            List<MerchantResponse> result = merchantService.getAllMerchants();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("SPA Oasis");
        }

        @Test
        @DisplayName("По ID: найден — возвращает")
        void getMerchantById_found_returns() {
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(createTestMerchant()));

            MerchantResponse result = merchantService.getMerchantById(1L);

            assertThat(result.getName()).isEqualTo("SPA Oasis");
            // phone no longer in merchant entity — lives in primaryLocation
        }

        @Test
        @DisplayName("По ID: не найден → ResourceNotFoundException")
        void getMerchantById_notFound_throws() {
            when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> merchantService.getMerchantById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("не найден");
        }

        @Test
        @DisplayName("Создание: успешное — active=true")
        void createMerchant_success() {
            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("New SPA");
            request.setDescription("Описание");
            request.setPhone("+998901111111");

            when(merchantRepository.save(any(Merchant.class))).thenAnswer(inv -> {
                Merchant m = inv.getArgument(0);
                m.setId(10L);
                return m;
            });
            when(merchantRepository.findById(10L)).thenAnswer(inv -> {
                Merchant m = Merchant.builder().id(10L).name("New SPA").description("Описание")
                        .active(true).build();
                return Optional.of(m);
            });

            MerchantResponse result = merchantService.createMerchant(request);

            assertThat(result.getName()).isEqualTo("New SPA");
            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("Обновление: успешное — обновляет поля")
        void updateMerchant_success() {
            Merchant existing = createTestMerchant();
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Updated SPA");
            request.setDescription("Новое описание");
            request.setPhone("+998909999999");

            MerchantResponse result = merchantService.updateMerchant(1L, request);

            assertThat(result.getName()).isEqualTo("Updated SPA");
        }

        @Test
        @DisplayName("Создание: телефон с пробелами нормализуется в auto-created location")
        void createMerchant_normalizesSpacedPhoneInLocation() {
            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Phone Test");
            request.setPhone("+998 90 123 45 67");

            when(merchantRepository.save(any(Merchant.class))).thenAnswer(inv -> {
                Merchant m = inv.getArgument(0);
                m.setId(20L);
                return m;
            });
            when(merchantRepository.findById(20L)).thenAnswer(inv -> {
                Merchant m = Merchant.builder().id(20L).name("Phone Test")
                        .active(true).build();
                return Optional.of(m);
            });

            merchantService.createMerchant(request);

            // Verify phone is no longer stored on merchant entity
            ArgumentCaptor<Merchant> merchantCaptor = ArgumentCaptor.forClass(Merchant.class);
            verify(merchantRepository).save(merchantCaptor.capture());

            // Verify phone is written to auto-created primary location (normalized)
            ArgumentCaptor<uz.topdim.coupon.entity.MerchantLocation> locCaptor =
                    ArgumentCaptor.forClass(uz.topdim.coupon.entity.MerchantLocation.class);
            verify(merchantLocationRepository).save(locCaptor.capture());
            assertThat(locCaptor.getValue().getPhone()).isEqualTo("+998901234567");
            assertThat(locCaptor.getValue().isPrimary()).isTrue();
        }

        @Test
        @DisplayName("Обновление: телефон с пробелами нормализуется в auto-created location")
        void updateMerchant_normalizesSpacedPhoneInLocation() {
            Merchant existing = createTestMerchant();
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Updated SPA");
            request.setPhone("+998 90 999 99 99");

            merchantService.updateMerchant(1L, request);

            // Verify phone is no longer updated on merchant entity
            // (existing phone stays as-is, new phone goes to location)
            ArgumentCaptor<uz.topdim.coupon.entity.MerchantLocation> locCaptor =
                    ArgumentCaptor.forClass(uz.topdim.coupon.entity.MerchantLocation.class);
            verify(merchantLocationRepository).save(locCaptor.capture());
            assertThat(locCaptor.getValue().getPhone()).isEqualTo("+998909999999");
        }

        @Test
        @DisplayName("Обновление: два primary location отклоняются до удаления существующих локаций")
        void updateMerchant_rejectsMultiplePrimaryLocationsBeforeDelete() {
            Merchant existing = createTestMerchant();
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));

            CreateMerchantRequest.LocationRequest first = new CreateMerchantRequest.LocationRequest();
            first.setTitle("Филиал 1");
            first.setAddress("ул. 1");
            first.setPrimary(true);

            CreateMerchantRequest.LocationRequest second = new CreateMerchantRequest.LocationRequest();
            second.setTitle("Филиал 2");
            second.setAddress("ул. 2");
            second.setPrimary(true);

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Updated SPA");
            request.setLocations(List.of(first, second));

            assertThatThrownBy(() -> merchantService.updateMerchant(1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("только одна");

            verify(merchantLocationRepository, never()).deleteAllByMerchantId(anyLong());
            verify(merchantLocationRepository, never()).save(any());
        }
    }

    // ==================== Categories ====================

    @Nested
    @DisplayName("Categories")
    class CategoryTests {

        private Category createTestCategory() {
            return Category.builder()
                    .id(1L).name("Красота").nameUz("Go'zallik")
                    .slug("krasota").iconUrl("/icon.svg")
                    .sortOrder(1).active(true).build();
        }

        @Test
        @DisplayName("Список: возвращает активные с сортировкой")
        void getAllCategories_returnsActiveSorted() {
            Category c1 = createTestCategory();
            Category c2 = Category.builder().id(2L).name("Еда").slug("eda").sortOrder(2).active(true).build();
            when(categoryRepository.findByActiveTrueOrderBySortOrder()).thenReturn(List.of(c1, c2));

            List<CategoryResponse> result = merchantService.getAllCategories();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Красота");
            assertThat(result.get(1).getName()).isEqualTo("Еда");
        }

        @Test
        @DisplayName("По ID: найдена — возвращает")
        void getCategoryById_found_returns() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(createTestCategory()));

            CategoryResponse result = merchantService.getCategoryById(1L);

            assertThat(result.getName()).isEqualTo("Красота");
            assertThat(result.getSlug()).isEqualTo("krasota");
        }

        @Test
        @DisplayName("По ID: не найдена → ResourceNotFoundException")
        void getCategoryById_notFound_throws() {
            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> merchantService.getCategoryById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Категория не найдена");
        }

        @Test
        @DisplayName("Создание: успешное — генерирует slug из названия")
        void createCategory_success_generatesSlug() {
            CreateCategoryRequest request = new CreateCategoryRequest();
            request.setName("Здоровье");
            request.setActive(true);

            when(categoryRepository.existsByName("Здоровье")).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
                Category c = inv.getArgument(0);
                c.setId(10L);
                return c;
            });

            CategoryResponse result = merchantService.createCategory(request);

            assertThat(result.getName()).isEqualTo("Здоровье");
            assertThat(result.getSlug()).isNotBlank();
        }

        @Test
        @DisplayName("Создание: с указанным slug — использует его")
        void createCategory_withSlug_usesProvided() {
            CreateCategoryRequest request = new CreateCategoryRequest();
            request.setName("Еда и напитки");
            request.setSlug("food");
            request.setActive(true);

            when(categoryRepository.existsByName("Еда и напитки")).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
                Category c = inv.getArgument(0);
                c.setId(11L);
                return c;
            });

            CategoryResponse result = merchantService.createCategory(request);

            assertThat(result.getSlug()).isEqualTo("food");
        }

        @Test
        @DisplayName("Создание: дубликат имени → IllegalArgumentException")
        void createCategory_duplicateName_throws() {
            CreateCategoryRequest request = new CreateCategoryRequest();
            request.setName("Красота");

            when(categoryRepository.existsByName("Красота")).thenReturn(true);

            assertThatThrownBy(() -> merchantService.createCategory(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("уже существует");
        }

        @Test
        @DisplayName("Обновление: успешное — меняет поля")
        void updateCategory_success() {
            Category existing = createTestCategory();
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateCategoryRequest request = new CreateCategoryRequest();
            request.setName("Красота и здоровье");
            request.setSlug("beauty-health");
            request.setActive(true);

            CategoryResponse result = merchantService.updateCategory(1L, request);

            assertThat(result.getName()).isEqualTo("Красота и здоровье");
            assertThat(result.getSlug()).isEqualTo("beauty-health");
        }

        @Test
        @DisplayName("Удаление: soft delete — ставит active=false")
        void deleteCategory_softDelete() {
            Category existing = createTestCategory();
            assertThat(existing.isActive()).isTrue();

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            merchantService.deleteCategory(1L);

            assertThat(existing.isActive()).isFalse();
            verify(categoryRepository).save(existing);
        }

        @Test
        @DisplayName("Удаление: не найдена → ResourceNotFoundException")
        void deleteCategory_notFound_throws() {
            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> merchantService.deleteCategory(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
