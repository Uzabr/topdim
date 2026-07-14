import { useIsDesktop } from '../hooks/useIsDesktop';
import CouponDetailDesktop from './CouponDetailDesktop';
import CouponMobile from './CouponMobile';

/**
 * Купон — два разных экрана (design_handoff_sizbiz/README.md → «Архитектура»):
 * на десктопе двухколоночная страница с липкой панелью покупки, на мобиле —
 * свой навбар, варианты лентой и покупка, закреплённая внизу.
 */
export default function CouponDetailPage() {
  return useIsDesktop() ? <CouponDetailDesktop /> : <CouponMobile />;
}
