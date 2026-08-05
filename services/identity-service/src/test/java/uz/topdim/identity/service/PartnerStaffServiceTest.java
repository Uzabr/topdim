package uz.topdim.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.identity.client.CouponMerchantClient;
import uz.topdim.identity.client.MerchantLocationResponse;
import uz.topdim.identity.client.MerchantOnboardingResponse;
import uz.topdim.identity.dto.CreateStaffRequest;
import uz.topdim.identity.dto.PartnerAccessContextResponse;
import uz.topdim.identity.dto.PartnerStaffResponse;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.Staff;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.ResourceNotFoundException;
import uz.topdim.identity.repository.RefreshTokenRepository;
import uz.topdim.identity.repository.StaffRepository;
import uz.topdim.identity.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerStaffServiceTest {

    @Mock private StaffRepository staffRepository;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private SecurityVersionService securityVersionService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CouponMerchantClient couponMerchantClient;

    @InjectMocks private PartnerStaffService partnerStaffService;

    private static final Long OWNER_USER_ID = 10L;
    private static final Long CASHIER_LOGIN_USER_ID = 20L;
    private static final Long MERCHANT_ID = 100L;
    private static final Long LOCATION_ID = 200L;

    private Staff createCashierStaff() {
        return Staff.builder()
                .id(1L).userId(OWNER_USER_ID).loginUserId(CASHIER_LOGIN_USER_ID)
                .name("Кассир Али").phone("+998901234567")
                .role("CASHIER").merchantId(MERCHANT_ID).merchantLocationId(LOCATION_ID)
                .active(true).build();
    }

    private void mockOwnerMerchantResolution() {
        var merchantResponse = new MerchantOnboardingResponse(MERCHANT_ID, "Test Shop", OWNER_USER_ID, true);
        when(couponMerchantClient.getMerchantByUserId(OWNER_USER_ID))
                .thenReturn(ApiResponse.success(merchantResponse));
    }

    private void mockOwnerLocationResolution(Long locationId) {
        var location = new MerchantLocationResponse(
                locationId, "Главный филиал", "Ташкент", "+998901234567", "09:00-22:00", true, true);
        when(couponMerchantClient.getMerchantLocationsByUserId(OWNER_USER_ID))
                .thenReturn(ApiResponse.success(List.of(location)));
    }

    // ==================== Access Context ====================

    @Nested
    @DisplayName("resolveAccessContext")
    class AccessContextTests {

        @Test
        @DisplayName("Owner — user is merchant owner, returns OWNER context")
        void ownerContext_success() {
            when(staffRepository.findByLoginUserId(OWNER_USER_ID)).thenReturn(Optional.empty());
            mockOwnerMerchantResolution();

            PartnerAccessContextResponse ctx = partnerStaffService.resolveAccessContext(OWNER_USER_ID);

            assertThat(ctx.getRole()).isEqualTo("OWNER");
            assertThat(ctx.getMerchantId()).isEqualTo(MERCHANT_ID);
            assertThat(ctx.getMerchantLocationId()).isNull();
            assertThat(ctx.isCanViewDashboard()).isTrue();
            assertThat(ctx.isCanRedeem()).isTrue();
        }

        @Test
        @DisplayName("Inactive owner merchant — access context is rejected")
        void ownerContext_inactiveMerchant_rejected() {
            when(staffRepository.findByLoginUserId(OWNER_USER_ID)).thenReturn(Optional.empty());
            when(couponMerchantClient.getMerchantByUserId(OWNER_USER_ID))
                    .thenReturn(ApiResponse.success(
                            new MerchantOnboardingResponse(MERCHANT_ID, "Disabled Shop", OWNER_USER_ID, false)));

            assertThatThrownBy(() -> partnerStaffService.resolveAccessContext(OWNER_USER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("не активен");
        }

        @Test
        @DisplayName("Cashier — staff login user, returns CASHIER context with location")
        void cashierContext_success() {
            Staff cashier = createCashierStaff();
            when(staffRepository.findByLoginUserId(CASHIER_LOGIN_USER_ID)).thenReturn(Optional.of(cashier));
            mockOwnerMerchantResolution();
            mockOwnerLocationResolution(LOCATION_ID);

            PartnerAccessContextResponse ctx = partnerStaffService.resolveAccessContext(CASHIER_LOGIN_USER_ID);

            assertThat(ctx.getRole()).isEqualTo("CASHIER");
            assertThat(ctx.getMerchantId()).isEqualTo(MERCHANT_ID);
            assertThat(ctx.getMerchantLocationId()).isEqualTo(LOCATION_ID);
            assertThat(ctx.getStaffId()).isEqualTo(1L);
            assertThat(ctx.getStaffName()).isEqualTo("Кассир Али");
            assertThat(ctx.isCanViewDashboard()).isFalse();
            assertThat(ctx.isCanRedeem()).isTrue();
        }

        @Test
        @DisplayName("Cashier of inactive merchant — access context is rejected")
        void cashierContext_inactiveMerchant_rejected() {
            Staff cashier = createCashierStaff();
            when(staffRepository.findByLoginUserId(CASHIER_LOGIN_USER_ID)).thenReturn(Optional.of(cashier));
            when(couponMerchantClient.getMerchantByUserId(OWNER_USER_ID))
                    .thenReturn(ApiResponse.success(
                            new MerchantOnboardingResponse(MERCHANT_ID, "Disabled Shop", OWNER_USER_ID, false)));

            assertThatThrownBy(() -> partnerStaffService.resolveAccessContext(CASHIER_LOGIN_USER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("не активен");
        }

        @Test
        @DisplayName("Cashier bound to foreign or inactive location — access context is rejected")
        void cashierContext_unknownLocation_rejected() {
            Staff cashier = createCashierStaff();
            when(staffRepository.findByLoginUserId(CASHIER_LOGIN_USER_ID)).thenReturn(Optional.of(cashier));
            mockOwnerMerchantResolution();
            when(couponMerchantClient.getMerchantLocationsByUserId(OWNER_USER_ID))
                    .thenReturn(ApiResponse.success(List.of()));

            assertThatThrownBy(() -> partnerStaffService.resolveAccessContext(CASHIER_LOGIN_USER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("не принадлежит мерчанту или не активен");
        }

        @Test
        @DisplayName("Inactive cashier — rejected")
        void inactiveCashier_rejected() {
            Staff cashier = createCashierStaff();
            cashier.setActive(false);
            when(staffRepository.findByLoginUserId(CASHIER_LOGIN_USER_ID)).thenReturn(Optional.of(cashier));

            assertThatThrownBy(() -> partnerStaffService.resolveAccessContext(CASHIER_LOGIN_USER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("деактивирован");
        }

        @Test
        @DisplayName("Cashier without location — rejected")
        void cashierWithoutLocation_rejected() {
            Staff cashier = createCashierStaff();
            cashier.setMerchantLocationId(null);
            when(staffRepository.findByLoginUserId(CASHIER_LOGIN_USER_ID)).thenReturn(Optional.of(cashier));

            assertThatThrownBy(() -> partnerStaffService.resolveAccessContext(CASHIER_LOGIN_USER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("не привязан к филиалу");
        }

        @Test
        @DisplayName("Manager — staff with MANAGER role, can view dashboard")
        void managerContext_canViewDashboard() {
            Staff manager = createCashierStaff();
            manager.setRole("MANAGER");
            manager.setMerchantLocationId(null);
            when(staffRepository.findByLoginUserId(CASHIER_LOGIN_USER_ID)).thenReturn(Optional.of(manager));
            mockOwnerMerchantResolution();

            PartnerAccessContextResponse ctx = partnerStaffService.resolveAccessContext(CASHIER_LOGIN_USER_ID);

            assertThat(ctx.getRole()).isEqualTo("MANAGER");
            assertThat(ctx.isCanViewDashboard()).isTrue();
            assertThat(ctx.isCanRedeem()).isTrue();
        }

        @Test
        @DisplayName("Unknown user — no merchant found → ResourceNotFoundException")
        void unknownUser_noMerchant() {
            when(staffRepository.findByLoginUserId(999L)).thenReturn(Optional.empty());
            when(couponMerchantClient.getMerchantByUserId(999L)).thenThrow(new RuntimeException("not found"));

            assertThatThrownBy(() -> partnerStaffService.resolveAccessContext(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Add Staff ====================

    @Nested
    @DisplayName("addStaff")
    class AddStaffTests {

        @Test
        @DisplayName("Add cashier with login credentials — creates user and staff")
        void addCashierWithLogin() {
            mockOwnerMerchantResolution();
            mockOwnerLocationResolution(LOCATION_ID);

            CreateStaffRequest request = new CreateStaffRequest();
            request.setName("Новый Кассир");
            request.setPhone("+998900000000");
            request.setRole("CASHIER");
            request.setLoginEmail("cashier@test.com");
            request.setTemporaryPassword("temp123");
            request.setMerchantLocationId(LOCATION_ID);

            when(userRepository.existsByEmailIgnoreCase("cashier@test.com")).thenReturn(false);
            when(passwordEncoder.encode("temp123")).thenReturn("$2a$encoded");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(30L);
                return u;
            });
            when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> {
                Staff s = inv.getArgument(0);
                s.setId(5L);
                return s;
            });

            PartnerStaffResponse result = partnerStaffService.addStaff(OWNER_USER_ID, request);

            assertThat(result.getName()).isEqualTo("Новый Кассир");
            verify(userRepository).save(argThat(u ->
                    u.getEmail().equals("cashier@test.com") && u.getRole() == Role.PARTNER));
            verify(staffRepository).save(argThat(s ->
                    s.getLoginUserId().equals(30L) && s.getMerchantId().equals(MERCHANT_ID)));
        }

        @Test
        @DisplayName("Add cashier with duplicate email — rejected")
        void addCashierDuplicateEmail() {
            mockOwnerMerchantResolution();
            mockOwnerLocationResolution(LOCATION_ID);

            CreateStaffRequest request = new CreateStaffRequest();
            request.setName("Кассир");
            request.setPhone("+998900000001");
            request.setLoginEmail("existing@test.com");
            request.setTemporaryPassword("temp123");
            request.setMerchantLocationId(LOCATION_ID);

            when(userRepository.existsByEmailIgnoreCase("existing@test.com")).thenReturn(true);

            assertThatThrownBy(() -> partnerStaffService.addStaff(OWNER_USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("уже существует");
        }

        @Test
        @DisplayName("Add cashier with foreign or inactive location — rejected before creating login user")
        void addCashier_unknownLocation_rejected() {
            mockOwnerMerchantResolution();
            when(couponMerchantClient.getMerchantLocationsByUserId(OWNER_USER_ID))
                    .thenReturn(ApiResponse.success(List.of()));

            CreateStaffRequest request = new CreateStaffRequest();
            request.setName("Кассир Чужого Филиала");
            request.setPhone("+998900000010");
            request.setRole("CASHIER");
            request.setLoginEmail("foreign-location@test.com");
            request.setTemporaryPassword("password123");
            request.setMerchantLocationId(999L);

            assertThatThrownBy(() -> partnerStaffService.addStaff(OWNER_USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не принадлежит мерчанту или не активен");

            verify(userRepository, never()).save(any());
            verify(staffRepository, never()).save(any());
        }

        @Test
        @DisplayName("Add staff without login credentials — no user created")
        void addStaffWithoutLogin() {
            mockOwnerMerchantResolution();

            CreateStaffRequest request = new CreateStaffRequest();
            request.setName("Менеджер");
            request.setPhone("+998900000002");
            request.setRole("MANAGER");

            when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> {
                Staff s = inv.getArgument(0);
                s.setId(6L);
                return s;
            });

            PartnerStaffResponse result = partnerStaffService.addStaff(OWNER_USER_ID, request);

            assertThat(result.getRole()).isEqualTo("MANAGER");
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Add cashier without merchantLocationId — rejected (beta rule)")
        void addStaff_cashierWithoutLocation_throws() {
            mockOwnerMerchantResolution();

            CreateStaffRequest request = new CreateStaffRequest();
            request.setName("Кассир Без Филиала");
            request.setPhone("+998900000003");
            request.setRole("CASHIER");
            request.setLoginEmail("noloc@test.com");
            request.setTemporaryPassword("password123");
            request.setMerchantLocationId(null);

            assertThatThrownBy(() -> partnerStaffService.addStaff(OWNER_USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("привязан к филиалу");
        }

        @Test
        @DisplayName("Add cashier without loginEmail — rejected (beta rule)")
        void addStaff_cashierWithoutLoginEmail_throws() {
            mockOwnerMerchantResolution();

            CreateStaffRequest request = new CreateStaffRequest();
            request.setName("Кассир Без Email");
            request.setPhone("+998900000004");
            request.setRole("CASHIER");
            request.setMerchantLocationId(LOCATION_ID);
            request.setTemporaryPassword("password123");
            // loginEmail = null

            assertThatThrownBy(() -> partnerStaffService.addStaff(OWNER_USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("email для входа");
        }

        @Test
        @DisplayName("Add cashier without temporaryPassword — rejected (beta rule)")
        void addStaff_cashierWithoutTemporaryPassword_throws() {
            mockOwnerMerchantResolution();

            CreateStaffRequest request = new CreateStaffRequest();
            request.setName("Кассир Без Пароля");
            request.setPhone("+998900000005");
            request.setRole("CASHIER");
            request.setMerchantLocationId(LOCATION_ID);
            request.setLoginEmail("nopass@test.com");
            // temporaryPassword = null

            assertThatThrownBy(() -> partnerStaffService.addStaff(OWNER_USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не короче 6 символов");
        }

        @Test
        @DisplayName("Add cashier with short password (< 6 chars) — rejected (beta rule)")
        void addStaff_cashierWithShortPassword_throws() {
            mockOwnerMerchantResolution();

            CreateStaffRequest request = new CreateStaffRequest();
            request.setName("Кассир Короткий Пароль");
            request.setPhone("+998900000006");
            request.setRole("CASHIER");
            request.setMerchantLocationId(LOCATION_ID);
            request.setLoginEmail("short@test.com");
            request.setTemporaryPassword("12345"); // only 5 chars

            assertThatThrownBy(() -> partnerStaffService.addStaff(OWNER_USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не короче 6 символов");
        }
    }

    // ==================== Remove Staff ====================

    @Nested
    @DisplayName("removeStaff")
    class RemoveStaffTests {

        @Test
        @DisplayName("Remove cashier — disables staff login and revokes all sessions")
        void removeStaff_disablesLoginAndRevokesSessions() {
            Staff staff = createCashierStaff();
            User loginUser = User.builder()
                    .id(CASHIER_LOGIN_USER_ID)
                    .email("cashier@test.com")
                    .password("encoded")
                    .firstName("Кассир Али")
                    .role(Role.PARTNER)
                    .enabled(true)
                    .securityVersion(3L)
                    .build();
            mockOwnerMerchantResolution();
            when(staffRepository.findByUserIdAndId(OWNER_USER_ID, 1L)).thenReturn(Optional.of(staff));
            when(userRepository.findByIdForUpdate(CASHIER_LOGIN_USER_ID)).thenReturn(Optional.of(loginUser));
            when(staffRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            partnerStaffService.removeStaff(OWNER_USER_ID, 1L);

            assertThat(staff.isActive()).isFalse();
            assertThat(loginUser.isEnabled()).isFalse();
            assertThat(loginUser.getSecurityVersion()).isEqualTo(4L);
            verify(staffRepository).save(staff);
            verify(userRepository).save(loginUser);
            verify(securityVersionService).publishSecurityVersion(CASHIER_LOGIN_USER_ID, 4L);
            verify(refreshTokenRepository).revokeAllByUser(loginUser);
        }

        @Test
        @DisplayName("Remove cashier retry — keeps disabled state and still revokes stray refresh tokens")
        void removeStaff_alreadyDisabled_isIdempotent() {
            Staff staff = createCashierStaff();
            staff.setActive(false);
            User loginUser = User.builder()
                    .id(CASHIER_LOGIN_USER_ID)
                    .email("cashier@test.com")
                    .password("encoded")
                    .firstName("Кассир Али")
                    .role(Role.PARTNER)
                    .enabled(false)
                    .securityVersion(4L)
                    .build();
            mockOwnerMerchantResolution();
            when(staffRepository.findByUserIdAndId(OWNER_USER_ID, 1L)).thenReturn(Optional.of(staff));
            when(userRepository.findByIdForUpdate(CASHIER_LOGIN_USER_ID)).thenReturn(Optional.of(loginUser));

            partnerStaffService.removeStaff(OWNER_USER_ID, 1L);

            assertThat(loginUser.getSecurityVersion()).isEqualTo(4L);
            verify(staffRepository, never()).save(any());
            verify(userRepository, never()).save(any());
            verifyNoInteractions(securityVersionService);
            verify(refreshTokenRepository).revokeAllByUser(loginUser);
        }

        @Test
        @DisplayName("Remove legacy staff without login — only deactivates staff record")
        void removeStaff_withoutLoginUser_onlyDeactivatesStaff() {
            Staff staff = createCashierStaff();
            staff.setLoginUserId(null);
            mockOwnerMerchantResolution();
            when(staffRepository.findByUserIdAndId(OWNER_USER_ID, 1L)).thenReturn(Optional.of(staff));
            when(staffRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            partnerStaffService.removeStaff(OWNER_USER_ID, 1L);

            assertThat(staff.isActive()).isFalse();
            verify(staffRepository).save(staff);
            verifyNoInteractions(userRepository, securityVersionService, refreshTokenRepository);
        }

        @Test
        @DisplayName("Remove cashier with missing login user — rejects inconsistent partial update")
        void removeStaff_missingLoginUser_rejectsWithoutDeactivatingStaff() {
            Staff staff = createCashierStaff();
            mockOwnerMerchantResolution();
            when(staffRepository.findByUserIdAndId(OWNER_USER_ID, 1L)).thenReturn(Optional.of(staff));
            when(userRepository.findByIdForUpdate(CASHIER_LOGIN_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> partnerStaffService.removeStaff(OWNER_USER_ID, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Учётная запись сотрудника не найдена");

            assertThat(staff.isActive()).isTrue();
            verify(staffRepository, never()).save(any());
            verify(userRepository, never()).save(any());
            verifyNoInteractions(securityVersionService, refreshTokenRepository);
        }

        @Test
        @DisplayName("Remove non-existent staff — ResourceNotFoundException")
        void removeStaff_notFound() {
            mockOwnerMerchantResolution();
            when(staffRepository.findByUserIdAndId(OWNER_USER_ID, 999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> partnerStaffService.removeStaff(OWNER_USER_ID, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
