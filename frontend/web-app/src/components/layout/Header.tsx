import { useLocation } from 'react-router-dom';
import { useIsDesktop } from '../../hooks/useIsDesktop';
import { hasOwnHeader } from '../../utils/mobileScreens';
import HeaderDesktop from './HeaderDesktop';
import HeaderMobile from './HeaderMobile';

/**
 * Один сайт — два дизайна (design_handoff_sizbiz/README.md → «Архитектура»).
 * Рендерим ровно одну шапку, а не прячем вторую через CSS: иначе в DOM были бы
 * дубли id и целей для анимаций-«воронок» (шаг 6).
 */
export default function Header() {
  const isDesktop = useIsDesktop();
  const { pathname } = useLocation();

  if (isDesktop) return <HeaderDesktop />;
  // Купон, корзина, оплата, профиль, избранное, поиск — со своим навбаром.
  if (hasOwnHeader(pathname)) return null;
  return <HeaderMobile />;
}
