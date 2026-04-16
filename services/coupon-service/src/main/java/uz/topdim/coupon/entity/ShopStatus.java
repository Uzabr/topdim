package uz.topdim.coupon.entity;

/**
 * Статус магазина в справочнике.
 * ACTIVE — опубликован.
 * PENDING_REVIEW — ожидает модерации (будущий Telegram-бот).
 * INACTIVE — деактивирован.
 */
public enum ShopStatus {
    ACTIVE,
    PENDING_REVIEW,
    INACTIVE
}
