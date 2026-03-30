package uz.topdim.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.auth.dto.AuditLogResponse;
import uz.topdim.auth.dto.ChangeRoleRequest;
import uz.topdim.auth.dto.CreateAdminRequest;
import uz.topdim.auth.service.SuperAdminService;
import uz.topdim.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/v1/super")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    @PostMapping("/admins")
    public ResponseEntity<ApiResponse<Long>> createAdmin(
            @RequestHeader("X-User-Id") Long currentAdminId,
            @Valid @RequestBody CreateAdminRequest request
    ) {
        Long newAdminId = superAdminService.createAdmin(currentAdminId, request);
        return ResponseEntity.ok(ApiResponse.success("Администратор успешно создан", newAdminId));
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

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AuditLogResponse> logs = superAdminService.getAuditLogs(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
