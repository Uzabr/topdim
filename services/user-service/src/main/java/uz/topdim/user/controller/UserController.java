package uz.topdim.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.user.dto.*;
import uz.topdim.user.service.UserService;

import java.util.List;
import java.util.Map;

/**
 * REST контроллер пользователей.
 * Endpoints: profile (GET/PUT), favorites (GET/POST/DELETE).
 * Все endpoints требуют JWT.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ==================== Profile ====================

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(userId)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Профиль обновлён", userService.updateProfile(userId, request)));
    }

    // ==================== Favorites ====================

    @GetMapping("/me/favorites")
    public ResponseEntity<ApiResponse<List<FavoriteResponse>>> getFavorites(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(userService.getFavorites(userId)));
    }

    @PostMapping("/me/favorites")
    public ResponseEntity<ApiResponse<FavoriteResponse>> addFavorite(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, Long> request
    ) {
        FavoriteResponse favorite = userService.addFavorite(userId, request.get("couponOfferId"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Добавлено в избранное", favorite));
    }

    @DeleteMapping("/me/favorites/{couponOfferId}")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long couponOfferId
    ) {
        userService.removeFavorite(userId, couponOfferId);
        return ResponseEntity.ok(ApiResponse.success("Удалено из избранного", null));
    }

}
