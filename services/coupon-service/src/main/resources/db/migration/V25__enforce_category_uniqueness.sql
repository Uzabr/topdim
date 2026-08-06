CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_name_ci
    ON categories (LOWER(name));

CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_slug_ci
    ON categories (LOWER(slug))
    WHERE slug IS NOT NULL;
