import i18n from '../i18n';

/**
 * Format price in Uzbek sums
 */
export function formatPrice(price: number): string {
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  return price.toLocaleString(locale) + ' ' + i18n.t('common.currency.sum');
}

/**
 * Format discount percentage
 */
export function calcDiscount(oldPrice: number, newPrice: number): number {
  if (!oldPrice || oldPrice <= newPrice) return 0;
  return Math.round((1 - newPrice / oldPrice) * 100);
}

/**
 * Format date to localized string
 */
export function formatDate(dateStr: string): string {
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  return new Date(dateStr).toLocaleDateString(locale, {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}

/**
 * Calculate days remaining until a date
 */
export function daysUntil(dateStr: string): number {
  return Math.max(0, Math.ceil((new Date(dateStr).getTime() - Date.now()) / 86400000));
}

/** Дата уже прошла (срок продажи/использования истёк). */
export function isPast(dateStr: string): boolean {
  return new Date(dateStr).getTime() < Date.now();
}

/**
 * Pluralize Russian word (simplified)
 */
export function pluralize(n: number, one: string, few: string, many: string): string {
  const abs = Math.abs(n) % 100;
  const lastDigit = abs % 10;
  if (abs > 10 && abs < 20) return many;
  if (lastDigit > 1 && lastDigit < 5) return few;
  if (lastDigit === 1) return one;
  return many;
}
