import { useEffect } from 'react';
import { useParams, useNavigate, useLocation, Outlet } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

const SUPPORTED_LANGS = ['ru', 'uz'];

/**
 * Синхронизирует язык из URL (/:lang/...) с i18n.
 * Если язык в URL отсутствует или невалиден — редиректит на текущий i18n язык.
 */
export default function LocaleLayout() {
  const { lang } = useParams<{ lang: string }>();
  const { i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!lang || !SUPPORTED_LANGS.includes(lang)) {
      // Redirect to current language prefix
      const currentLang = i18n.language?.substring(0, 2) || 'ru';
      const validLang = SUPPORTED_LANGS.includes(currentLang) ? currentLang : 'ru';
      navigate(`/${validLang}${location.pathname}${location.search}`, { replace: true });
      return;
    }

    if (i18n.language !== lang) {
      i18n.changeLanguage(lang);
      localStorage.setItem('language', lang);
    }
  }, [lang, i18n, navigate, location.pathname, location.search]);

  return <Outlet />;
}
