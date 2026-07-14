import { useLocation } from 'react-router-dom';
import { useIsDesktop } from '../../hooks/useIsDesktop';
import HeaderDesktop from './HeaderDesktop';
import HeaderMobile from './HeaderMobile';

/** Экраны, у которых в мобильном макете своя навигация вместо общей шапки. */
const OWN_HEADER = [/^\/(ru|uz)\/coupons\/\d+/];

/**
 * Один сайт — два дизайна (design_handoff_sizbiz/README.md → «Архитектура»).
 * Рендерим ровно одну шапку, а не прячем вторую через CSS: иначе в DOM были бы
 * дубли id и целей для анимаций-«воронок» (шаг 6).
 */
export default function Header() {
  const isDesktop = useIsDesktop();
  const { pathname } = useLocation();

  if (isDesktop) return <HeaderDesktop />;
  if (OWN_HEADER.some((route) => route.test(pathname))) return null;
  return <HeaderMobile />;
}
