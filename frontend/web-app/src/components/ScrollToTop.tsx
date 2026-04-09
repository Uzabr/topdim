import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Плавный скролл вверх при смене маршрута.
 */
export default function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [pathname]);

  return null;
}
