-- Добавляем уникальный индекс на order_id в таблице payments,
-- чтобы гарантировать один платёж на заказ на уровне БД.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_order_id ON payments(order_id);
