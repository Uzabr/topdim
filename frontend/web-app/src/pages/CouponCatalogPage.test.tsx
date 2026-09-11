// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import type { CouponOffer } from '../api/coupons';
import { couponsApi } from '../api/coupons';
import CouponCatalogPage from './CouponCatalogPage';

const desktop = { current: false };

vi.mock('../hooks/useIsDesktop', () => ({
  useIsDesktop: () => desktop.current,
}));

vi.mock('../utils/withMinDelay', () => ({
  INFINITE_SCROLL_LOADER_MS: 500,
  withMinDelay: <T,>(promise: Promise<T>) => promise,
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('../components/coupon/CouponCard', () => ({
  default: ({ coupon }: { coupon: { id: number; title: string } }) => (
    <article data-testid={`coupon-${coupon.id}`}>{coupon.title}</article>
  ),
}));

vi.mock('../api/coupons', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/coupons')>();
  return {
    ...actual,
    couponsApi: {
      ...actual.couponsApi,
      getCatalog: vi.fn(),
      getCategories: vi.fn(),
    },
  };
});

type ObserverInstance = {
  callback: IntersectionObserverCallback;
  observe: ReturnType<typeof vi.fn>;
  disconnect: ReturnType<typeof vi.fn>;
};

let latestObserver: ObserverInstance | null = null;

function offer(id: number): CouponOffer {
  return {
    id,
    title: `Coupon ${id}`,
    merchant: { id: 1, name: 'Shop' },
    category: { id: 1, name: 'Food', slug: 'food' },
    fromPrice: 10_000,
    buyUntil: '2026-12-31',
    useUntil: '2026-12-31',
    giftAvailable: false,
    status: 'ACTIVE',
    totalSold: 0,
    viewCount: 0,
    options: [],
    images: [],
    createdAt: '2026-01-01T00:00:00Z',
  };
}

function catalogResponse(
  content: CouponOffer[],
  opts: { number?: number; last?: boolean; totalElements?: number } = {},
) {
  const number = opts.number ?? 0;
  const last = opts.last ?? true;
  return {
    data: {
      success: true,
      data: {
        content,
        totalElements: opts.totalElements ?? (last ? content.length : content.length + 20),
        totalPages: last ? number + 1 : number + 2,
        size: 20,
        number,
        last,
      },
      timestamp: '2026-01-01T00:00:00Z',
    },
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function renderCatalog() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <CouponCatalogPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function intersectSentinel() {
  act(() => {
    latestObserver?.callback(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      latestObserver as unknown as IntersectionObserver,
    );
  });
}

describe('CouponCatalogPage infinite scroll (mobile)', () => {
  beforeEach(() => {
    desktop.current = false;
    latestObserver = null;
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        observe = vi.fn();
        disconnect = vi.fn();
        unobserve = vi.fn();
        constructor(callback: IntersectionObserverCallback) {
          latestObserver = {
            callback,
            observe: this.observe,
            disconnect: this.disconnect,
          };
        }
      },
    );
    vi.mocked(couponsApi.getCategories).mockResolvedValue({
      data: { success: true, data: [], timestamp: '2026-01-01T00:00:00Z' },
    } as never);
    vi.mocked(couponsApi.getCatalog).mockReset();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('does not show page buttons on the phone and appends the next page on scroll', async () => {
    vi.mocked(couponsApi.getCatalog).mockImplementation(async (params) => {
      const page = params?.page ?? 0;
      if (page === 0) {
        return catalogResponse([offer(1)], { last: false, totalElements: 2 }) as never;
      }
      return catalogResponse([offer(2)], { number: 1, last: true, totalElements: 2 }) as never;
    });

    renderCatalog();

    expect(await screen.findByTestId('coupon-1')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /common.back/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /common.next/ })).toBeNull();
    expect(screen.queryByTestId('coupon-2')).toBeNull();

    intersectSentinel();

    expect(await screen.findByTestId('coupon-2')).toBeTruthy();
    expect(screen.getByTestId('coupon-1')).toBeTruthy();
    expect(couponsApi.getCatalog).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1, size: 20 }),
    );
  });

  it('shows a loader while the next page is in flight', async () => {
    const nextPage = deferred<ReturnType<typeof catalogResponse>>();
    vi.mocked(couponsApi.getCatalog).mockImplementation(async (params) => {
      const page = params?.page ?? 0;
      if (page === 0) {
        return catalogResponse([offer(1)], { last: false, totalElements: 2 }) as never;
      }
      return nextPage.promise as never;
    });

    renderCatalog();
    expect(await screen.findByTestId('coupon-1')).toBeTruthy();

    intersectSentinel();

    expect(await screen.findByRole('status')).toBeTruthy();
    expect(screen.getByText('catalog.loadingMore')).toBeTruthy();

    nextPage.resolve(catalogResponse([offer(2)], { number: 1, last: true, totalElements: 2 }));

    expect(await screen.findByTestId('coupon-2')).toBeTruthy();
    await waitFor(() => {
      expect(screen.queryByRole('status')).toBeNull();
    });
  });

  it('lets the user retry when the next page fails', async () => {
    vi.mocked(couponsApi.getCatalog)
      .mockResolvedValueOnce(
        catalogResponse([offer(1)], { last: false, totalElements: 2 }) as never,
      )
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(
        catalogResponse([offer(2)], { number: 1, last: true, totalElements: 2 }) as never,
      );

    renderCatalog();
    expect(await screen.findByTestId('coupon-1')).toBeTruthy();

    intersectSentinel();

    expect(await screen.findByText('catalog.loadMoreError')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'catalog.retry' }));

    expect(await screen.findByTestId('coupon-2')).toBeTruthy();
  });
});

describe('CouponCatalogPage pagination (desktop)', () => {
  beforeEach(() => {
    desktop.current = true;
    vi.mocked(couponsApi.getCategories).mockResolvedValue({
      data: { success: true, data: [], timestamp: '2026-01-01T00:00:00Z' },
    } as never);
    vi.mocked(couponsApi.getCatalog).mockReset();
  });

  afterEach(cleanup);

  it('keeps page buttons and replaces the list instead of appending', async () => {
    vi.mocked(couponsApi.getCatalog).mockImplementation(async (params) => {
      const page = params?.page ?? 0;
      if (page === 0) {
        return catalogResponse([offer(1)], { last: false, totalElements: 2 }) as never;
      }
      return catalogResponse([offer(2)], { number: 1, last: true, totalElements: 2 }) as never;
    });

    renderCatalog();

    expect(await screen.findByTestId('coupon-1')).toBeTruthy();
    expect(screen.getByRole('button', { name: /common.next/ })).toBeTruthy();
    expect(screen.queryByTestId('catalog-scroll-sentinel')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /common.next/ }));

    expect(await screen.findByTestId('coupon-2')).toBeTruthy();
    expect(screen.queryByTestId('coupon-1')).toBeNull();
  });
});
