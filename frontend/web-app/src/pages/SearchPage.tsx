import { useIsDesktop } from '../hooks/useIsDesktop';
import SearchDesktop from './SearchDesktop';
import SearchMobile from './SearchMobile';

/** Поиск — два разных экрана (см. design_handoff_sizbiz/README.md). */
export default function SearchPage() {
  return useIsDesktop() ? <SearchDesktop /> : <SearchMobile />;
}
