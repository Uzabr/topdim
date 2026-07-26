package uz.topdim.identity.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.identity.dto.*;
import uz.topdim.identity.exception.AuthException;
import uz.topdim.identity.service.AuthService;
import uz.topdim.identity.service.EmailConfirmationService;
import uz.topdim.identity.service.OtpService;
import uz.topdim.identity.service.PasswordResetService;
import uz.topdim.common.dto.ApiResponse;

import java.time.Duration;
import java.util.Arrays;

/**
 * REST контроллер аутентификации.
 * M4: refresh token передаётся через httpOnly cookie, не в JSON body.
 *
 * <p>Endpoints: register, login, refresh, logout, change-password,
 * phone/request + phone/confirm (T5 — телефон-OTP вход/регистрация/восстановление),
 * password-reset/request, password-reset/confirm.
 * {@code /guest} депрекирован (410 Gone) — небезопасный вход без проверки владения.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailConfirmationService emailConfirmationService;
    private final OtpService otpService;

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final Duration REFRESH_TOKEN_MAX_AGE = Duration.ofDays(7);

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.register(request);
        addRefreshTokenCookie(response, authResponse.getRefreshToken());
        authResponse.setRefreshToken(null); // не отдаём в JSON body
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Регистрация прошла успешно", authResponse));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        addRefreshTokenCookie(response, authResponse.getRefreshToken());
        authResponse.setRefreshToken(null);
        return ResponseEntity.ok(ApiResponse.success("Вход выполнен успешно", authResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken == null) {
            throw new AuthException("Refresh token отсутствует");
        }
        RefreshTokenRequest tokenRequest = new RefreshTokenRequest();
        tokenRequest.setRefreshToken(refreshToken);
        AuthResponse authResponse = authService.refreshToken(tokenRequest);
        addRefreshTokenCookie(response, authResponse.getRefreshToken());
        authResponse.setRefreshToken(null);
        return ResponseEntity.ok(ApiResponse.success("Токен обновлён", authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request,
            HttpServletResponse response) {
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        String refreshToken = extractRefreshTokenFromCookie(request);
        authService.logout(accessToken, refreshToken);
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.success("Выход выполнен", null));
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Пароль успешно изменён", null));
    }

    /**
     * Гостевой вход ОТКЛЮЧЁН (T5): позволял войти без проверки владения телефоном/email —
     * небезопасно. Маршрут оставлен физически (не 404/500), чтобы старые клиенты получили
     * явный сигнал вместо неожиданной ошибки. Используйте /auth/phone/request + /auth/phone/confirm.
     */
    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<Void>> guestAuth() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.error("Гостевой вход отключён, используйте вход по номеру телефона"));
    }

    /**
     * Запрос OTP-кода на телефон (T5). Всегда 202 — не раскрываем, существует ли аккаунт
     * с этим номером (anti-enumeration, тот же принцип что и password-reset/request).
     */
    @PostMapping("/phone/request")
    public ResponseEntity<ApiResponse<Void>> phoneOtpRequest(@Valid @RequestBody PhoneOtpRequest req) {
        otpService.requestOtp(req.getPhone());
        return ResponseEntity.accepted().body(ApiResponse.success("Код отправлен", null));
    }

    /**
     * Подтверждение OTP-кода — вход/регистрация/восстановление одним путём.
     * Refresh-токен уходит в httpOnly cookie, не в JSON body (консистентно с login/register).
     */
    @PostMapping("/phone/confirm")
    public ResponseEntity<ApiResponse<AuthResponse>> phoneOtpConfirm(
            @Valid @RequestBody PhoneOtpConfirmRequest req,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.phoneAuth(req.getPhone(), req.getCode());
        addRefreshTokenCookie(response, authResponse.getRefreshToken());
        authResponse.setRefreshToken(null);
        return ResponseEntity.ok(ApiResponse.success("Вход выполнен", authResponse));
    }

    /**
     * Вход/регистрация через Telegram Login Widget.
     * Подпись и свежесть проверяются на сервере; refresh-токен — в httpOnly cookie.
     */
    @PostMapping("/telegram")
    public ResponseEntity<ApiResponse<AuthResponse>> telegramAuth(
            @Valid @RequestBody TelegramAuthRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.telegramAuth(request);
        addRefreshTokenCookie(response, authResponse.getRefreshToken());
        authResponse.setRefreshToken(null);
        return ResponseEntity.ok(ApiResponse.success("Вход через Telegram выполнен", authResponse));
    }

    /**
     * Вход/регистрация через Google (ID-token из Google Identity Services).
     * ID-token проверяется на сервере ({@code GoogleTokenVerifier}); refresh-токен —
     * в httpOnly cookie, консистентно с login/telegram/phone.
     */
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleAuth(
            @Valid @RequestBody GoogleAuthRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.googleAuth(request.getIdToken());
        addRefreshTokenCookie(response, authResponse.getRefreshToken());
        authResponse.setRefreshToken(null);
        return ResponseEntity.ok(ApiResponse.success("Вход через Google выполнен", authResponse));
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

    // ==================== Cookie helpers (M4) ====================

    /**
     * Устанавливает refreshToken в httpOnly cookie.
     * Path ограничен /api/v1/auth — cookie не отправляется на другие endpoints.
     */
    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Удаляет refreshToken cookie (при logout).
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Извлекает refreshToken из cookie.
     */
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_TOKEN_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }
}
