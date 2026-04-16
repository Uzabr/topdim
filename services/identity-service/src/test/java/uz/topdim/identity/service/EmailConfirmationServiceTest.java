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
class EmailConfirmationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthActionTokenRepository actionTokenRepository;
    @Mock private NotificationSender notificationSender;

    @InjectMocks
    private EmailConfirmationService emailConfirmationService;

    private User createUser(boolean emailVerified) {
        return User.builder()
                .id(9L)
                .email("user@topdim.uz")
                .emailVerified(emailVerified)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("requestEmailConfirmation: создаёт новый token и отправляет его на email")
    void requestEmailConfirmation_createsNewTokenAndSendsNotification() {
        User user = createUser(false);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(actionTokenRepository.findActiveByUserIdAndType(9L, AuthActionType.EMAIL_CONFIRM)).thenReturn(List.of());
        when(actionTokenRepository.save(any(AuthActionToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        emailConfirmationService.requestEmailConfirmation(9L);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<AuthActionToken> tokenCaptor = ArgumentCaptor.forClass(AuthActionToken.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sentTokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(actionTokenRepository).revokeAllActiveByUserIdAndType(9L, AuthActionType.EMAIL_CONFIRM);
        verify(actionTokenRepository).save(tokenCaptor.capture());
        verify(notificationSender).sendEmailConfirmationToken(emailCaptor.capture(), sentTokenCaptor.capture());

        AuthActionToken saved = tokenCaptor.getValue();
        assertThat(emailCaptor.getValue()).isEqualTo("user@topdim.uz");
        assertThat(saved.getUserId()).isEqualTo(9L);
        assertThat(saved.getType()).isEqualTo(AuthActionType.EMAIL_CONFIRM);
        assertThat(saved.getTarget()).isEqualTo("user@topdim.uz");
        assertThat(saved.getTokenHash()).isEqualTo(PasswordResetService.sha256(sentTokenCaptor.getValue()));
        assertThat(saved.getExpiresAt()).isBetween(before.plusMinutes(60), after.plusMinutes(60));
    }

    @Test
    @DisplayName("requestEmailConfirmation: уже подтверждённый email не должен отправляться повторно")
    void requestEmailConfirmation_alreadyVerified_throws() {
        when(userRepository.findById(9L)).thenReturn(Optional.of(createUser(true)));

        assertThatThrownBy(() -> emailConfirmationService.requestEmailConfirmation(9L))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("уже подтверждён");

        verify(actionTokenRepository, never()).save(any(AuthActionToken.class));
        verify(notificationSender, never()).sendEmailConfirmationToken(any(), any());
    }

    @Test
    @DisplayName("requestEmailConfirmation: повторный запрос в пределах cooldown отклоняется")
    void requestEmailConfirmation_withinCooldown_throws() {
        User user = createUser(false);
        AuthActionToken latestToken = AuthActionToken.builder()
                .userId(9L)
                .type(AuthActionType.EMAIL_CONFIRM)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .lastSentAt(LocalDateTime.now().minusSeconds(20))
                .build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(actionTokenRepository.findActiveByUserIdAndType(9L, AuthActionType.EMAIL_CONFIRM))
                .thenReturn(List.of(latestToken));

        assertThatThrownBy(() -> emailConfirmationService.requestEmailConfirmation(9L))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("60 секунд");

        verify(actionTokenRepository, never()).revokeAllActiveByUserIdAndType(any(), any());
        verify(actionTokenRepository, never()).save(any(AuthActionToken.class));
        verify(notificationSender, never()).sendEmailConfirmationToken(any(), any());
    }

    @Test
    @DisplayName("confirmEmail: валидный token подтверждает email и помечает token used")
    void confirmEmail_validToken_marksEmailVerifiedAndConsumesToken() {
        String plainToken = "email-token";
        AuthActionToken actionToken = AuthActionToken.builder()
                .userId(9L)
                .type(AuthActionType.EMAIL_CONFIRM)
                .tokenHash(PasswordResetService.sha256(plainToken))
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        User user = createUser(false);

        when(actionTokenRepository.findByTokenHashAndType(PasswordResetService.sha256(plainToken), AuthActionType.EMAIL_CONFIRM))
                .thenReturn(Optional.of(actionToken));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(actionTokenRepository.save(any(AuthActionToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        emailConfirmationService.confirmEmail(plainToken);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(actionToken.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(actionTokenRepository).save(actionToken);
    }

    @Test
    @DisplayName("confirmEmail: повторное подтверждение уже verified email остаётся идемпотентным")
    void confirmEmail_alreadyVerified_consumesTokenWithoutResavingUser() {
        String plainToken = "already-verified-token";
        AuthActionToken actionToken = AuthActionToken.builder()
                .userId(9L)
                .type(AuthActionType.EMAIL_CONFIRM)
                .tokenHash(PasswordResetService.sha256(plainToken))
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        User verifiedUser = createUser(true);

        when(actionTokenRepository.findByTokenHashAndType(PasswordResetService.sha256(plainToken), AuthActionType.EMAIL_CONFIRM))
                .thenReturn(Optional.of(actionToken));
        when(userRepository.findById(9L)).thenReturn(Optional.of(verifiedUser));
        when(actionTokenRepository.save(any(AuthActionToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        emailConfirmationService.confirmEmail(plainToken);

        assertThat(actionToken.getUsedAt()).isNotNull();
        verify(userRepository, never()).save(any(User.class));
        verify(actionTokenRepository).save(actionToken);
    }

    @Test
    @DisplayName("confirmEmail: недействительный token отклоняется")
    void confirmEmail_invalidToken_throws() {
        when(actionTokenRepository.findByTokenHashAndType(PasswordResetService.sha256("missing-token"), AuthActionType.EMAIL_CONFIRM))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailConfirmationService.confirmEmail("missing-token"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Недействительный");

        verify(userRepository, never()).findById(any());
    }
}
