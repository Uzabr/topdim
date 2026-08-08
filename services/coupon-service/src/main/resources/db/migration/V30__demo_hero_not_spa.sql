-- V30: «Купон дня» (hero) не должен быть спа.
--
-- Hero на главной = getCatalog(sortBy='discount', size=1).content[0] — т.е. оффер с
-- максимальной discount_percent. Сортировка только по discountPercent DESC без тай-брейка
-- (CouponOfferService: case "discount"), поэтому при нескольких 20% наверх попадал
-- «СПА-день» (Serenity SPA). Делаем единственным максимумом (20%) НЕ-спа оффер
-- «Napoli» Пиццерию, а прочим двадцаткам (спа, PS-клуб, фитнес, фотостудия) снижаем
-- скидку ниже 20 → купоном дня детерминированно становится пицца.
--
-- «Napoli» (Большая пицца + напиток) не трогаем — остаётся 20% и становится единственным
-- максимумом. Пересчитываем from_price и coupon_price = old_price*(1-%/100) у изменяемых.
-- Правит только демо (website='https://demo.sizbiz.uz').

WITH d(title, disc, newprice) AS (
    VALUES
        ('СПА-день: хаммам + пилинг + массаж', 12, 484000.00),  -- old 550000  (спа уходит с верха)
        ('2 часа игры на PlayStation 5',       15,  85000.00),  -- old 100000
        ('Абонемент на месяц',                 18, 410000.00),  -- old 500000
        ('Фотосессия 1 час + 15 фото',         18, 328000.00)   -- old 400000
)
UPDATE coupon_offers c
SET discount_percent = d.disc,
    from_price       = d.newprice
FROM d, merchants m
WHERE c.merchant_id = m.id
  AND m.website = 'https://demo.sizbiz.uz'
  AND c.title = d.title;

WITH d(title, newprice) AS (
    VALUES
        ('СПА-день: хаммам + пилинг + массаж', 484000.00),
        ('2 часа игры на PlayStation 5',        85000.00),
        ('Абонемент на месяц',                 410000.00),
        ('Фотосессия 1 час + 15 фото',         328000.00)
)
UPDATE coupon_options o
SET coupon_price = d.newprice
FROM d, coupon_offers c, merchants m
WHERE o.coupon_offer_id = c.id
  AND c.merchant_id = m.id
  AND m.website = 'https://demo.sizbiz.uz'
  AND c.title = d.title;
