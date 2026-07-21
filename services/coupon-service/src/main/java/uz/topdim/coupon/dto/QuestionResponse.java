package uz.topdim.coupon.dto;

import lombok.Builder;
import lombok.Data;
import uz.topdim.coupon.entity.QuestionStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class QuestionResponse {
    private Long id;
    private Long couponOfferId;
    private String userName;
    private String question;
    private String answer;
    private LocalDateTime answeredAt;
    private QuestionStatus status;
    private String rejectReason;
    private LocalDateTime createdAt;
}
