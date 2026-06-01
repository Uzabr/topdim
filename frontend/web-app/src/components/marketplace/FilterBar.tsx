import { useMemo } from 'react';
import { SlidersHorizontal } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import './FilterBar.css';

interface FilterBarProps {
  category: string;
  priceRange: string;
  distance: string;
  onCategoryChange: (value: string) => void;
  onPriceRangeChange: (value: string) => void;
  onDistanceChange: (value: string) => void;
}

export default function FilterBar({
  category,
  priceRange,
  distance,
  onCategoryChange,
  onPriceRangeChange,
  onDistanceChange,
}: FilterBarProps) {
  const { t } = useTranslation();

  const categoryOptions = useMemo(() => [
    { value: 'Все', label: t('marketplace.categories.all') },
    { value: 'Еда', label: t('marketplace.categories.food') },
    { value: 'Beauty', label: t('marketplace.categories.beauty') },
    { value: 'Sport', label: t('marketplace.categories.sport') },
    { value: 'Дом', label: t('marketplace.categories.home') },
    { value: 'Техника', label: t('marketplace.categories.tech') },
  ], [t]);

  const priceOptions = useMemo(() => [
    { value: 'Любая цена', label: t('marketplace.price.any') },
    { value: 'До 100k', label: t('marketplace.price.to100k') },
    { value: '100k–250k', label: t('marketplace.price.100to250') },
    { value: '250k+', label: t('marketplace.price.250plus') },
  ], [t]);

  const distanceOptions = useMemo(() => [
    { value: 'До 1 км', label: t('marketplace.distance.1km') },
    { value: 'До 5 км', label: t('marketplace.distance.5km') },
    { value: 'До 10 км', label: t('marketplace.distance.10km') },
  ], [t]);

  return (
    <div className="filter-bar surface-card">
      <div className="filter-bar__label">
        <SlidersHorizontal size={18} />
        <span>{t('marketplace.filters')}</span>
      </div>

      <select value={category} onChange={(event) => onCategoryChange(event.target.value)}>
        {categoryOptions.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>

      <select value={priceRange} onChange={(event) => onPriceRangeChange(event.target.value)}>
        {priceOptions.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>

      <select value={distance} onChange={(event) => onDistanceChange(event.target.value)}>
        {distanceOptions.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}
