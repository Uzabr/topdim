import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { reviewsApi } from '../../api/reviews';
import { formatDate } from '../../utils/format';
import './ReviewsBlock.css';

interface ReviewsBlockProps {
  couponId: number;
  averageRating?: number;
  reviewCount: number;
}

const PAGE = 6;

/** «★★★★☆» — тот же приём, что в референсе: символы, а не иконки. */
function stars(rating: number): string {
  const full = Math.round(rating);
  return '★'.repeat(full) + '☆'.repeat(Math.max(0, 5 - full));
}

/**
 * Отзывы купона. Бейдж «Проверенная покупка» — у всех: отзыв на бэкенде можно
 * оставить только по использованному купону (reviews eligibility).
 */
export default function ReviewsBlock({ couponId, averageRating, reviewCount }: ReviewsBlockProps) {
  const { t } = useTranslation();
  const [size, setSize] = useState(PAGE);

  const { data, isLoading } = useQuery({
    queryKey: ['coupon-reviews', couponId, size],
    queryFn: () => reviewsApi.getForCoupon(couponId, 0, size),
    select: (res) => res.data.data,
    retry: false,
  });

  const reviews = data?.content ?? [];
  const total = data?.totalElements ?? reviewCount;

  if (isLoading) {
    return <p className="reviews__empty">{t('common.loading')}</p>;
  }

  if (reviews.length === 0) {
    return (
      <section className="reviews">
        <h2 className="detail-h2">{t('couponDetail.reviews')}</h2>
        <p className="reviews__empty">{t('couponDetail.noReviews')}</p>
      </section>
    );
  }

  return (
    <section className="reviews">
      <div className="reviews__head">
        <h2 className="detail-h2">
          {t('couponDetail.reviews')}
          {averageRating ? ` · ★ ${averageRating.toFixed(1).replace('.', ',')}` : ''}
        </h2>
        {reviews.length < total && (
          <button type="button" className="reviews__all" onClick={() => setSize(total)}>
            {t('couponDetail.allReviews', { count: total })}
          </button>
        )}
      </div>

      <div className="reviews__list">
        {reviews.map((r) => (
          <article key={r.id} className="review">
            <header className="review__head">
              <span className="review__name">{r.userName || t('common.user')}</span>
              <span className="review__stars" aria-label={String(r.rating)}>
                {stars(r.rating)}
              </span>
              <span className="review__verified">{t('couponDetail.verifiedPurchase')}</span>
              <span className="review__date">{formatDate(r.createdAt)}</span>
            </header>
            {r.comment && <p className="review__text">{r.comment}</p>}
          </article>
        ))}
      </div>
    </section>
  );
}
