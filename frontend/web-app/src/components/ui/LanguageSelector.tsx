import { useTranslation } from 'react-i18next';
import { useNavigate, useLocation } from 'react-router-dom';
import Select from './Select';

/* Код языка текстом: эмодзи-флаги в UI запрещены (дизайн-система «Оса») */
const LangCode = ({ code }: { code: 'ru' | 'uz' }) => (
  <span className="select-lang-code" aria-hidden="true">
    {code.toUpperCase()}
  </span>
);

export default function LanguageSelector() {
  const { i18n, t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  const currentLang = i18n.language?.substring(0, 2) || 'ru';

  const options = [
    { id: 'ru', label: t('language.ru'), icon: <LangCode code="ru" /> },
    { id: 'uz', label: t('language.uz'), icon: <LangCode code="uz" /> },
  ];

  return (
    <Select
      options={options}
      value={currentLang}
      onChange={(val) => {
        const newLang = val as string;
        i18n.changeLanguage(newLang);
        localStorage.setItem('language', newLang);
        
        const currentPath = location.pathname;
        const pathWithoutLang = currentPath.replace(/^\/(ru|uz)/, '');
        navigate(`/${newLang}${pathWithoutLang || '/'}${location.search}`, { replace: true });
      }}
      triggerIcon={<LangCode code={currentLang as 'ru' | 'uz'} />}
      minWidth="105px"
      align="right"
    />
  );
}
