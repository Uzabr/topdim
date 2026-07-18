-- Сид 5 ситуаций из продуктовой доки (раздел 10.6 Навигация).
-- Заголовки ru/uz взяты из frontend/web-app/src/locales/{ru,uz}.json → home.situations.*.
-- Привязки купонов НЕ засеваются — заполняет админ.

INSERT INTO situations (slug, title, title_uz, featured, sort_order, active) VALUES
    ('kids',     'Отдохнуть с детьми',         'Bolalar bilan dam olish',            TRUE,  0, TRUE),
    ('beauty',   'Привести себя в порядок',    'O''zingizga g''amxo''rlik qilish',   FALSE, 1, TRUE),
    ('health',   'Проверить здоровье',         'Sog''liqni tekshirish',              FALSE, 2, TRUE),
    ('dinner',   'Сходить на ужин',            'Kechki ovqatga chiqish',             FALSE, 3, TRUE),
    ('weekend',  'Выходные за городом',        'Shahar tashqarisida dam olish',      FALSE, 4, TRUE);
