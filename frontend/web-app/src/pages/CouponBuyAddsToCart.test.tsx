// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CouponOffer } from '../api/coupons';
import CouponDetailDesktop from './CouponDetailDesktop';
import CouponMobile from './CouponMobile';

const { addToCart, isFavorite, navigate, toggleFavorite, translate } = vi.hoisted(() => ({
  addToCart: vi.fn(),
  isFavorite: vi.fn(() => false),
  navigate: vi.fn(),
  toggleFavorite: vi.fn(),
  translate: (key: string) => key,
}));

vi.mock('react-i18next', () => ({
  initReactI18next: {
    type: '3rdParty',
    init: vi.fn(),
  },
  useTranslation: () => ({
    t: translate,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigate,
  useParams: () => ({ id: '42' }),
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
}));

vi.mock('../hooks/useLocalePath', () => ({
  useLocalePath: () => (path: string) => `/ru${path}`,
}));

vi.mock('../store/cartStore', () => ({
  useCartStore: () => ({ addToCart }),
}));

vi.mock('../store/favoritesStore', () => ({
  useFavoritesStore: () => ({ toggleFavorite, isFavorite }),
}));

// Дочерние блоки купона не относятся к задаче (T5: «Купить» → корзина без
// прыжка на checkout) — заглушаем их, чтобы тест был сфокусирован и не падал
// на несвязанной вёрстке/данных.
vi.mock('../components/ui/Breadcrumbs', () => ({ default: () => null }));
vi.mock('../components/coupon/CouponCard', () => ({ default: () => null }));
vi.mock('../components/coupon-detail/CouponGallery', () => ({ default: () => null }));
vi.mock('../components/coupon-detail/OptionPicker', () => ({ default: () => null }));
vi.mock('../components/coupon-detail/ReviewsBlock', () => ({ default: () => null }));
vi.mock('../components/coupon-detail/ShareMenu', () => ({ default: () => null }));
vi.mock('../components/coupon-detail/WhereSection', () => ({ default: () => null }));
vi.mock('../components/ui/DropTabs', () => ({ default: () => null }));
vi.mock('../components/coupon-detail/PurchasePanel', () => ({
  default: ({ onBuy }: { onBuy: () => void }) => (
    <button type="button" onClick={onBuy}>
      couponDetail.buy
    </button>
  ),
}));

const option = {
  id: 501,
  title: 'Стандарт',
  regularPrice: 200000,
  couponPrice: 150000,
  quantitySold: 3,
  status: 'ACTIVE',
};

const coupon: CouponOffer = {
  id: 42,
  title: 'Ужин на двоих',
  offerDescription: 'Романтический ужин на двоих в ресторане «Плов».',
  merchant: {
    id: 7,
    name: 'Ресторан «Плов»',
    primaryLocation: {
      id: 1,
      address: 'Яккасарай, ул. Шота Руставели 21',
    },
  },
  category: { id: 3, name: 'Рестораны', slug: 'restaurants' },
  oldPrice: 200000,
  fromPrice: 150000,
  discountPercent: 25,
  buyUntil: '2099-12-31T00:00:00Z',
  useUntil: '2099-12-31T00:00:00Z',
  giftAvailable: false,
  status: 'ACTIVE',
  totalSold: 10,
  viewCount: 100,
  averageRating: 4.5,
  reviewCount: 2,
  options: [option],
  images: [],
  createdAt: '2026-01-01T00:00:00Z',
};

vi.mock('@tanstack/react-query', async (importOriginal) => ({
  ...await importOriginal<typeof import('@tanstack/react-query')>(),
  useQuery: (opts: { queryKey: unknown[] }) => {
    const key = opts.queryKey[0];
    if (key === 'coupon') return { data: coupon, isLoading: false, isError: false };
    if (key === 'related-coupons') return { data: [], isLoading: false, isError: false };
    if (key === 'coupon-reviews') return { data: undefined, isLoading: false, isError: false };
    return { data: undefined, isLoading: false, isError: false };
  },
}));

const expectedCartItem = {
  couponOfferId: 42,
  couponOptionId: 501,
  couponTitle: 'Ужин на двоих',
  optionTitle: 'Стандарт',
  unitPrice: 150000,
  oldPrice: 200000,
  quantity: 1,
  coverImageUrl: undefined,
};

const variants = [
  { name: 'desktop', Component: CouponDetailDesktop, buyName: 'couponDetail.buy' },
  { name: 'mobile', Component: CouponMobile, buyName: 'couponDetail.buy' },
] as const;

describe.each(variants)('$name: «Купить» кладёт купон в корзину', ({ Component, buyName }) => {
  beforeEach(() => {
    addToCart.mockReset();
    navigate.mockReset();
  });

  afterEach(cleanup);

  it('calls addToCart and does NOT navigate to /checkout', () => {
    render(<Component />);

    const buyButton = screen.getByRole('button', { name: buyName });
    fireEvent.click(buyButton);

    expect(addToCart).toHaveBeenCalledTimes(1);
    expect(addToCart).toHaveBeenCalledWith(expectedCartItem);
    expect(navigate).not.toHaveBeenCalled();
  });
});
