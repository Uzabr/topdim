import { Heart, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useFavoritesStore } from '../store/favoritesStore';
import CouponCard from '../components/coupon/CouponCard';
import type { CouponCardData } from '../components/coupon/CouponCard';
import { couponsApi } from '../api/coupons';
import { topdimDeals } from '../data/topdim';
import { useLocalePath } from '../hooks/useLocalePath';
import './FavoritesPage.css';

export default function FavoritesPage() {
  const { favoriteIds } = useFavoritesStore();
  const lp = useLocalePath();

  const { data: couponsData } = useQuery({
    queryKey: ['coupons-favorites'],
    queryFn: () => couponsApi.getCatalog({ size: 200 }),
    select: (res) => res.data.data.content,
  });

  const apiDeals = couponsData || [];

  const mappedDeals: CouponCardData[] = (apiDeals.length > 0)
    ? apiDeals.map((deal: any) => ({
        id: deal.id,
        title: deal.title,
        shortDescription: deal.shortDescription,
        merchant: deal.merchant,
        category: deal.category,
        oldPrice: deal.oldPrice,
        fromPrice: deal.fromPrice,
        discountPercent: deal.discountPercent,
        coverImageUrl: deal.coverImageUrl,
        totalSold: deal.totalSold || 0,
        rating: deal.averageRating || 4.5 + Math.random() * 0.4,
        reviewCount: deal.reviewCount || Math.floor((deal.totalSold || 0) * 0.3),
        address: deal.address,
        location: deal.address || 'Ташкент',
        isHot: (deal.discountPercent || 0) >= 50,
        countdownText: '23:59:59',
        giftAvailable: deal.giftAvailable,
      }))
    : topdimDeals.map((d) => ({
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
        reviewCount: d.reviews,
        location: d.location,
        isHot: d.isHot,
        giftAvailable: d.giftAvailable,
      }));

  const mapped = mappedDeals.filter(deal => favoriteIds.includes(deal.id));

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
          <Link to={lp('/coupons')} className="primary-button">
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
