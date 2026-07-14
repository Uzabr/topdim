import { useIsDesktop } from '../hooks/useIsDesktop';
import HomeDesktop from './HomeDesktop';
import HomeMobile from './HomeMobile';

/**
 * Главная — два разных дизайна (design_handoff_sizbiz/README.md → «Архитектура»),
 * а не один макет с адаптивом. Рендерим ровно один: разметка, данные и анимации
 * у них не совпадают.
 */
export default function HomePage() {
  return useIsDesktop() ? <HomeDesktop /> : <HomeMobile />;
}
