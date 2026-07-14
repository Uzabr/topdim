import { useIsDesktop } from '../../hooks/useIsDesktop';
import HeaderDesktop from './HeaderDesktop';
import HeaderMobile from './HeaderMobile';

/**
 * Один сайт — два дизайна (design_handoff_sizbiz/README.md → «Архитектура»).
 * Рендерим ровно одну шапку, а не прячем вторую через CSS: иначе в DOM были бы
 * дубли id и целей для анимаций-«воронок» (шаг 6).
 */
export default function Header() {
  return useIsDesktop() ? <HeaderDesktop /> : <HeaderMobile />;
}
