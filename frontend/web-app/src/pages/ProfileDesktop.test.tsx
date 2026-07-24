// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { OrderResponse, PurchasedCoupon } from '../api/orders';
import ProfileDesktop from './ProfileDesktop';

const navigateMock = vi.hoisted(() => vi.fn());
const getOrdersMock = vi.hoisted(() => vi.fn());

const queryState = vi.hoisted(() => ({
  coupons: [] as PurchasedCoupon[],
  complaints: [] as Array<{ purchasedCouponId: number; status: string }>,
  reviews: [] as Array<{ couponOfferId: number }>,
  orderPages: [] as Array<{
    data: {
      data: {
        content: OrderResponse[];
        totalElements: number;
        totalPages: number;
        size: number;
        number: number;
        last: boolean;
      };
    };
  }>,
  ordersLoading: false,
  ordersError: false,
  ordersLoadingError: false,
  ordersFetchNextPageError: false,
  ordersFetchingNextPage: false,
  refetchOrders: vi.fn(),
  fetchNextPageOrders: vi.fn(),
  locationSearch: '',
}));

vi.mock('../api/orders', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/orders')>();
  return {
    ...actual,
    ordersApi: {
      ...actual.ordersApi,
      getOrders: getOrdersMock,
    },
  };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('@tanstack/react-query', () => ({
  useQuery: ({ queryKey }: { queryKey: string[] }) => {
    if (queryKey[0] === 'my-coupons') return { data: queryState.coupons, isLoading: false };
    if (queryKey[0] === 'my-complaints') return { data: queryState.complaints };
    if (queryKey[0] === 'my-reviews') return { data: queryState.reviews };
    return { data: [] };
  },
  useInfiniteQuery: (options: {
    queryFn: ({ pageParam }: { pageParam: number }) => unknown;
    select?: (data: { pages: typeof queryState.orderPages; pageParams: number[] }) => unknown;
    getNextPageParam: (
      lastPage: (typeof queryState.orderPages)[number],
      pages: typeof queryState.orderPages,
    ) => number | undefined;
  }) => {
    const lastPage = queryState.orderPages.at(-1);
    const nextPage = lastPage
      ? options.getNextPageParam(lastPage, queryState.orderPages)
      : undefined;
    return {
      data: options.select?.({
        pages: queryState.orderPages,
        pageParams: queryState.orderPages.map((page) => page.data.data.number),
      }),
      isLoading: queryState.ordersLoading,
      isError: queryState.ordersError,
      isLoadingError: queryState.ordersLoadingError,
      isFetchNextPageError: queryState.ordersFetchNextPageError,
      refetch: queryState.refetchOrders,
      fetchNextPage: () => {
        queryState.fetchNextPageOrders();
        return nextPage === undefined ? Promise.resolve() : options.queryFn({ pageParam: nextPage });
      },
      hasNextPage: nextPage !== undefined,
      isFetchingNextPage: queryState.ordersFetchingNextPage,
    };
  },
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: () => ({
    user: { id: 7, email: 'user@example.com', firstName: 'Ada', role: 'USER' },
    logout: vi.fn(),
    isAuthenticated: true,
  }),
}));

vi.mock('react-router-dom', () => ({
  Link: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useLocation: () => ({ pathname: '/ru/profile', search: queryState.locationSearch }),
  useNavigate: () => navigateMock,
}));

vi.mock('../hooks/useLocalePath', () => ({
  useLocalePath: () => (path: string) => `/ru${path}`,
}));

vi.mock('../utils/format', () => ({
  daysUntil: () => 1,
  formatDate: (value: string) => value,
  formatPrice: (value: number) => String(value),
}));

const activeCoupon: PurchasedCoupon = {
  id: 1,
  couponOfferId: 10,
  couponOptionId: 100,
  couponTitle: 'Active dinner',
  optionTitle: 'Dinner for two',
  couponCode: 'ACTIVE-1',
  qrToken: '',
  status: 'ACTIVE',
  purchasedAt: '2026-07-01T10:00:00Z',
  expiresAt: '2026-08-01T10:00:00Z',
};

const pendingOrder: OrderResponse = {
  id: 91,
  orderNumber: 'ORD-91',
  totalAmount: 125000,
  status: 'PENDING',
  userEmail: 'user@example.com',
  userPhone: '+998901234567',
  itemCount: 1,
  createdAt: '2026-07-25T10:00:00Z',
};

function orderPage(
  content: OrderResponse[],
  number = 0,
  last = true,
): (typeof queryState.orderPages)[number] {
  return {
    data: {
      data: {
        content,
        totalElements: content.length,
        totalPages: last ? number + 1 : number + 2,
        size: 20,
        number,
        last,
      },
    },
  };
}

function couponActions(title: string, selector: string) {
  const coupon = screen.getByText(title).closest(selector);
  expect(coupon).not.toBeNull();
  return within(coupon as HTMLElement);
}

describe('ProfileDesktop coupon actions', () => {
  afterEach(cleanup);

  beforeEach(() => {
    navigateMock.mockReset();
    getOrdersMock.mockReset();
    queryState.refetchOrders.mockReset();
    queryState.fetchNextPageOrders.mockReset();
    queryState.orderPages = [orderPage([], 0, true)];
    queryState.ordersLoading = false;
    queryState.ordersError = false;
    queryState.ordersLoadingError = false;
    queryState.ordersFetchNextPageError = false;
    queryState.ordersFetchingNextPage = false;
    queryState.locationSearch = '';
    queryState.coupons = [
      activeCoupon,
      {
        ...activeCoupon,
        id: 2,
        couponOfferId: 20,
        couponTitle: 'Pending refund',
        couponCode: 'PENDING-2',
        status: 'REFUND_PENDING',
        expiresAt: '2026-09-01T10:00:00Z',
      },
      {
        ...activeCoupon,
        id: 3,
        couponOfferId: 30,
        couponTitle: 'Used without review',
        couponCode: 'USED-3',
        status: 'USED',
      },
      {
        ...activeCoupon,
        id: 4,
        couponOfferId: 40,
        couponTitle: 'Used with review',
        couponCode: 'USED-4',
        status: 'USED',
      },
      {
        ...activeCoupon,
        id: 5,
        couponOfferId: 50,
        couponTitle: 'Cancelled with complaint',
        couponCode: 'CANCELLED-5',
        status: 'CANCELLED',
      },
    ];
    queryState.complaints = [
      { purchasedCouponId: 1, status: 'PENDING' },
      { purchasedCouponId: 5, status: 'IN_REVIEW' },
    ];
    queryState.reviews = [{ couponOfferId: 40 }];
  });

  it('shows policy-allowed actions on each live coupon', () => {
    render(<ProfileDesktop />);

    const active = couponActions('Active dinner', '.ticket');
    expect(active.getByRole('button', { name: 'profile.refundMoney' })).toBeTruthy();
    expect(active.queryByRole('button', { name: 'profile.complain' })).toBeNull();
    expect(active.getByText('profile.complaintPending')).toBeTruthy();

    const refundPending = couponActions('Pending refund', '.active-coupon');
    expect(refundPending.queryByRole('button', { name: 'profile.refundShort' })).toBeNull();
    expect(refundPending.getByRole('button', { name: 'profile.complain' })).toBeTruthy();
    expect(refundPending.queryByText('profile.complaintPending')).toBeNull();
  });

  it('shows policy-allowed review and complaint controls on each archived coupon', () => {
    render(<ProfileDesktop />);

    const unreviewed = couponActions('Used without review', '.archive-row');
    expect(unreviewed.getByRole('button', { name: 'profile.archive.leaveReview' })).toBeTruthy();
    expect(unreviewed.getByRole('button', { name: 'profile.complain' })).toBeTruthy();

    const reviewed = couponActions('Used with review', '.archive-row');
    expect(reviewed.queryByRole('button', { name: 'profile.archive.leaveReview' })).toBeNull();
    expect(reviewed.getByRole('button', { name: 'profile.complain' })).toBeTruthy();

    const cancelled = couponActions('Cancelled with complaint', '.archive-row');
    expect(cancelled.queryByRole('button', { name: 'profile.archive.leaveReview' })).toBeNull();
    expect(cancelled.queryByRole('button', { name: 'profile.complain' })).toBeNull();
    expect(cancelled.getByText('profile.complaintPending')).toBeTruthy();
  });
});

describe('ProfileDesktop order history', () => {
  afterEach(cleanup);

  beforeEach(() => {
    navigateMock.mockReset();
    getOrdersMock.mockReset();
    queryState.refetchOrders.mockReset();
    queryState.fetchNextPageOrders.mockReset();
    queryState.coupons = [];
    queryState.complaints = [];
    queryState.reviews = [];
    queryState.orderPages = [orderPage([], 0, true)];
    queryState.ordersLoading = false;
    queryState.ordersError = false;
    queryState.ordersLoadingError = false;
    queryState.ordersFetchNextPageError = false;
    queryState.ordersFetchingNextPage = false;
    queryState.locationSearch = '?tab=orders';
  });

  it('shows loading without claiming that order history is empty', () => {
    queryState.ordersLoading = true;

    render(<ProfileDesktop />);

    expect(screen.getByText('profile.orders.loading')).toBeTruthy();
    expect(screen.queryByText('profile.orders.empty')).toBeNull();
  });

  it('shows an order loading failure and retries it', () => {
    queryState.ordersError = true;
    queryState.ordersLoadingError = true;

    render(<ProfileDesktop />);
    fireEvent.click(screen.getByRole('button', { name: 'profile.orders.retry' }));

    expect(screen.getByText('profile.orders.error')).toBeTruthy();
    expect(queryState.refetchOrders).toHaveBeenCalledOnce();
    expect(screen.queryByText('profile.orders.empty')).toBeNull();
  });

  it('shows the empty state only after an empty successful response', () => {
    render(<ProfileDesktop />);

    expect(screen.getByText('profile.orders.empty')).toBeTruthy();
    expect(screen.queryByText('profile.orders.loading')).toBeNull();
    expect(screen.queryByText('profile.orders.error')).toBeNull();
  });

  it('lets the user continue payment only for a pending order', () => {
    queryState.orderPages = [
      orderPage([
        pendingOrder,
        { ...pendingOrder, id: 92, orderNumber: 'ORD-92', status: 'PAID' },
      ]),
    ];

    render(<ProfileDesktop />);
    const continuePayment = screen.getByRole('button', {
      name: 'profile.orders.continuePayment',
    });
    fireEvent.click(continuePayment);

    expect(screen.getAllByText('profile.orders.continuePayment')).toHaveLength(1);
    expect(navigateMock).toHaveBeenCalledWith('/ru/payment/91');
  });

  it('loads page 1 with 20 records when the first page is not last', () => {
    queryState.orderPages = [orderPage([pendingOrder], 0, false)];
    getOrdersMock.mockResolvedValue(orderPage([], 1, true));

    render(<ProfileDesktop />);
    fireEvent.click(screen.getByRole('button', { name: 'profile.orders.loadMore' }));

    expect(getOrdersMock).toHaveBeenCalledWith(1, 20);
  });

  it('renders records aggregated from page 0 and page 1', () => {
    queryState.orderPages = [
      orderPage([{ ...pendingOrder, id: 201, title: 'Page zero order' }], 0, false),
      orderPage([{ ...pendingOrder, id: 202, title: 'Page one order' }], 1, true),
    ];

    render(<ProfileDesktop />);

    expect(screen.getByText('Page zero order')).toBeTruthy();
    expect(screen.getByText('Page one order')).toBeTruthy();
  });

  it('keeps page 0 visible and retries only the failed next page', () => {
    queryState.orderPages = [
      orderPage([{ ...pendingOrder, id: 201, title: 'Page zero order' }], 0, false),
    ];
    queryState.ordersError = true;
    queryState.ordersFetchNextPageError = true;

    render(<ProfileDesktop />);

    expect(screen.getByText('Page zero order')).toBeTruthy();
    expect(screen.getByText('profile.orders.loadMoreError')).toBeTruthy();
    expect(screen.queryByText('profile.orders.error')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'profile.orders.retry' }));

    expect(queryState.fetchNextPageOrders).toHaveBeenCalledOnce();
    expect(queryState.refetchOrders).not.toHaveBeenCalled();
  });
});
