import { ChevronLeft, Heart } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { couponsApi } from '../api/coupons';
import GuestAuthPrompt from '../components/auth/GuestAuthPrompt';
import MobileCouponCard from '../components/mobile/MobileCouponCard';
import { useAuthStore } from '../store/authStore';
import { useFavoritesStore } from '../store/favoritesStore';
import { useLocalePath } from '../hooks/useLocalePath';
import './FavoritesMobile.css';

/** Избранное на мобиле. Референс: «Мобилка - 8 Избранное». */
export default function FavoritesMobile() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const { isAuthenticated } = useAuthStore();
  const { favoriteIds } = useFavoritesStore();

  const { data: coupons = [] } = useQuery({
    queryKey: ['coupons-favorites'],
    queryFn: () => couponsApi.getCatalog({ size: 200 }),
    select: (res) => res.data.data.content,
    enabled: isAuthenticated,
  });

  const favorites = coupons.filter((c) => favoriteIds.includes(c.id));

  return (
    <div className="fmob">
      <div className="mbar">
        <button
          type="button"
          className="mround mround--glass"
          onClick={() => (window.history.length > 1 ? navigate(-1) : navigate(lp('/')))}
          aria-label={t('common.back')}
        >
          <ChevronLeft size={18} strokeWidth={2} />
        </button>

        <span className="mbar__title">{t('favorites.title')}</span>

        <span className="fmob__count">{isAuthenticated && favorites.length ? favorites.length : ''}</span>
      </div>

      {!isAuthenticated ? (
        <GuestAuthPrompt
          icon={<Heart size={34} strokeWidth={1.6} />}
          title={t('favorites.guestTitle')}
          description={t('favorites.guestDesc')}
          loginLabel={t('favorites.guestLogin')}
          variant="mobile"
        />
      ) : favorites.length === 0 ? (
        <div className="fmob__empty">
          <span className="fmob__empty-icon">
            <Heart size={34} strokeWidth={1.6} />
          </span>
          <h1 className="fmob__empty-title">{t('favorites.emptyTitle')}</h1>
          <p className="fmob__empty-text">{t('favorites.emptyDesc')}</p>
          <Link to={lp('/coupons')} className="fmob__empty-btn">
            {t('favorites.goCatalog')}
          </Link>
        </div>
      ) : (
        <div className="fmob__grid">
          {favorites.map((coupon) => (
            <MobileCouponCard key={coupon.id} coupon={coupon} variant="grid" />
          ))}
        </div>
      )}
    </div>
  );
}
