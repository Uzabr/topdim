package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.dto.AdminStaffResponse;
import uz.topdim.identity.dto.AuditLogResponse;
import uz.topdim.identity.dto.CreateAdminRequest;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.AuditLogRepository;
import uz.topdim.identity.repository.RefreshTokenRepository;
import uz.topdim.identity.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityVersionService securityVersionService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public Long createAdmin(Long currentAdminId, CreateAdminRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedPhone = normalizePhone(request.getPhone());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalStateException("Email уже используется");
        }

        if (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone)) {
            throw new IllegalStateException("Телефон уже используется");
        }

        Role assignRole = request.getRole() != null ? request.getRole() : Role.ADMIN;

        User adminUser = User.builder()
                .email(normalizedEmail)
                .phone(normalizedPhone)
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(assignRole)
                .enabled(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build();

        userRepository.save(adminUser);

        auditLogService.logAction(currentAdminId, "CREATE_ADMIN", "USER", adminUser.getId(),
                "Создан пользователь с ролью " + assignRole.name() + ", email: " + normalizedEmail);

        return adminUser.getId();
    }

    @Transactional
    public void deleteUser(Long currentAdminId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        // Cleanup Redis
        securityVersionService.removeSecurityVersion(userId);

        userRepository.delete(user);
        auditLogService.logAction(currentAdminId, "DELETE_USER", "USER", userId,
                "Удален пользователь: " + user.getEmail());
    }

    @Transactional
    public void changeRole(Long currentAdminId, Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        String oldRole = user.getRole().name();
        user.setRole(newRole);

        // Bump securityVersion — инвалидирует все access tokens (роль в JWT устарела)
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        userRepository.save(user);

        // Publish to Redis for gateway
        securityVersionService.publishSecurityVersion(user.getId(), user.getSecurityVersion());

        // Revoke all refresh tokens — force re-login с новой ролью
        refreshTokenRepository.revokeAllByUser(user);

        auditLogService.logAction(currentAdminId, "CHANGE_ROLE", "USER", userId,
                "Роль изменена с " + oldRole + " на " + newRole.name());
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(log -> AuditLogResponse.builder()
                        .id(log.getId()).userId(log.getUserId()).action(log.getAction())
                        .entityName(log.getEntityName()).entityId(log.getEntityId())
                        .details(log.getDetails()).createdAt(log.getCreatedAt()).build());
    }

    @Transactional(readOnly = true)
    public Page<AdminStaffResponse> getStaffByRole(Role role, Pageable pageable) {
        return userRepository.findByRole(role, pageable)
                .map(user -> AdminStaffResponse.builder()
                        .id(user.getId()).email(user.getEmail()).phone(user.getPhone())
                        .firstName(user.getFirstName()).lastName(user.getLastName())
                        .role(user.getRole().name()).enabled(user.isEnabled())
                        .createdAt(user.getCreatedAt()).build());
    }

    @Transactional
    public void blockUser(Long currentAdminId, Long userId, boolean blocked) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        user.setEnabled(!blocked);

        // Bump securityVersion
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        userRepository.save(user);

        // Publish to Redis for gateway
        securityVersionService.publishSecurityVersion(user.getId(), user.getSecurityVersion());

        // При блокировке — revoke все refresh tokens
        if (blocked) {
            refreshTokenRepository.revokeAllByUser(user);
        }

        String action = blocked ? "BLOCK_USER" : "UNBLOCK_USER";
        auditLogService.logAction(currentAdminId, action, "USER", userId,
                (blocked ? "Заблокирован" : "Разблокирован") + " пользователь: " + user.getEmail());
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase().trim();
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
