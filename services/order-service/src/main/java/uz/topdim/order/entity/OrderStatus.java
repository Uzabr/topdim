package uz.topdim.order.entity;

/**
 * Статусы заказа.
 * PENDING, PAID, COMPLETED, CANCELLED, REFUNDED.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    COMPLETED,
    CANCELLED,
    REFUND_REQUESTED,
    REFUNDED
}
