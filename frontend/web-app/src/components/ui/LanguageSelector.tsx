import { useTranslation } from 'react-i18next';
import { useNavigate, useLocation } from 'react-router-dom';
import { Globe } from 'lucide-react';
import Select from './Select';

export default function LanguageSelector() {
  const { i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  const options = [
    { id: 'ru', label: 'Ру' },
    { id: 'uz', label: 'Узб' },
  ];

  return (
    <Select
      options={options}
      value={i18n.language?.substring(0, 2) || 'ru'}
      onChange={(val) => {
        const newLang = val as string;
        i18n.changeLanguage(newLang);
        localStorage.setItem('language', newLang);
        // Replace the lang prefix in the current URL
        const currentPath = location.pathname;
        const pathWithoutLang = currentPath.replace(/^\/(ru|uz)/, '');
        navigate(`/${newLang}${pathWithoutLang || '/'}${location.search}`, { replace: true });
      }}
      triggerIcon={<Globe size={15} />}
      minWidth="105px"
      align="right"
    />
  );
}
