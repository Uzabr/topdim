package uz.topdim.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_requests")
@Getter @Setter @Builder @NoArgsConstructor
/**
 * Запрос на возврат средств.
 * Создаётся пользователем per purchased coupon, обрабатывается админом.
 * Статусы: PENDING → APPROVED_PROCESSING → REFUNDED / REJECTED.
 */
@AllArgsConstructor
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchased_coupon_id")
    private PurchasedCoupon purchasedCoupon;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RefundStatus status = RefundStatus.PENDING;

    @Column
    private String adminComment;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "expected_refund_at")
    private LocalDateTime expectedRefundAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime resolvedAt;

    public enum RefundStatus {
        PENDING,
        APPROVED_PROCESSING,
        REFUNDED,
        REJECTED
    }
}
