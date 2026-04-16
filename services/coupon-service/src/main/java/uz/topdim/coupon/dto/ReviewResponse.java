package uz.topdim.coupon.dto;

import lombok.Builder;
import lombok.Data;
import uz.topdim.coupon.entity.ReviewStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewResponse {
    private Long id;
    private Long userId;
    private String userName;
    private Long couponOfferId;
    private int rating;
    private String comment;
    private ReviewStatus status;
    private String rejectReason;
    private LocalDateTime createdAt;
}
