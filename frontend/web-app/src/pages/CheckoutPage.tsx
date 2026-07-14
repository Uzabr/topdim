import { useIsDesktop } from '../hooks/useIsDesktop';
import CheckoutDesktop from './CheckoutDesktop';
import CheckoutMobile from './CheckoutMobile';

/** Оформление — два разных экрана (см. design_handoff_sizbiz/README.md). */
export default function CheckoutPage() {
  return useIsDesktop() ? <CheckoutDesktop /> : <CheckoutMobile />;
}
