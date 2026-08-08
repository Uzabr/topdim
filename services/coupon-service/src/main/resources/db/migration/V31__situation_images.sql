-- V31: картинки для ситуаций «Что хотите сегодня?» (блок Situations на главной).
--
-- В V23 ситуации засеяны без image_url (NULL) → плитки были без фото. Проставляем каждой
-- подходящее по смыслу Unsplash-фото (commercial use, проверены на 200 и визуально):
--   kids    «Отдохнуть с детьми»      → мама с детьми
--   beauty  «Привести себя в порядок» → уходовая процедура
--   health  «Проверить здоровье»      → врач с пациентом (чекап)
--   dinner  «Сходить на ужин»         → ужин в ресторане
--   weekend «Выходные за городом»     → домики на природе
-- Идемпотентно по slug: обновляем только эти 5 существующих ситуаций.

UPDATE situations SET image_url = 'https://images.unsplash.com/photo-1476703993599-0035a21b17a9?auto=format&fit=crop&w=1200&q=80' WHERE slug = 'kids';
UPDATE situations SET image_url = 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=1200&q=80' WHERE slug = 'beauty';
UPDATE situations SET image_url = 'https://images.unsplash.com/photo-1631217868264-e5b90bb7e133?auto=format&fit=crop&w=1200&q=80' WHERE slug = 'health';
UPDATE situations SET image_url = 'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?auto=format&fit=crop&w=1200&q=80' WHERE slug = 'dinner';
UPDATE situations SET image_url = 'https://images.unsplash.com/photo-1583001931096-959e9a1a6223?auto=format&fit=crop&w=1200&q=80' WHERE slug = 'weekend';
