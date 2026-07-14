import type { CouponOffer, CouponOption } from '../api/coupons';

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

/** Остаток по одной опции. null — лимита нет, ограничивать нечем. */
export function optionLeft(option: CouponOption): number | null {
  if (!option.quantityLimit || option.quantityLimit <= 0) return null;
  return Math.max(0, option.quantityLimit - (option.quantitySold ?? 0));
}

/** Прогресс выкупа одной опции. null, если у неё нет лимита. */
export function optionStock(option: CouponOption): Stock | null {
  const left = optionLeft(option);
  if (left === null) return null;

  const limit = option.quantityLimit as number;
  const sold = Math.min(limit, option.quantitySold ?? 0);

  return {
    limit,
    sold,
    left,
    percent: Math.min(100, Math.round((sold / limit) * 100)),
  };
}
