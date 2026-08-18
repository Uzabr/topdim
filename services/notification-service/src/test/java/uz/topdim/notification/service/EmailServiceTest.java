package uz.topdim.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;

    @Test
    void profileUpdateUsesConfiguredPartnerBaseUrl() {
        EmailService service = new EmailService(
                true,
                "noreply@sizbiz.uz",
                "https://partner.staging.sizbiz.uz/",
                mailSender
        );

        service.sendProfileUpdateEmail(
                "owner@example.uz",
                "Статус заявки",
                "Заявка одобрена",
                "/company/requests/7"
        );

        ArgumentCaptor<SimpleMailMessage> message =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getText())
                .contains("https://partner.staging.sizbiz.uz/company/requests/7");
    }

    @Test
    void disabledEmailFailsSoDeliveryCanBeRetried() {
        EmailService service = new EmailService(
                false,
                "noreply@sizbiz.uz",
                "https://partner.sizbiz.uz",
                mailSender
        );

        assertThatThrownBy(() -> service.sendProfileUpdateEmail(
                "owner@example.uz",
                "Статус заявки",
                "Заявка одобрена",
                "/company/requests/7"
        )).isInstanceOf(IllegalStateException.class);
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }
}
