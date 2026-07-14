import { useIsDesktop } from '../hooks/useIsDesktop';
import PaymentDesktop from './PaymentDesktop';
import PaymentMobile from './PaymentMobile';

/** Оплата и «оплачено» — два разных экрана (см. design_handoff_sizbiz/README.md). */
export default function PaymentPage() {
  return useIsDesktop() ? <PaymentDesktop /> : <PaymentMobile />;
}
