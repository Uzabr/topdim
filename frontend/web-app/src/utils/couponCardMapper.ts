import type { CouponOffer } from '../api/coupons';
import type { CouponCardData } from '../components/coupon/CouponCard';
import { deriveCouponPreview } from './couponPreview';

export function mapCouponOfferToCardData(coupon: CouponOffer): CouponCardData {
  const discount =
    coupon.discountPercent ||
    (coupon.oldPrice
      ? Math.round((1 - coupon.fromPrice / coupon.oldPrice) * 100)
      : 0);

  return {
    id: coupon.id,
    title: coupon.title,
    offerDescription: coupon.offerDescription,
    shortDescription: deriveCouponPreview(coupon.offerDescription),
    merchant: coupon.merchant,
    category: coupon.category,
    oldPrice: coupon.oldPrice,
    fromPrice: coupon.fromPrice,
    discountPercent: coupon.discountPercent,
    coverImageUrl: coupon.coverImageUrl,
    totalSold: coupon.totalSold,
    rating: coupon.averageRating,
    reviewCount: coupon.reviewCount,
    address: coupon.merchant?.primaryLocation?.address,
    location: coupon.merchant?.primaryLocation?.address,
    giftAvailable: coupon.giftAvailable,
    isHot: discount >= 50,
  };
}
