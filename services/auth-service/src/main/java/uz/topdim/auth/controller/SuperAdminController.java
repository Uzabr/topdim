package uz.topdim.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.auth.dto.AuditLogResponse;
import uz.topdim.auth.dto.ChangeRoleRequest;
import uz.topdim.auth.dto.CreateAdminRequest;
import uz.topdim.auth.dto.StaffResponse;
import uz.topdim.auth.entity.Role;
import uz.topdim.auth.service.SuperAdminService;
import uz.topdim.common.dto.ApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/super")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    /** Список сотрудников по роли (ADMIN, MODERATOR). */
    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<Page<StaffResponse>>> getStaff(
            @RequestParam(defaultValue = "ADMIN") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Role staffRole = Role.valueOf(role.toUpperCase());
        Page<StaffResponse> staff = superAdminService.getStaffByRole(
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

    /** Блокировка/разблокировка сотрудника. */
    @PatchMapping("/users/{id}/block")
    public ResponseEntity<ApiResponse<Void>> blockUser(
            @RequestHeader("X-User-Id") Long currentAdminId,
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> request
    ) {
        boolean blocked = Boolean.TRUE.equals(request.get("blocked"));
        superAdminService.blockUser(currentAdminId, id, blocked);
        String message = blocked ? "Сотрудник заблокирован" : "Сотрудник разблокирован";
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AuditLogResponse> logs = superAdminService.getAuditLogs(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}

