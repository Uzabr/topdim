import { useEffect, useMemo, useState } from 'react';
import { ArrowLeft, ChevronDown, Heart, ShoppingBag } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { couponsApi } from '../api/coupons';
import { reviewsApi } from '../api/reviews';
import CouponUnavailableState from '../components/coupon/CouponUnavailableState';
import { useLocalePath } from '../hooks/useLocalePath';
import { useCartStore } from '../store/cartStore';
import { useFavoritesStore } from '../store/favoritesStore';
import { optionLeft } from '../utils/couponStock';
import { calcDiscount, formatDate } from '../utils/format';
import './CouponMobile.css';

/** Район из адреса: «Яккасарай, ул. Шота Руставели 21» → «Яккасарай». */
function district(address?: string): string | undefined {
  return address?.split(',')[0]?.trim() || undefined;
}

function stars(rating: number): string {
  const full = Math.round(rating);
  return '★'.repeat(full) + '☆'.repeat(Math.max(0, 5 - full));
}

/** Маршрут в 2ГИС — карты у нас и так на mapgl. */
function routeUrl(lat: number, lon: number): string {
  return `https://2gis.uz/tashkent/directions/points/%7C${lon}%2C${lat}`;
}

/**
 * Мобильная страница купона: фото, варианты лентой, стеклянные карточки и
 * закреплённая снизу покупка. Референс: «Мобилка - 3 Купон».
 */
export default function CouponMobile() {
  const { t, i18n } = useTranslation();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const { addToCart } = useCartStore();
  const { toggleFavorite, isFavorite } = useFavoritesStore();

  const [optionId, setOptionId] = useState<number | null>(null);
  const [revOpen, setRevOpen] = useState(false);
  const [whereOpen, setWhereOpen] = useState(false);
  const [popping, setPopping] = useState(false);
  const [photo, setPhoto] = useState(0);

  const {
    data: coupon,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['coupon', id],
    queryFn: () => couponsApi.getById(Number(id)),
    select: (res) => res.data.data,
    enabled: !!id,
    retry: false,
  });

  // Отзывы тянем только когда шторку раскрыли — на мобиле это лишний запрос.
  const { data: reviewsPage } = useQuery({
    queryKey: ['coupon-reviews', coupon?.id],
    queryFn: () => reviewsApi.getForCoupon(Number(coupon?.id), 0, 10),
    select: (res) => res.data.data,
    enabled: revOpen && !!coupon?.id,
    retry: false,
  });

  useEffect(() => {
    if (coupon) document.title = `${coupon.title} — sizbiz`;
  }, [coupon]);

  // Переход на другой купон без размонтирования — сброс выбора прямо в рендере.
  const [prevId, setPrevId] = useState(id);
  if (prevId !== id) {
    setPrevId(id);
    setOptionId(null);
    setRevOpen(false);
    setWhereOpen(false);
    setPhoto(0);
  }

  const options = useMemo(() => coupon?.options ?? [], [coupon]);
  const selected = useMemo(
    () => options.find((o) => o.id === optionId) ?? options[0],
    [options, optionId],
  );

  if (isLoading) {
    return <p className="cmob__loading">{t('couponDetail.loading')}</p>;
  }

  if (isError || !coupon) {
    return (
      <div className="cmob">
        <CouponUnavailableState />
      </div>
    );
  }

  const c = coupon;
  const images = c.images?.length ? c.images : c.coverImageUrl ? [c.coverImageUrl] : [];
  const location = c.merchant?.primaryLocation;
  const area = district(location?.address);
  const fav = isFavorite(c.id);
  const reviews = reviewsPage?.content ?? [];
  const reviewCount = reviewsPage?.totalElements ?? c.reviewCount ?? 0;
  const hasCoords = location?.latitude != null && location?.longitude != null;

  const price = selected?.couponPrice ?? c.fromPrice;
  const oldPrice = selected?.regularPrice ?? c.oldPrice;
  const money = (v: number) => `${v.toLocaleString(locale)} ${t('common.currency.sum')}`;

  const putInCart = () => {
    if (!selected) return;
    addToCart({
      couponOfferId: c.id,
      couponOptionId: selected.id,
      couponTitle: c.title,
      optionTitle: selected.title,
      unitPrice: selected.couponPrice,
      oldPrice: selected.regularPrice,
      quantity: 1,
      coverImageUrl: c.coverImageUrl,
    });
  };

  const toggleFav = () => {
    if (!fav) {
      setPopping(true);
      setTimeout(() => setPopping(false), 450);
    }
    toggleFavorite(c.id);
  };

  return (
    <div className="cmob">
      <div className="mbar cmob__bar">
        <button
          type="button"
          className="mround"
          // Прямой заход по ссылке: назад некуда — уводим на главную, а не из сайта.
          onClick={() => (window.history.length > 1 ? navigate(-1) : navigate(lp('/')))}
          aria-label={t('common.back')}
        >
          <ArrowLeft size={17} strokeWidth={2} />
        </button>

        <span className="mbar__title">{t('mobile.coupon.title')}</span>

        <Link to={lp('/cart')} className="mround" aria-label={t('bottomNav.cart')}>
          <ShoppingBag size={16} strokeWidth={1.9} />
        </Link>
      </div>

      <div className="cmob__gallery">
        <div
          className="cmob__strip"
          onScroll={(e) => {
            const el = e.currentTarget;
            setPhoto(Math.round(el.scrollLeft / Math.max(1, el.clientWidth)));
          }}
        >
          {images.length === 0 ? (
            <div className="cmob__shot cmob__shot--empty">{c.merchant?.name}</div>
          ) : (
            images.map((src) => (
              <div className="cmob__shot" key={src}>
                <img src={src} alt={c.title} />
              </div>
            ))
          )}
        </div>

        {images.length > 1 && (
          <div className="cmob__dots">
            {images.map((src, i) => (
              <span key={src} className={`cmob__dot${i === photo ? ' cmob__dot--on' : ''}`} />
            ))}
          </div>
        )}
      </div>

      <div className="cmob__head">
        <h1 className="cmob__title">{c.title}</h1>
        <button
          type="button"
          className={`cmob__fav${fav ? ' cmob__fav--on' : ''}${popping ? ' cmob__fav--pop' : ''}`}
          onClick={toggleFav}
          aria-label={t('couponDetail.favorite')}
          aria-pressed={fav}
        >
          <Heart size={16} fill={fav ? 'currentColor' : 'none'} />
        </button>
      </div>

      <p className="cmob__meta">
        {[
          c.merchant?.name,
          c.averageRating
            ? `★ ${c.averageRating.toFixed(1).replace('.', ',')} (${c.reviewCount ?? 0})`
            : null,
          area,
        ]
          .filter(Boolean)
          .join(' · ')}
      </p>

      {options.length > 0 && selected && (
        <>
          <div className="cmob__section-head">
            <span className="cmob__section-title">{t('couponDetail.variants')}</span>
            <span className="cmob__section-count">
              {options.length} {t('common.units.pcs')}
            </span>
          </div>

          <div className="cmob__variants">
            {options.map((option) => {
              const left = optionLeft(option);
              const off = calcDiscount(option.regularPrice, option.couponPrice);
              const out = left === 0;
              return (
                <button
                  key={option.id}
                  type="button"
                  className={`cvar${option.id === selected.id ? ' cvar--on' : ''}${out ? ' cvar--out' : ''}`}
                  onClick={() => setOptionId(option.id)}
                  disabled={out}
                >
                  <span className="cvar__label">{option.title}</span>
                  <span className="cvar__price">{money(option.couponPrice)}</span>
                  <span className="cvar__row">
                    {option.regularPrice > option.couponPrice && (
                      <span className="cvar__old">{option.regularPrice.toLocaleString(locale)}</span>
                    )}
                    {off > 0 && <span className="cvar__off">−{off}%</span>}
                  </span>
                  {left !== null && (
                    <span className="cvar__left">
                      {out
                        ? t('couponDetail.optionSoldOut')
                        : t('couponDetail.optionLeft', { count: left })}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </>
      )}

      {c.offerDescription && (
        <section className="glass">
          <h2 className="glass__title">{t('couponDetail.description')}</h2>
          <p className="glass__text">{c.offerDescription}</p>
          <p className="glass__note">
            {t('couponDetail.useUntilLabel')}: {formatDate(c.useUntil)}
          </p>
        </section>
      )}

      <section className="glass glass--fold">
        <button type="button" className="glass__toggle" onClick={() => setRevOpen((v) => !v)}>
          <span className="glass__title">
            {t('couponDetail.reviews')} <span className="glass__count">{reviewCount}</span>
          </span>
          <ChevronDown size={15} className={`glass__arrow${revOpen ? ' glass__arrow--up' : ''}`} />
        </button>

        {revOpen && (
          <div className="glass__body">
            {reviews.length === 0 ? (
              <p className="glass__text">{t('couponDetail.noReviews')}</p>
            ) : (
              reviews.map((r) => (
                <article className="crev" key={r.id}>
                  <div className="crev__head">
                    <span className="crev__name">{r.userName || t('common.user')}</span>
                    <span className="crev__stars">{stars(r.rating)}</span>
                  </div>
                  {r.comment && <p className="crev__text">{r.comment}</p>}
                  <span className="crev__badge">{t('couponDetail.verifiedPurchase')}</span>
                </article>
              ))
            )}
          </div>
        )}
      </section>

      {/* «Вопросов» из макета нет: на бэкенде такого эндпоинта не существует.
          Вместо них — «Где», без этого купон не дойдёт до заведения. */}
      {location?.address && (
        <section className="glass glass--fold">
          <button type="button" className="glass__toggle" onClick={() => setWhereOpen((v) => !v)}>
            <span className="glass__title">{t('couponDetail.where')}</span>
            <ChevronDown
              size={15}
              className={`glass__arrow${whereOpen ? ' glass__arrow--up' : ''}`}
            />
          </button>

          {whereOpen && (
            <div className="glass__body">
              <p className="glass__text">{location.address}</p>
              {(location.workingHours || location.phone) && (
                <p className="glass__note">
                  {[location.workingHours, location.phone].filter(Boolean).join(' · ')}
                </p>
              )}
              {hasCoords && (
                <a
                  className="glass__link"
                  href={routeUrl(location.latitude as number, location.longitude as number)}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {t('couponDetail.route')}
                </a>
              )}
            </div>
          )}
        </section>
      )}

      <div className="cmob__buy">
        <div className="cmob__prices">
          {oldPrice && oldPrice > price && (
            <span className="cmob__old">{oldPrice.toLocaleString(locale)}</span>
          )}
          <span className="cmob__price">{money(price)}</span>
        </div>

        <div className="cmob__actions">
          <button
            type="button"
            className="cmob__cart"
            onClick={putInCart}
            aria-label={t('couponDetail.addToCart')}
            disabled={!selected}
          >
            <ShoppingBag size={17} strokeWidth={1.9} />
          </button>

          <button
            type="button"
            className="cmob__cta"
            // Корзинная модель: кладём купон в корзину и остаёмся на странице —
            // дровер/бейдж корзины открывается сам (cartStore.addToCart).
            onClick={putInCart}
            disabled={!selected}
          >
            {t('couponDetail.buy')}
          </button>
        </div>
      </div>
    </div>
  );
}
