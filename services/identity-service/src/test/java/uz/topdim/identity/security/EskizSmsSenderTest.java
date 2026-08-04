package uz.topdim.identity.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class EskizSmsSenderTest {

    private static final String BASE_URL = "https://notify.eskiz.uz/api";
    private static final String LOGIN_URL = BASE_URL + "/auth/login";
    private static final String SEND_URL = BASE_URL + "/message/sms/send";
    private static final String TEMPLATE = "SizBiz tasdiqlash kodi: {code}";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private EskizSmsSender sender;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger senderLogger;

    @BeforeEach
    void init() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // Package-private конструктор с уже собранным RestClient: публичный (DI) конструктор сам
        // выставляет requestFactory с таймаутами и затёр бы мок, который MockRestServiceServer.bindTo()
        // только что подставил в builder.
        RestClient mockedRestClient = builder.baseUrl(BASE_URL).build();
        sender = new EskizSmsSender(mockedRestClient, "test@eskiz.uz", "secret-pass", "4546", TEMPLATE);

        senderLogger = (Logger) LoggerFactory.getLogger(EskizSmsSender.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        senderLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        senderLogger.detachAppender(logAppender);
    }

    private static MultiValueMap<String, String> sendForm(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mobile_phone", "998901234567");
        form.add("message", "SizBiz tasdiqlash kodi: " + code);
        form.add("from", "4546");
        return form;
    }

    private static String loginResponseJson(String token) {
        return """
                {"message":"token_generated","data":{"token":"%s"}}
                """.formatted(token);
    }

    @Test
    void sendOtp_happyPath_logsInThenSendsWithBearerToken() {
        server.expect(requestTo(LOGIN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(new LinkedMultiValueMap<>(java.util.Map.of(
                        "email", java.util.List.of("test@eskiz.uz"),
                        "password", java.util.List.of("secret-pass")))))
                .andRespond(withSuccess(loginResponseJson("tok-1"), MediaType.APPLICATION_JSON));

        server.expect(requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andExpect(content().formData(sendForm("123456")))
                .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));

        sender.sendOtp("+998901234567", "123456");

        server.verify();
    }

    @Test
    void sendOtp_secondCall_reusesTheCachedToken_noSecondLogin() {
        server.expect(requestTo(LOGIN_URL))
                .andRespond(withSuccess(loginResponseJson("tok-1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withSuccess("{\"id\":\"2\"}", MediaType.APPLICATION_JSON));

        sender.sendOtp("+998901234567", "111111");
        sender.sendOtp("+998901234567", "222222");

        // Только один запрос логина был зарегистрирован/ожидался — verify() провалится,
        // если реализация дернёт login() ещё раз (лишний запрос не будет соответствовать ни одному expectation).
        server.verify();
    }

    @Test
    void sendOtp_sendReturns401_refreshesTokenOnceAndRetries() {
        server.expect(requestTo(LOGIN_URL))
                .andRespond(withSuccess(loginResponseJson("stale-tok"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL))
                .andExpect(header("Authorization", "Bearer stale-tok"))
                .andRespond(withStatus(UNAUTHORIZED).body("{\"message\":\"Token is invalid\"}").contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(LOGIN_URL))
                .andRespond(withSuccess(loginResponseJson("fresh-tok"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL))
                .andExpect(header("Authorization", "Bearer fresh-tok"))
                .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> sender.sendOtp("+998901234567", "123456")).doesNotThrowAnyException();

        server.verify();
    }

    @Test
    void sendOtp_loginFails_doesNotThrow_butLogsErrorWithStackTrace() {
        server.expect(requestTo(LOGIN_URL))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR).body("boom"));

        assertThatCode(() -> sender.sendOtp("+998901234567", "123456")).doesNotThrowAnyException();

        server.verify();
        assertThat(logAppender.list)
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        // причина сбоя не глотается: реальный Throwable (для трейса), не только текст
                        && event.getThrowableProxy() != null);
    }

    @Test
    void sendOtp_sendFailsWithNonAuthError_doesNotThrow_logsHttpStatusAndBody() {
        server.expect(requestTo(LOGIN_URL))
                .andRespond(withSuccess(loginResponseJson("tok-1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR)
                        .body("{\"message\":\"limit exceeded\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatCode(() -> sender.sendOtp("+998901234567", "123456")).doesNotThrowAnyException();

        server.verify();
        assertThat(logAppender.list)
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        && event.getThrowableProxy() != null
                        // причина сбоя (HTTP-статус + тело ответа) реально попадает в текст лога,
                        // а не теряется за голым enum-исходом
                        && event.getFormattedMessage().contains("500")
                        && event.getFormattedMessage().contains("limit exceeded"));
    }
}
