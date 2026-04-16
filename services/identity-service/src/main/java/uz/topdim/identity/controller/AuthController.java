package uz.topdim.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.identity.dto.*;
import uz.topdim.identity.exception.AuthException;
import uz.topdim.identity.service.AuthService;
import uz.topdim.identity.service.EmailConfirmationService;
import uz.topdim.identity.service.PasswordResetService;
import uz.topdim.common.dto.ApiResponse;

/**
 * REST контроллер аутентификации.
 * Endpoints: register, login, refresh, logout, change-password, guest,
 * password-reset/request, password-reset/confirm.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailConfirmationService emailConfirmationService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Регистрация прошла успешно", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Вход выполнен успешно", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Токен обновлён", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        // Извлекаем access token из Authorization header для blacklist jti
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        authService.logout(accessToken, request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Выход выполнен", null));
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Пароль успешно изменён", null));
    }

    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<AuthResponse>> guestAuth(@Valid @RequestBody GuestAuthRequest request) {
        AuthResponse response = authService.guestAuth(request);
        return ResponseEntity.ok(ApiResponse.success("Гостевой доступ предоставлен", response));
    }

    /**
     * Запрос сброса пароля.
     * Всегда 202 — защита от enumeration (не раскрываем, существует ли email).
     */
    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        "Если указанный email зарегистрирован, вы получите инструкции по сбросу пароля",
                        null));
    }

    /**
     * Подтверждение сброса пароля.
     * Токен одноразовый, неистёкший, правильного типа.
     */
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AuthException("Новый пароль и подтверждение не совпадают");
        }
        passwordResetService.confirmReset(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Пароль успешно сброшен. Войдите с новым паролем.", null));
    }

    // ==================== Email Confirmation ====================

    /**
     * Запрос отправки кода подтверждения email.
     * Cooldown 60 секунд между повторными запросами.
     */
    @PostMapping("/confirm/request")
    public ResponseEntity<ApiResponse<Void>> requestEmailConfirmation(
            @RequestHeader("X-User-Id") Long userId) {
        emailConfirmationService.requestEmailConfirmation(userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Код подтверждения отправлен на ваш email", null));
    }

    /**
     * Подтверждение email.
     */
    @PostMapping("/confirm/email")
    public ResponseEntity<ApiResponse<Void>> confirmEmail(
            @Valid @RequestBody EmailConfirmRequest request) {
        emailConfirmationService.confirmEmail(request.getToken());
        return ResponseEntity.ok(ApiResponse.success("Email успешно подтверждён", null));
    }
}
