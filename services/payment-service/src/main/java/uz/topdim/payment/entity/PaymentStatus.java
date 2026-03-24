package uz.topdim.payment.entity;

/**
 * Статусы платежа.
 * PENDING, PROCESSING, COMPLETED, FAILED, REFUNDED.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}
