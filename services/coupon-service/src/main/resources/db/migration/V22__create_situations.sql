-- Ситуации (кураторские подборки купонов для главной).
-- Вариант A: явная привязка situation ↔ coupon через join-таблицу.

CREATE TABLE situations (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(64)  NOT NULL UNIQUE,    -- стабильный ключ: kids, beauty, ...
    title       VARCHAR(128) NOT NULL,           -- ru
    title_uz    VARCHAR(128),                    -- uz
    image_url   VARCHAR(512),
    featured    BOOLEAN NOT NULL DEFAULT FALSE,  -- тёмный хиро на 2 колонки
    sort_order  INT     NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE situation_coupons (
    situation_id BIGINT NOT NULL REFERENCES situations(id) ON DELETE CASCADE,
    coupon_id    BIGINT NOT NULL REFERENCES coupon_offers(id) ON DELETE CASCADE,
    sort_order   INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (situation_id, coupon_id)
);

-- Обратный индекс: быстрый поиск ситуаций по купону.
CREATE INDEX idx_situation_coupons_coupon ON situation_coupons(coupon_id);
-- Прямой индекс: стабильный порядок купонов внутри кураторской подборки.
CREATE INDEX idx_situation_coupons_situation_sort ON situation_coupons(situation_id, sort_order);
