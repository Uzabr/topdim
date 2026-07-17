import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Star, X } from 'lucide-react';
import { reviewsApi } from '../../api/reviews';
import type { PurchasedCoupon } from '../../api/orders';
import { formatDate } from '../../utils/format';
import './ReviewModal.css';

interface ReviewModalProps {
  coupon: PurchasedCoupon;
  onClose: () => void;
}

/** Комментарий необязателен, но backend валидирует @Size(min = 10) — короче не отправляем. */
const COMMENT_MIN = 10;

/**
 * Отзыв в модалке, а не переходом на другую страницу
 * (design_handoff_sizbiz → «Профиль», «Модалка отзыва»).
 */
export default function ReviewModal({ coupon, onClose }: ReviewModalProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  const { mutate, isPending } = useMutation({
    mutationFn: () =>
      reviewsApi.create({
        couponOfferId: coupon.couponOfferId,
        rating,
        comment: comment.trim() ? comment.trim() : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-reviews'] });
      onClose();
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: { message?: string } } };
      setError(e.response?.data?.message || t('profile.review.error'));
    },
  });

  const submit = () => {
    const trimmed = comment.trim();
    if (rating < 1) {
      setError(t('profile.review.ratingRequired'));
      return;
    }
    if (trimmed && trimmed.length < COMMENT_MIN) {
      setError(t('profile.review.commentTooShort', { count: COMMENT_MIN }));
      return;
    }
    setError('');
    mutate();
  };

  return (
    <div className="review-modal-overlay" onClick={onClose} role="presentation">
      <div className="review-modal" onClick={(e) => e.stopPropagation()}>
        <div className="review-modal__head">
          <div>
            <h3 className="review-modal__title">
              {t('profile.review.title', { merchant: coupon.merchantName || coupon.couponTitle })}
            </h3>
            {coupon.usedAt && (
              <p className="review-modal__subtitle">
                {t('profile.review.subtitle', { date: formatDate(coupon.usedAt) })}
              </p>
            )}
          </div>
          <button
            type="button"
            className="review-modal__close"
            onClick={onClose}
            aria-label={t('common.close')}
          >
            <X size={15} />
          </button>
        </div>

        <div className="review-modal__stars">
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              type="button"
              className={`review-modal__star${n <= rating ? ' review-modal__star--on' : ''}`}
              onClick={() => {
                setRating(n);
                setError('');
              }}
              aria-label={String(n)}
            >
              <Star size={32} fill="currentColor" strokeWidth={0} />
            </button>
          ))}
        </div>

        <div className="review-modal__comment">
          <textarea
            value={comment}
            onChange={(e) => {
              setComment(e.target.value);
              setError('');
            }}
            placeholder={t('profile.review.commentPlaceholder')}
            maxLength={2000}
            rows={3}
          />
        </div>

        {error && <p className="review-modal__error">{error}</p>}

        <div className="review-modal__actions">
          <button
            type="button"
            className="review-modal__submit"
            onClick={submit}
            disabled={isPending}
          >
            {isPending ? t('profile.review.sending') : t('profile.review.submit')}
          </button>
          <button type="button" className="review-modal__later" onClick={onClose}>
            {t('profile.review.later')}
          </button>
        </div>

        <p className="review-modal__note">
          <span className="review-modal__dot" />
          {t('profile.review.verified')}
        </p>
      </div>
    </div>
  );
}
