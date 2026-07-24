// @vitest-environment jsdom
import { QueryClientProvider, useQuery } from '@tanstack/react-query';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../api/auth';
import { queryClient } from '../queryClient';
import { profileQueryKeys } from '../queries/profileQueries';
import { useAuthStore } from './authStore';

const { resetFavorites, syncFavorites, setCartMode, syncCart, fetchCart } = vi.hoisted(() => ({
  resetFavorites: vi.fn(),
  syncFavorites: vi.fn().mockResolvedValue(undefined),
  setCartMode: vi.fn(),
  syncCart: vi.fn(),
  fetchCart: vi.fn(),
}));

vi.mock('../api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn().mockResolvedValue(undefined),
    getMe: vi.fn().mockRejectedValue(new Error('profile refresh disabled in isolation test')),
  },
}));

vi.mock('./cartStore', () => ({
  useCartStore: {
    getState: () => ({
      setMode: setCartMode,
      syncLocalCartToBackend: syncCart,
      fetchBackendCart: fetchCart,
    }),
  },
}));

vi.mock('./favoritesStore', () => ({
  useFavoritesStore: {
    getState: () => ({
      reset: resetFavorites,
      syncWithBackend: syncFavorites,
    }),
  },
}));

const accountA = {
  id: 1,
  email: 'a@example.com',
  firstName: 'Account A',
  role: 'USER',
};

const accountB = {
  id: 2,
  email: 'b@example.com',
  firstName: 'Account B',
  role: 'USER',
};

const privateCouponFetch = vi.fn(async () => {
  const userId = useAuthStore.getState().user?.id;
  return userId === accountA.id ? 'A private coupon' : 'B private coupon';
});

function PrivateCouponHarness() {
  const user = useAuthStore((state) => state.user);
  const result = useQuery({
    queryKey: profileQueryKeys.coupons(user?.id ?? 0),
    queryFn: privateCouponFetch,
    enabled: user != null,
  });
  return <div>{result.data ?? 'signed out'}</div>;
}

describe('account session isolation', () => {
  afterEach(cleanup);

  beforeEach(() => {
    localStorage.clear();
    queryClient.clear();
    privateCouponFetch.mockClear();
    resetFavorites.mockClear();
    syncFavorites.mockClear();
    setCartMode.mockClear();
    syncCart.mockClear();
    vi.mocked(authApi.login).mockReset();
    vi.mocked(authApi.logout).mockClear();
    useAuthStore.setState({ user: null, isAuthenticated: false, isLoading: false });
  });

  it('never renders account A cached data after logout and login as account B', async () => {
    vi.mocked(authApi.login)
      .mockResolvedValueOnce({
        data: {
          data: { accessToken: 'token-a', user: accountA },
        },
      } as Awaited<ReturnType<typeof authApi.login>>)
      .mockResolvedValueOnce({
        data: {
          data: { accessToken: 'token-b', user: accountB },
        },
      } as Awaited<ReturnType<typeof authApi.login>>);

    render(
      <QueryClientProvider client={queryClient}>
        <PrivateCouponHarness />
      </QueryClientProvider>,
    );

    await act(async () => {
      await useAuthStore.getState().login({ email: accountA.email, password: 'Strong1!' });
    });
    expect(await screen.findByText('A private coupon')).toBeTruthy();
    expect(queryClient.getQueryData(profileQueryKeys.coupons(accountA.id)))
      .toBe('A private coupon');
    queryClient.getMutationCache().build(queryClient, {
      mutationKey: ['account-a-private-mutation'],
      mutationFn: async () => undefined,
    });

    act(() => useAuthStore.getState().logout());

    await waitFor(() => expect(screen.getByText('signed out')).toBeTruthy());
    expect(queryClient.getQueryData(profileQueryKeys.coupons(accountA.id))).toBeUndefined();
    expect(queryClient.getMutationCache().getAll()).toHaveLength(0);
    expect(resetFavorites).toHaveBeenCalledOnce();

    await act(async () => {
      await useAuthStore.getState().login({ email: accountB.email, password: 'Strong1!' });
    });

    expect(await screen.findByText('B private coupon')).toBeTruthy();
    expect(screen.queryByText('A private coupon')).toBeNull();
    expect(privateCouponFetch).toHaveBeenCalledTimes(2);
  });
});
