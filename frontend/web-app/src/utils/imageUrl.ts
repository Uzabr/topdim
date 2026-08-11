/**
 * Right-size изображений на лету.
 *
 * Демо-данные (миграции V27–V31) и ситуации приходят как внешние
 * `images.unsplash.com/...?w=1200&q=80` — исходник ~160 KB, хотя карточка рисуется
 * на 300–600 px. Unsplash Images API уважает query-параметры `w`/`q`/`auto`/`fit`,
 * поэтому просим ровно нужную ширину и `auto=format` (webp/avif где поддерживается) —
 * это режет вес каждой обложки в 4–6 раз без потери видимого качества.
 *
 * Любой НЕ-unsplash URL (будущий собственный media-storage) возвращаем без изменений —
 * его ресайзом должен заниматься media-service, а не фронт.
 *
 * @param url    исходный URL обложки (может быть undefined/null — вернём как есть)
 * @param width  желаемая ширина в CSS-пикселях (для retina берём с запасом ×2)
 * @param quality JPEG/webp-качество 1–100 (по умолчанию 70 — незаметно на превью)
 */
export function srcAt(
  url: string | undefined | null,
  width: number,
  quality = 70,
): string | undefined {
  if (!url) return url ?? undefined;

  let u: URL;
  try {
    u = new URL(url);
  } catch {
    return url; // относительный/битый URL — не трогаем
  }

  if (u.hostname !== 'images.unsplash.com') return url;

  u.searchParams.set('w', String(Math.round(width)));
  u.searchParams.set('q', String(quality));
  u.searchParams.set('auto', 'format');
  if (!u.searchParams.has('fit')) u.searchParams.set('fit', 'crop');
  return u.toString();
}
