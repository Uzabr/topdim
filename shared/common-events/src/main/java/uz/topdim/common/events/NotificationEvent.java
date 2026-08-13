package uz.topdim.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Событие для отправки in-app уведомлений.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String eventKey;
    private Long userId;
    private String title;
    private String message;
    private String type; // e.g., "INFO", "ALERT", "SUCCESS", "WARNING"
    private String deepLink;
    private LocalDateTime timestamp;
}
