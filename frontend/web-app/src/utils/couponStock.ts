import type { CouponOffer } from '../api/coupons';

export interface Stock {
  limit: number;
  sold: number;
  left: number;
  /** Доля выкупа, 0…100. */
  percent: number;
}

/**
 * Остаток по купону — суммарно по всем опциям. null, если ни у одной опции
 * нет лимита: тогда прогресс выкупа показывать нечем.
 */
export function couponStock(coupon: Pick<CouponOffer, 'options'>): Stock | null {
  const limited = coupon.options?.filter((o) => o.quantityLimit && o.quantityLimit > 0) ?? [];
  if (limited.length === 0) return null;

  const limit = limited.reduce((sum, o) => sum + (o.quantityLimit ?? 0), 0);
  const sold = limited.reduce((sum, o) => sum + (o.quantitySold ?? 0), 0);

  return {
    limit,
    sold,
    left: Math.max(0, limit - sold),
    percent: limit > 0 ? Math.min(100, Math.round((sold / limit) * 100)) : 0,
  };
}
