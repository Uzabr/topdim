import { Link } from 'react-router-dom';
import { ArrowLeft, SearchX } from 'lucide-react';
import { useLocalePath } from '../../hooks/useLocalePath';

interface CouponUnavailableStateProps {
  title?: string;
  description?: string;
}

export default function CouponUnavailableState({
  title = 'Купон недоступен',
  description = 'Этот купон больше не доступен публично или ссылка устарела.',
}: CouponUnavailableStateProps) {
  const lp = useLocalePath();

  return (
    <div className="coupon-unavailable">
      <div className="coupon-unavailable__card">
        <div className="coupon-unavailable__icon">
          <SearchX size={34} />
        </div>
        <h1>{title}</h1>
        <p>{description}</p>
        <div className="coupon-unavailable__actions">
          <Link to={lp('/coupons')} className="coupon-unavailable__primary">
            Смотреть активные купоны
          </Link>
          <Link to={lp('/')} className="coupon-unavailable__secondary">
            <ArrowLeft size={16} />
            На главную
          </Link>
        </div>
      </div>
    </div>
  );
}
