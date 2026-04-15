package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.topdim.identity.entity.AuthActionToken;
import uz.topdim.identity.entity.AuthActionType;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.AuthException;
import uz.topdim.identity.repository.AuthActionTokenRepository;
import uz.topdim.identity.repository.RefreshTokenRepository;
import uz.topdim.identity.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthActionTokenRepository actionTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SecurityVersionService securityVersionService;
    @Mock private NotificationSender notificationSender;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User createUser(boolean enabled) {
        return User.builder()
                .id(11L)
                .email("user@topdim.uz")
                .password("old-hash")
                .enabled(enabled)
                .securityVersion(4L)
                .build();
    }

    @Test
    @DisplayName("requestReset: существующий email -> revoke старых токенов, save нового hash и отправка plain token")
    void requestReset_existingUser_revokesOldTokensSavesHashAndSendsPlainToken() {
        User user = createUser(true);
        when(userRepository.findByEmailIgnoreCase("user@topdim.uz")).thenReturn(Optional.of(user));
        when(actionTokenRepository.save(any(AuthActionToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        passwordResetService.requestReset("  USER@TopDim.UZ ");
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<AuthActionToken> tokenCaptor = ArgumentCaptor.forClass(AuthActionToken.class);
        ArgumentCaptor<String> targetCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> plainTokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(actionTokenRepository).revokeAllActiveByUserIdAndType(11L, AuthActionType.PASSWORD_RESET);
        verify(actionTokenRepository).save(tokenCaptor.capture());
        verify(notificationSender).sendPasswordResetToken(targetCaptor.capture(), plainTokenCaptor.capture());

        AuthActionToken saved = tokenCaptor.getValue();
        String target = targetCaptor.getValue();
        String plainToken = plainTokenCaptor.getValue();

        assertThat(target).isEqualTo("user@topdim.uz");
        assertThat(saved.getUserId()).isEqualTo(11L);
        assertThat(saved.getType()).isEqualTo(AuthActionType.PASSWORD_RESET);
        assertThat(saved.getTarget()).isEqualTo("user@topdim.uz");
        assertThat(saved.getTokenHash()).isEqualTo(PasswordResetService.sha256(plainToken));
        assertThat(saved.getExpiresAt()).isBetween(before.plusMinutes(30), after.plusMinutes(30));
        assertThat(saved.getLastSentAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("requestReset: несуществующий email не раскрывает наличие пользователя и не вызывает side effects")
    void requestReset_unknownEmail_isNoop() {
        when(userRepository.findByEmailIgnoreCase("missing@topdim.uz")).thenReturn(Optional.empty());

        assertThatCode(() -> passwordResetService.requestReset("missing@topdim.uz"))
                .doesNotThrowAnyException();

        verify(actionTokenRepository, never()).revokeAllActiveByUserIdAndType(any(), any());
        verify(actionTokenRepository, never()).save(any(AuthActionToken.class));
        verify(notificationSender, never()).sendPasswordResetToken(any(), any());
    }

    @Test
    @DisplayName("confirmReset: валидный token меняет пароль, bump securityVersion и помечает token used")
    void confirmReset_validToken_updatesPasswordRevokesSessionsAndMarksTokenUsed() {
        String plainToken = "reset-token";
        AuthActionToken actionToken = AuthActionToken.builder()
                .userId(11L)
                .type(AuthActionType.PASSWORD_RESET)
                .tokenHash(PasswordResetService.sha256(plainToken))
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        User user = createUser(true);

        when(actionTokenRepository.findByTokenHashAndType(PasswordResetService.sha256(plainToken), AuthActionType.PASSWORD_RESET))
                .thenReturn(Optional.of(actionToken));
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass123!")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(actionTokenRepository.save(any(AuthActionToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        passwordResetService.confirmReset(plainToken, "NewPass123!");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getSecurityVersion()).isEqualTo(5L);
        assertThat(actionToken.getUsedAt()).isNotNull();
        verify(securityVersionService).publishSecurityVersion(11L, 5L);
        verify(refreshTokenRepository).revokeAllByUser(user);
        verify(actionTokenRepository).save(actionToken);
    }

    @Test
    @DisplayName("confirmReset: expired token отклоняется без побочных эффектов")
    void confirmReset_expiredToken_throwsWithoutSideEffects() {
        String plainToken = "expired-token";
        AuthActionToken actionToken = AuthActionToken.builder()
                .userId(11L)
                .type(AuthActionType.PASSWORD_RESET)
                .tokenHash(PasswordResetService.sha256(plainToken))
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();

        when(actionTokenRepository.findByTokenHashAndType(PasswordResetService.sha256(plainToken), AuthActionType.PASSWORD_RESET))
                .thenReturn(Optional.of(actionToken));

        assertThatThrownBy(() -> passwordResetService.confirmReset(plainToken, "NewPass123!"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Недействительный");

        verify(userRepository, never()).findById(any());
        verify(refreshTokenRepository, never()).revokeAllByUser(any());
        verify(securityVersionService, never()).publishSecurityVersion(anyLong(), anyLong());
    }

    @Test
    @DisplayName("confirmReset: заблокированный пользователь не может завершить reset")
    void confirmReset_blockedUser_throwsAndDoesNotConsumeToken() {
        String plainToken = "blocked-reset-token";
        AuthActionToken actionToken = AuthActionToken.builder()
                .userId(11L)
                .type(AuthActionType.PASSWORD_RESET)
                .tokenHash(PasswordResetService.sha256(plainToken))
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        User blockedUser = createUser(false);

        when(actionTokenRepository.findByTokenHashAndType(PasswordResetService.sha256(plainToken), AuthActionType.PASSWORD_RESET))
                .thenReturn(Optional.of(actionToken));
        when(userRepository.findById(11L)).thenReturn(Optional.of(blockedUser));

        assertThatThrownBy(() -> passwordResetService.confirmReset(plainToken, "NewPass123!"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("заблокирован");

        assertThat(actionToken.getUsedAt()).isNull();
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenRepository, never()).revokeAllByUser(any());
        verify(actionTokenRepository, never()).save(any(AuthActionToken.class));
    }
}
