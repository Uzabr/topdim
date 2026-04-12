package uz.topdim.coupon.entity;

/**
 * Статусы купонного предложения (State Machine — Консьерж-модель).
 *
 * <p>Основной флоу:
 * LEAD → DRAFT → WAITING_FOR_MERCHANT → ACTIVE
 *                                     ↘ REVISION_REQUESTED → DRAFT → ...
 */
public enum CouponStatus {
    /** Заявка/лид — начальная точка входа. */
    LEAD,
    /** Черновик, заполняется менеджером. */
    DRAFT,
    /** Отправлен партнёру (мерчанту) на согласование. */
    WAITING_FOR_MERCHANT,
    /** Партнёр запросил правки, купон возвращён менеджеру. */
    REVISION_REQUESTED,
    /** Партнёр одобрил — купон опубликован в каталоге. */
    ACTIVE
}
