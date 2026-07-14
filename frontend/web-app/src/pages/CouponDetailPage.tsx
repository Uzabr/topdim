import { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Check } from 'lucide-react';
import { couponsApi } from '../api/coupons';
import { useCartStore } from '../store/cartStore';
import Breadcrumbs from '../components/ui/Breadcrumbs';
import CouponCard from '../components/coupon/CouponCard';
import CouponUnavailableState from '../components/coupon/CouponUnavailableState';
import CouponGallery from '../components/coupon-detail/CouponGallery';
import OptionPicker from '../components/coupon-detail/OptionPicker';
import PurchasePanel from '../components/coupon-detail/PurchasePanel';
import ReviewsBlock from '../components/coupon-detail/ReviewsBlock';
import ShareMenu from '../components/coupon-detail/ShareMenu';
import WhereSection from '../components/coupon-detail/WhereSection';
import { useLocalePath } from '../hooks/useLocalePath';
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
import { localizedName } from '../utils/localizedText';
import './CouponDetailPage.css';

type Block = 'info' | 'reviews';

/** Район из адреса: «Яккасарай, ул. Шота Руставели 21» → «Яккасарай». */
function district(address?: string): string | undefined {
  return address?.split(',')[0]?.trim() || undefined;
}

export default function CouponDetailPage() {
  const { t, i18n } = useTranslation();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const { addToCart } = useCartStore();

  const [block, setBlock] = useState<Block>('info');
  const [optionId, setOptionId] = useState<number | null>(null);
  const [toast, setToast] = useState('');

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

  const { data: related = [] } = useQuery({
    queryKey: ['related-coupons', coupon?.category?.id, coupon?.id],
    queryFn: () => couponsApi.getCatalog({ categoryId: coupon?.category?.id, size: 5 }),
    select: (res) => res.data.data.content,
    enabled: !!coupon?.category?.id,
  });

  useEffect(() => {
    if (coupon) document.title = `${coupon.title} — sizbiz`;
  }, [coupon]);

  // Переход на другой купон (по «похожим») — сбрасываем выбор прямо в рендере:
  // компонент не размонтируется, а эффект здесь дал бы лишний каскад рендеров.
  const [prevId, setPrevId] = useState(id);
  if (prevId !== id) {
    setPrevId(id);
    setOptionId(null);
    setBlock('info');
  }

  const options = useMemo(() => coupon?.options ?? [], [coupon]);
  const selected = useMemo(
    () => options.find((o) => o.id === optionId) ?? options[0],
    [options, optionId],
  );

  if (isLoading) {
    return (
      <div className="container detail">
        <p className="detail__loading">{t('couponDetail.loading')}</p>
      </div>
    );
  }

  if (isError || !coupon) {
    return (
      <div className="container detail">
        <CouponUnavailableState />
      </div>
    );
  }

  const c = coupon;
  const images = c.images?.length ? c.images : c.coverImageUrl ? [c.coverImageUrl] : [];
  const area = district(c.merchant?.primaryLocation?.address);

  const putInCart = () => {
    if (!selected) return;
    addToCart({
      couponOfferId: c.id,
      couponOptionId: selected.id,
      couponTitle: c.title,
      optionTitle: selected.title,
      unitPrice: selected.couponPrice,
      quantity: 1,
      coverImageUrl: c.coverImageUrl,
    });
  };

  const handleBuy = () => {
    putInCart();
    navigate(lp('/checkout'));
  };

  const handleAddToCart = () => {
    putInCart();
    setToast(t('couponDetail.addedToCart', { title: selected?.title ?? c.title }));
    setTimeout(() => setToast(''), 3000);
  };

  const relatedCards = related.filter((r) => r.id !== c.id).slice(0, 4);

  return (
    <div className="container detail">
      <Breadcrumbs
        items={[
          {
            label: c.category ? localizedName(c.category, i18n.language) : t('common.catalog'),
            to: c.category ? `/coupons?categoryId=${c.category.id}` : '/coupons',
          },
          { label: c.title },
        ]}
      />

      <div className="detail__grid">
        <div className="detail__main">
          <CouponGallery
            images={images}
            alt={c.title}
            fallbackText={c.merchant?.name}
            couponId={c.id}
          />

          <h1 className="detail__title">{c.title}</h1>

          <div className="detail__meta">
            {c.merchant?.name && <span className="detail__merchant">{c.merchant.name}</span>}
            {c.averageRating != null && c.averageRating > 0 && (
              <span>
                ★ {c.averageRating.toFixed(1).replace('.', ',')} ·{' '}
                {t('couponDetail.reviewsCount', { count: c.reviewCount ?? 0 })}
              </span>
            )}
            {c.totalSold > 0 && (
              <span>{t('couponDetail.sold', { count: c.totalSold.toLocaleString(locale) })}</span>
            )}
            {area && <span>{area}</span>}
          </div>

          <div className="detail__blocks">
            <div className="detail__pills" id="cblocks">
              <button
                type="button"
                data-blockkey="info"
                className={`detail__pill${block === 'info' ? ' detail__pill--active' : ''}`}
                onClick={() => setBlock('info')}
              >
                {t('couponDetail.tabInfo')}
              </button>
              <button
                type="button"
                data-blockkey="reviews"
                className={`detail__pill${block === 'reviews' ? ' detail__pill--active' : ''}`}
                onClick={() => setBlock('reviews')}
              >
                {t('couponDetail.tabReviews')}
                {c.reviewCount ? ` · ${c.reviewCount}` : ''}
              </button>
            </div>

            <ShareMenu title={c.title} />
          </div>

          {block === 'info' ? (
            <>
              {options.length > 0 && selected && (
                <OptionPicker
                  options={options}
                  selectedId={selected.id}
                  onSelect={setOptionId}
                />
              )}

              {c.offerDescription && (
                <section className="detail__section">
                  <h2 className="detail-h2">{t('couponDetail.description')}</h2>
                  <div className="detail__description">{c.offerDescription}</div>
                </section>
              )}

              <WhereSection
                location={c.merchant?.primaryLocation}
                merchantName={c.merchant?.name}
              />
            </>
          ) : (
            <ReviewsBlock
              couponId={c.id}
              averageRating={c.averageRating}
              reviewCount={c.reviewCount ?? 0}
            />
          )}
        </div>

        <aside className="detail__aside">
          {selected && (
            <PurchasePanel
              coupon={c}
              option={selected}
              onBuy={handleBuy}
              onAddToCart={handleAddToCart}
            />
          )}
        </aside>
      </div>

      {relatedCards.length > 0 && (
        <section className="detail__related">
          <h2 className="detail__related-title">{t('couponDetail.related')}</h2>
          <div className="detail__related-grid">
            {relatedCards.map((r) => (
              <CouponCard key={r.id} coupon={mapCouponOfferToCardData(r)} />
            ))}
          </div>
        </section>
      )}

      {toast && (
        <div className="detail__toast">
          <Check size={18} />
          {toast}
        </div>
      )}
    </div>
  );
}
