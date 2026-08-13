package uz.topdim.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

class TelegramNotificationSenderTest {

    @Test
    void sendsUrlEncodedUnicodeMessageAndAbsolutePartnerLink() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramNotificationSender sender = new TelegramNotificationSender(
                builder.build(), "123:secret-token", "https://partner.sizbiz.uz");
        server.expect(once(), requestTo(
                        "https://api.telegram.org/bot123:secret-token/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("chat_id=998877"),
                        org.hamcrest.Matchers.containsString(
                                "%D0%97%D0%B0%D1%8F%D0%B2%D0%BA%D0%B0"),
                        org.hamcrest.Matchers.containsString(
                                "https%3A%2F%2Fpartner.sizbiz.uz%2Fcompany%2Frequests%2F7"))))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        sender.send(998877L, "Заявка одобрена", "/company/requests/7");

        server.verify();
    }

    @Test
    void emptyTokenFailsClosedBeforeAnyNetworkCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramNotificationSender sender = new TelegramNotificationSender(
                builder.build(), " ", "https://partner.sizbiz.uz");

        assertThatThrownBy(() -> sender.send(998877L, "Message", "/company/requests/7"))
                .isInstanceOf(TelegramDeliveryException.class)
                .hasMessageContaining("не настроен");
        server.verify();
    }

    @Test
    void telegramHttpFailureBecomesDeliveryExceptionWithoutResponseDetails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramNotificationSender sender = new TelegramNotificationSender(
                builder.build(), "123:secret-token", "https://partner.sizbiz.uz");
        server.expect(requestTo("https://api.telegram.org/bot123:secret-token/sendMessage"))
                .andRespond(withTooManyRequests());

        assertThatThrownBy(() -> sender.send(
                        998877L, "Message", "/company/requests/7"))
                .isInstanceOf(TelegramDeliveryException.class)
                .hasMessage("Telegram delivery failed")
                .hasMessageNotContaining("123:secret-token")
                .hasMessageNotContaining("998877");
        server.verify();
    }
}
