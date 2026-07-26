package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.topdim.identity.dto.AuthResponse;
import uz.topdim.identity.dto.ChangePasswordRequest;
import uz.topdim.identity.dto.GuestAuthRequest;
import uz.topdim.identity.dto.LoginRequest;
import uz.topdim.identity.dto.RefreshTokenRequest;
import uz.topdim.identity.dto.RegisterRequest;
import uz.topdim.identity.dto.TelegramAuthRequest;
import uz.topdim.identity.entity.RefreshToken;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.TrustLevel;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.AuthException;
import uz.topdim.identity.repository.RefreshTokenRepository;
import uz.topdim.identity.repository.UserRepository;
import uz.topdim.identity.security.JwtService;
import uz.topdim.identity.security.TelegramLoginVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private SecurityVersionService securityVersionService;
    @Mock private TelegramLoginVerifier telegramLoginVerifier;
    @Mock private TrustService trustService;

    @InjectMocks
    private AuthService authService;

    private User createUser(Role role, boolean enabled) {
        return User.builder()
                .id(1L)
                .email("user@topdim.uz")
                .phone("+998901234567")
                .password("hashed-password")
                .firstName("Test")
                .lastName("User")
                .role(role)
                .enabled(enabled)
                .emailVerified(true)
                .phoneVerified(true)
                .securityVersion(2L)
                .build();
    }

    private RefreshToken createRefreshToken(User user, boolean revoked) {
        return RefreshToken.builder()
                .tokenHash(PasswordResetService.sha256("refresh-token"))
                .user(user)
                .expiresAt(Instant.now().plusSeconds(600))
                .revoked(revoked)
                .build();
    }

    private void stubTokenGeneration(User user) {
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900_000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604_800_000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(trustService.computeTrustLevel(user)).thenReturn(TrustLevel.L1);
    }

    @Test
    @DisplayName("Регистрация: нормализует email перед сохранением и выдачей токенов")
    void register_normalizesEmailBeforePersisting() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("  User@TopDim.UZ ");
        request.setPhone(" +998901234567 ");
        request.setPassword("SafePass123!");
        request.setFirstName("Ali");
        request.setLastName("Valiyev");

        when(userRepository.existsByEmailIgnoreCase("user@topdim.uz")).thenReturn(false);
        when(userRepository.existsByPhone("+998901234567")).thenReturn(false);
        when(passwordEncoder.encode("SafePass123!")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(jwtService.getAccessTokenExpiration()).thenReturn(900_000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604_800_000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(trustService.computeTrustLevel(any(User.class))).thenReturn(TrustLevel.L0);

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@topdim.uz");
        assertThat(userCaptor.getValue().getPhone()).isEqualTo("+998901234567");
        assertThat(response.getUser().getEmail()).isEqualTo("user@topdim.uz");
        verify(userRepository).existsByEmailIgnoreCase("user@topdim.uz");
    }

    @Test
    @DisplayName("M5: дубликат телефона → нейтральное сообщение (не раскрывает что именно занято)")
    void register_duplicatePhone_throwsNeutralMessage() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@topdim.uz");
        request.setPhone("+998901234567");
        request.setPassword("SafePass123!");
        request.setFirstName("Ali");

        when(userRepository.existsByEmailIgnoreCase("user@topdim.uz")).thenReturn(false);
        when(userRepository.existsByPhone("+998901234567")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthException.class)
                .hasMessage("Не удалось зарегистрироваться с указанными данными");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("M5: дубликат email → то же нейтральное сообщение (anti-enumeration)")
    void register_duplicateEmail_throwsSameNeutralMessage() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@topdim.uz");
        request.setPhone("+998900000000");
        request.setPassword("SafePass123!");
        request.setFirstName("Ali");

        when(userRepository.existsByEmailIgnoreCase("existing@topdim.uz")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthException.class)
                .hasMessage("Не удалось зарегистрироваться с указанными данными");

        verify(userRepository, never()).save(any(User.class));
        // Phone check should not even be called — short-circuit
    }

    @Test
    @DisplayName("Логин: заблокированный пользователь получает generic auth error")
    void login_blockedUser_throwsGenericErrorAndRecordsAttempt() {
        LoginRequest request = new LoginRequest();
        request.setEmail("USER@topdim.uz");
        request.setPassword("SafePass123!");

        User blockedUser = createUser(Role.USER, false);

        when(loginAttemptService.getDelay("user@topdim.uz")).thenReturn(Duration.ZERO);
        when(userRepository.findByEmailIgnoreCase("user@topdim.uz")).thenReturn(Optional.of(blockedUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Неверный email или пароль");

        verify(loginAttemptService).recordFailedAttempt("user@topdim.uz");
        verify(refreshTokenRepository, never()).revokeAllByUser(any(User.class));
    }

    @Test
    @DisplayName("Логин: soft-deleted пользователь получает ту же generic auth error")
    void login_deletedUser_throwsGenericErrorAndRecordsAttempt() {
        LoginRequest request = new LoginRequest();
        request.setEmail("USER@topdim.uz");
        request.setPassword("SafePass123!");

        User deletedUser = createUser(Role.USER, true);
        deletedUser.setDeleted(true);

        when(loginAttemptService.getDelay("user@topdim.uz")).thenReturn(Duration.ZERO);
        when(userRepository.findByEmailIgnoreCase("user@topdim.uz")).thenReturn(Optional.of(deletedUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .hasMessage("Неверный email или пароль");

        verify(loginAttemptService).recordFailedAttempt("user@topdim.uz");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(refreshTokenRepository, never()).revokeAllByUser(any(User.class));
    }

    @Test
    @DisplayName("Логин: сохраняет только SHA-256 refresh-токена, клиенту возвращает plain token")
    void login_success_storesOnlyRefreshTokenHash() {
        LoginRequest request = new LoginRequest();
        request.setEmail("USER@topdim.uz");
        request.setPassword("SafePass123!");

        User user = createUser(Role.USER, true);
        when(loginAttemptService.getDelay("user@topdim.uz")).thenReturn(Duration.ZERO);
        when(userRepository.findByEmailIgnoreCase("user@topdim.uz")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SafePass123!", "hashed-password")).thenReturn(true);
        stubTokenGeneration(user);

        AuthResponse response = authService.login(request);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(tokenCaptor.getValue().getTokenHash())
                .isEqualTo(PasswordResetService.sha256(response.getRefreshToken()))
                .isNotEqualTo(response.getRefreshToken());
        verify(loginAttemptService).resetAttempts("user@topdim.uz");
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    @DisplayName("Логин: trustLevel в ответе берётся из TrustService.computeTrustLevel, а не из хранимой колонки user.trustLevel")
    void login_success_usesComputedTrustLevelNotStoredColumn() {
        LoginRequest request = new LoginRequest();
        request.setEmail("USER@topdim.uz");
        request.setPassword("SafePass123!");

        User user = createUser(Role.USER, true);
        // Хранимая колонка сознательно "устарела" (L0) — ответ всё равно должен быть L1,
        // т.к. источник правды — TrustService, а не user.trustLevel.
        user.setTrustLevel(TrustLevel.L0);

        when(loginAttemptService.getDelay("user@topdim.uz")).thenReturn(Duration.ZERO);
        when(userRepository.findByEmailIgnoreCase("user@topdim.uz")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SafePass123!", "hashed-password")).thenReturn(true);
        stubTokenGeneration(user); // возвращает TrustLevel.L1 для trustService.computeTrustLevel(user)

        AuthResponse response = authService.login(request);

        assertThat(response.getUser().getTrustLevel()).isEqualTo("L1");
        verify(trustService).computeTrustLevel(user);
    }

    @Test
    @DisplayName("Refresh: заблокированный пользователь не получает новую пару токенов")
    void refreshToken_blockedUser_revokesTokenAndThrows() {
        User blockedUser = createUser(Role.USER, false);
        RefreshToken refreshToken = createRefreshToken(blockedUser, false);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-token");

        when(refreshTokenRepository.findByTokenHash(PasswordResetService.sha256("refresh-token")))
                .thenReturn(Optional.of(refreshToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("заблокирован");

        assertThat(refreshToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(refreshToken);
        verify(jwtService, never()).generateAccessToken(any(User.class));
    }

    @Test
    @DisplayName("Refresh: повторное использование того же токена запрещено")
    void refreshToken_reuseOfSameTokenFailsOnSecondCall() {
        User user = createUser(Role.USER, true);
        RefreshToken refreshToken = createRefreshToken(user, false);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-token");

        when(refreshTokenRepository.findByTokenHash(PasswordResetService.sha256("refresh-token")))
                .thenReturn(Optional.of(refreshToken));
        stubTokenGeneration(user);

        AuthResponse firstResponse = authService.refreshToken(request);

        assertThat(firstResponse.getAccessToken()).isEqualTo("access-token");
        assertThat(refreshToken.isRevoked()).isTrue();

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("отозван");
    }

    @Test
    @DisplayName("Refresh: legacy plaintext в БД не совпадает с SHA-256 lookup и отклоняется")
    void refreshToken_plaintextDatabaseValueDoesNotAuthenticate() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("legacy-plaintext-token");
        String expectedHash = PasswordResetService.sha256("legacy-plaintext-token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthException.class)
                .hasMessage("Невалидный refresh token");

        verify(refreshTokenRepository).findByTokenHash(expectedHash);
        verify(refreshTokenRepository, never()).findByTokenHash("legacy-plaintext-token");
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Смена пароля: успешная — обновляет пароль, securityVersion и отзывает refresh токены")
    void changePassword_success_updatesPasswordSecurityVersionAndRevokesTokens() {
        User user = createUser(Role.USER, true);
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPass123!");
        request.setNewPassword("NewPass123!");
        request.setConfirmPassword("NewPass123!");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass123!", "hashed-password")).thenReturn(true);
        when(passwordEncoder.encode("NewPass123!")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.changePassword(1L, request);

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getSecurityVersion()).isEqualTo(3L);
        verify(securityVersionService).publishSecurityVersion(1L, 3L);
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    @DisplayName("Смена пароля: несовпадение new и confirm не меняет состояние")
    void changePassword_mismatchConfirmation_throwsWithoutSideEffects() {
        User user = createUser(Role.USER, true);
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPass123!");
        request.setNewPassword("NewPass123!");
        request.setConfirmPassword("Mismatch123!");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass123!", "hashed-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(1L, request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("не совпадают");

        assertThat(user.getSecurityVersion()).isEqualTo(2L);
        verify(userRepository, never()).save(any(User.class));
        verify(securityVersionService, never()).publishSecurityVersion(anyLong(), anyLong());
        verify(refreshTokenRepository, never()).revokeAllByUser(any(User.class));
    }

    @Test
    @DisplayName("Logout: blacklists access token по jti и отзывает refresh token")
    void logout_blacklistsAccessTokenAndRevokesRefreshToken() {
        User user = createUser(Role.USER, true);
        RefreshToken refreshToken = createRefreshToken(user, false);

        when(jwtService.extractJti("access-token")).thenReturn("jti-123");
        when(jwtService.getRemainingExpiration("access-token")).thenReturn(5_000L);
        when(refreshTokenRepository.findByTokenHash(PasswordResetService.sha256("refresh-token")))
                .thenReturn(Optional.of(refreshToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.logout("access-token", "refresh-token");

        assertThat(refreshToken.isRevoked()).isTrue();
        verify(tokenBlacklistService).blacklist("jti-123", 5_000L);
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    @DisplayName("Logout: повторный вызов с несуществующим refresh token остаётся идемпотентным")
    void logout_missingToken_isIdempotent() {
        when(refreshTokenRepository.findByTokenHash(PasswordResetService.sha256("missing-token")))
                .thenReturn(Optional.empty());

        assertThatCode(() -> authService.logout(null, "missing-token"))
                .doesNotThrowAnyException();

        verify(refreshTokenRepository)
                .findByTokenHash(PasswordResetService.sha256("missing-token"));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(tokenBlacklistService, never()).blacklist(anyString(), anyLong());
    }

    @Test
    @DisplayName("Logout: ошибка при blacklist не должна мешать revoke refresh token")
    void logout_blacklistFailure_stillRevokesRefreshToken() {
        User user = createUser(Role.USER, true);
        RefreshToken refreshToken = createRefreshToken(user, false);

        when(jwtService.extractJti("broken-access")).thenThrow(new RuntimeException("bad token state"));
        when(refreshTokenRepository.findByTokenHash(PasswordResetService.sha256("refresh-token")))
                .thenReturn(Optional.of(refreshToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> authService.logout("broken-access", "refresh-token"))
                .doesNotThrowAnyException();

        assertThat(refreshToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    @DisplayName("Guest auth: не должен логинить существующего обычного пользователя по одному телефону")
    void guestAuth_existingRegularUser_throws() {
        GuestAuthRequest request = new GuestAuthRequest();
        request.setPhone("+998901234567");
        request.setName("Guest");

        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.of(createUser(Role.USER, true)));

        assertThatThrownBy(() -> authService.guestAuth(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("уже привязан");

        verify(userRepository, never()).save(any(User.class));
        verify(jwtService, never()).generateAccessToken(any(User.class));
    }

    @Test
    @DisplayName("Guest auth: заблокированный guest не должен получать токены")
    void guestAuth_blockedGuest_throws() {
        GuestAuthRequest request = new GuestAuthRequest();
        request.setPhone("+998901234567");
        request.setName("Guest");

        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.of(createUser(Role.GUEST, false)));

        assertThatThrownBy(() -> authService.guestAuth(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("заблокирован");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Guest auth: soft-deleted guest не должен получать токены")
    void guestAuth_deletedGuest_throws() {
        GuestAuthRequest request = new GuestAuthRequest();
        request.setPhone("+998901234567");
        request.setName("Guest");

        User deletedGuest = createUser(Role.GUEST, true);
        deletedGuest.setDeleted(true);
        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.of(deletedGuest));

        assertThatThrownBy(() -> authService.guestAuth(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("заблокирован");

        verify(refreshTokenRepository, never()).revokeAllByUser(any(User.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Guest auth: существующий guest отзывает старые сессии перед выдачей новой")
    void guestAuth_existingGuest_revokesOldSessions() {
        GuestAuthRequest request = new GuestAuthRequest();
        request.setPhone("+998901234567");
        request.setName("Guest");

        User existingGuest = createUser(Role.GUEST, true);
        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.of(existingGuest));
        stubTokenGeneration(existingGuest);

        AuthResponse response = authService.guestAuth(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        InOrder sessionOrder = inOrder(refreshTokenRepository);
        sessionOrder.verify(refreshTokenRepository).revokeAllByUser(existingGuest);
        sessionOrder.verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Guest auth: создаёт нового гостя и выдаёт ему токены")
    void guestAuth_newGuest_createsGuestUser() {
        GuestAuthRequest request = new GuestAuthRequest();
        request.setPhone(" +998901234567 ");
        request.setName("Guest User");

        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("guest-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(15L);
            return saved;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900_000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604_800_000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(trustService.computeTrustLevel(any(User.class))).thenReturn(TrustLevel.L0);

        AuthResponse response = authService.guestAuth(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.GUEST);
        assertThat(userCaptor.getValue().getPhone()).isEqualTo("+998901234567");
        assertThat(response.getUser().getRole()).isEqualTo("GUEST");
        verify(refreshTokenRepository, atLeastOnce()).save(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).revokeAllByUser(any(User.class));
    }

    @Test
    @DisplayName("Telegram auth: новый telegram id → создаёт пользователя USER/L0 и выдаёт токены")
    void telegramAuth_newUser_createsL0User() {
        TelegramAuthRequest request = new TelegramAuthRequest();
        request.setId(777L);
        request.setFirstName("Иван");
        request.setUsername("ivan");
        request.setAuthDate(Instant.now().getEpochSecond());
        request.setHash("valid"); // verifier замокан → verify() ничего не делает

        when(userRepository.findByTelegramChatId(777L)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("tg-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900_000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604_800_000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(trustService.computeTrustLevel(any(User.class))).thenReturn(TrustLevel.L0);

        AuthResponse response = authService.telegramAuth(request);

        verify(telegramLoginVerifier).verify(request);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getRole()).isEqualTo(Role.USER);
        assertThat(created.getTrustLevel()).isEqualTo(TrustLevel.L0);
        assertThat(created.getTelegramChatId()).isEqualTo(777L);
        assertThat(created.getTelegramUsername()).isEqualTo("ivan");
        assertThat(created.getTelegramLinkedAt()).isNotNull();
        assertThat(created.getEmail()).isEqualTo("tg_777@topdim.uz");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUser().getRole()).isEqualTo("USER");
        verify(refreshTokenRepository, never()).revokeAllByUser(any(User.class));
    }

    @Test
    @DisplayName("Telegram auth: soft-deleted пользователь не должен получать токены")
    void telegramAuth_deletedUser_throws() {
        TelegramAuthRequest request = new TelegramAuthRequest();
        request.setId(777L);
        request.setAuthDate(Instant.now().getEpochSecond());
        request.setHash("valid");

        User deletedUser = createUser(Role.USER, true);
        deletedUser.setDeleted(true);
        deletedUser.setTelegramChatId(777L);
        when(userRepository.findByTelegramChatId(777L)).thenReturn(Optional.of(deletedUser));

        assertThatThrownBy(() -> authService.telegramAuth(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("заблокирован");

        verify(telegramLoginVerifier).verify(request);
        verify(refreshTokenRepository, never()).revokeAllByUser(any(User.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Telegram auth: существующий telegram id → логин без создания нового пользователя")
    void telegramAuth_existingUser_logsIn() {
        TelegramAuthRequest request = new TelegramAuthRequest();
        request.setId(777L);
        request.setUsername("ivan_new");
        request.setAuthDate(Instant.now().getEpochSecond());
        request.setHash("valid");

        User existing = User.builder()
                .id(42L).email("tg_777@topdim.uz").password("x")
                .firstName("Иван").role(Role.USER).enabled(true)
                .telegramChatId(777L).telegramUsername("ivan")
                .trustLevel(TrustLevel.L0).build();
        when(userRepository.findByTelegramChatId(777L)).thenReturn(Optional.of(existing));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900_000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604_800_000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(trustService.computeTrustLevel(existing)).thenReturn(TrustLevel.L0);

        AuthResponse response = authService.telegramAuth(request);

        verify(userRepository, never()).save(any(User.class));
        InOrder sessionOrder = inOrder(refreshTokenRepository);
        sessionOrder.verify(refreshTokenRepository).revokeAllByUser(existing);
        sessionOrder.verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(existing.getTelegramUsername()).isEqualTo("ivan_new"); // username обновился
        assertThat(response.getUser().getId()).isEqualTo(42L);
    }
}
