package uz.topdim.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.identity.dto.AdminUserResponse;
import uz.topdim.identity.dto.BlockUserRequest;
import uz.topdim.identity.service.UserService;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getAllUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers(role, search, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUserAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByIdAdmin(id)));
    }

    @PatchMapping("/{id}/block")
    public ResponseEntity<ApiResponse<AdminUserResponse>> blockUser(
            @RequestHeader("X-User-Id") Long actorId,
            @PathVariable Long id,
            @Valid @RequestBody BlockUserRequest request) {
        boolean blocked = request.getBlocked();
        AdminUserResponse user = userService.blockUser(actorId, id, blocked);
        String message = blocked ? "Пользователь заблокирован" : "Пользователь разблокирован";
        return ResponseEntity.ok(ApiResponse.success(message, user));
    }
}
