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
import org.springframework.data.jpa.domain.Specification;
import uz.topdim.identity.dto.AuditLogFilterRequest;
import uz.topdim.identity.dto.AuditLogResponse;
import uz.topdim.identity.entity.AuditLog;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.AuditLogRepository;
import uz.topdim.identity.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    @DisplayName("logAction сохраняет snapshot email/name/role инициатора")
    void logAction_storesActorSnapshot() {
        User actor = User.builder()
                .id(7L)
                .email("admin@topdim.uz")
                .firstName("Admin")
                .lastName("Tester")
                .role(Role.ADMIN)
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditLogService.logAction(7L, "BLOCK_USER", "users", 42L, "blocked");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getUserEmail()).isEqualTo("admin@topdim.uz");
        assertThat(saved.getUserName()).isEqualTo("Admin Tester");
        assertThat(saved.getUserRole()).isEqualTo("ADMIN");
        assertThat(saved.getAction()).isEqualTo("BLOCK_USER");
        assertThat(saved.getEntityName()).isEqualTo("users");
        assertThat(saved.getEntityId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("search возвращает обогащённый ответ")
    void search_mapsResponse() {
        AuditLog log = AuditLog.builder()
                .id(1L)
                .userId(7L)
                .userEmail("admin@topdim.uz")
                .userName("Admin Tester")
                .userRole("ADMIN")
                .action("CHANGE_ROLE")
                .entityName("staff")
                .entityId(8L)
                .details("role changed")
                .build();
        when(auditLogRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditLogResponse> page = auditLogService.search(
                AuditLogFilterRequest.builder().build(),
                PageRequest.of(0, 20)
        );

        assertThat(page.getContent()).hasSize(1);
        AuditLogResponse row = page.getContent().getFirst();
        assertThat(row.getUserEmail()).isEqualTo("admin@topdim.uz");
        assertThat(row.getUserRole()).isEqualTo("ADMIN");
        assertThat(row.getEntityName()).isEqualTo("staff");
    }
}
