import { useIsDesktop } from '../hooks/useIsDesktop';
import ProfileDesktop from './ProfileDesktop';
import ProfileMobile from './ProfileMobile';

/** Профиль — два разных экрана (см. design_handoff_sizbiz/README.md). */
export default function ProfilePage() {
  return useIsDesktop() ? <ProfileDesktop /> : <ProfileMobile />;
}
