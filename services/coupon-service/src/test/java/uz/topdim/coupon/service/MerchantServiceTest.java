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
import uz.topdim.coupon.dto.CreateMerchantOnboardingRequest;
import uz.topdim.coupon.dto.CreateMerchantRequest;
import uz.topdim.coupon.dto.MerchantResponse;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
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
    @Mock private CouponOfferRepository couponOfferRepository;
    @Mock private MerchantProfileChangeRequestRepository profileChangeRequestRepository;

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
        @DisplayName("Partner profile by resolved merchant ID exposes published profile version")
        void getPartnerMerchant_returnsProfileVersion() {
            Merchant merchant = createTestMerchant();
            merchant.setProfileVersion(5L);
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of());

            MerchantResponse result = merchantService.getPartnerMerchant(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getProfileVersion()).isEqualTo(5L);
        }

        @Test
        @DisplayName("Partner profile by resolved merchant ID rejects inactive merchant")
        void getPartnerMerchant_inactiveMerchant_throws() {
            Merchant merchant = createTestMerchant();
            merchant.setActive(false);
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

            assertThatThrownBy(() -> merchantService.getPartnerMerchant(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Мерчант не активен");

            verifyNoInteractions(merchantLocationRepository);
        }

        @Test
        @DisplayName("Partner profile: inactive merchant is rejected")
        void getMyMerchant_inactiveMerchant_throws() {
            Merchant merchant = createTestMerchant();
            merchant.setUserId(10L);
            merchant.setActive(false);
            when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));

            assertThatThrownBy(() -> merchantService.getMyMerchant(10L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Мерчант не активен");
        }

        @Test
        @DisplayName("Partner locations: inactive merchant is rejected before returning branches")
        void getLocationsByOwnerUserId_inactiveMerchant_throws() {
            Merchant merchant = createTestMerchant();
            merchant.setUserId(10L);
            merchant.setActive(false);
            when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));

            assertThatThrownBy(() -> merchantService.getLocationsByOwnerUserId(10L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Мерчант не активен");

            verifyNoInteractions(merchantLocationRepository);
        }

        @Test
        @DisplayName("Создание: успешное — active=true")
        void createMerchant_success() {
            CreateMerchantRequest.LocationRequest primary = new CreateMerchantRequest.LocationRequest();
            primary.setAddress("Ташкент");
            primary.setPhone("+998901111111");
            primary.setPrimary(true);

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("New SPA");
            request.setDescription("Описание");
            request.setLocations(List.of(primary));

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
            existing.setProfileVersion(4L);
            MerchantProfileChangeRequest pending = MerchantProfileChangeRequest.builder()
                    .merchant(existing)
                    .baseProfileVersion(4L)
                    .status(MerchantProfileChangeStatus.PENDING_REVIEW)
                    .build();
            when(merchantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(profileChangeRequestRepository
                    .findByMerchantIdAndBaseProfileVersionAndStatusIn(
                            1L, 4L, MerchantProfileChangeStatus.activeStatuses()))
                    .thenReturn(List.of(pending));

            CreateMerchantRequest.LocationRequest primary = new CreateMerchantRequest.LocationRequest();
            primary.setAddress("ул. Обновлённая");
            primary.setPhone("+998909999999");
            primary.setPrimary(true);

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Updated SPA");
            request.setDescription("Новое описание");
            request.setLocations(List.of(primary));

            MerchantResponse result = merchantService.updateMerchant(1L, request);

            assertThat(result.getName()).isEqualTo("Updated SPA");
            assertThat(existing.getProfileVersion()).isEqualTo(5L);
            assertThat(pending.getStatus()).isEqualTo(MerchantProfileChangeStatus.OUTDATED);
            assertThat(pending.getDecidedAt()).isNotNull();
            verify(profileChangeRequestRepository)
                    .findByMerchantIdAndBaseProfileVersionAndStatusIn(
                            eq(1L), eq(4L), eq(uz.topdim.coupon.entity.MerchantProfileChangeStatus.activeStatuses()));
            verify(profileChangeRequestRepository).saveAll(List.of(pending));
        }

        @Test
        @DisplayName("Создание: телефон с пробелами нормализуется в normalized location")
        void createMerchant_normalizesSpacedPhoneInNormalizedLocation() {
            CreateMerchantRequest.LocationRequest primary = new CreateMerchantRequest.LocationRequest();
            primary.setTitle("Основной адрес");
            primary.setPhone("+998 90 123 45 67");
            primary.setPrimary(true);

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Phone Test");
            request.setLocations(List.of(primary));

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

            ArgumentCaptor<Merchant> merchantCaptor = ArgumentCaptor.forClass(Merchant.class);
            verify(merchantRepository).save(merchantCaptor.capture());

            ArgumentCaptor<uz.topdim.coupon.entity.MerchantLocation> locCaptor =
                    ArgumentCaptor.forClass(uz.topdim.coupon.entity.MerchantLocation.class);
            verify(merchantLocationRepository).save(locCaptor.capture());
            assertThat(locCaptor.getValue().getPhone()).isEqualTo("+998901234567");
            assertThat(locCaptor.getValue().isPrimary()).isTrue();
        }

        @Test
        @DisplayName("Обновление: телефон с пробелами нормализуется в normalized location")
        void updateMerchant_normalizesSpacedPhoneInNormalizedLocation() {
            Merchant existing = createTestMerchant();
            when(merchantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateMerchantRequest.LocationRequest primary = new CreateMerchantRequest.LocationRequest();
            primary.setTitle("Основной адрес");
            primary.setPhone("+998 90 999 99 99");
            primary.setPrimary(true);

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Updated SPA");
            request.setLocations(List.of(primary));

            merchantService.updateMerchant(1L, request);

            ArgumentCaptor<uz.topdim.coupon.entity.MerchantLocation> locCaptor =
                    ArgumentCaptor.forClass(uz.topdim.coupon.entity.MerchantLocation.class);
            verify(merchantLocationRepository).save(locCaptor.capture());
            assertThat(locCaptor.getValue().getPhone()).isEqualTo("+998909999999");
        }

        @Test
        @DisplayName("Обновление: сохраняет ID существующей локации и её координаты")
        void updateMerchant_existingLocation_updatesInPlace() {
            Merchant existing = createTestMerchant();
            MerchantLocation location = MerchantLocation.builder()
                    .id(10L)
                    .merchant(existing)
                    .title("Старое название")
                    .address("Старый адрес")
                    .phone("+998901111111")
                    .latitude(41.1)
                    .longitude(69.1)
                    .primary(true)
                    .active(true)
                    .build();
            when(merchantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(merchantLocationRepository.findByMerchantId(1L)).thenReturn(List.of(location));

            CreateMerchantRequest.LocationRequest locationRequest = new CreateMerchantRequest.LocationRequest();
            locationRequest.setId(10L);
            locationRequest.setTitle("Новое название");
            locationRequest.setAddress("Новый адрес");
            locationRequest.setPhone("+998 90 999 99 99");
            locationRequest.setLatitude(41.3111);
            locationRequest.setLongitude(69.2797);
            locationRequest.setPrimary(true);

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Updated SPA");
            request.setLocations(List.of(locationRequest));

            merchantService.updateMerchant(1L, request);

            verify(merchantLocationRepository).save(argThat(saved ->
                    saved == location
                            && saved.getId().equals(10L)
                            && saved.getTitle().equals("Новое название")
                            && saved.getPhone().equals("+998909999999")
                            && saved.getLatitude().equals(41.3111)
                            && saved.getLongitude().equals(69.2797)
                            && saved.isActive()));
            verify(merchantLocationRepository, never()).deleteAllByMerchantId(anyLong());
        }

        @Test
        @DisplayName("Обновление: удалённая из формы локация деактивируется без смены ID")
        void updateMerchant_omittedLocation_softDeactivates() {
            Merchant existing = createTestMerchant();
            MerchantLocation location = MerchantLocation.builder()
                    .id(10L)
                    .merchant(existing)
                    .address("Ташкент")
                    .phone("+998901111111")
                    .primary(true)
                    .active(true)
                    .build();
            when(merchantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(merchantLocationRepository.findByMerchantId(1L)).thenReturn(List.of(location));
            when(couponOfferRepository.existsByMerchantIdAndStatusIn(eq(1L), anyList())).thenReturn(false);

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Updated SPA");
            request.setLocations(List.of());

            merchantService.updateMerchant(1L, request);

            verify(merchantLocationRepository).save(argThat(saved ->
                    saved == location && saved.getId().equals(10L) && !saved.isActive() && !saved.isPrimary()));
            verify(merchantLocationRepository, never()).deleteAllByMerchantId(anyLong());
        }

        @Test
        @DisplayName("Обновление: ID локации другого мерчанта отклоняется")
        void updateMerchant_foreignLocationId_rejected() {
            Merchant existing = createTestMerchant();
            when(merchantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
            when(merchantLocationRepository.findByMerchantId(1L)).thenReturn(List.of());

            CreateMerchantRequest.LocationRequest locationRequest = new CreateMerchantRequest.LocationRequest();
            locationRequest.setId(999L);
            locationRequest.setAddress("Чужой адрес");
            locationRequest.setPrimary(true);

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Updated SPA");
            request.setLocations(List.of(locationRequest));

            assertThatThrownBy(() -> merchantService.updateMerchant(1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не принадлежит мерчанту");

            verify(merchantLocationRepository, never()).save(any());
            verify(merchantLocationRepository, never()).deleteAllByMerchantId(anyLong());
        }

        @Test
        @DisplayName("Обновление: два primary location отклоняются до удаления существующих локаций")
        void updateMerchant_rejectsMultiplePrimaryLocationsBeforeDelete() {
            Merchant existing = createTestMerchant();
            when(merchantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));

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

        @Test
        @DisplayName("Создание: locations без primary → первый location становится primary")
        void createMerchant_locationsWithoutPrimary_promotesFirst() {
            CreateMerchantRequest.LocationRequest first = new CreateMerchantRequest.LocationRequest();
            first.setAddress("Ташкент");
            first.setPhone("+998 90 123 45 67");

            CreateMerchantRequest.LocationRequest second = new CreateMerchantRequest.LocationRequest();
            second.setAddress("Самарканд");

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("SPA Oasis");
            request.setLocations(List.of(first, second));

            when(merchantRepository.save(any(Merchant.class))).thenAnswer(inv -> {
                Merchant merchant = inv.getArgument(0);
                merchant.setId(30L);
                return merchant;
            });
            when(merchantRepository.findById(30L)).thenReturn(Optional.of(
                    Merchant.builder().id(30L).name("SPA Oasis").active(true).build()));

            merchantService.createMerchant(request);

            ArgumentCaptor<uz.topdim.coupon.entity.MerchantLocation> locCaptor =
                    ArgumentCaptor.forClass(uz.topdim.coupon.entity.MerchantLocation.class);
            verify(merchantLocationRepository, times(2)).save(locCaptor.capture());
            assertThat(locCaptor.getAllValues().get(0).isPrimary()).isTrue();
            assertThat(locCaptor.getAllValues().get(0).getPhone()).isEqualTo("+998901234567");
            assertThat(locCaptor.getAllValues().get(1).isPrimary()).isFalse();
        }

        // ==================== Location Update Safety ====================

        @Test
        @DisplayName("updateMerchant: null locations preserves existing locations")
        void updateMerchant_nullLocations_preservesExistingLocations() {
            Merchant existing = createTestMerchant();
            when(merchantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of(
                    uz.topdim.coupon.entity.MerchantLocation.builder()
                            .id(10L)
                            .merchant(existing)
                            .address("Ташкент, ул. Нукус, 10")
                            .primary(true)
                            .active(true)
                            .build()
            ));

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Updated SPA");
            request.setDescription("Новое описание");
            request.setLocations(null);

            MerchantResponse result = merchantService.updateMerchant(1L, request);

            assertThat(result.getPrimaryLocation()).isNotNull();
            verify(merchantLocationRepository, never()).deleteAllByMerchantId(1L);
        }

        @Test
        @DisplayName("updateMerchant: empty locations with ACTIVE/WAITING coupons is rejected")
        void updateMerchant_emptyLocationsWithPublicationDependentCoupons_throws() {
            Merchant existing = createTestMerchant();
            when(merchantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
            when(couponOfferRepository.existsByMerchantIdAndStatusIn(
                    eq(1L),
                    eq(List.of(CouponStatus.WAITING_FOR_MERCHANT, CouponStatus.ACTIVE))
            )).thenReturn(true);

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Updated SPA");
            request.setLocations(List.of());

            assertThatThrownBy(() -> merchantService.updateMerchant(1L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Нельзя удалить все locations");

            verify(merchantLocationRepository, never()).deleteAllByMerchantId(anyLong());
        }

        @Test
        @DisplayName("updateMerchant: empty locations without dependent coupons avoids destructive delete")
        void updateMerchant_emptyLocationsWithoutDependentCoupons_avoidsDestructiveDelete() {
            Merchant existing = createTestMerchant();
            when(merchantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(couponOfferRepository.existsByMerchantIdAndStatusIn(
                    eq(1L),
                    eq(List.of(CouponStatus.WAITING_FOR_MERCHANT, CouponStatus.ACTIVE))
            )).thenReturn(false);
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of());

            CreateMerchantRequest request = new CreateMerchantRequest();
            request.setName("Updated SPA");
            request.setLocations(List.of());

            merchantService.updateMerchant(1L, request);

            verify(merchantLocationRepository, never()).deleteAllByMerchantId(anyLong());
        }
        @Test
        @DisplayName("createFromOnboarding: existing merchant by userId returns existing merchant")
        void createFromOnboarding_existingUserMerchant_returnsExisting() {
            Merchant existing = Merchant.builder()
                    .id(77L)
                    .userId(10L)
                    .name("Ali Cafe")
                    .active(true)
                    .build();

            CreateMerchantOnboardingRequest request = new CreateMerchantOnboardingRequest();
            request.setUserId(10L);
            request.setName("Ali Cafe");

            when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(existing));
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(77L)).thenReturn(List.of());

            MerchantResponse result = merchantService.createFromOnboarding(request);

            assertThat(result.getId()).isEqualTo(77L);
            verify(merchantRepository, never()).save(any(Merchant.class));
            verify(merchantLocationRepository, never()).save(any(uz.topdim.coupon.entity.MerchantLocation.class));
        }

        // ==================== Publication Readiness ====================

        @Test
        @DisplayName("Publication ready — active merchant, primary location with address and phone")
        void publicationReady_allConditions() {
            Merchant merchant = createTestMerchant();
            var primaryLoc = uz.topdim.coupon.entity.MerchantLocation.builder()
                    .id(100L).merchant(merchant).title("Главный").address("Ташкент")
                    .phone("+998901234567").primary(true).active(true).build();

            when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of(primaryLoc));

            MerchantResponse result = merchantService.getMerchantById(1L);

            assertThat(result.isPublicationReady()).isTrue();
            assertThat(result.getPublicationBlockReason()).isNull();
        }

        @Test
        @DisplayName("Publication blocked — merchant inactive")
        void publicationBlocked_merchantInactive() {
            Merchant merchant = createTestMerchant();
            merchant.setActive(false);
            var primaryLoc = uz.topdim.coupon.entity.MerchantLocation.builder()
                    .id(100L).merchant(merchant).address("Ташкент").phone("+998901234567")
                    .primary(true).active(true).build();

            when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of(primaryLoc));

            MerchantResponse result = merchantService.getMerchantById(1L);

            assertThat(result.isPublicationReady()).isFalse();
            assertThat(result.getPublicationBlockReason()).isEqualTo("Мерчант не активен");
        }

        @Test
        @DisplayName("Publication blocked — no primary location")
        void publicationBlocked_noPrimaryLocation() {
            Merchant merchant = createTestMerchant();
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of());

            MerchantResponse result = merchantService.getMerchantById(1L);

            assertThat(result.isPublicationReady()).isFalse();
            assertThat(result.getPublicationBlockReason()).isEqualTo("Нет primary location");
        }

        @Test
        @DisplayName("Publication blocked — missing address on primary location")
        void publicationBlocked_missingAddress() {
            Merchant merchant = createTestMerchant();
            var primaryLoc = uz.topdim.coupon.entity.MerchantLocation.builder()
                    .id(100L).merchant(merchant).address("").phone("+998901234567")
                    .primary(true).active(true).build();

            when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of(primaryLoc));

            MerchantResponse result = merchantService.getMerchantById(1L);

            assertThat(result.isPublicationReady()).isFalse();
            assertThat(result.getPublicationBlockReason()).isEqualTo("Не указан адрес");
        }

        @Test
        @DisplayName("Publication blocked — missing phone on primary location")
        void publicationBlocked_missingPhone() {
            Merchant merchant = createTestMerchant();
            var primaryLoc = uz.topdim.coupon.entity.MerchantLocation.builder()
                    .id(100L).merchant(merchant).address("Ташкент").phone(null)
                    .primary(true).active(true).build();

            when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of(primaryLoc));

            MerchantResponse result = merchantService.getMerchantById(1L);

            assertThat(result.isPublicationReady()).isFalse();
            assertThat(result.getPublicationBlockReason()).isEqualTo("Не указан телефон");
        }

        // ==================== Activate/Deactivate ====================

        @Test
        @DisplayName("Activate merchant — success")
        void activateMerchant_success() {
            Merchant merchant = createTestMerchant();
            merchant.setActive(false);
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
            when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of());

            merchantService.setMerchantActiveStatus(1L, true);

            verify(merchantRepository).save(argThat(m -> m.isActive()));
        }

        @Test
        @DisplayName("Deactivate merchant — success when no dependent coupons")
        void deactivateMerchant_successNoDependents() {
            Merchant merchant = createTestMerchant();
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
            when(couponOfferRepository.existsByMerchantIdAndStatusIn(eq(1L), anyList())).thenReturn(false);
            when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of());

            merchantService.setMerchantActiveStatus(1L, false);

            verify(merchantRepository).save(argThat(m -> !m.isActive()));
        }

        @Test
        @DisplayName("Deactivate merchant — blocked when active coupons exist")
        void deactivateMerchant_blockedActiveCoupons() {
            Merchant merchant = createTestMerchant();
            when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
            when(couponOfferRepository.existsByMerchantIdAndStatusIn(eq(1L), anyList())).thenReturn(true);

            assertThatThrownBy(() -> merchantService.setMerchantActiveStatus(1L, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Нельзя деактивировать мерчанта");
        }

        @Test
        @DisplayName("Set active status — merchant not found")
        void setActiveStatus_notFound() {
            when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> merchantService.setMerchantActiveStatus(999L, true))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        // ==================== Admin Paginated Search ====================

        @Test
        @DisplayName("Admin merchant page — returns summary with coupon counts")
        void adminMerchantPage_returnsSummaryWithCouponCounts() {
            Merchant merchant = createTestMerchant();
            var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
            var page = new org.springframework.data.domain.PageImpl<>(List.of(merchant), pageable, 1);
            var primaryLoc = uz.topdim.coupon.entity.MerchantLocation.builder()
                    .id(100L).merchant(merchant).address("Ташкент").phone("+998901234567")
                    .primary(true).active(true).build();

            when(merchantRepository.searchMerchants(null, null, pageable)).thenReturn(page);
            when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of(primaryLoc));
            when(couponOfferRepository.countByMerchantIdAndStatus(1L, CouponStatus.ACTIVE)).thenReturn(3L);
            when(couponOfferRepository.countByMerchantIdAndStatus(1L, CouponStatus.WAITING_FOR_MERCHANT)).thenReturn(1L);
            when(couponOfferRepository.countByMerchantId(1L)).thenReturn(5L);

            var result = merchantService.getAdminMerchantPage(null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            var summary = result.getContent().get(0);
            assertThat(summary.getName()).isEqualTo("SPA Oasis");
            assertThat(summary.isPublicationReady()).isTrue();
            assertThat(summary.getActiveCouponsCount()).isEqualTo(3L);
            assertThat(summary.getWaitingCouponsCount()).isEqualTo(1L);
            assertThat(summary.getTotalCouponsCount()).isEqualTo(5L);
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
