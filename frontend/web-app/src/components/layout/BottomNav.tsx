import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Heart, Home, ShoppingBag, User } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../store/authStore';
import { useLocalePath } from '../../hooks/useLocalePath';
import { playDrop } from '../../utils/playDrop';
import LoginModal from '../auth/LoginModal';
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
function indexOfRoute(pathname: string): number {
  const path = pathname.replace(/^\/(ru|uz)/, '') || '/';
  const i = ITEMS.findIndex((item) => item.path !== '/' && path.startsWith(item.path));
  return i === -1 ? 0 : i;
}

/**
 * Мобильная навигация: чёрная таблетка, жёлтая капля перетекает между иконками
 * в три фазы (сжатие → полёт с растяжением → разлив с пружиной) и «плюпает».
 * Гость жмёт «Профиль» → модалка входа; после входа капля дотекает до профиля.
 */
export default function BottomNav() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const { isAuthenticated } = useAuthStore();

  const routeIndex = indexOfRoute(location.pathname);

  const [pos, setPos] = useState(routeIndex);
  const [active, setActive] = useState(routeIndex);
  const [phase, setPhase] = useState<Phase>('idle');
  const [loginOpen, setLoginOpen] = useState(false);
  const pendingRef = useRef<number | null>(null);
  const timers = useRef<number[]>([]);

  // Маршрут сменился не через навигацию (ссылка, кнопка «назад») — капля садится на место.
  const [prevRoute, setPrevRoute] = useState(routeIndex);
  if (prevRoute !== routeIndex && phase === 'idle') {
    setPrevRoute(routeIndex);
    setPos(routeIndex);
    setActive(routeIndex);
  }

  useEffect(() => () => timers.current.forEach(clearTimeout), []);

  const go = (index: number) => {
    if (phase !== 'idle' || index === active) return;

    // Профиль для гостя — сначала вход; капля ждёт результата.
    if (ITEMS[index].key === 'profile' && !isAuthenticated) {
      pendingRef.current = index;
      setLoginOpen(true);
      return;
    }

    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      setPos(index);
      setActive(index);
      setPrevRoute(index);
      navigate(lp(ITEMS[index].path));
      return;
    }

    setPhase('ball');
    timers.current = [
      window.setTimeout(() => {
        setPos(index);
        setPhase('fly');
      }, BALL),
      window.setTimeout(() => {
        setActive(index);
        setPhase('pour');
        playDrop();
      }, BALL + FLY),
      window.setTimeout(() => {
        setPhase('idle');
        setPrevRoute(index);
        navigate(lp(ITEMS[index].path));
      }, BALL + FLY + POUR),
    ];
  };

  const onLoggedIn = () => {
    setLoginOpen(false);
    const pending = pendingRef.current;
    pendingRef.current = null;
    if (pending != null) window.setTimeout(() => go(pending), 350);
  };

  return (
    <>
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

      {loginOpen && (
        <LoginModal
          onClose={() => {
            setLoginOpen(false);
            pendingRef.current = null;
          }}
          onSuccess={onLoggedIn}
        />
      )}
    </>
  );
}
