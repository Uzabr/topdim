import { useEffect, useRef, useState } from 'react';
import { Search, X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useLocalePath } from '../../hooks/useLocalePath';
import './SearchOverlay.css';

interface SearchOverlayProps {
  onClose: () => void;
}

/**
 * Поиск — не страница, а фуллскрин-оверлей поверх любой страницы
 * (design_handoff_sizbiz → «Шапка», «Поиск-оверлей»). Esc или клик мимо — закрыть.
 */
export default function SearchOverlay({ onClose }: SearchOverlayProps) {
  const { t } = useTranslation();
  const lp = useLocalePath();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  const suggestions = t('header.searchSuggestions', { returnObjects: true }) as unknown as string[];

  useEffect(() => {
    inputRef.current?.focus();

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);

    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      window.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = prevOverflow;
    };
  }, [onClose]);

  const submit = (term: string) => {
    const q = term.trim();
    if (!q) return;
    onClose();
    navigate(`${lp('/search')}?q=${encodeURIComponent(q)}`);
  };

  return (
    <div className="search-overlay" onClick={onClose} role="presentation">
      <div className="search-overlay__panel" onClick={(e) => e.stopPropagation()}>
        <div className="search-overlay__bar">
          <Search size={17} className="search-overlay__bar-icon" />
          <input
            ref={inputRef}
            className="search-overlay__input"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') submit(query);
            }}
            placeholder={t('header.searchPlaceholder')}
          />
          <button
            type="button"
            className="search-overlay__close"
            onClick={onClose}
            aria-label={t('common.close')}
          >
            <X size={16} />
          </button>
        </div>

        <div className="search-overlay__panel-suggestions">
          <p className="search-overlay__title">{t('header.frequentlySearched')}</p>
          {suggestions.map((s) => (
            <button
              key={s}
              type="button"
              className="search-overlay__suggestion"
              onClick={() => submit(s)}
            >
              <Search size={13} />
              {s}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
