import { SlidersHorizontal } from 'lucide-react';
import './FilterBar.css';

interface FilterBarProps {
  category: string;
  priceRange: string;
  distance: string;
  onCategoryChange: (value: string) => void;
  onPriceRangeChange: (value: string) => void;
  onDistanceChange: (value: string) => void;
}

const categoryOptions = ['Все', 'Еда', 'Beauty', 'Sport', 'Дом', 'Техника'];
const priceOptions = ['Любая цена', 'До 100k', '100k–250k', '250k+'];
const distanceOptions = ['До 1 км', 'До 5 км', 'До 10 км'];

export default function FilterBar({
  category,
  priceRange,
  distance,
  onCategoryChange,
  onPriceRangeChange,
  onDistanceChange,
}: FilterBarProps) {
  return (
    <div className="filter-bar surface-card">
      <div className="filter-bar__label">
        <SlidersHorizontal size={18} />
        <span>Фильтры</span>
      </div>

      <select value={category} onChange={(event) => onCategoryChange(event.target.value)}>
        {categoryOptions.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>

      <select value={priceRange} onChange={(event) => onPriceRangeChange(event.target.value)}>
        {priceOptions.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>

      <select value={distance} onChange={(event) => onDistanceChange(event.target.value)}>
        {distanceOptions.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </div>
  );
}
