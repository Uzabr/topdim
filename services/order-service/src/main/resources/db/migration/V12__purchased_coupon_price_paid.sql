-- Добавляет уплаченную цену на купленный купон (для отображения в профиле «мои купоны»).
-- Цена фиксируется на момент покупки из order_items.unit_price.
ALTER TABLE purchased_coupons ADD COLUMN price_paid NUMERIC(12,2);

-- Бэкофилл существующих купонов: цена берётся из соответствующей позиции заказа
-- по (order_id, coupon_offer_id, coupon_option_id). unit_price одинаков для всех
-- купонов одной позиции, поэтому сопоставление однозначно по цене.
UPDATE purchased_coupons pc
SET price_paid = oi.unit_price
FROM order_items oi
WHERE oi.order_id = pc.order_id
  AND oi.coupon_offer_id = pc.coupon_offer_id
  AND oi.coupon_option_id = pc.coupon_option_id
  AND pc.price_paid IS NULL;
