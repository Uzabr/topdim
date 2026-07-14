import { useEffect, useState, useRef } from 'react';
import { useParams, useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Heart, Gift, TrendingUp, Calendar, Clock, MapPin, Check, PenLine, Info, Star } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { couponsApi } from '../api/coupons';
import type { CouponOption } from '../api/coupons';
import { reviewsApi } from '../api/reviews';
import { useCartStore } from '../store/cartStore';
import { useFavoritesStore } from '../store/favoritesStore';
import { useAuthStore } from '../store/authStore';
import { formatPrice, daysUntil, formatDate } from '../utils/format';
import Breadcrumbs from '../components/ui/Breadcrumbs';
import ImageSlider from '../components/ui/ImageSlider';
import StarRating from '../components/ui/StarRating';
import ShareButton from '../components/ui/ShareButton';
import CouponCard from '../components/coupon/CouponCard';
import type { CouponCardData } from '../components/coupon/CouponCard';
import { useLocalePath } from '../hooks/useLocalePath';
import { deriveCouponPreview } from '../utils/couponPreview';
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
import CouponUnavailableState from '../components/coupon/CouponUnavailableState';
import CouponVariantsSection from '../components/coupon-detail/CouponVariantsSection';
import CouponImportantInfoSection from '../components/coupon-detail/CouponImportantInfoSection';
import MerchantInfoSection from '../components/coupon-detail/MerchantInfoSection';
import ReviewForm from '../components/coupon-detail/ReviewForm';
import './CouponDetailPage.css';

export default function CouponDetailPage() {
  const { t, i18n } = useTranslation();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const { id } = useParams<{ id: string }>();
  const { addToCart } = useCartStore();
  const { toggleFavorite, isFavorite } = useFavoritesStore();
  const { isAuthenticated } = useAuthStore();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = searchParams.get('tab') === 'reviews' ? 'reviews' : 'info';
  const [activeTab, setActiveTab] = useState<'info' | 'reviews'>(initialTab);

  const changeTab = (tab: 'info' | 'reviews') => {
    setActiveTab(tab);
    setSearchParams(tab === 'reviews' ? { tab: 'reviews' } : {});
  };
  const [toastMessage, setToastMessage] = useState('');
  const optionsRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const lp = useLocalePath();

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

  const { data: relatedCoupons = [] } = useQuery({
    queryKey: ['related-coupons', coupon?.category?.id, coupon?.id],
    queryFn: () => couponsApi.getCatalog({
      categoryId: coupon?.category?.id,
      size: 6,
    }),
    select: (res) => res.data.data.content,
    enabled: !!coupon?.category?.id,
  });

  // Real reviews from API (non-blocking — may fail for guests)
  const { data: reviewsData } = useQuery({
    queryKey: ['coupon-reviews', id],
    queryFn: () => reviewsApi.getForCoupon(Number(id), 0, 20),
    select: (res) => res.data.data,
    enabled: !!id,
    retry: false,
  });

  const reviews = reviewsData?.content ?? [];
  const reviewCount = coupon?.reviewCount ?? reviews.length;
  const avgRating = coupon?.averageRating ?? (reviews.length > 0 ? reviews.reduce((s, r) => s + r.rating, 0) / reviews.length : 0);

  // Eligibility check (only for authenticated users)
  const { data: eligibilityData } = useQuery({
    queryKey: ['review-eligibility', Number(id)],
    queryFn: () => reviewsApi.getEligibility(Number(id)),
    select: (res) => res.data.data,
    enabled: !!id && isAuthenticated,
    retry: false,
  });
  const canReview = eligibilityData?.eligible ?? false;

  useEffect(() => {
    if (!coupon || typeof document === 'undefined') return;
    document.title = `${coupon.title} | TopDim`;
  }, [coupon]);

  if (isLoading) {
    return (
      <div className="detail-page">
        <div className="detail-loading">
          <div>
            <div className="detail-loading__spinner" />
            <div className="detail-loading__text">{t('couponDetail.loading')}</div>
          </div>
        </div>
      </div>
    );
  }

  if (isError || !coupon) {
    return (
      <div className="detail-page">
        <CouponUnavailableState />
      </div>
    );
  }

  const c = coupon;
  const buyDaysLeft = daysUntil(c.buyUntil);
  const discount = c.discountPercent || (c.oldPrice ? Math.round((1 - c.fromPrice / c.oldPrice) * 100) : 0);
  const fav = isFavorite(c.id);
  const images = c.images?.length > 0 ? c.images : (c.coverImageUrl ? [c.coverImageUrl] : []);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(''), 3000);
  };

  const handleAddToCart = (option: CouponOption) => {
    addToCart({
      couponOfferId: c.id,
      couponOptionId: option.id,
      couponTitle: c.title,
      optionTitle: option.title,
      unitPrice: option.couponPrice,
      quantity: 1,
      coverImageUrl: c.coverImageUrl,
    });
    showToast(t('couponDetail.addedToCart', { title: option.title }));
  };

  const handleBuyNow = (option: CouponOption) => {
    addToCart({
      couponOfferId: c.id,
      couponOptionId: option.id,
      couponTitle: c.title,
      optionTitle: option.title,
      unitPrice: option.couponPrice,
      quantity: 1,
      coverImageUrl: c.coverImageUrl,
    });
    navigate(lp('/checkout'));
  };

  const scrollToOptions = () => {
    optionsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  const relatedDeals: CouponCardData[] = relatedCoupons
    .filter((deal) => deal.id !== c.id)
    .slice(0, 6)
    .map(mapCouponOfferToCardData);

  return (
    <div className="detail-page">
      {/* Breadcrumbs */}
      <div className="container">
        <Breadcrumbs items={[
          { label: c.category?.name || t('common.catalog'), to: `/coupons${c.category ? `?category=${c.category.slug}` : ''}` },
          { label: c.title },
        ]} />
      </div>

      {/* ═══ HERO SECTION ═══ */}
      <div className="container">
        <div className="hero-section">
          {/* Left: Image Gallery */}
          <div className="hero-gallery">
            <ImageSlider images={images} alt={c.title} fallbackText={c.merchant?.name || 'TopDim'} />
          </div>

          {/* Right: Summary Panel */}
          <div className="hero-summary">
            <div className="detail-merchant-badge">{c.merchant?.name}</div>
            <h1 className="detail-title">{c.title}</h1>
            {(() => {
              const preview = deriveCouponPreview(c.offerDescription);
              return preview ? <p className="detail-short-desc">{preview}</p> : null;
            })()}

            {/* Discount badge & pricing */}
            <div className="hero-pricing">
              {discount > 0 && <span className="hero-discount-badge">-{discount}%</span>}
              <span className="hero-price">{t('common.from')} {formatPrice(c.fromPrice)}</span>
              {c.oldPrice && <span className="hero-old-price">{formatPrice(c.oldPrice)}</span>}
            </div>

            {/* Location */}
            {c.merchant?.primaryLocation?.address && (
              <div className="hero-meta-item">
                <MapPin size={15} />
                <span>{c.merchant.primaryLocation.address}</span>
              </div>
            )}

            {/* Dates */}
            <div className="hero-dates">
              <div className="hero-meta-item">
                <Calendar size={15} />
                <span>{t('couponDetail.buyUntil', { date: formatDate(c.buyUntil) })}</span>
                {buyDaysLeft <= 7 && (
                  <span className="hero-urgent">
                    {buyDaysLeft === 0 ? t('couponDetail.lastDay') : t('couponDetail.daysLeft', { count: buyDaysLeft })}
                  </span>
                )}
              </div>
              <div className="hero-meta-item">
                <Clock size={15} />
                <span>{t('couponDetail.useUntil', { date: formatDate(c.useUntil) })}</span>
              </div>
            </div>

            {/* Stats (real data only) */}
            <div className="detail-stats">
              {avgRating > 0 && (
                <StarRating rating={avgRating} reviewCount={reviewCount} size={16} />
              )}
              <span className="detail-stat">
                <TrendingUp size={15} />
                {t('couponDetail.purchases', { count: c.totalSold.toLocaleString(locale) })}
              </span>
              {c.giftAvailable && (
                <span className="detail-stat detail-stat--gift">
                  <Gift size={15} />
                  {t('couponDetail.gift')}
                </span>
              )}
            </div>

            {/* CTA */}
            <button className="hero-cta primary-button" onClick={scrollToOptions}>
              {t('couponDetail.selectCert')}
            </button>
          </div>
        </div>
      </div>

      {/* ═══ TABS + ACTION BAR ═══ */}
      <div className="container">
        <div className="detail-action-bar">
          <div className="detail-tabs-row">
            <button
              className={`detail-tab-btn ${activeTab === 'info' ? 'detail-tab-btn--active' : ''}`}
              onClick={() => changeTab('info')}
            >
              {t('couponDetail.tabInfo')}
            </button>
            <button
              className={`detail-tab-btn ${activeTab === 'reviews' ? 'detail-tab-btn--active' : ''}`}
              onClick={() => changeTab('reviews')}
            >
              {t('couponDetail.tabReviews')} {reviewCount > 0 && <span className="detail-tab-badge">{reviewCount}</span>}
            </button>
          </div>
          <div className="detail-action-buttons">
            <button
              className={`detail-fav-btn ${fav ? 'detail-fav-btn--active' : ''}`}
              onClick={() => toggleFavorite(c.id)}
              aria-label={t('couponDetail.favorite')}
            >
              <Heart size={18} fill={fav ? 'currentColor' : 'none'} />
              <span className="detail-action-label">{t('couponDetail.favorite')}</span>
            </button>
            <ShareButton title={c.title} variant="icon" />
          </div>
        </div>
      </div>

      {/* ═══ TAB CONTENT ═══ */}
      <div className="container">
        {activeTab === 'info' && (
          <div className="detail-info-layout">
            {/* Left column: variants + important info */}
            <div className="detail-info-left">
              <CouponVariantsSection
                ref={optionsRef}
                coupon={c}
                onBuy={handleBuyNow}
                onAddToCart={handleAddToCart}
              />

              {/* Offer description */}
              {c.offerDescription && (
                <div className="detail-block">
                  <h2 className="detail-section-title">{t('couponDetail.description')}</h2>
                  <div className="markdown-body">
                    <ReactMarkdown>{c.offerDescription}</ReactMarkdown>
                  </div>
                </div>
              )}

              <CouponImportantInfoSection coupon={c} />
            </div>

            {/* Right column: merchant info */}
            <div className="detail-info-right">
              <MerchantInfoSection coupon={c} />
            </div>
          </div>
        )}

        {activeTab === 'reviews' && (
          <div className="detail-reviews-tab">
            {/* Review Form / Notice */}
            {!isAuthenticated ? (
              <div className="review-notice">
                <PenLine className="review-notice__icon" size={24} strokeWidth={1.5} />
                <p className="review-notice__text">
                  <Link to={lp('/login')} className="review-notice__link">{t('couponDetail.loginToReview')}</Link>{t('couponDetail.loginToReviewSuffix')}
                </p>
              </div>
            ) : canReview ? (
              <ReviewForm couponOfferId={c.id} />
            ) : (
              <div className="review-notice">
                <Info className="review-notice__icon" size={24} strokeWidth={1.5} />
                <p className="review-notice__text">
                  {t('couponDetail.reviewAfterUse')}
                </p>
              </div>
            )}

            {reviews.length === 0 ? (
              <div className="reviews-empty">
                <Star className="reviews-empty__icon" size={40} strokeWidth={1.5} />
                <h3>{t('couponDetail.noReviews')}</h3>
                <p>{t('couponDetail.beFirstReview')}</p>
              </div>
            ) : (
              <>
                <div className="reviews-summary">
                  <div className="reviews-summary__big">
                    <span className="reviews-summary__number">{avgRating.toFixed(1)}</span>
                    <StarRating rating={avgRating} showCount={false} size={20} />
                    <span className="reviews-summary__total">{t('couponDetail.reviewsCount', { count: reviewCount })}</span>
                  </div>
                </div>
                <div className="reviews-list">
                  {reviews.map((review) => (
                    <div key={review.id} className="review-card">
                      <div className="review-card__header">
                        <div className="review-card__avatar">
                          {(review.userName || 'U').charAt(0)}
                        </div>
                        <div>
                          <div className="review-card__author">{review.userName || t('common.user')}</div>
                          <StarRating rating={review.rating} showCount={false} size={13} />
                        </div>
                        <span className="review-card__date">{formatDate(review.createdAt)}</span>
                      </div>
                      <p className="review-card__text">{review.comment}</p>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        )}

        {/* Related deals */}
        {relatedDeals.length > 0 && (
          <div className="detail-related">
            <h2 className="detail-section-title">{t('couponDetail.related')}</h2>
            <div className="detail-related__carousel">
              {relatedDeals.map((deal) => (
                <CouponCard key={deal.id} coupon={deal} layout="carousel" />
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Sticky CTA (mobile) */}
      <button className="detail-sticky-cta" onClick={scrollToOptions}>
        {t('couponDetail.selectCert')}
      </button>

      {/* Toast notification */}
      {toastMessage && (
        <div className="detail-toast">
          <Check size={18} />
          {toastMessage}
        </div>
      )}
    </div>
  );
}
