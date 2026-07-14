import { useCallback, useEffect, useLayoutEffect, useRef } from 'react';
import { playDrop } from '../../utils/playDrop';
import './DropTabs.css';

export interface DropTab<K extends string> {
  key: K;
  label: string;
  /** Счётчик справа от подписи (например, число активных купонов). */
  badge?: number;
}

interface DropTabsProps<K extends string> {
  id: string;
  tabs: DropTab<K>[];
  active: K;
  onChange: (key: K) => void;
  /** Оформление таблеток: 'paper' — белые с тенью, 'card' — #FAF9F6. */
  variant?: 'paper' | 'card';
}

/** Фазы капли (мс): сжатие → полёт → разлив с пружиной. */
const SQUEEZE = 160;
const FLIGHT = 440;
const SETTLE = 680;

const FLIGHT_EASE =
  'left 0.27s cubic-bezier(0.5, 0, 0.5, 1), transform 0.27s cubic-bezier(0.5, 0, 0.5, 1)';
const SETTLE_EASE =
  'left 0.22s cubic-bezier(0.3, 1.5, 0.5, 1), width 0.22s cubic-bezier(0.3, 1.5, 0.5, 1), transform 0.22s ease-out';

/**
 * Табы с «каплей»: чёрный индикатор сжимается в шарик, летит к цели, растягиваясь
 * по горизонтали, и разливается в кнопку с пружиной — плюс звук «плюп» в момент
 * приземления (design_handoff_sizbiz → «Профиль» / «Страница купона»).
 *
 * Капля — декорация, поэтому ей рулим напрямую через DOM: React-состояние здесь
 * дало бы каскад рендеров на каждый кадр фазы.
 */
export default function DropTabs<K extends string>({
  id,
  tabs,
  active,
  onChange,
  variant = 'paper',
}: DropTabsProps<K>) {
  const rootRef = useRef<HTMLDivElement>(null);
  const blobRef = useRef<HTMLSpanElement>(null);
  const timers = useRef<number[]>([]);
  const busy = useRef(false);

  const buttonOf = useCallback(
    (key: string) =>
      rootRef.current?.querySelector<HTMLButtonElement>(`[data-tabkey="${key}"]`) ?? null,
    [],
  );

  /** Мгновенно посадить каплю на активный таб (первый рендер, ресайз, смена языка). */
  const snap = useCallback(() => {
    const blob = blobRef.current;
    const el = buttonOf(active);
    if (!blob || !el) return;

    blob.style.transition = 'none';
    blob.style.transform = 'scaleX(1)';
    blob.style.left = `${el.offsetLeft}px`;
    blob.style.width = `${el.offsetWidth}px`;
    blob.style.opacity = '1';
  }, [active, buttonOf]);

  useLayoutEffect(snap, [snap, tabs]);

  useEffect(() => {
    const onResize = () => {
      if (!busy.current) snap();
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [snap]);

  useEffect(() => () => timers.current.forEach(clearTimeout), []);

  const go = (key: K) => {
    if (busy.current || key === active) return;

    const blob = blobRef.current;
    const target = buttonOf(key);
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

    if (!blob || !target || reduced) {
      onChange(key);
      return;
    }

    const from = { left: blob.offsetLeft, width: blob.offsetWidth };
    const to = { left: target.offsetLeft, width: target.offsetWidth };
    const h = target.offsetHeight;
    busy.current = true;

    // 1. Сжатие в шарик на месте.
    blob.style.transition = 'left 0.15s ease-in, width 0.15s ease-in';
    blob.style.left = `${from.left + from.width / 2 - h / 2}px`;
    blob.style.width = `${h}px`;

    timers.current = [
      // 2. Полёт к цели с растяжением по горизонтали.
      window.setTimeout(() => {
        onChange(key);
        blob.style.transition = FLIGHT_EASE;
        blob.style.left = `${to.left + to.width / 2 - h / 2}px`;
        blob.style.transform = 'scaleX(1.7)';
      }, SQUEEZE),

      // 3. Разлив в кнопку с пружиной + звук приземления.
      window.setTimeout(() => {
        blob.style.transition = SETTLE_EASE;
        blob.style.left = `${to.left}px`;
        blob.style.width = `${to.width}px`;
        blob.style.transform = 'scaleX(1)';
        playDrop();
      }, FLIGHT),

      window.setTimeout(() => {
        busy.current = false;
      }, SETTLE),
    ];
  };

  return (
    <div className={`dtabs dtabs--${variant}`} id={id} ref={rootRef} role="tablist">
      <span className="dtabs__blob" ref={blobRef} aria-hidden="true" />

      {tabs.map((tab) => (
        <button
          key={tab.key}
          type="button"
          role="tab"
          aria-selected={tab.key === active}
          data-tabkey={tab.key}
          className={`dtab${tab.key === active ? ' dtab--active' : ''}`}
          onClick={() => go(tab.key)}
        >
          <span className="dtab__label">
            {tab.label}
            {tab.badge != null && tab.badge > 0 && <span className="dtab__badge">{tab.badge}</span>}
          </span>
        </button>
      ))}
    </div>
  );
}
