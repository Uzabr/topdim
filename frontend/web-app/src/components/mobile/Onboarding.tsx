import { useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import type { CouponOffer } from '../../api/coupons';
import { useScrollLock } from '../../hooks/useScrollLock';
import './Onboarding.css';

/** Минимальный сдвиг по X, чтобы не путать свайп с тапом. */
const SWIPE_MIN_PX = 48;

/**
 * Горизонтальный жест: 1 — следующий слайд, -1 — предыдущий, 0 — не свайп
 * (короткий жест или вертикальное движение).
 */
export function swipeAxis(dx: number, dy: number, minPx = SWIPE_MIN_PX): -1 | 0 | 1 {
  if (Math.abs(dx) < minPx || Math.abs(dx) <= Math.abs(dy)) return 0;
  return dx < 0 ? 1 : -1;
}

interface OnboardingProps {
  /** Карточки для «конвейера» на первом слайде — настоящие купоны, не картинки. */
  deals: CouponOffer[];
  onDone: () => void;
}

const SLIDES = 3;
/** Конвейер: три карточки на одном цикле, сдвинутые по фазе на треть. */
const CONVEY_S = 13.5;

const CONFETTI = [
  { left: '8%', w: 8, h: 13, r: '3px', color: 'var(--blob-pink)', dur: 4.6, delay: 0 },
  { left: '22%', w: 9, h: 9, r: '50%', color: 'var(--blob-mint)', dur: 5.4, delay: 1.2 },
  { left: '37%', w: 7, h: 12, r: '3px', color: 'var(--blob-lemon)', dur: 4.2, delay: 2.1 },
  { left: '55%', w: 8, h: 8, r: '50%', color: 'var(--blob-lilac)', dur: 5.8, delay: 0.6 },
  { left: '68%', w: 9, h: 14, r: '3px', color: 'var(--blob-pink)', dur: 4.9, delay: 2.8 },
  { left: '82%', w: 7, h: 7, r: '50%', color: 'var(--blob-lemon)', dur: 5.1, delay: 1.7 },
  { left: '92%', w: 8, h: 12, r: '3px', color: 'var(--blob-mint)', dur: 4.4, delay: 3.4 },
];

/**
 * Онбординг первого запуска. Референс: «Мобилка - 1 Онбординг».
 * Порталом в body: он перекрывает и нижнюю навигацию, и пятна фона.
 */
export default function Onboarding({ deals, onDone }: OnboardingProps) {
  const { t } = useTranslation();
  const [slide, setSlide] = useState(0);
  const [leaving, setLeaving] = useState(false);
  const swipeOrigin = useRef<{ x: number; y: number; id: number } | null>(null);
  const didSwipe = useRef(false);
  useScrollLock();

  const finish = () => {
    if (leaving) return;
    setLeaving(true);
    setTimeout(onDone, 380);
  };

  const next = () => (slide < SLIDES - 1 ? setSlide(slide + 1) : finish());

  const onPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    if ((e.target as Element).closest('button, a')) return;
    didSwipe.current = false;
    swipeOrigin.current = { x: e.clientX, y: e.clientY, id: e.pointerId };
    e.currentTarget.setPointerCapture?.(e.pointerId);
  };

  const onPointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    const origin = swipeOrigin.current;
    swipeOrigin.current = null;
    if (!origin || origin.id !== e.pointerId) return;
    const axis = swipeAxis(e.clientX - origin.x, e.clientY - origin.y);
    if (axis === 0) return;
    didSwipe.current = true;
    if (axis === 1) next();
    else if (slide > 0) setSlide(slide - 1);
  };

  const onPointerCancel = () => {
    swipeOrigin.current = null;
  };

  /** После свайпа не даём синтетическому click нажать «Далее» ещё раз. */
  const onClickCapture = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!didSwipe.current) return;
    didSwipe.current = false;
    e.preventDefault();
    e.stopPropagation();
  };

  const cards = deals.slice(0, 3);

  return createPortal(
    <div
      className={`onb${leaving ? ' onb--out' : ''}`}
      onPointerDown={onPointerDown}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerCancel}
      onClickCapture={onClickCapture}
    >
      <span className="onb__blob onb__blob--1" />
      <span className="onb__blob onb__blob--2" />
      <span className="onb__blob onb__blob--3" />
      <span className="onb__blob onb__blob--4" />

      <button type="button" className="onb__skip" onClick={finish}>
        {t('mobile.onboarding.skip')}
      </button>

      {slide === 0 && (
        <>
          <div className="onb__stage">
            {cards.map((coupon, i) => (
              <div
                key={coupon.id}
                className="onb__convey"
                style={{ animationDelay: `${-CONVEY_S + (CONVEY_S / 3) * i}s` }}
              >
                <div className={`onb__card onb__card--${i}`}>
                  {coupon.coverImageUrl && <img src={coupon.coverImageUrl} alt="" />}
                  <span className="onb__card-title">{coupon.title}</span>
                </div>
              </div>
            ))}
          </div>

          <div className="onb__text">
            <p className="onb__line onb__line--1">
              <strong className="onb__big">{t('mobile.onboarding.s1.a')}</strong>{' '}
              <span className="onb__thin">{t('mobile.onboarding.s1.b')}</span>
            </p>
            <p className="onb__line onb__line--2">
              <span className="onb__thin">{t('mobile.onboarding.s1.c')}</span>{' '}
              <strong className="onb__big">{t('mobile.onboarding.s1.d')}</strong>
            </p>
            <p className="onb__line onb__line--3 onb__sub">{t('mobile.onboarding.s1.sub')}</p>
          </div>
        </>
      )}

      {slide === 1 && (
        <>
          <div className="onb__stage onb__stage--center">
            <div className="onb__ticket">
              <div className="onb__ticket-top">
                <span className="onb__ticket-label">{t('profile.ticket.yours')}</span>
                <span className="onb__ticket-title">
                  {cards[0]?.title ?? t('mobile.onboarding.s2.sample')}
                </span>
                <span className="onb__ticket-meta">{cards[0]?.merchant?.name}</span>
              </div>

              <div className="onb__ticket-perf">
                <span className="onb__notch onb__notch--l" />
                <span className="onb__notch onb__notch--r" />
              </div>

              <div className="onb__ticket-bottom">
                {/* Декоративный QR: настоящий появится в профиле после покупки. */}
                <span className="onb__qr" aria-hidden="true" />
              </div>
            </div>
          </div>

          <div className="onb__text">
            <p className="onb__line onb__line--1">
              <strong className="onb__big">{t('mobile.onboarding.s2.a')}</strong>
            </p>
            <p className="onb__line onb__line--2">
              <span className="onb__thin">{t('mobile.onboarding.s2.b')}</span>{' '}
              <strong className="onb__big">{t('mobile.onboarding.s2.c')}</strong>
            </p>
            <p className="onb__line onb__line--3 onb__sub">{t('mobile.onboarding.s2.sub')}</p>
          </div>
        </>
      )}

      {slide === 2 && (
        <div className="onb__final">
          {CONFETTI.map((c) => (
            <span
              key={c.left}
              className="onb__conf"
              style={{
                left: c.left,
                width: c.w,
                height: c.h,
                borderRadius: c.r,
                background: c.color,
                animationDuration: `${c.dur}s`,
                animationDelay: `${c.delay}s`,
              }}
            />
          ))}

          <div className="onb__final-text">
            <p className="onb__line onb__line--1 onb__huge">{t('mobile.onboarding.s3.a')}</p>
            <p className="onb__line onb__line--2 onb__huge onb__huge--xl">
              {t('mobile.onboarding.s3.b')}
            </p>
            <p className="onb__line onb__line--3 onb__mid">
              {t('mobile.onboarding.s3.c')}{' '}
              <strong>{t('mobile.onboarding.s3.d')}</strong>
            </p>
            <p className="onb__line onb__line--4 onb__with">
              <span className="onb__mid">{t('mobile.onboarding.s3.e')}</span>
              <span className="onb__logo">sizbiz</span>
            </p>
            <p className="onb__line onb__line--5 onb__huge">
              {t('mobile.onboarding.s3.f')} <span className="onb__mid">{t('mobile.onboarding.s3.g')}</span>
            </p>
          </div>
        </div>
      )}

      <div className="onb__dots">
        {Array.from({ length: SLIDES }, (_, i) => (
          <button
            key={i}
            type="button"
            className={`onb__dot${i === slide ? ' onb__dot--on' : ''}`}
            onClick={() => setSlide(i)}
            aria-label={`${i + 1}`}
          />
        ))}
      </div>

      <div className="onb__foot">
        <button type="button" className="onb__btn" onClick={next}>
          {slide < SLIDES - 1 ? t('common.next') : t('mobile.onboarding.start')}
        </button>
      </div>
    </div>,
    document.body,
  );
}
