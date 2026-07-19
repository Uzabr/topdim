/** Локализованное поле name / nameUz с бэкенда */
export function localizedName(
  item: { name: string; nameUz?: string | null },
  language: string,
): string {
  return language.startsWith('uz') ? (item.nameUz || item.name) : item.name;
}

/** Локализованное поле title / titleUz с бэкенда */
export function localizedTitle(
  item: { title: string; titleUz?: string | null },
  language: string,
): string {
  return language.startsWith('uz') ? (item.titleUz || item.title) : item.title;
}
