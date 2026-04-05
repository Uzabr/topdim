import { useTranslation } from 'react-i18next';
import { Globe } from 'lucide-react';
import Select from './Select';

export default function LanguageSelector() {
  const { i18n } = useTranslation();

  const options = [
    { id: 'ru', label: 'Русский' },
    { id: 'uz', label: "O'zbekcha" },
  ];

  return (
    <Select
      options={options}
      value={i18n.language}
      onChange={(val) => {
        const newLang = val as string;
        i18n.changeLanguage(newLang);
        localStorage.setItem('language', newLang);
      }}
      triggerIcon={<Globe size={15} />}
      minWidth="105px"
      align="right"
    />
  );
}
