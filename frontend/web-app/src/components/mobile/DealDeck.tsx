import { useState } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { CouponOffer } from '../../api/coupons';
import { useCountdown } from '../../hooks/useCountdown';
import { useLocalePath } from '../../hooks/useLocalePath';
import { couponStock } from '../../utils/couponStock';
import { calcDiscount, formatDate } from '../../utils/format';
import './DealDeck.css';

interface DealDeckProps {
  /** Горящие купоны: первый — «купон дня», остальные листаются в слайдере. */
  deals: CouponOffer[];
}

function discountOf(coupon: CouponOffer): number {
  return coupon.discountPercent || calcDiscount(coupon.oldPrice ?? 0, coupon.fromPrice);
}

/** Срок: живой таймер, только если до конца продажи реально меньше суток. */
function DeadlineBadge({ until, className }: { until: string; className: string }) {
  const { t } = useTranslation();
  const countdown = useCountdown(until);

  return (
    <span className={className}>
      {countdown?.isUrgent ? countdown.clock : t('home.hero.endsOn', { date: formatDate(until) })}
    </span>
  );
}

/**
 * Стопка «Купон дня»: чёрная карточка с двумя подложками; тап раскрывает
 * фуллскрин-слайдер, где карточки вылетают веером (design_handoff_sizbiz →
 * «Мобилка - 2 Главная»).
 */
export default function DealDeck({ deals }: DealDeckProps) {
  const { t, i18n } = useTranslation();
  const lp = useLocalePath();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const currency = t('common.currency.sum');

  const [open, setOpen] = useState(false);

  if (deals.length === 0) return null;

  const [top] = deals;

  return (
    <>
      <button type="button" className="deck" onClick={() => setOpen(true)}>
        <span className="deck__back deck__back--1" />
        <span className="deck__back deck__back--2" />

        <span className="deck__card">
          {top.coverImageUrl && <img src={top.coverImageUrl} alt="" loading="lazy" />}

          <span className="deck__tag">{t('home.hero.label')}</span>
          <DeadlineBadge until={top.buyUntil} className="deck__timer" />

          <span className="deck__foot">
            <span className="deck__text">
              <span className="deck__title">{top.title}</span>
              {deals.length > 1 && (
                <span className="deck__more">
                  {t('mobile.deck.more', { count: deals.length - 1 })}
                </span>
              )}
            </span>
            <span className="deck__off">−{discountOf(top)}%</span>
          </span>
        </span>
      </button>

      {/* Портал: у карусели-родителя есть animation с transform, а он делает её
          containing block для position: fixed — оверлей обрезало бы по карусели. */}
      {open && createPortal(
        <div className="deckview" onClick={() => setOpen(false)} role="dialog" aria-modal="true">
          <div className="deckview__head">
            <span className="deckview__title">{t('mobile.deck.title')}</span>
            <button
              type="button"
              className="deckview__close"
              onClick={() => setOpen(false)}
              aria-label={t('common.close')}
            >
              <X size={15} />
            </button>
          </div>

          <div className="deckview__row" onClick={(e) => e.stopPropagation()}>
            {deals.map((deal, i) => {
              const stock = couponStock(deal);
              return (
                <article
                  key={deal.id}
                  className="dcard"
                  style={{ animationDelay: `${i * 0.09}s` }}
                >
                  <Link to={lp(`/coupons/${deal.id}`)} className="dcard__link" aria-label={deal.title} />

                  <div className="dcard__photo">
                    {deal.coverImageUrl && <img src={deal.coverImageUrl} alt="" loading="lazy" />}
                  </div>

                  {/* «Купон дня» — только у первого: остальные просто горящие. */}
                  {i === 0 && <span className="dcard__tag">{t('home.hero.label')}</span>}
                  <DeadlineBadge until={deal.buyUntil} className="dcard__timer" />

                  <div className="dcard__body">
                    <div className="dcard__row">
                      <span className="dcard__title">{deal.title}</span>
                      <span className="dcard__off">−{discountOf(deal)}%</span>
                    </div>
                    <span className="dcard__price">
                      {t('common.from')} {deal.fromPrice.toLocaleString(locale)} {currency}
                    </span>

                    <div className="dcard__bottom">
                      {stock && (
                        <>
                          <div className="dcard__bar">
                            <span
                              className="dcard__bar-fill"
                              style={{ width: `${stock.percent}%` }}
                            />
                          </div>
                          <span className="dcard__left">
                            {t('home.hero.left', { left: stock.left, total: stock.limit })}
                          </span>
                        </>
                      )}

                      <Link to={lp(`/coupons/${deal.id}`)} className="dcard__buy">
                        {t('home.hero.buy')}
                      </Link>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}
