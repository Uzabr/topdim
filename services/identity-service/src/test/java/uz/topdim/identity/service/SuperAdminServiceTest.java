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
import uz.topdim.identity.repository.AuditLogRepository;
import uz.topdim.identity.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private PasswordEncoder passwordEncoder;

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
}
