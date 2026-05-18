import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Star, Send, CheckCircle } from 'lucide-react';
import { reviewsApi } from '../../api/reviews';
import './ReviewForm.css';

interface ReviewFormProps {
  couponOfferId: number;
}

export default function ReviewForm({ couponOfferId }: ReviewFormProps) {
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
      const msg = e.response?.data?.message || 'Не удалось отправить отзыв';
      setError(msg);
    },
  });

  if (submitted) {
    return (
      <div className="review-form-success">
        <CheckCircle size={32} />
        <h4>Спасибо за отзыв!</h4>
        <p>Ваш отзыв отправлен на модерацию и появится после проверки.</p>
      </div>
    );
  }

  const canSubmit = rating >= 1 && comment.trim().length >= 10;

  return (
    <div className="review-form">
      <h4 className="review-form__title">Оставить отзыв</h4>

      {/* Star Rating */}
      <div className="review-form__stars">
        <span className="review-form__stars-label">Оценка:</span>
        <div className="review-form__stars-row">
          {[1, 2, 3, 4, 5].map((star) => (
            <button
              key={star}
              type="button"
              className={`review-form__star ${star <= (hoveredRating || rating) ? 'review-form__star--active' : ''}`}
              onMouseEnter={() => setHoveredRating(star)}
              onMouseLeave={() => setHoveredRating(0)}
              onClick={() => setRating(star)}
              aria-label={`${star} звёзд`}
            >
              <Star size={24} fill={star <= (hoveredRating || rating) ? 'currentColor' : 'none'} />
            </button>
          ))}
        </div>
      </div>

      {/* Comment */}
      <textarea
        className="review-form__textarea"
        placeholder="Поделитесь впечатлениями (минимум 10 символов)"
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
        {mutation.isPending ? 'Отправка...' : (
          <>
            <Send size={16} />
            Отправить отзыв
          </>
        )}
      </button>
    </div>
  );
}
