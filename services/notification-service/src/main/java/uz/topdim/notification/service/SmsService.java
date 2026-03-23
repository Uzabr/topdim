package uz.topdim.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * SMS notification service via Eskiz.uz API.
 * Stub mode when disabled (logs only).
 */
@Slf4j
@Service
public class SmsService {

    private final boolean enabled;
    private final String apiUrl;
    private final String apiToken;

    public SmsService(
            @Value("${notification.sms.enabled:false}") boolean enabled,
            @Value("${notification.sms.api-url:https://notify.eskiz.uz/api}") String apiUrl,
            @Value("${notification.sms.api-token:}") String apiToken
    ) {
        this.enabled = enabled;
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
    }

    public void sendCouponPurchasedSms(String phone, String couponTitle, String couponCode) {
        String message = String.format("TopDim: Купон \"%s\" — код: %s", couponTitle, couponCode);
        sendSms(phone, message);
    }

    private void sendSms(String phone, String message) {
        if (!enabled) {
            log.info("[SMS STUB] To: {}, Message: {}", phone, message);
            return;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, String> body = Map.of(
                    "mobile_phone", phone,
                    "message", message
            );
            // Eskiz.uz API call
            restTemplate.postForObject(apiUrl + "/message/sms/send", body, String.class);
            log.info("SMS sent to {}", phone);
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", phone, e.getMessage());
        }
    }
}
