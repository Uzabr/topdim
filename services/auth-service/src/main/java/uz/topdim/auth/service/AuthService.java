package uz.topdim.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.auth.dto.*;
import uz.topdim.auth.entity.RefreshToken;
import uz.topdim.auth.entity.Role;
import uz.topdim.auth.entity.User;
import uz.topdim.auth.exception.AuthException;
import uz.topdim.auth.repository.RefreshTokenRepository;
import uz.topdim.auth.repository.UserRepository;
import uz.topdim.auth.security.JwtService;

import java.time.Instant;
import java.util.UUID;

/**
 * Сервис аутентификации.
 * Регистрация, логин, обновление токенов и logout.
 * При logout access token заносится в Redis blacklist.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Регистрация нового пользователя.
     * Проверяет уникальность email/phone, хэширует пароль.
     *
     * @param request данные регистрации (email, phone, password, name)
     * @return AuthResponse с access и refresh токенами
     * @throws AuthException если email или phone уже заняты
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("Пользователь с таким email уже существует");
        }

        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new AuthException("Пользователь с таким номером телефона уже существует");
        }

        User user = User.builder()
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.USER)
                .enabled(true)
                .emailVerified(false)
                .phoneVerified(false)
                .build();

        user = userRepository.save(user);

        return buildAuthResponse(user);
    }

    /**
     * Аутентификация пользователя.
     * Проверяет credentials через AuthenticationManager, отзывает старые refresh tokens.
     *
     * @param request email и password
     * @return AuthResponse с новыми токенами
     * @throws AuthException при неверных credentials
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthException("Неверный email или пароль"));

        // Revoke old refresh tokens
        refreshTokenRepository.revokeAllByUser(user);

        return buildAuthResponse(user);
    }

    /**
     * Обновление access token по refresh token.
     * Валидирует refresh token (не отозван, не истёк), генерирует новую пару.
     *
     * @param request содержит refreshToken
     * @return AuthResponse с новыми токенами
     * @throws AuthException если токен невалиден, отозван или истёк
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new AuthException("Невалидный refresh token"));

        if (refreshToken.isRevoked()) {
            throw new AuthException("Refresh token отозван");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthException("Refresh token истёк");
        }

        // Revoke old and create new
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return buildAuthResponse(refreshToken.getUser());
    }

    /**
     * Выход из системы.
     * Отзывает refresh token (revoked = true в БД).
     *
     * @param refreshToken значение refresh токена для отзыва
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    /**
     * Формирует ответ аутентификации.
     * Генерирует access token, создаёт refresh token, собирает UserDto.
     */
    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenStr = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .role(user.getRole().name())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .build();
    }

    /**
     * Создаёт refresh token (UUID) и сохраняет в БД.
     *
     * @param user пользователь для привязки токена
     * @return строковое значение refresh token
     */
    private String createRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpiration()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return token;
    }
}
