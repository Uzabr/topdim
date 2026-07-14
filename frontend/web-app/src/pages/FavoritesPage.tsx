import { useIsDesktop } from '../hooks/useIsDesktop';
import FavoritesDesktop from './FavoritesDesktop';
import FavoritesMobile from './FavoritesMobile';

/** Избранное — два разных экрана (см. design_handoff_sizbiz/README.md). */
export default function FavoritesPage() {
  return useIsDesktop() ? <FavoritesDesktop /> : <FavoritesMobile />;
}
