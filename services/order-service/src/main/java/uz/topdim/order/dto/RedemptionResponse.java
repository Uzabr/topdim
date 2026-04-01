package uz.topdim.order.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * DTO записи о погашении купона (для партнёра).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionResponse {
    private Long id;
    private String couponTitle;
    private String optionTitle;
    private String couponCode;
    private String redeemedByStaff;
    private String note;
    private LocalDateTime redeemedAt;
}
