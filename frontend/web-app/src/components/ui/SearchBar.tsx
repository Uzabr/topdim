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
  suggestions?: { id: number; label: string }[];
  onSuggestionClick?: (id: number, label: string) => void;
}

export default function SearchBar({
  value,
  onChange,
  onSubmit,
  placeholder,
  autoFocus = false,
  suggestions = [],
  onSuggestionClick,
}: SearchBarProps) {
  const { t } = useTranslation();
  const [internalVal, setInternalVal] = useState('');
  const [isFocused, setIsFocused] = useState(false);

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
    setIsFocused(false);
  };

  const normalizedInput = displayVal.trim().toLowerCase();
  const filteredSuggestions = suggestions.filter((s) => s.label.toLowerCase().includes(normalizedInput));

  const showDropdown = isFocused && normalizedInput.length > 0;

  return (
    <div className="search-bar-container">
      <form className={`search-bar ${isFocused ? 'search-bar--focused' : ''}`} onSubmit={handleSubmit}>
        <Search size={18} className="search-bar__icon" />
        <input
          type="text"
          className="search-bar__input"
          placeholder={placeholder || defaultPlaceholder}
          value={displayVal}
          onChange={handleChange}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setTimeout(() => setIsFocused(false), 200)} // delay to allow clicks on dropdown
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

      {showDropdown && (
        <div className="search-dropdown">
          {filteredSuggestions.length > 0 ? (
            filteredSuggestions.map((suggestion) => (
              <div
                key={suggestion.id}
                className="search-dropdown__item"
                onClick={() => {
                  if (onSuggestionClick) onSuggestionClick(suggestion.id, suggestion.label);
                  if (value === undefined) setInternalVal(suggestion.label);
                  if (onChange) onChange(suggestion.label);
                  setIsFocused(false);
                }}
              >
                <Search size={14} className="search-dropdown__icon" />
                <span>{suggestion.label}</span>
              </div>
            ))
          ) : (
            <div className="search-dropdown__empty">
              КАТЕГОРИЯ ОТСУТСТВУЕТ
            </div>
          )}
        </div>
      )}
    </div>
  );
}
