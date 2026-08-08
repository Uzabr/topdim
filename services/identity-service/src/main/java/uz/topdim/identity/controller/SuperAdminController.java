package uz.topdim.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.identity.dto.AdminStaffResponse;
import uz.topdim.identity.dto.AuditLogResponse;
import uz.topdim.identity.dto.BlockUserRequest;
import uz.topdim.identity.dto.ChangeRoleRequest;
import uz.topdim.identity.dto.CreateAdminRequest;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.service.SuperAdminService;
import uz.topdim.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/v1/super")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<Page<AdminStaffResponse>>> getStaff(
            @RequestParam(defaultValue = "ADMIN") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePage(page, size);
        Role staffRole = parseStaffRole(role);
        Page<AdminStaffResponse> staff = superAdminService.getStaffByRole(
                staffRole, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(staff));
    }

    @PostMapping("/admins")
    public ResponseEntity<ApiResponse<Long>> createAdmin(
            @RequestHeader("X-User-Id") Long currentAdminId,
            @Valid @RequestBody CreateAdminRequest request
    ) {
        Long newAdminId = superAdminService.createAdmin(currentAdminId, request);
        return ResponseEntity.ok(ApiResponse.success("Сотрудник успешно создан", newAdminId));
    }

    @DeleteMapping("/admins/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAdmin(
            @RequestHeader("X-User-Id") Long currentAdminId,
            @PathVariable Long id
    ) {
        superAdminService.deleteUser(currentAdminId, id);
        return ResponseEntity.ok(ApiResponse.success("Пользователь удален", null));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<Void>> changeUserRole(
            @RequestHeader("X-User-Id") Long currentAdminId,
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request
    ) {
        superAdminService.changeRole(currentAdminId, id, request.getNewRole());
        return ResponseEntity.ok(ApiResponse.success("Роль успешно изменена", null));
    }

    @PatchMapping("/users/{id}/block")
    public ResponseEntity<ApiResponse<Void>> blockUser(
            @RequestHeader("X-User-Id") Long currentAdminId,
            @PathVariable Long id,
            @Valid @RequestBody BlockUserRequest request
    ) {
        boolean blocked = request.getBlocked();
        superAdminService.blockUser(currentAdminId, id, blocked);
        String message = blocked ? "Сотрудник заблокирован" : "Сотрудник разблокирован";
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePage(page, size);
        Page<AuditLogResponse> logs = superAdminService.getAuditLogs(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    private Role parseStaffRole(String role) {
        if (role == null) {
            throw new IllegalArgumentException("Допустимы только роли ADMIN или MODERATOR");
        }
        try {
            Role parsed = Role.valueOf(role.trim().toUpperCase());
            if (parsed == Role.ADMIN || parsed == Role.MODERATOR) {
                return parsed;
            }
        } catch (IllegalArgumentException ignored) {
            // Единый понятный ответ для неизвестных и нештатных ролей.
        }
        throw new IllegalArgumentException("Допустимы только роли ADMIN или MODERATOR");
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Номер страницы не может быть отрицательным");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Размер страницы должен быть от 1 до 100");
        }
    }
}
