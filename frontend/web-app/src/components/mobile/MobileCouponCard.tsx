import { useState } from 'react';
import type { MouseEvent } from 'react';
import { Heart } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { CouponOffer } from '../../api/coupons';
import { useLocalePath } from '../../hooks/useLocalePath';
import { useFavoritesStore } from '../../store/favoritesStore';
import { calcDiscount } from '../../utils/format';
import { srcAt } from '../../utils/imageUrl';
import './MobileCouponCard.css';

interface MobileCouponCardProps {
  coupon: CouponOffer;
  /** carousel — 238×300 в ряду; grid — плитка сетки 2 колонки с лупой. */
  variant: 'carousel' | 'grid';
}

/**
 * Безрамочная карточка мобилки: фото на всю карточку, текст поверх градиента,
 * сердце в углу. Референс: design_handoff_sizbiz/«Мобилка - 2 Главная».
 */
export default function MobileCouponCard({ coupon, variant }: MobileCouponCardProps) {
  const { t, i18n } = useTranslation();
  const lp = useLocalePath();
  const { toggleFavorite, isFavorite } = useFavoritesStore();
  const [popping, setPopping] = useState(false);

  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const discount = coupon.discountPercent || calcDiscount(coupon.oldPrice ?? 0, coupon.fromPrice);
  const fav = isFavorite(coupon.id);

  // Карточка целиком — ссылка, поэтому клик по сердцу гасим вручную.
  const toggle = (e: MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!fav) {
      setPopping(true);
      setTimeout(() => setPopping(false), 400);
    }
    toggleFavorite(coupon.id);
  };

  return (
    <Link
      to={lp(`/coupons/${coupon.id}`)}
      className={`mcard mcard--${variant}`}
      {...(variant === 'grid' ? { 'data-lens': '' } : {})}
    >
      <span className="mcard__photo">
        {coupon.coverImageUrl && <img src={srcAt(coupon.coverImageUrl, 640)} alt="" loading="lazy" />}
      </span>

      <button
        type="button"
        className={`mcard__fav${fav ? ' mcard__fav--on' : ''}${popping ? ' mcard__fav--pop' : ''}`}
        onClick={toggle}
        aria-label={t('couponDetail.favorite')}
        aria-pressed={fav}
      >
        <Heart size={20} fill={fav ? 'currentColor' : 'none'} />
      </button>

      <span className="mcard__foot">
        <span className="mcard__text">
          <span className="mcard__title">{coupon.title}</span>
          <span className="mcard__price">
            {t('common.from')} {coupon.fromPrice.toLocaleString(locale)} {t('common.currency.sum')}
          </span>
        </span>
        {discount > 0 && <span className="mcard__off">−{discount}%</span>}
      </span>
    </Link>
  );
}
