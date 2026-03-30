package uz.topdim.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.user.dto.AdminUserResponse;
import uz.topdim.user.service.UserService;

import java.util.Map;

/**
 * REST контроллер для администраторов (управление пользователями).
 * Endpoints: получение списка пользователей, блокировка.
 * Требует роль ADMIN или SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    /** Список пользователей с пагинацией и фильтрами (Admin). */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getAllUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers(role, search, page, size)));
    }

    /** Детали пользователя по ID (Admin). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUserAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByIdAdmin(id)));
    }

    /** Блокировка/разблокировка пользователя (Admin). */
    @PatchMapping("/{id}/block")
    public ResponseEntity<ApiResponse<AdminUserResponse>> blockUser(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> request
    ) {
        boolean blocked = Boolean.TRUE.equals(request.get("blocked"));
        AdminUserResponse user = userService.blockUser(id, blocked);
        String message = blocked ? "Пользователь заблокирован" : "Пользователь разблокирован";
        return ResponseEntity.ok(ApiResponse.success(message, user));
    }
}
