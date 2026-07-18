import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueries } from '@tanstack/react-query';
import { couponsApi } from '../../api/coupons';
import { useLocalePath } from '../../hooks/useLocalePath';
import { SITUATIONS } from '../../data/situations';
import './Situations.css';

/**
 * «Что хотите сегодня?» — кураторская подборка ситуаций (НЕ категории).
 * Первая плитка — чёрный «ситуация-хиро» на две колонки; остальные светлые.
 * Клик → поиск по каталогу (`/search?q=`). Счётчик — реальный `totalElements`,
 * скрыт при нуле. Референс: design_handoff_sizbiz → «Главная - образец».
 * Данные-заглушка живут в src/data/situations.ts (до backend-подборок).
 */
export default function Situations() {
  const { t } = useTranslation();
  const lp = useLocalePath();

  const counts = useQueries({
    queries: SITUATIONS.map((s) => ({
      queryKey: ['coupons', 'situation-count', s.query],
      queryFn: () => couponsApi.getCatalog({ search: s.query, size: 1 }),
      select: (res: Awaited<ReturnType<typeof couponsApi.getCatalog>>) =>
        res.data.data.totalElements,
    })),
  });

  return (
    <section className="tiles-section">
      <h2 className="tiles-section__title">{t('home.tiles.title')}</h2>

      <div className="tiles">
        {SITUATIONS.map((situation, i) => {
          const count = counts[i]?.data;
          return (
            <Link
              key={situation.key}
              to={`${lp('/search')}?q=${encodeURIComponent(situation.query)}`}
              className={`tile${situation.featured ? ' tile--lead' : ''}`}
            >
              <span className="tile__name">{t(`home.situations.${situation.key}`)}</span>
              {count !== undefined && count > 0 && (
                <span className="tile__count">{t('home.tiles.count', { count })}</span>
              )}
            </Link>
          );
        })}
      </div>
    </section>
  );
}
