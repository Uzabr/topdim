package uz.topdim.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.auth.dto.*;
import uz.topdim.auth.service.AuthService;
import uz.topdim.common.dto.ApiResponse;

/**
 * REST контроллер аутентификации.
 * Endpoints: register, login, refresh, logout.
 * Все endpoints без JWT (кроме logout).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/v1/auth/register — Регистрация нового пользователя.
     *
     * @param request email, phone, password, firstName, lastName
     * @return 201 Created с токенами и данными пользователя
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Регистрация прошла успешно", response));
    }

    /**
     * POST /api/v1/auth/login — Вход в систему.
     *
     * @param request email и password
     * @return 200 OK с access/refresh токенами
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Вход выполнен успешно", response));
    }

    /**
     * POST /api/v1/auth/refresh — Обновление access token.
     *
     * @param request содержит refreshToken
     * @return 200 OK с новой парой токенов
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Токен обновлён", response));
    }

    /**
     * POST /api/v1/auth/logout — Выход из системы.
     * Отзывает refresh token.
     *
     * @param request содержит refreshToken
     * @return 200 OK
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Выход выполнен", null));
    }

    /**
     * PUT /api/v1/auth/change-password — Смена пароля.
     * Требует JWT (X-User-Id от Gateway).
     *
     * @param userId ID пользователя из JWT header
     * @param request currentPassword, newPassword, confirmPassword
     * @return 200 OK
     */
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Пароль успешно изменён", null));
    }

    /**
     * POST /api/v1/auth/guest — Гостевая аутентификация (Silent Registration).
     * Создаёт GUEST-пользователя по телефону и возвращает JWT.
     *
     * @param request телефон и имя
     * @return 200 OK с токенами
     */
    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<AuthResponse>> guestAuth(@Valid @RequestBody GuestAuthRequest request) {
        AuthResponse response = authService.guestAuth(request);
        return ResponseEntity.ok(ApiResponse.success("Гостевой доступ предоставлен", response));
    }
}
