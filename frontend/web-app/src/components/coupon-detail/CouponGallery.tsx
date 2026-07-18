import { useState } from 'react';
import { ArrowLeft, ArrowRight, Heart } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useFavoritesStore } from '../../store/favoritesStore';
import './CouponGallery.css';

interface CouponGalleryProps {
  images: string[];
  alt: string;
  fallbackText?: string;
  couponId: number;
}

/**
 * Одно окно 420px со слайдером по images[]: стрелки, точки (активная жёлтая),
 * счётчик «N / M». На фото только ♡ — бейджа скидки здесь нет, он в карточке
 * покупки (design_handoff_sizbiz → «Страница купона»).
 */
export default function CouponGallery({ images, alt, fallbackText, couponId }: CouponGalleryProps) {
  const { t } = useTranslation();
  const { toggleFavorite, isFavorite } = useFavoritesStore();
  const [index, setIndex] = useState(0);

  const count = images.length;
  const fav = isFavorite(couponId);
  const go = (next: number) => setIndex(((next % count) + count) % count);

  return (
    <div className="gallery">
      {count === 0 ? (
        <div className="gallery__fallback">{fallbackText}</div>
      ) : (
        <div className="gallery__track" style={{ transform: `translateX(-${index * 100}%)` }}>
          {images.map((src, i) => (
            <div key={src + i} className="gallery__slide">
              <img src={src} alt={alt} loading={i === 0 ? 'eager' : 'lazy'} />
            </div>
          ))}
        </div>
      )}

      <button
        type="button"
        className={`gallery__fav${fav ? ' gallery__fav--active' : ''}`}
        onClick={() => toggleFavorite(couponId)}
        aria-label={t('couponDetail.favorite')}
        aria-pressed={fav}
      >
        <Heart size={17} fill={fav ? 'currentColor' : 'none'} />
      </button>

      {count > 1 && (
        <>
          <button
            type="button"
            className="gallery__arrow gallery__arrow--prev"
            onClick={() => go(index - 1)}
            aria-label={t('couponDetail.prevPhoto')}
          >
            <ArrowLeft size={17} />
          </button>
          <button
            type="button"
            className="gallery__arrow gallery__arrow--next"
            onClick={() => go(index + 1)}
            aria-label={t('couponDetail.nextPhoto')}
          >
            <ArrowRight size={17} />
          </button>

          <div className="gallery__dots">
            {images.map((src, i) => (
              <button
                key={src + i}
                type="button"
                className={`gallery__dot${i === index ? ' gallery__dot--active' : ''}`}
                onClick={() => setIndex(i)}
                aria-label={t('couponDetail.goToPhoto', { n: i + 1 })}
              />
            ))}
          </div>

          <span className="gallery__counter">
            {index + 1} / {count}
          </span>
        </>
      )}
    </div>
  );
}
