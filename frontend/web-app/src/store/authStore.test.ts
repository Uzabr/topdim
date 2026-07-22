import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../api/auth';
import { useAuthStore } from './authStore';

const { syncFavorites, syncCart, setCartMode, fetchCart } = vi.hoisted(() => ({
  syncFavorites: vi.fn().mockResolvedValue(undefined),
  syncCart: vi.fn(),
  setCartMode: vi.fn(),
  fetchCart: vi.fn(),
}));

vi.mock('../api/auth', () => ({
  authApi: {
    login: vi.fn(),
  },
}));

vi.mock('./cartStore', () => ({
  useCartStore: {
    getState: () => ({
      syncLocalCartToBackend: syncCart,
      setMode: setCartMode,
      fetchBackendCart: fetchCart,
    }),
  },
}));

vi.mock('./favoritesStore', () => ({
  useFavoritesStore: {
    getState: () => ({ syncWithBackend: syncFavorites }),
  },
}));

const user = {
  id: 1,
  email: 'user@example.com',
  firstName: 'Ada',
  role: 'USER',
};

describe('authStore favorites hydration', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({ user: null, isAuthenticated: false, isLoading: false });
  });

  it('starts favorites synchronization after successful login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      data: {
        success: true,
        data: { accessToken: 'token', tokenType: 'Bearer', expiresIn: 900, user },
        timestamp: '2026-07-22T10:00:00',
      },
    } as Awaited<ReturnType<typeof authApi.login>>);

    await useAuthStore.getState().login({ email: user.email, password: 'Strong1!' });

    expect(localStorage.getItem('accessToken')).toBe('token');
    expect(syncFavorites).toHaveBeenCalledOnce();
  });

  it('starts favorites synchronization when a stored authenticated session is restored', () => {
    localStorage.setItem('accessToken', 'stored-token');
    localStorage.setItem('user', JSON.stringify(user));

    useAuthStore.getState().loadFromStorage();

    expect(useAuthStore.getState().isAuthenticated).toBe(true);
    expect(syncFavorites).toHaveBeenCalledOnce();
  });
});
