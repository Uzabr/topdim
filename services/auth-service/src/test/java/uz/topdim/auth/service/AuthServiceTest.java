package uz.topdim.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.topdim.auth.dto.*;
import uz.topdim.auth.entity.RefreshToken;
import uz.topdim.auth.entity.Role;
import uz.topdim.auth.entity.User;
import uz.topdim.auth.exception.AuthException;
import uz.topdim.auth.repository.RefreshTokenRepository;
import uz.topdim.auth.repository.UserRepository;
import uz.topdim.auth.security.JwtService;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private OutboxService outboxService;

    @InjectMocks
    private AuthService authService;

    // ==================== Register ====================

    @Test
    @DisplayName("Регистрация: успешная — создаёт юзера и возвращает токены")
    void register_success_createsUserAndReturnsTokens() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@topdim.uz");
        request.setPhone("+998901234567");
        request.setPassword("password123");
        request.setFirstName("Иван");
        request.setLastName("Иванов");

        when(userRepository.existsByEmail("test@topdim.uz")).thenReturn(false);
        when(userRepository.existsByPhone("+998901234567")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access_token_123");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access_token_123");
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getEmail()).isEqualTo("test@topdim.uz");
        assertThat(response.getUser().getRole()).isEqualTo("USER");

        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode("password123");
        verify(outboxService).createUserRegisteredEvent(any(User.class), anyString());
    }

    @Test
    @DisplayName("Регистрация: дубликат email → AuthException")
    void register_duplicateEmail_throwsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("exists@topdim.uz");
        request.setPassword("password");

        when(userRepository.existsByEmail("exists@topdim.uz")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("email уже существует");

        verify(userRepository, never()).save(any());
        verify(outboxService, never()).createUserRegisteredEvent(any(), anyString());
    }

    @Test
    @DisplayName("Регистрация: дубликат телефона → AuthException")
    void register_duplicatePhone_throwsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@topdim.uz");
        request.setPhone("+998901234567");
        request.setPassword("password");

        when(userRepository.existsByEmail("new@topdim.uz")).thenReturn(false);
        when(userRepository.existsByPhone("+998901234567")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("номером телефона уже существует");

        verify(userRepository, never()).save(any());
    }

    // ==================== Login ====================

    @Test
    @DisplayName("Логин: успешный — аутентифицирует и возвращает токены")
    void login_success_returnsTokens() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@topdim.uz");
        request.setPassword("password");

        User user = User.builder()
                .id(1L).email("user@topdim.uz").firstName("Test").lastName("User")
                .role(Role.USER).build();

        when(loginAttemptService.getDelay("user@topdim.uz")).thenReturn(java.time.Duration.ZERO);
        when(userRepository.findByEmail("user@topdim.uz")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access_token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access_token");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    @DisplayName("Логин: неверный пароль → BadCredentialsException")
    void login_badCredentials_throwsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@topdim.uz");
        request.setPassword("wrong");

        when(loginAttemptService.getDelay("user@topdim.uz")).thenReturn(java.time.Duration.ZERO);
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Неверный email или пароль");

        verify(loginAttemptService).recordFailedAttempt("user@topdim.uz");
    }

    // ==================== Refresh Token ====================

    @Test
    @DisplayName("Refresh: успешный — отзывает старый и выдаёт новый")
    void refreshToken_success() {
        User user = User.builder()
                .id(1L).email("user@topdim.uz").firstName("Test").lastName("User")
                .role(Role.USER).build();

        RefreshToken token = RefreshToken.builder()
                .token("valid-refresh-token").user(user)
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(false).build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");

        when(refreshTokenRepository.findByToken("valid-refresh-token")).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(user)).thenReturn("new_access_token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604800000L);

        AuthResponse response = authService.refreshToken(request);

        assertThat(response.getAccessToken()).isEqualTo("new_access_token");
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Refresh: отозванный токен → AuthException")
    void refreshToken_revoked_throwsException() {
        RefreshToken token = RefreshToken.builder()
                .token("revoked-token").revoked(true)
                .expiresAt(Instant.now().plusSeconds(3600)).build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("revoked-token");

        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("отозван");
    }

    @Test
    @DisplayName("Refresh: истёкший токен → AuthException")
    void refreshToken_expired_throwsException() {
        RefreshToken token = RefreshToken.builder()
                .token("expired-token").revoked(false)
                .expiresAt(Instant.now().minusSeconds(100)).build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired-token");

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("истёк");
    }

    // ==================== Logout ====================

    @Test
    @DisplayName("Logout: отзывает refresh token")
    void logout_revokesRefreshToken() {
        User user = User.builder().id(1L).email("user@topdim.uz").firstName("Test").role(Role.USER).build();
        RefreshToken token = RefreshToken.builder()
                .token("logout-token").revoked(false).user(user).build();

        when(refreshTokenRepository.findByToken("logout-token")).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.logout("logout-token");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    // ==================== Change Password ====================

    @Test
    @DisplayName("Смена пароля: успешная — обновляет хэш и отзывает токены")
    void changePassword_success_updatesAndRevokesTokens() {
        User user = User.builder()
                .id(1L).email("user@topdim.uz").password("old_hash")
                .firstName("Test").role(Role.USER).build();

        ChangePasswordRequest request = new ChangePasswordRequest(
                "currentPass", "newPass123", "newPass123"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("currentPass", "old_hash")).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("new_hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.changePassword(1L, request);

        assertThat(user.getPassword()).isEqualTo("new_hash");
        verify(passwordEncoder).encode("newPass123");
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    @DisplayName("Смена пароля: неверный текущий → AuthException")
    void changePassword_wrongCurrentPassword_throwsException() {
        User user = User.builder()
                .id(1L).email("user@topdim.uz").password("old_hash")
                .firstName("Test").role(Role.USER).build();

        ChangePasswordRequest request = new ChangePasswordRequest(
                "wrongPass", "newPass123", "newPass123"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "old_hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(1L, request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Неверный текущий пароль");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Смена пароля: пароли не совпадают → AuthException")
    void changePassword_mismatch_throwsException() {
        User user = User.builder()
                .id(1L).email("user@topdim.uz").password("old_hash")
                .firstName("Test").role(Role.USER).build();

        ChangePasswordRequest request = new ChangePasswordRequest(
                "currentPass", "newPass123", "differentPass"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("currentPass", "old_hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(1L, request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("не совпадают");

        verify(userRepository, never()).save(any());
    }

    // ==================== Guest Auth ====================

    @Test
    @DisplayName("Гость: новый телефон → создаёт GUEST пользователя и возвращает токены")
    void guestAuth_newPhone_createsGuestUserAndReturnsTokens() {
        GuestAuthRequest request = new GuestAuthRequest();
        request.setPhone("+998901234567");
        request.setName("Гость Иван");

        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("random_hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(100L);
            return u;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("guest_access_token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.guestAuth(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("guest_access_token");
        assertThat(response.getUser().getRole()).isEqualTo("GUEST");
        assertThat(response.getUser().getFirstName()).isEqualTo("Гость Иван");

        verify(userRepository).save(argThat(user ->
                user.getRole() == Role.GUEST &&
                user.getPhone().equals("+998901234567") &&
                user.getEmail().contains("guest_")
        ));
        verify(outboxService).createUserRegisteredEvent(any(User.class), anyString());
    }

    @Test
    @DisplayName("Гость: существующий телефон → возвращает токены без создания нового пользователя")
    void guestAuth_existingPhone_returnsTokensWithoutCreatingNewUser() {
        User existingUser = User.builder()
                .id(50L).email("guest_+998901234567@topdim.uz")
                .phone("+998901234567").firstName("Существующий")
                .role(Role.GUEST).build();

        GuestAuthRequest request = new GuestAuthRequest();
        request.setPhone("+998901234567");
        request.setName("Другое имя");

        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.of(existingUser));
        when(jwtService.generateAccessToken(existingUser)).thenReturn("existing_access_token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.guestAuth(request);

        assertThat(response.getAccessToken()).isEqualTo("existing_access_token");
        assertThat(response.getUser().getFirstName()).isEqualTo("Существующий"); // не меняет имя

        verify(userRepository, never()).save(any()); // не создаёт нового
        verify(outboxService, never()).createUserRegisteredEvent(any(), anyString()); // не создаёт событие
    }
}
