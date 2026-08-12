package uz.topdim.identity.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class EmailNotificationSenderTest {

    @Mock JavaMailSender mailSender;

    SimpleMeterRegistry registry;
    EmailNotificationSender sender;

    @BeforeEach
    void init() {
        registry = new SimpleMeterRegistry();
        sender = new EmailNotificationSender(mailSender, "noreply@sizbiz.uz", registry, "https://sizbiz.uz", "smtp-user", "smtp-pass");
    }

    @Test
    void sendPasswordResetToken_smtpFails_doesNotThrow_andIncrementsFailedCounter() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> sender.sendPasswordResetToken("user@example.com", "123456"))
                .doesNotThrowAnyException();

        assertThat(registry.counter("notification.email.failed", "type", "reset").count()).isEqualTo(1.0);
    }

    @Test
    void sendEmailConfirmationToken_smtpFails_doesNotThrow_andIncrementsFailedCounter() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> sender.sendEmailConfirmationToken("user@example.com", "654321"))
                .doesNotThrowAnyException();

        assertThat(registry.counter("notification.email.failed", "type", "confirm").count()).isEqualTo(1.0);
    }

    @Test
    void sendPasswordResetToken_happyPath_doesNotIncrementFailedCounter() {
        sender.sendPasswordResetToken("user@example.com", "123456");

        assertThat(registry.counter("notification.email.failed", "type", "reset").count()).isEqualTo(0.0);
    }

    @Test
    void sendEmailChangeToken_smtpFails_doesNotThrow_andIncrementsFailedCounter() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> sender.sendEmailChangeToken("new@example.com", "789012"))
                .doesNotThrowAnyException();

        assertThat(registry.counter("notification.email.failed", "type", "change").count()).isEqualTo(1.0);
    }

    @Test
    void sendEmailChangeToken_happyPath_doesNotIncrementFailedCounter() {
        sender.sendEmailChangeToken("new@example.com", "789012");

        assertThat(registry.counter("notification.email.failed", "type", "change").count()).isEqualTo(0.0);
    }
}
