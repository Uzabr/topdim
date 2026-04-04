import { Heart, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useFavoritesStore } from '../store/favoritesStore';
import CouponCard from '../components/coupon/CouponCard';
import type { CouponCardData } from '../components/coupon/CouponCard';
import './FavoritesPage.css';

export default function FavoritesPage() {
  const { favorites } = useFavoritesStore();

  const mapped: CouponCardData[] = favorites.map((d: any) => ({
    id: d.id,
    title: d.title,
    shortDescription: d.shortDescription,
    merchant: d.merchant || { id: 0, name: 'TopDim' },
    category: d.category,
    oldPrice: d.oldPrice,
    fromPrice: d.fromPrice,
    discountPercent: d.discountPercent,
    coverImageUrl: d.coverImageUrl || d.image,
    totalSold: d.totalSold,
    rating: d.rating,
    reviewCount: d.reviewCount || d.reviews,
    location: d.location || d.address,
    isHot: d.isHot,
    giftAvailable: d.giftAvailable,
  }));

  return (
    <div className="favorites-page container">
      <div className="favorites-header">
        <Heart size={24} className="favorites-icon" />
        <h1>Избранное</h1>
        {mapped.length > 0 && (
          <span className="favorites-count">{mapped.length}</span>
        )}
      </div>

      {mapped.length === 0 ? (
        <div className="favorites-empty surface-card">
          <span className="favorites-empty__icon">💛</span>
          <h3>Пока пусто</h3>
          <p>Добавляйте понравившиеся купоны, нажимая на ❤️ на карточке</p>
          <Link to="/coupons" className="primary-button">
            Перейти в каталог
            <ArrowRight size={16} />
          </Link>
        </div>
      ) : (
        <div className="favorites-grid">
          {mapped.map((deal) => (
            <CouponCard key={deal.id} coupon={deal} layout="card" />
          ))}
        </div>
      )}
    </div>
  );
}
