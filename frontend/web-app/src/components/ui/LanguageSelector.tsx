import { useTranslation } from 'react-i18next';
import { useNavigate, useLocation } from 'react-router-dom';
import Select from './Select';

// 🇷🇺 🇺🇿 Компонент для отображения флага (эмодзи)
const Flag = ({ code }: { code: 'ru' | 'uz' }) => (
  <span className="select-flag" aria-hidden="true">
    {code === 'ru' ? '🇷🇺' : '🇺🇿'}
  </span>
);

export default function LanguageSelector() {
  const { i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  const currentLang = i18n.language?.substring(0, 2) || 'ru';

  const options = [
    { id: 'ru', label: 'Ру', icon: <Flag code="ru" /> },
    { id: 'uz', label: 'Узб', icon: <Flag code="uz" /> },
  ];

  return (
    <Select
      options={options}
      value={currentLang}
      onChange={(val) => {
        const newLang = val as string;
        i18n.changeLanguage(newLang);
        localStorage.setItem('language', newLang);
        
        // Replace the lang prefix in the current URL
        const currentPath = location.pathname;
        const pathWithoutLang = currentPath.replace(/^\/(ru|uz)/, '');
        navigate(`/${newLang}${pathWithoutLang || '/'}${location.search}`, { replace: true });
      }}
      // Показываем флаг текущего языка в заголовке селекта
      triggerIcon={<Flag code={currentLang as 'ru' | 'uz'} />}
      minWidth="105px"
      align="right"
    />
  );
}