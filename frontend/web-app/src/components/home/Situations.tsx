import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { couponsApi } from '../../api/coupons';
import { useLocalePath } from '../../hooks/useLocalePath';
import { localizedTitle } from '../../utils/localizedText';
import './Situations.css';

/**
 * «Что хотите сегодня?» — кураторская подборка ситуаций из API (не категории).
 * Первая плитка — чёрный «ситуация-хиро» на две колонки; остальные светлые.
 * Клик → каталог, отфильтрованный по ситуации (`/coupons?situation=<key>`).
 * Счётчик и картинка — из бэкенда (`GET /api/v1/situations`).
 */
export default function Situations() {
  const { t, i18n } = useTranslation();
  const lp = useLocalePath();

  const { data: situations = [] } = useQuery({
    queryKey: ['situations'],
    queryFn: () => couponsApi.getSituations(),
    select: (res) => res.data.data,
  });

  if (situations.length === 0) return null;

  return (
    <section className="tiles-section">
      <h2 className="tiles-section__title">{t('home.tiles.title')}</h2>

      <div className="tiles">
        {situations.map((situation) => (
          <Link
            key={situation.key}
            to={`${lp('/coupons')}?situation=${encodeURIComponent(situation.key)}`}
            className={`tile${situation.featured ? ' tile--lead' : ''}${situation.imageUrl ? ' tile--photo' : ''}`}
            style={situation.imageUrl ? { backgroundImage: `url(${situation.imageUrl})` } : undefined}
          >
            <span className="tile__name">{localizedTitle(situation, i18n.language)}</span>
            {situation.couponCount > 0 && (
              <span className="tile__count">
                {t('home.tiles.count', { count: situation.couponCount })}
              </span>
            )}
          </Link>
        ))}
      </div>
    </section>
  );
}
