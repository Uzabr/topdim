import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Heart, Home, ShoppingBag, User } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocalePath } from '../../hooks/useLocalePath';
import { playDrop } from '../../utils/playDrop';
import './BottomNav.css';

/** Фазы капли (мс) — как в референсе «Мобилка - 2 Главная». */
const BALL = 150;
const FLY = 280;
const POUR = 240;

/** Шаг капли: иконка 44px + зазор 14px. */
const STEP = 58;

const ITEMS = [
  { key: 'home', path: '/', icon: Home, labelKey: 'bottomNav.home' },
  { key: 'favorites', path: '/favorites', icon: Heart, labelKey: 'bottomNav.favorites' },
  { key: 'cart', path: '/cart', icon: ShoppingBag, labelKey: 'bottomNav.cart' },
  { key: 'profile', path: '/profile', icon: User, labelKey: 'bottomNav.profile' },
] as const;

type Phase = 'idle' | 'ball' | 'fly' | 'pour';

/** Какой вкладке соответствует текущий маршрут. */
export function indexOfRoute(pathname: string): number {
  const path = pathname.replace(/^\/(ru|uz)/, '') || '/';
  const i = ITEMS.findIndex((item) => item.path !== '/' && path.startsWith(item.path));
  return i === -1 ? 0 : i;
}

/**
 * Мобильная навигация: чёрная таблетка, жёлтая капля перетекает между иконками
 * в три фазы (сжатие → полёт с растяжением → разлив с пружиной) и «плюпает».
 * Гость спокойно попадает на корзину / избранное / профиль — вход предлагается на странице.
 *
 * Капля следует за URL. Раньше анимация заканчивалась до navigate(), и сверка
 * с ещё старым pathname возвращала каплю на предыдущую вкладку, затем снова вперёд.
 */
export default function BottomNav() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const lp = useLocalePath();

  const routeIndex = indexOfRoute(location.pathname);

  const [pos, setPos] = useState(routeIndex);
  const [active, setActive] = useState(routeIndex);
  const [phase, setPhase] = useState<Phase>('idle');
  const timers = useRef<number[]>([]);
  const prevRoute = useRef(routeIndex);

  useEffect(() => () => timers.current.forEach(clearTimeout), []);

  useEffect(() => {
    if (prevRoute.current === routeIndex) return;
    prevRoute.current = routeIndex;

    timers.current.forEach(clearTimeout);

    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      setPos(routeIndex);
      setActive(routeIndex);
      setPhase('idle');
      return;
    }

    setPhase('ball');
    timers.current = [
      window.setTimeout(() => {
        setPos(routeIndex);
        setPhase('fly');
      }, BALL),
      window.setTimeout(() => {
        setActive(routeIndex);
        setPhase('pour');
        playDrop();
      }, BALL + FLY),
      window.setTimeout(() => {
        setPhase('idle');
      }, BALL + FLY + POUR),
    ];
  }, [routeIndex]);

  const go = (index: number) => {
    if (index === routeIndex) return;
    navigate(lp(ITEMS[index].path));
  };

  return (
    <nav className="mnav" aria-label={t('common.navigation')}>
      <span
        className={`mnav__blob mnav__blob--${phase}`}
        style={{ left: 12 + pos * STEP }}
        aria-hidden="true"
      />

      {ITEMS.map((item, i) => {
        const Icon = item.icon;
        const isActive = i === active && (phase === 'idle' || phase === 'pour');
        return (
          <button
            key={item.key}
            type="button"
            className={`mnav__item${isActive ? ' mnav__item--active' : ''}`}
            onClick={() => go(i)}
            aria-current={i === routeIndex ? 'page' : undefined}
            aria-label={t(item.labelKey)}
          >
            <Icon size={22} strokeWidth={1.8} />
          </button>
        );
      })}
    </nav>
  );
}
