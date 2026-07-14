import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { Category } from '../../api/coupons';
import { useLocalePath } from '../../hooks/useLocalePath';
import { localizedName } from '../../utils/localizedText';
import './CategoryTiles.css';

interface CategoryTilesProps {
  categories: Category[];
  /** categoryId → сколько купонов. Пока счётчик не приехал — не показываем. */
  counts: Record<number, number | undefined>;
}

/**
 * «Что хотите сегодня?» — крупные плитки навигации. Первая чёрная и на две
 * колонки (design_handoff_sizbiz → «Главная»).
 */
export default function CategoryTiles({ categories, counts }: CategoryTilesProps) {
  const { t, i18n } = useTranslation();
  const lp = useLocalePath();

  if (categories.length === 0) return null;

  return (
    <section className="tiles-section">
      <h2 className="tiles-section__title">{t('home.tiles.title')}</h2>

      <div className="tiles">
        {categories.map((category, i) => {
          const count = counts[category.id];
          return (
            <Link
              key={category.id}
              to={`${lp('/coupons')}?categoryId=${category.id}`}
              className={`tile${i === 0 ? ' tile--lead' : ''}`}
            >
              <span className="tile__name">{localizedName(category, i18n.language)}</span>
              {count !== undefined && (
                <span className="tile__count">{t('home.tiles.count', { count })}</span>
              )}
            </Link>
          );
        })}
      </div>
    </section>
  );
}
