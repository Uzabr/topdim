package uz.topdim.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.auth.dto.AuditLogResponse;
import uz.topdim.auth.dto.CreateAdminRequest;
import uz.topdim.auth.dto.StaffResponse;
import uz.topdim.auth.entity.Role;
import uz.topdim.auth.entity.User;
import uz.topdim.auth.repository.AuditLogRepository;
import uz.topdim.auth.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long createAdmin(Long currentAdminId, CreateAdminRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email уже используется");
        }
        
        Role assignRole = request.getRole() != null ? request.getRole() : Role.ADMIN;

        User adminUser = User.builder()
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(assignRole)
                .enabled(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build();
        
        userRepository.save(adminUser);

        auditLogService.logAction(
                currentAdminId,
                "CREATE_ADMIN",
                "USER",
                adminUser.getId(),
                "Создан пользователь с ролью " + assignRole.name() + ", email: " + request.getEmail()
        );

        return adminUser.getId();
    }

    @Transactional
    public void deleteUser(Long currentAdminId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        
        userRepository.delete(user);

        auditLogService.logAction(
                currentAdminId,
                "DELETE_USER",
                "USER",
                userId,
                "Удален пользователь: " + user.getEmail()
        );
    }

    @Transactional
    public void changeRole(Long currentAdminId, Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        
        String oldRole = user.getRole().name();
        user.setRole(newRole);
        userRepository.save(user);

        auditLogService.logAction(
                currentAdminId,
                "CHANGE_ROLE",
                "USER",
                userId,
                "Роль изменена с " + oldRole + " на " + newRole.name()
        );
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(log -> AuditLogResponse.builder()
                        .id(log.getId())
                        .userId(log.getUserId())
                        .action(log.getAction())
                        .entityName(log.getEntityName())
                        .entityId(log.getEntityId())
                        .details(log.getDetails())
                        .createdAt(log.getCreatedAt())
                        .build());
    }

    @Transactional(readOnly = true)
    public Page<StaffResponse> getStaffByRole(Role role, Pageable pageable) {
        return userRepository.findByRole(role, pageable)
                .map(user -> StaffResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .role(user.getRole().name())
                        .enabled(user.isEnabled())
                        .createdAt(user.getCreatedAt())
                        .build());
    }

    @Transactional
    public void blockUser(Long currentAdminId, Long userId, boolean blocked) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        user.setEnabled(!blocked);
        userRepository.save(user);

        String action = blocked ? "BLOCK_USER" : "UNBLOCK_USER";
        auditLogService.logAction(
                currentAdminId,
                action,
                "USER",
                userId,
                (blocked ? "Заблокирован" : "Разблокирован") + " пользователь: " + user.getEmail()
        );
    }
}
