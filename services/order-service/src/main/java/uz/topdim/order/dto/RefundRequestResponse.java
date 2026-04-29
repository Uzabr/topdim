package uz.topdim.order.dto;

import lombok.Builder;
import lombok.Data;
import uz.topdim.order.entity.RefundRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RefundRequestResponse {
    private Long id;
    private Long orderId;
    private Long purchasedCouponId;
    private Long userId;
    private String couponTitle;
    private String optionTitle;
    private String couponCode;
    private String merchantName;
    private BigDecimal refundAmount;
    private String reason;
    private RefundRequest.RefundStatus status;
    private String adminComment;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime expectedRefundAt;
    private LocalDateTime completedAt;
}
