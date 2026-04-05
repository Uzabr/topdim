import { useState } from 'react';
import { Search, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import './SearchBar.css';

interface SearchBarProps {
  value?: string;
  onChange?: (val: string) => void;
  onSubmit?: (val: string) => void;
  placeholder?: string;
  autoFocus?: boolean;
}

export default function SearchBar({
  value,
  onChange,
  onSubmit,
  placeholder,
  autoFocus = false,
}: SearchBarProps) {
  const { t } = useTranslation();
  const [internalVal, setInternalVal] = useState('');

  const displayVal = value !== undefined ? value : internalVal;
  const defaultPlaceholder = t('search.placeholder', { defaultValue: 'Поиск...' });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newVal = e.target.value;
    if (value === undefined) setInternalVal(newVal);
    if (onChange) onChange(newVal);
  };

  const handleClear = () => {
    if (value === undefined) setInternalVal('');
    if (onChange) onChange('');
    if (onSubmit) onSubmit('');
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (onSubmit) onSubmit(displayVal);
  };

  return (
    <form className="search-bar" onSubmit={handleSubmit}>
      <Search size={18} className="search-bar__icon" />
      <input
        type="text"
        className="search-bar__input"
        placeholder={placeholder || defaultPlaceholder}
        value={displayVal}
        onChange={handleChange}
        autoFocus={autoFocus}
      />
      {displayVal.length > 0 && (
        <button type="button" className="search-bar__clear" onClick={handleClear} aria-label="Очистить">
          <X size={16} />
        </button>
      )}
      <button type="submit" className="search-bar__submit">
        {t('search.button', { defaultValue: 'Найти' })}
      </button>
    </form>
  );
}
