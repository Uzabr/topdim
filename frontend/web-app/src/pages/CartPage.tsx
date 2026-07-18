import { useIsDesktop } from '../hooks/useIsDesktop';
import CartDesktop from './CartDesktop';
import CartMobile from './CartMobile';

/** Корзина — два разных экрана (см. design_handoff_sizbiz/README.md). */
export default function CartPage() {
  return useIsDesktop() ? <CartDesktop /> : <CartMobile />;
}
