import { useEffect, useState } from 'react';

/**
 * Брейкпоинт редизайна (design_handoff_sizbiz/README.md → «Архитектура»):
 * ≥768px — десктопный дизайн, ниже — мобильный (отдельный, Этап 2).
 */
const DESKTOP_QUERY = '(min-width: 768px)';

export function useIsDesktop(): boolean {
  const [isDesktop, setIsDesktop] = useState(() => window.matchMedia(DESKTOP_QUERY).matches);

  useEffect(() => {
    const mql = window.matchMedia(DESKTOP_QUERY);
    const onChange = (e: MediaQueryListEvent) => setIsDesktop(e.matches);
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, []);

  return isDesktop;
}
