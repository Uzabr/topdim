-- Composite indexes for coupon catalog performance

-- Coupon offers: catalog browsing (category + status + sorting)
CREATE INDEX IF NOT EXISTS idx_coupon_offers_category_status ON coupon_offers(category_id, status);
CREATE INDEX IF NOT EXISTS idx_coupon_offers_status_created ON coupon_offers(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_coupon_offers_status_sold ON coupon_offers(status, total_sold DESC);
CREATE INDEX IF NOT EXISTS idx_coupon_offers_merchant ON coupon_offers(merchant_id);

-- Coupon options: loaded with offer
CREATE INDEX IF NOT EXISTS idx_coupon_options_status ON coupon_options(status);

-- Categories: sorted listing
CREATE INDEX IF NOT EXISTS idx_categories_active_sort ON categories(active, sort_order);

-- Coupon images: loaded with offer
CREATE INDEX IF NOT EXISTS idx_coupon_images_offer ON coupon_images(coupon_offer_id, sort_order);
