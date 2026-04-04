import { useState, useRef, useEffect } from 'react';
import { MapPin, Check, ChevronDown } from 'lucide-react';
import { useCityStore, CITIES } from '../../store/cityStore';
import { useTranslation } from 'react-i18next';
import './CitySelector.css';

export default function CitySelector() {
  const { selectedCity, setCity, detectCity } = useCityStore();
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const { i18n } = useTranslation();

  useEffect(() => {
    detectCity();
  }, [detectCity]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const getCityName = (city: typeof CITIES[0]) =>
    i18n.language === 'uz' ? city.nameUz : city.name;

  return (
    <div className="city-selector" ref={dropdownRef}>
      <button
        className="city-selector__trigger"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        aria-haspopup="listbox"
      >
        <MapPin size={15} />
        <span>{getCityName(selectedCity)}</span>
        <ChevronDown
          size={14}
          className={`city-selector__chevron ${open ? 'city-selector__chevron--open' : ''}`}
        />
      </button>

      {open && (
        <div className="city-selector__dropdown" role="listbox">
          <div className="city-selector__header">Выберите город</div>
          {CITIES.map((city) => (
            <button
              key={city.id}
              className={`city-selector__option ${
                selectedCity.id === city.id ? 'city-selector__option--active' : ''
              }`}
              onClick={() => {
                setCity(city);
                setOpen(false);
              }}
              role="option"
              aria-selected={selectedCity.id === city.id}
            >
              <MapPin size={14} />
              <span>{getCityName(city)}</span>
              {selectedCity.id === city.id && <Check size={16} className="city-selector__check" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
