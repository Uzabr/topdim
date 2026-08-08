package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.topdim.identity.dto.AdminStaffResponse;
import uz.topdim.identity.dto.CreateAdminRequest;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.ResourceNotFoundException;
import uz.topdim.identity.repository.RefreshTokenRepository;
import uz.topdim.identity.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SecurityVersionService securityVersionService;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private SuperAdminService superAdminService;

    @Test
    @DisplayName("createAdmin: дубликат телефона должен отклоняться до сохранения")
    void createAdmin_duplicatePhone_throws() {
        CreateAdminRequest request = new CreateAdminRequest();
        request.setEmail("admin2@topdim.uz");
        request.setPhone("+998901234567");
        request.setPassword("AdminSafe123!");
        request.setFirstName("Admin");
        request.setRole(Role.ADMIN);

        when(userRepository.existsByEmailIgnoreCase("admin2@topdim.uz")).thenReturn(false);
        when(userRepository.existsByPhone("+998901234567")).thenReturn(true);

        assertThatThrownBy(() -> superAdminService.createAdmin(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Телефон уже используется");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("createAdmin: нормализует email и телефон перед сохранением")
    void createAdmin_normalizesEmailAndPhone() {
        CreateAdminRequest request = new CreateAdminRequest();
        request.setEmail("  Admin2@TopDim.UZ ");
        request.setPhone(" +998901112233 ");
        request.setPassword("AdminSafe123!");
        request.setFirstName("Admin");
        request.setLastName("User");
        request.setRole(Role.MODERATOR);

        when(userRepository.existsByEmailIgnoreCase("admin2@topdim.uz")).thenReturn(false);
        when(userRepository.existsByPhone("+998901112233")).thenReturn(false);
        when(passwordEncoder.encode("AdminSafe123!")).thenReturn("hashed-admin-pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(22L);
            return user;
        });

        Long createdId = superAdminService.createAdmin(7L, request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(createdId).isEqualTo(22L);
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("admin2@topdim.uz");
        assertThat(userCaptor.getValue().getPhone()).isEqualTo("+998901112233");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.MODERATOR);
    }

    @Test
    @DisplayName("getStaffByRole: возвращает сотрудников нужной роли")
    void getStaffByRole_returnsRequestedRoleOnly() {
        User moderator = User.builder()
                .id(3L)
                .email("mod@topdim.uz")
                .role(Role.MODERATOR)
                .enabled(true)
                .build();
        Page<User> page = new PageImpl<>(List.of(moderator));

        when(userRepository.findByRole(Role.MODERATOR, PageRequest.of(0, 20))).thenReturn(page);

        Page<AdminStaffResponse> response = superAdminService.getStaffByRole(Role.MODERATOR, PageRequest.of(0, 20));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getRole()).isEqualTo("MODERATOR");
    }

    @Test
    @DisplayName("createAdmin: нельзя создать сотрудника с ролью вне ADMIN/MODERATOR")
    void createAdmin_nonStaffRole_rejected() {
        CreateAdminRequest request = createRequest(Role.SUPER_ADMIN);

        assertThatThrownBy(() -> superAdminService.createAdmin(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADMIN или MODERATOR");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("changeRole: суперадминистратор не может изменить собственную роль")
    void changeRole_self_rejected() {
        assertThatThrownBy(() -> superAdminService.changeRole(7L, 7L, Role.MODERATOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("собственную роль");

        verifyNoInteractions(userRepository, securityVersionService, refreshTokenRepository, auditLogService);
    }

    @Test
    @DisplayName("changeRole: роль другого суперадминистратора защищена")
    void changeRole_superAdminTarget_rejected() {
        User target = staff(8L, Role.SUPER_ADMIN, true, 2L);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> superAdminService.changeRole(7L, 8L, Role.ADMIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPER_ADMIN");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(securityVersionService, refreshTokenRepository, auditLogService);
    }

    @Test
    @DisplayName("changeRole: неизвестный сотрудник возвращает not found")
    void changeRole_missingUser_notFound() {
        when(userRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> superAdminService.changeRole(7L, 99L, Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Пользователь не найден");
    }

    @Test
    @DisplayName("changeRole: штатная смена роли инвалидирует обе сессии и записывается в аудит")
    void changeRole_staffRole_updatesAndInvalidatesSessions() {
        User target = staff(8L, Role.MODERATOR, true, 3L);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(target));

        superAdminService.changeRole(7L, 8L, Role.ADMIN);

        assertThat(target.getRole()).isEqualTo(Role.ADMIN);
        assertThat(target.getSecurityVersion()).isEqualTo(4L);
        verify(userRepository).save(target);
        verify(securityVersionService).publishSecurityVersion(8L, 4L);
        verify(refreshTokenRepository).revokeAllByUser(target);
        verify(auditLogService).logAction(7L, "CHANGE_ROLE", "staff", 8L,
                "Роль изменена с MODERATOR на ADMIN");
    }

    @Test
    @DisplayName("changeRole: повтор той же роли идемпотентен")
    void changeRole_sameRole_noSideEffects() {
        User target = staff(8L, Role.ADMIN, true, 3L);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(target));

        superAdminService.changeRole(7L, 8L, Role.ADMIN);

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(securityVersionService, refreshTokenRepository, auditLogService);
    }

    @Test
    @DisplayName("blockUser: суперадминистратор не может заблокировать себя")
    void blockUser_self_rejected() {
        assertThatThrownBy(() -> superAdminService.blockUser(7L, 7L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("самого себя");

        verifyNoInteractions(userRepository, securityVersionService, refreshTokenRepository, auditLogService);
    }

    @Test
    @DisplayName("blockUser: другой суперадминистратор защищён от блокировки")
    void blockUser_superAdminTarget_rejected() {
        User target = staff(8L, Role.SUPER_ADMIN, true, 2L);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> superAdminService.blockUser(7L, 8L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPER_ADMIN");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(securityVersionService, refreshTokenRepository, auditLogService);
    }

    @Test
    @DisplayName("blockUser: повторная блокировка идемпотентна и не инвалидирует сессию повторно")
    void blockUser_alreadyBlocked_noSideEffects() {
        User target = staff(8L, Role.ADMIN, false, 3L);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(target));

        superAdminService.blockUser(7L, 8L, true);

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(securityVersionService, refreshTokenRepository, auditLogService);
        assertThat(target.getSecurityVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("blockUser: блокировка инвалидирует access и refresh токены и записывается в аудит")
    void blockUser_activeStaff_blocksAndInvalidatesSessions() {
        User target = staff(8L, Role.ADMIN, true, 3L);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(target));

        superAdminService.blockUser(7L, 8L, true);

        assertThat(target.isEnabled()).isFalse();
        assertThat(target.getSecurityVersion()).isEqualTo(4L);
        verify(userRepository).save(target);
        verify(securityVersionService).publishSecurityVersion(8L, 4L);
        verify(refreshTokenRepository).revokeAllByUser(target);
        verify(auditLogService).logAction(7L, "BLOCK_USER", "staff", 8L,
                "Заблокирован пользователь: staff8@topdim.uz");
    }

    @Test
    @DisplayName("deleteUser: нельзя удалить собственную учетную запись")
    void deleteUser_self_rejected() {
        assertThatThrownBy(() -> superAdminService.deleteUser(7L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("собственную учетную запись");

        verifyNoInteractions(userRepository, securityVersionService, auditLogService);
    }

    @Test
    @DisplayName("deleteUser: другой суперадминистратор защищён от удаления")
    void deleteUser_superAdminTarget_rejected() {
        User target = staff(8L, Role.SUPER_ADMIN, true, 2L);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> superAdminService.deleteUser(7L, 8L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPER_ADMIN");

        verify(userRepository, never()).delete(any(User.class));
        verifyNoInteractions(securityVersionService, auditLogService);
    }

    private CreateAdminRequest createRequest(Role role) {
        CreateAdminRequest request = new CreateAdminRequest();
        request.setEmail("new.staff@topdim.uz");
        request.setPassword("SafeAdmin9!");
        request.setFirstName("New");
        request.setRole(role);
        return request;
    }

    private User staff(Long id, Role role, boolean enabled, long securityVersion) {
        return User.builder()
                .id(id)
                .email("staff" + id + "@topdim.uz")
                .firstName("Staff")
                .password("hash")
                .role(role)
                .enabled(enabled)
                .securityVersion(securityVersion)
                .build();
    }
}
