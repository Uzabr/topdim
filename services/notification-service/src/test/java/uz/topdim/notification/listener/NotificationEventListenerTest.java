package uz.topdim.notification.listener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.notification.client.IdentityNotificationTargetClient;
import uz.topdim.notification.client.InternalNotificationTargetResponse;
import uz.topdim.notification.entity.NotificationDeliveryChannel;
import uz.topdim.notification.entity.NotificationDeliveryStatus;
import uz.topdim.notification.repository.NotificationDeliveryRepository;
import uz.topdim.notification.repository.NotificationRepository;
import uz.topdim.notification.service.EmailService;
import uz.topdim.notification.service.NotificationDeliveryService;
import uz.topdim.notification.service.NotificationService;
import uz.topdim.notification.service.TelegramDeliveryException;
import uz.topdim.notification.service.TelegramNotificationSender;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_listener;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        NotificationEventListener.class,
        NotificationDeliveryService.class,
        NotificationService.class
})
class NotificationEventListenerTest {

    @Autowired private NotificationEventListener listener;
    @Autowired private NotificationDeliveryService deliveryService;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationDeliveryRepository deliveryRepository;
    @MockBean private IdentityNotificationTargetClient targetClient;
    @MockBean private TelegramNotificationSender telegramSender;
    @MockBean private EmailService emailService;

    @Test
    void duplicateEventCreatesOneInAppNotificationAndOneTelegramDelivery() {
        NotificationEvent event = event("evt-7", 41L);
        when(targetClient.getTarget(41L)).thenReturn(ApiResponse.success(
                new InternalNotificationTargetResponse(
                        41L, "manager@example.uz", true, 998877L, true)));

        listener.handleNotificationEvent(event);
        listener.handleNotificationEvent(event);

        assertThat(notificationRepository.findByEventKeyAndUserId("evt-7", 41L))
                .hasValueSatisfying(notification -> {
                    assertThat(notification.getDeepLink())
                            .isEqualTo("/company/requests/7");
                    assertThat(notification.isRead()).isFalse();
                });
        assertThat(deliveryRepository.findAll())
                .singleElement()
                .satisfies(delivery -> {
                    assertThat(delivery.getChannel())
                            .isEqualTo(NotificationDeliveryChannel.TELEGRAM);
                    assertThat(delivery.getStatus())
                            .isEqualTo(NotificationDeliveryStatus.SENT);
                    assertThat(delivery.getAttemptCount()).isEqualTo(1);
                });
        verify(telegramSender).send(998877L, event.getMessage(), event.getDeepLink());
    }

    @Test
    void telegramFailureDoesNotRemoveInAppNotification() {
        NotificationEvent event = event("evt-8", 42L);
        when(targetClient.getTarget(42L)).thenReturn(ApiResponse.success(
                new InternalNotificationTargetResponse(
                        42L, "owner@example.uz", true, 112233L, true)));
        doThrow(new TelegramDeliveryException("timeout"))
                .when(telegramSender)
                .send(112233L, event.getMessage(), event.getDeepLink());

        listener.handleNotificationEvent(event);

        assertThat(notificationRepository.findByEventKeyAndUserId("evt-8", 42L))
                .isPresent();
        assertThat(deliveryRepository.findByEventKeyAndUserIdAndChannel(
                        "evt-8", 42L, NotificationDeliveryChannel.TELEGRAM))
                .hasValueSatisfying(delivery -> {
                    assertThat(delivery.getStatus())
                            .isEqualTo(NotificationDeliveryStatus.RETRY);
                    assertThat(delivery.getAttemptCount()).isEqualTo(1);
                    assertThat(delivery.getNextAttemptAt()).isNotNull();
                });
    }

    @Test
    void unavailableTelegramUsesVerifiedEmailOnlyOnce() {
        NotificationEvent event = event("evt-9", 43L);
        when(targetClient.getTarget(43L)).thenReturn(ApiResponse.success(
                new InternalNotificationTargetResponse(
                        43L, "owner@example.uz", true, null, false)));

        listener.handleNotificationEvent(event);
        listener.handleNotificationEvent(event);

        assertThat(notificationRepository.findByEventKeyAndUserId("evt-9", 43L))
                .isPresent();
        assertThat(deliveryRepository.findAll())
                .singleElement()
                .satisfies(delivery -> {
                    assertThat(delivery.getChannel())
                            .isEqualTo(NotificationDeliveryChannel.EMAIL);
                    assertThat(delivery.getStatus())
                            .isEqualTo(NotificationDeliveryStatus.SENT);
                    assertThat(delivery.getAttemptCount()).isEqualTo(1);
                });
        verify(emailService).sendProfileUpdateEmail(
                "owner@example.uz", event.getTitle(), event.getMessage(), event.getDeepLink());
        verify(telegramSender, never()).send(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void terminalTelegramFailureCreatesOneEmailFallback() {
        NotificationEvent event = event("evt-10", 44L);
        when(targetClient.getTarget(44L)).thenReturn(ApiResponse.success(
                new InternalNotificationTargetResponse(
                        44L, "manager@example.uz", true, 445566L, true)));
        doThrow(new TelegramDeliveryException("timeout"))
                .when(telegramSender)
                .send(445566L, event.getMessage(), event.getDeepLink());
        listener.handleNotificationEvent(event);
        var telegram = deliveryRepository.findByEventKeyAndUserIdAndChannel(
                "evt-10", 44L, NotificationDeliveryChannel.TELEGRAM).orElseThrow();
        telegram.setAttemptCount(4);
        telegram.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        deliveryRepository.saveAndFlush(telegram);

        int delivered = deliveryService.retryPendingDeliveries();
        deliveryService.retryPendingDeliveries();

        assertThat(delivered).isEqualTo(1);
        assertThat(deliveryRepository.findByEventKeyAndUserIdAndChannel(
                        "evt-10", 44L, NotificationDeliveryChannel.TELEGRAM))
                .hasValueSatisfying(delivery -> {
                    assertThat(delivery.getStatus())
                            .isEqualTo(NotificationDeliveryStatus.FAILED);
                    assertThat(delivery.getAttemptCount()).isEqualTo(5);
                    assertThat(delivery.getNextAttemptAt()).isNull();
                });
        assertThat(deliveryRepository.findByEventKeyAndUserIdAndChannel(
                        "evt-10", 44L, NotificationDeliveryChannel.EMAIL))
                .hasValueSatisfying(delivery -> {
                    assertThat(delivery.getStatus())
                            .isEqualTo(NotificationDeliveryStatus.SENT);
                    assertThat(delivery.getAttemptCount()).isEqualTo(1);
                });
        verify(emailService).sendProfileUpdateEmail(
                "manager@example.uz", event.getTitle(), event.getMessage(), event.getDeepLink());
    }

    @Test
    void temporaryIdentityFailureKeepsInAppAndSchedulesTargetResolutionRetry() {
        NotificationEvent event = event("evt-11", 45L);
        when(targetClient.getTarget(45L))
                .thenThrow(new IllegalStateException("identity unavailable"));

        listener.handleNotificationEvent(event);

        assertThat(notificationRepository.findByEventKeyAndUserId("evt-11", 45L))
                .isPresent();
        assertThat(deliveryRepository.findAll())
                .singleElement()
                .satisfies(delivery -> {
                    assertThat(delivery.getChannel())
                            .isEqualTo(NotificationDeliveryChannel.TELEGRAM);
                    assertThat(delivery.getStatus())
                            .isEqualTo(NotificationDeliveryStatus.RETRY);
                    assertThat(delivery.getAttemptCount()).isZero();
                    assertThat(delivery.getNextAttemptAt()).isNotNull();
                    assertThat(delivery.getLastError()).isEqualTo("TargetUnavailable");
                });
    }

    @Test
    void inAppResponseExposesDeepLinkToPartnerRequest() {
        NotificationEvent event = event("evt-12", 46L);
        when(targetClient.getTarget(46L)).thenReturn(ApiResponse.success(
                new InternalNotificationTargetResponse(
                        46L, null, false, null, false)));
        listener.handleNotificationEvent(event);

        var response = notificationService.getUserNotifications(
                46L, false, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(response.getContent())
                .singleElement()
                .satisfies(notification -> assertThat(notification.getDeepLink())
                        .isEqualTo("/company/requests/7"));
        assertThat(deliveryRepository.findAll()).isEmpty();
    }

    private NotificationEvent event(String eventKey, Long userId) {
        return NotificationEvent.builder()
                .eventKey(eventKey)
                .userId(userId)
                .title("Статус заявки")
                .message("Компания «Market», заявка #7: APPROVED")
                .type("SUCCESS")
                .deepLink("/company/requests/7")
                .timestamp(LocalDateTime.of(2026, 8, 13, 10, 0))
                .build();
    }
}
