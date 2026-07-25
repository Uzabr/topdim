// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { OrderResponse, PurchasedCoupon } from '../api/orders';
import type { ReviewStatus } from '../api/reviews';
import ProfileMobile from './ProfileMobile';

const navigateMock = vi.hoisted(() => vi.fn());
const getOrdersMock = vi.hoisted(() => vi.fn());

const queryState = vi.hoisted(() => ({
  coupons: [] as PurchasedCoupon[],
  complaints: [] as Array<{ purchasedCouponId: number; status: string }>,
  reviews: [] as Array<{ couponOfferId: number; status: ReviewStatus }>,
  couponsLoading: false,
  couponsError: false,
  complaintsLoading: false,
  complaintsError: false,
  reviewsLoading: false,
  reviewsError: false,
  refetchCoupons: vi.fn(),
  refetchComplaints: vi.fn(),
  refetchReviews: vi.fn(),
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

vi.mock('@tanstack/react-query', async (importOriginal) => ({
  ...await importOriginal<typeof import('@tanstack/react-query')>(),
  useQuery: ({ queryKey }: { queryKey: Array<string | number | boolean> }) => {
    if (queryKey[0] === 'my-coupons') {
      return {
        data: queryState.coupons,
        isLoading: queryState.couponsLoading,
        isError: queryState.couponsError,
        refetch: queryState.refetchCoupons,
      };
    }
    if (queryKey[0] === 'my-complaints') {
      return {
        data: queryState.complaints,
        isLoading: queryState.complaintsLoading,
        isError: queryState.complaintsError,
        refetch: queryState.refetchComplaints,
      };
    }
    if (queryKey[0] === 'my-reviews') {
      return {
        data: queryState.reviews,
        isLoading: queryState.reviewsLoading,
        isError: queryState.reviewsError,
        refetch: queryState.refetchReviews,
      };
    }
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

describe('ProfileMobile coupon actions', () => {
  afterEach(cleanup);

  beforeEach(() => {
    navigateMock.mockReset();
    getOrdersMock.mockReset();
    queryState.refetchOrders.mockReset();
    queryState.fetchNextPageOrders.mockReset();
    queryState.refetchCoupons.mockReset();
    queryState.refetchComplaints.mockReset();
    queryState.refetchReviews.mockReset();
    queryState.orderPages = [orderPage([], 0, true)];
    queryState.ordersLoading = false;
    queryState.ordersError = false;
    queryState.ordersLoadingError = false;
    queryState.ordersFetchNextPageError = false;
    queryState.ordersFetchingNextPage = false;
    queryState.couponsLoading = false;
    queryState.couponsError = false;
    queryState.complaintsLoading = false;
    queryState.complaintsError = false;
    queryState.reviewsLoading = false;
    queryState.reviewsError = false;
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
    queryState.reviews = [{ couponOfferId: 40, status: 'APPROVED' }];
  });

  it('shows policy-allowed actions on each live coupon', () => {
    render(<ProfileMobile />);

    const active = couponActions('Active dinner', '.pticket');
    expect(active.getByRole('button', { name: 'profile.refundMoney' })).toBeTruthy();
    expect(active.queryByRole('button', { name: 'profile.complain' })).toBeNull();
    expect(active.getByText('profile.complaintPending')).toBeTruthy();

    const refundPending = couponActions('Pending refund', '.prow');
    expect(refundPending.queryByRole('button', { name: 'profile.refundShort' })).toBeNull();
    expect(refundPending.getByRole('button', { name: 'profile.complain' })).toBeTruthy();
    expect(refundPending.queryByText('profile.complaintPending')).toBeNull();
  });

  it('shows policy-allowed review and complaint controls on each archived coupon', () => {
    render(<ProfileMobile />);

    const unreviewed = couponActions('Used without review', '.parc');
    expect(unreviewed.getByRole('button', { name: 'profile.archive.leaveReview' })).toBeTruthy();
    expect(unreviewed.getByRole('button', { name: 'profile.complain' })).toBeTruthy();

    const reviewed = couponActions('Used with review', '.parc');
    expect(reviewed.queryByRole('button', { name: 'profile.archive.leaveReview' })).toBeNull();
    expect(reviewed.getByRole('button', { name: 'profile.complain' })).toBeTruthy();

    const cancelled = couponActions('Cancelled with complaint', '.parc');
    expect(cancelled.queryByRole('button', { name: 'profile.archive.leaveReview' })).toBeNull();
    expect(cancelled.queryByRole('button', { name: 'profile.complain' })).toBeNull();
    expect(cancelled.getByText('profile.complaintPending')).toBeTruthy();
  });

  it('withholds coupon actions while complaint or review policy is still loading', () => {
    queryState.reviewsLoading = true;

    render(<ProfileMobile />);

    expect(screen.getByText('profile.loadingCoupons')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'profile.refundMoney' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'profile.complain' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'profile.archive.leaveReview' })).toBeNull();
  });

  it.each([
    ['coupon', 'couponsError', 'refetchCoupons'],
    ['complaint', 'complaintsError', 'refetchComplaints'],
    ['review', 'reviewsError', 'refetchReviews'],
  ] as const)(
    'renders a retryable %s policy error instead of an empty success state',
    (_name, errorKey, refetchKey) => {
      queryState[errorKey] = true;

      render(<ProfileMobile />);
      fireEvent.click(screen.getByRole('button', { name: 'profile.couponPolicy.retry' }));

      expect(screen.getByRole('alert').textContent).toContain('profile.couponPolicy.error');
      expect(queryState[refetchKey]).toHaveBeenCalledOnce();
      expect(screen.queryByText('profile.emptyCoupons.active.title')).toBeNull();
      expect(screen.queryByRole('button', { name: 'profile.refundMoney' })).toBeNull();
    },
  );

  it('uses open complaints and blocking reviews found beyond the first 100 records', () => {
    queryState.complaints = [
      ...Array.from({ length: 100 }, (_, index) => ({
        purchasedCouponId: 1000 + index,
        status: 'RESOLVED',
      })),
      { purchasedCouponId: 1, status: 'PENDING' },
    ];
    queryState.reviews = [
      ...Array.from({ length: 100 }, (_, index) => ({
        couponOfferId: 1000 + index,
        status: 'REJECTED' as const,
      })),
      { couponOfferId: 30, status: 'APPROVED' },
    ];

    render(<ProfileMobile />);

    expect(couponActions('Active dinner', '.pticket')
      .queryByRole('button', { name: 'profile.complain' })).toBeNull();
    expect(couponActions('Used without review', '.parc')
      .queryByRole('button', { name: 'profile.archive.leaveReview' })).toBeNull();
  });

  it('allows a rejected review to be resubmitted but blocks pending and approved reviews', () => {
    queryState.reviews = [
      { couponOfferId: 30, status: 'REJECTED' },
      { couponOfferId: 40, status: 'PENDING' },
    ];

    render(<ProfileMobile />);

    expect(couponActions('Used without review', '.parc')
      .getByRole('button', { name: 'profile.archive.leaveReview' })).toBeTruthy();
    expect(couponActions('Used with review', '.parc')
      .queryByRole('button', { name: 'profile.archive.leaveReview' })).toBeNull();
  });

  it('sorts an active coupon without expiry after dated active coupons', () => {
    queryState.coupons = [
      {
        ...activeCoupon,
        id: 6,
        couponTitle: 'No expiry coupon',
        expiresAt: undefined,
      },
      {
        ...activeCoupon,
        id: 7,
        couponTitle: 'Dated coupon',
        expiresAt: '2026-07-30T10:00:00Z',
      },
    ];

    render(<ProfileMobile />);

    expect(screen.getByText('Dated coupon').closest('.pticket')).not.toBeNull();
    expect(screen.getByText('No expiry coupon').closest('.prow')).not.toBeNull();
  });
});

describe('ProfileMobile order history', () => {
  afterEach(cleanup);

  beforeEach(() => {
    navigateMock.mockReset();
    getOrdersMock.mockReset();
    queryState.refetchOrders.mockReset();
    queryState.fetchNextPageOrders.mockReset();
    queryState.refetchCoupons.mockReset();
    queryState.refetchComplaints.mockReset();
    queryState.refetchReviews.mockReset();
    queryState.coupons = [];
    queryState.complaints = [];
    queryState.reviews = [];
    queryState.orderPages = [orderPage([], 0, true)];
    queryState.ordersLoading = false;
    queryState.ordersError = false;
    queryState.ordersLoadingError = false;
    queryState.ordersFetchNextPageError = false;
    queryState.ordersFetchingNextPage = false;
    queryState.couponsLoading = false;
    queryState.couponsError = false;
    queryState.complaintsLoading = false;
    queryState.complaintsError = false;
    queryState.reviewsLoading = false;
    queryState.reviewsError = false;
    queryState.locationSearch = '?tab=orders';
  });

  it('shows loading without claiming that order history is empty', () => {
    queryState.ordersLoading = true;

    render(<ProfileMobile />);

    expect(screen.getByText('profile.orders.loading')).toBeTruthy();
    expect(screen.queryByText('profile.orders.empty')).toBeNull();
  });

  it('shows an order loading failure and retries it', () => {
    queryState.ordersError = true;
    queryState.ordersLoadingError = true;

    render(<ProfileMobile />);
    fireEvent.click(screen.getByRole('button', { name: 'profile.orders.retry' }));

    expect(screen.getByText('profile.orders.error')).toBeTruthy();
    expect(queryState.refetchOrders).toHaveBeenCalledOnce();
    expect(screen.queryByText('profile.orders.empty')).toBeNull();
  });

  it('shows the empty state only after an empty successful response', () => {
    render(<ProfileMobile />);

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

    render(<ProfileMobile />);
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

    render(<ProfileMobile />);
    fireEvent.click(screen.getByRole('button', { name: 'profile.orders.loadMore' }));

    expect(getOrdersMock).toHaveBeenCalledWith(1, 20);
  });

  it('renders records aggregated from page 0 and page 1', () => {
    queryState.orderPages = [
      orderPage([{ ...pendingOrder, id: 201, title: 'Page zero order' }], 0, false),
      orderPage([{ ...pendingOrder, id: 202, title: 'Page one order' }], 1, true),
    ];

    render(<ProfileMobile />);

    expect(screen.getByText('Page zero order')).toBeTruthy();
    expect(screen.getByText('Page one order')).toBeTruthy();
  });

  it('keeps page 0 visible and retries only the failed next page', () => {
    queryState.orderPages = [
      orderPage([{ ...pendingOrder, id: 201, title: 'Page zero order' }], 0, false),
    ];
    queryState.ordersError = true;
    queryState.ordersFetchNextPageError = true;

    render(<ProfileMobile />);

    expect(screen.getByText('Page zero order')).toBeTruthy();
    expect(screen.getByText('profile.orders.loadMoreError')).toBeTruthy();
    expect(screen.queryByText('profile.orders.error')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'profile.orders.retry' }));

    expect(queryState.fetchNextPageOrders).toHaveBeenCalledOnce();
    expect(queryState.refetchOrders).not.toHaveBeenCalled();
  });

  it('disables and relabels the next-page retry while it is fetching', () => {
    queryState.orderPages = [
      orderPage([{ ...pendingOrder, id: 201, title: 'Page zero order' }], 0, false),
    ];
    queryState.ordersFetchNextPageError = true;
    queryState.ordersFetchingNextPage = true;

    render(<ProfileMobile />);

    const retry = screen.getByRole('button', { name: 'profile.orders.loading' });
    expect((retry as HTMLButtonElement).disabled).toBe(true);
  });
});
