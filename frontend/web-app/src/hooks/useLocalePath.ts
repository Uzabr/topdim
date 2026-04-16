import { useTranslation } from 'react-i18next';

/**
 * Возвращает функцию localePath, которая добавляет текущий языковой префикс к пути.
 * Пример: localePath('/coupons') → '/ru/coupons'
 */
export function useLocalePath() {
  const { i18n } = useTranslation();
  const lang = i18n.language?.substring(0, 2) || 'ru';

  return (path: string) => {
    if (path.startsWith(`/${lang}`)) return path;
    return `/${lang}${path.startsWith('/') ? '' : '/'}${path}`;
  };
}
