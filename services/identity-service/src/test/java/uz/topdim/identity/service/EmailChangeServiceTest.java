package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.identity.entity.AuthActionToken;
import uz.topdim.identity.entity.AuthActionType;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.AuthException;
import uz.topdim.identity.repository.AuthActionTokenRepository;
import uz.topdim.identity.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailChangeServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthActionTokenRepository actionTokenRepository;
    @Mock private NotificationSender notificationSender;

    @InjectMocks
    private EmailChangeService emailChangeService;

    private User createUser() {
        return User.builder()
                .id(9L)
                .email("tg_12345@topdim.uz")
                .emailVerified(false)
                .enabled(true)
                .build();
    }

    // ==================== requestEmailChange ====================

    @Test
    @DisplayName("requestEmailChange: happy path — токен создан, письмо отправлено на НОВЫЙ адрес")
    void requestEmailChange_createsNewTokenAndSendsToNewAddress() {
        User user = createUser();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("new@topdim.uz")).thenReturn(false);
        when(actionTokenRepository.findActiveByUserIdAndType(9L, AuthActionType.EMAIL_CHANGE)).thenReturn(List.of());
        when(actionTokenRepository.save(any(AuthActionToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        emailChangeService.requestEmailChange(9L, "  New@TopDim.uz ");
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<AuthActionToken> tokenCaptor = ArgumentCaptor.forClass(AuthActionToken.class);
        ArgumentCaptor<String> targetCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> plainTokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(actionTokenRepository).revokeAllActiveByUserIdAndType(9L, AuthActionType.EMAIL_CHANGE);
        verify(actionTokenRepository).save(tokenCaptor.capture());
        verify(notificationSender).sendEmailChangeToken(targetCaptor.capture(), plainTokenCaptor.capture());

        AuthActionToken saved = tokenCaptor.getValue();
        assertThat(targetCaptor.getValue()).isEqualTo("new@topdim.uz");
        assertThat(saved.getUserId()).isEqualTo(9L);
        assertThat(saved.getType()).isEqualTo(AuthActionType.EMAIL_CHANGE);
        assertThat(saved.getTarget()).isEqualTo("new@topdim.uz");
        assertThat(saved.getTokenHash()).isEqualTo(PasswordResetService.sha256(plainTokenCaptor.getValue()));
        assertThat(saved.getExpiresAt()).isBetween(before.plusMinutes(60), after.plusMinutes(60));
    }

    @Test
    @DisplayName("requestEmailChange: email уже занят другим пользователем -> IllegalStateException (409)")
    void requestEmailChange_emailAlreadyTaken_throwsIllegalState() {
        User user = createUser();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("taken@topdim.uz")).thenReturn(true);

        assertThatThrownBy(() -> emailChangeService.requestEmailChange(9L, "taken@topdim.uz"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже используется");

        verify(actionTokenRepository, never()).save(any(AuthActionToken.class));
        verify(notificationSender, never()).sendEmailChangeToken(any(), any());
    }

    @Test
    @DisplayName("requestEmailChange: повторный запрос в пределах cooldown отклоняется")
    void requestEmailChange_withinCooldown_throws() {
        User user = createUser();
        AuthActionToken latestToken = AuthActionToken.builder()
                .userId(9L)
                .type(AuthActionType.EMAIL_CHANGE)
                .target("new@topdim.uz")
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .lastSentAt(LocalDateTime.now().minusSeconds(20))
                .build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("new@topdim.uz")).thenReturn(false);
        when(actionTokenRepository.findActiveByUserIdAndType(9L, AuthActionType.EMAIL_CHANGE))
                .thenReturn(List.of(latestToken));

        assertThatThrownBy(() -> emailChangeService.requestEmailChange(9L, "new@topdim.uz"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("60 секунд");

        verify(actionTokenRepository, never()).revokeAllActiveByUserIdAndType(any(), any());
        verify(actionTokenRepository, never()).save(any(AuthActionToken.class));
        verify(notificationSender, never()).sendEmailChangeToken(any(), any());
    }

    @Test
    @DisplayName("requestEmailChange: старые EMAIL_CHANGE-токены пользователя revoke'ятся при новом запросе")
    void requestEmailChange_revokesOldTokens() {
        User user = createUser();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("new@topdim.uz")).thenReturn(false);
        when(actionTokenRepository.findActiveByUserIdAndType(9L, AuthActionType.EMAIL_CHANGE)).thenReturn(List.of());
        when(actionTokenRepository.save(any(AuthActionToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        emailChangeService.requestEmailChange(9L, "new@topdim.uz");

        verify(actionTokenRepository).revokeAllActiveByUserIdAndType(9L, AuthActionType.EMAIL_CHANGE);
    }

    @Test
    @DisplayName("requestEmailChange: несуществующий userId -> AuthException")
    void requestEmailChange_unknownUser_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailChangeService.requestEmailChange(99L, "new@topdim.uz"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("не найден");

        verify(actionTokenRepository, never()).save(any(AuthActionToken.class));
        verify(notificationSender, never()).sendEmailChangeToken(any(), any());
    }

    // ==================== confirmEmailChange ====================

    @Test
    @DisplayName("confirmEmailChange: валидный token обновляет email, ставит emailVerified=true и помечает token used")
    void confirmEmailChange_validToken_updatesEmailAndConsumesToken() {
        String plainToken = "email-change-token";
        AuthActionToken actionToken = AuthActionToken.builder()
                .userId(9L)
                .type(AuthActionType.EMAIL_CHANGE)
                .tokenHash(PasswordResetService.sha256(plainToken))
                .target("new@topdim.uz")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        User user = createUser();

        when(actionTokenRepository.findByTokenHashAndType(PasswordResetService.sha256(plainToken), AuthActionType.EMAIL_CHANGE))
                .thenReturn(Optional.of(actionToken));
        when(userRepository.existsByEmailIgnoreCase("new@topdim.uz")).thenReturn(false);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(actionTokenRepository.save(any(AuthActionToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        emailChangeService.confirmEmailChange(plainToken);

        assertThat(user.getEmail()).isEqualTo("new@topdim.uz");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(actionToken.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(actionTokenRepository).save(actionToken);
    }

    @Test
    @DisplayName("confirmEmailChange: несуществующий token -> AuthException")
    void confirmEmailChange_missingToken_throws() {
        when(actionTokenRepository.findByTokenHashAndType(PasswordResetService.sha256("missing-token"), AuthActionType.EMAIL_CHANGE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailChangeService.confirmEmailChange("missing-token"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Недействительный");

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmEmailChange: просроченный token -> AuthException без побочных эффектов")
    void confirmEmailChange_expiredToken_throwsWithoutSideEffects() {
        String plainToken = "expired-token";
        AuthActionToken actionToken = AuthActionToken.builder()
                .userId(9L)
                .type(AuthActionType.EMAIL_CHANGE)
                .tokenHash(PasswordResetService.sha256(plainToken))
                .target("new@topdim.uz")
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();

        when(actionTokenRepository.findByTokenHashAndType(PasswordResetService.sha256(plainToken), AuthActionType.EMAIL_CHANGE))
                .thenReturn(Optional.of(actionToken));

        assertThatThrownBy(() -> emailChangeService.confirmEmailChange(plainToken))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Недействительный");

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
        verify(actionTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmEmailChange: уже использованный token -> AuthException")
    void confirmEmailChange_usedToken_throws() {
        String plainToken = "used-token";
        AuthActionToken actionToken = AuthActionToken.builder()
                .userId(9L)
                .type(AuthActionType.EMAIL_CHANGE)
                .tokenHash(PasswordResetService.sha256(plainToken))
                .target("new@topdim.uz")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .usedAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(actionTokenRepository.findByTokenHashAndType(PasswordResetService.sha256(plainToken), AuthActionType.EMAIL_CHANGE))
                .thenReturn(Optional.of(actionToken));

        assertThatThrownBy(() -> emailChangeService.confirmEmailChange(plainToken))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Недействительный");

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmEmailChange: target занят на момент confirm (гонка) -> IllegalStateException (409)")
    void confirmEmailChange_targetTakenAtConfirmTime_throwsIllegalState() {
        String plainToken = "race-token";
        AuthActionToken actionToken = AuthActionToken.builder()
                .userId(9L)
                .type(AuthActionType.EMAIL_CHANGE)
                .tokenHash(PasswordResetService.sha256(plainToken))
                .target("new@topdim.uz")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        when(actionTokenRepository.findByTokenHashAndType(PasswordResetService.sha256(plainToken), AuthActionType.EMAIL_CHANGE))
                .thenReturn(Optional.of(actionToken));
        when(userRepository.existsByEmailIgnoreCase("new@topdim.uz")).thenReturn(true);

        assertThatThrownBy(() -> emailChangeService.confirmEmailChange(plainToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже используется");

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
        assertThat(actionToken.getUsedAt()).isNull();
        verify(actionTokenRepository, never()).save(any());
    }
}
