import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Star, Send, CheckCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { reviewsApi } from '../../api/reviews';
import './ReviewForm.css';

interface ReviewFormProps {
  couponOfferId: number;
}

export default function ReviewForm({ couponOfferId }: ReviewFormProps) {
  const { t } = useTranslation();
  const [rating, setRating] = useState(0);
  const [hoveredRating, setHoveredRating] = useState(0);
  const [comment, setComment] = useState('');
  const [error, setError] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => reviewsApi.create({ couponOfferId, rating, comment: comment.trim() }),
    onSuccess: () => {
      setSubmitted(true);
      queryClient.invalidateQueries({ queryKey: ['coupon-reviews', String(couponOfferId)] });
      queryClient.invalidateQueries({ queryKey: ['review-eligibility', couponOfferId] });
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: { message?: string } } };
      const msg = e.response?.data?.message || t('couponDetail.reviewForm.error');
      setError(msg);
    },
  });

  if (submitted) {
    return (
      <div className="review-form-success">
        <CheckCircle size={32} />
        <h4>{t('couponDetail.reviewForm.thanksTitle')}</h4>
        <p>{t('couponDetail.reviewForm.thanksDesc')}</p>
      </div>
    );
  }

  const canSubmit = rating >= 1 && comment.trim().length >= 10;

  return (
    <div className="review-form">
      <h4 className="review-form__title">{t('couponDetail.reviewForm.title')}</h4>

      <div className="review-form__stars">
        <span className="review-form__stars-label">{t('couponDetail.reviewForm.rating')}</span>
        <div className="review-form__stars-row">
          {[1, 2, 3, 4, 5].map((star) => (
            <button
              key={star}
              type="button"
              className={`review-form__star ${star <= (hoveredRating || rating) ? 'review-form__star--active' : ''}`}
              onMouseEnter={() => setHoveredRating(star)}
              onMouseLeave={() => setHoveredRating(0)}
              onClick={() => setRating(star)}
              aria-label={t('couponDetail.reviewForm.stars', { count: star })}
            >
              <Star size={24} fill={star <= (hoveredRating || rating) ? 'currentColor' : 'none'} />
            </button>
          ))}
        </div>
      </div>

      <textarea
        className="review-form__textarea"
        placeholder={t('couponDetail.reviewForm.placeholder')}
        value={comment}
        onChange={(e) => { setComment(e.target.value); setError(''); }}
        maxLength={2000}
        rows={4}
        id="review-comment-input"
      />
      <div className="review-form__counter">{comment.length}/2000</div>

      {error && <div className="review-form__error">{error}</div>}

      <button
        className="review-form__submit primary-button"
        disabled={!canSubmit || mutation.isPending}
        onClick={() => mutation.mutate()}
        id="review-submit-btn"
      >
        {mutation.isPending ? t('common.submitting') : (
          <>
            <Send size={16} />
            {t('couponDetail.reviewForm.submit')}
          </>
        )}
      </button>
    </div>
  );
}
