-- Composite indexes for bazaar/shop queries

-- Bazaars: filtered by city + active
CREATE INDEX IF NOT EXISTS idx_bazaars_city_active ON bazaars(city, active);
CREATE INDEX IF NOT EXISTS idx_bazaars_type ON bazaars(type);

-- Shops: filtered by bazaar + coupon availability
CREATE INDEX IF NOT EXISTS idx_shops_bazaar_coupon ON shops(bazaar_id, has_coupon);
CREATE INDEX IF NOT EXISTS idx_shops_bazaar_active ON shops(bazaar_id, active);
CREATE INDEX IF NOT EXISTS idx_shops_category_active ON shops(category_id, active);
CREATE INDEX IF NOT EXISTS idx_shops_name ON shops(name);

-- Shop product tags: search by tag
CREATE INDEX IF NOT EXISTS idx_shop_tags_tag_uz ON shop_product_tags(tag_uz);
