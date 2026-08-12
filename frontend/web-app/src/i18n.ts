import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

/**
 * Ленивая загрузка локалей: в бандл первичной загрузки попадает только активная
 * локаль (динамический import → отдельный чанк). Вторую подгружаем ТОЛЬКО при
 * переключении языка — поэтому используем switchLanguage()/loadLocale(), которые
 * сперва грузят бандл, а потом меняют язык (без «мигания» непереведённых ключей).
 */

const SUPPORTED = ['ru', 'uz'] as const;
type Lng = (typeof SUPPORTED)[number];

const loaders: Record<Lng, () => Promise<{ default: Record<string, unknown> }>> = {
  ru: () => import('./locales/ru.json'),
  uz: () => import('./locales/uz.json'),
};

function isLng(value: string | null | undefined): value is Lng {
  return value === 'ru' || value === 'uz';
}

/** Стартовая локаль: путь /ru|/uz → localStorage → navigator → ru (как раньше делал детектор). */
function detectInitial(): Lng {
  const seg = window.location.pathname.split('/')[1];
  if (isLng(seg)) return seg;
  const stored = localStorage.getItem('language');
  if (isLng(stored)) return stored;
  return navigator.language?.slice(0, 2) === 'uz' ? 'uz' : 'ru';
}

/** Идемпотентно догружает бандл локали. */
export async function loadLocale(lng: string): Promise<void> {
  const l = lng.slice(0, 2);
  if (!isLng(l) || i18n.hasResourceBundle(l, 'translation')) return;
  const mod = await loaders[l]();
  i18n.addResourceBundle(l, 'translation', mod.default, true, true);
}

/** Переключение языка: сперва грузим локаль, затем меняем — без флеша ключей. */
export async function switchLanguage(lng: string): Promise<void> {
  const l = lng.slice(0, 2);
  if (!isLng(l)) return;
  await loadLocale(l);
  await i18n.changeLanguage(l);
  localStorage.setItem('language', l);
}

/**
 * Инициализация i18n — вызывается ТОЛЬКО из main.tsx (перед первым рендером).
 * Намеренно НЕ на уровне модуля: иначе простой импорт switchLanguage из компонентов
 * (в т.ч. в юнит-тестах с моком react-i18next) запускал бы init и падал.
 * Грузит активную локаль и ждёт готовности, чтобы не мелькали сырые ключи.
 */
export function initI18n(): Promise<unknown> {
  const initial = detectInitial();
  return loaders[initial]().then((mod) =>
    i18n
      .use(LanguageDetector)
      .use(initReactI18next)
      .init({
        lng: initial,
        fallbackLng: 'ru',
        supportedLngs: SUPPORTED as unknown as string[],
        resources: { [initial]: { translation: mod.default } },
        interpolation: {
          escapeValue: false, // React strictly prevents XSS
        },
        react: {
          useSuspense: false, // готовность гарантируем через initI18n() в main.tsx
        },
        detection: {
          order: ['path', 'localStorage', 'navigator'],
          caches: ['localStorage'],
          lookupLocalStorage: 'language',
          lookupFromPathIndex: 0,
        },
      }),
  );
}

export default i18n;
