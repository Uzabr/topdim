import { useLocation } from 'react-router-dom';
import { useIsDesktop } from '../../hooks/useIsDesktop';
import './MobileBackdrop.css';

/** Раскладка пятен меняется от экрана к экрану (хендофф, «Мобильные токены»). */
type Scene = 'home' | 'coupon' | 'cart' | 'profile' | 'search' | 'partners' | 'plain';

function sceneOf(pathname: string): Scene {
  const path = pathname.replace(/^\/(ru|uz)/, '') || '/';
  if (path === '/') return 'home';
  if (path.startsWith('/coupons/')) return 'coupon';
  if (path.startsWith('/cart') || path.startsWith('/checkout') || path.startsWith('/payment'))
    return 'cart';
  if (path.startsWith('/profile') || path.startsWith('/favorites')) return 'profile';
  if (path.startsWith('/search')) return 'search';
  if (path.startsWith('/partners')) return 'partners';
  return 'plain';
}

/**
 * Фон мобильных экранов: тёплый белый + размытые пастельные пятна.
 * Живёт на уровне оболочки: при переходах между экранами пятна не пересоздаются,
 * а переезжают (в онбординге по хендоффу фон трогать нельзя).
 */
export default function MobileBackdrop() {
  const isDesktop = useIsDesktop();
  const { pathname } = useLocation();

  if (isDesktop) return null;

  return (
    <div className={`mbd mbd--${sceneOf(pathname)}`} aria-hidden="true">
      <span className="mbd__blob mbd__blob--1" />
      <span className="mbd__blob mbd__blob--2" />
      <span className="mbd__blob mbd__blob--3" />
      <span className="mbd__blob mbd__blob--4" />
    </div>
  );
}
