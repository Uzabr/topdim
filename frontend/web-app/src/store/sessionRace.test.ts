// @vitest-environment jsdom
import { waitFor } from '@testing-library/react';
import axios, {
  AxiosError,
  type InternalAxiosRequestConfig,
} from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi, type UserDto, type UserProfileResponse } from '../api/auth';
import apiClient from '../api/client';
import { favoritesApi, type FavoriteItem } from '../api/favorites';
import { ordersApi, type Cart } from '../api/orders';
import {
  advanceSessionGeneration,
  captureSessionGeneration,
  invalidateClientSession,
} from '../sessionCleanup';
import { useAuthStore } from './authStore';
import { useCartStore } from './cartStore';
import { useFavoritesStore } from './favoritesStore';

vi.mock('../api/auth', () => ({
  authApi: {
    login: vi.fn(),
    telegramAuth: vi.fn(),
    register: vi.fn(),
    logout: vi.fn().mockResolvedValue(undefined),
    getMe: vi.fn(),
    updateProfile: vi.fn(),
  },
}));

vi.mock('../api/orders', () => ({
  ordersApi: {
    getCart: vi.fn(),
    addToCart: vi.fn(),
    removeFromCart: vi.fn(),
    updateCartItemQuantity: vi.fn(),
  },
}));

vi.mock('../api/favorites', () => ({
  favoritesApi: {
    getAll: vi.fn(),
    add: vi.fn(),
    remove: vi.fn(),
  },
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((complete, fail) => {
    resolve = complete;
    reject = fail;
  });
  return { promise, resolve, reject };
}

const accountA: UserDto = {
  id: 1,
  email: 'a@example.com',
  firstName: 'Account A login',
  role: 'USER',
};

const accountB: UserDto = {
  id: 2,
  email: 'b@example.com',
  firstName: 'Account B login',
  role: 'USER',
};

function loginResponse(user: UserDto, accessToken: string) {
  return {
    data: {
      success: true,
      data: {
        accessToken,
        tokenType: 'Bearer',
        expiresIn: 900,
        user,
      },
      timestamp: '2026-07-25T10:00:00Z',
    },
  } as Awaited<ReturnType<typeof authApi.login>>;
}

function profileResponse(user: UserDto, firstName: string) {
  const profile: UserProfileResponse = {
    ...user,
    firstName,
    emailVerified: true,
    phoneVerified: false,
  };
  return {
    data: {
      success: true,
      data: profile,
      timestamp: '2026-07-25T10:00:00Z',
    },
  } as Awaited<ReturnType<typeof authApi.getMe>>;
}

function cartResponse(cart: Cart) {
  return {
    data: {
      success: true,
      data: cart,
      timestamp: '2026-07-25T10:00:00Z',
    },
  } as Awaited<ReturnType<typeof ordersApi.getCart>>;
}

function favoritesResponse(couponOfferIds: number[]) {
  const favorites: FavoriteItem[] = couponOfferIds.map(
    (couponOfferId, index) => ({
      id: index + 1,
      couponOfferId,
      createdAt: '2026-07-25T10:00:00Z',
    }),
  );
  return {
    data: {
      success: true,
      data: favorites,
      timestamp: '2026-07-25T10:00:00Z',
    },
  } as Awaited<ReturnType<typeof favoritesApi.getAll>>;
}

function unauthorized(config: InternalAxiosRequestConfig) {
  return new AxiosError(
    'Unauthorized',
    'ERR_BAD_REQUEST',
    config,
    undefined,
    {
      data: {},
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config,
    },
  );
}

const cartA: Cart = {
  id: 101,
  userId: accountA.id,
  items: [{
    id: 1001,
    couponOfferId: 11,
    couponOptionId: 111,
    couponTitle: 'A private cart item',
    optionTitle: 'A option',
    unitPrice: 10000,
    quantity: 1,
    subtotal: 10000,
    gift: false,
  }],
  totalAmount: 10000,
  totalItems: 1,
};

const cartB: Cart = {
  id: 202,
  userId: accountB.id,
  items: [{
    id: 2002,
    couponOfferId: 22,
    couponOptionId: 222,
    couponTitle: 'B private cart item',
    optionTitle: 'B option',
    unitPrice: 20000,
    quantity: 1,
    subtotal: 20000,
    gift: false,
  }],
  totalAmount: 20000,
  totalItems: 1,
};

describe('account-owned async session isolation', () => {
  beforeEach(() => {
    invalidateClientSession();
    localStorage.clear();
    vi.clearAllMocks();
    vi.mocked(authApi.logout).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.logout>>,
    );
    useAuthStore.setState({
      user: null,
      isAuthenticated: false,
      isLoading: false,
    });
    useCartStore.setState({
      mode: 'guest',
      localItems: [],
      backendItems: [],
      backendCartId: null,
      items: [],
      isOpen: false,
      isLoading: false,
      error: null,
      totalItems: 0,
      totalPrice: 0,
    });
    useFavoritesStore.setState({
      favoriteIds: [],
      showLimitModal: false,
      limitMessage: '',
    });
    vi.mocked(favoritesApi.add).mockResolvedValue(
      {} as Awaited<ReturnType<typeof favoritesApi.add>>,
    );
  });

  function hydrateAccountA() {
    localStorage.setItem('accessToken', 'token-a');
    localStorage.setItem('user', JSON.stringify(accountA));
    useAuthStore.setState({
      user: accountA,
      isAuthenticated: true,
      isLoading: false,
    });
    useCartStore.setState({
      mode: 'auth',
      localItems: [],
      backendItems: cartA.items,
      backendCartId: cartA.id,
      items: [{
        key: '11-111',
        couponOfferId: 11,
        couponOptionId: 111,
        couponTitle: 'A private cart item',
        optionTitle: 'A option',
        unitPrice: 10000,
        quantity: 1,
        addedAt: 0,
      }],
      isLoading: false,
      error: null,
      totalItems: 1,
      totalPrice: 10000,
    });
    useFavoritesStore.setState({ favoriteIds: [11] });
  }

  function configureDelayedAccountBState() {
    const accountBCart =
      deferred<Awaited<ReturnType<typeof ordersApi.getCart>>>();
    const accountBFavorites =
      deferred<Awaited<ReturnType<typeof favoritesApi.getAll>>>();
    vi.mocked(authApi.getMe).mockResolvedValue(
      profileResponse(accountB, 'Account B final'),
    );
    vi.mocked(ordersApi.getCart).mockReturnValue(accountBCart.promise);
    vi.mocked(favoritesApi.getAll).mockReturnValue(accountBFavorites.promise);
    return { accountBCart, accountBFavorites };
  }

  it('account A cart is removed immediately after B authentication succeeds', async () => {
    hydrateAccountA();
    const { accountBCart, accountBFavorites } =
      configureDelayedAccountBState();
    vi.mocked(authApi.login).mockResolvedValue(
      loginResponse(accountB, 'token-b'),
    );

    await expect(useAuthStore.getState().login({
      email: accountB.email,
      password: 'Strong1!',
    })).resolves.toBe(true);

    expect(useCartStore.getState().items).toEqual([]);
    expect(useCartStore.getState().backendItems).toEqual([]);
    accountBCart.resolve(cartResponse(cartB));
    accountBFavorites.resolve(favoritesResponse([22]));
  });

  it('account A cart stays absent while B cart is loading', async () => {
    hydrateAccountA();
    const { accountBCart, accountBFavorites } =
      configureDelayedAccountBState();
    vi.mocked(authApi.login).mockResolvedValue(
      loginResponse(accountB, 'token-b'),
    );

    await useAuthStore.getState().login({
      email: accountB.email,
      password: 'Strong1!',
    });
    await vi.waitFor(() => {
      expect(ordersApi.getCart).toHaveBeenCalledOnce();
      expect(useCartStore.getState().isLoading).toBe(true);
    });

    expect(useCartStore.getState().items).toEqual([]);
    expect(useCartStore.getState().backendCartId).toBeNull();
    accountBCart.resolve(cartResponse(cartB));
    accountBFavorites.resolve(favoritesResponse([22]));
  });

  it('account A favorites are never sent to B', async () => {
    hydrateAccountA();
    const { accountBCart, accountBFavorites } =
      configureDelayedAccountBState();
    vi.mocked(authApi.login).mockResolvedValue(
      loginResponse(accountB, 'token-b'),
    );

    await useAuthStore.getState().login({
      email: accountB.email,
      password: 'Strong1!',
    });
    await vi.waitFor(() => expect(favoritesApi.getAll).toHaveBeenCalledOnce());

    expect(useFavoritesStore.getState().favoriteIds).toEqual([]);
    expect(JSON.parse(
      localStorage.getItem('favorites-storage') ?? '{}',
    )).toMatchObject({ state: { favoriteIds: [] } });
    accountBFavorites.resolve(favoritesResponse([22]));
    accountBCart.resolve(cartResponse(cartB));
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(favoritesApi.add).not.toHaveBeenCalled();
  });

  it.each([
    {
      name: 'password login',
      prepare: () => vi.mocked(authApi.login).mockResolvedValue(
        loginResponse(accountB, 'token-b'),
      ),
      authenticate: () => useAuthStore.getState().login({
        email: accountB.email,
        password: 'Strong1!',
      }),
    },
    {
      name: 'registration',
      prepare: () => vi.mocked(authApi.register).mockResolvedValue(
        loginResponse(accountB, 'token-b'),
      ),
      authenticate: () => useAuthStore.getState().register({
        email: accountB.email,
        password: 'Strong1!',
        firstName: accountB.firstName,
      }),
    },
    {
      name: 'Telegram login',
      prepare: () => vi.mocked(authApi.telegramAuth).mockResolvedValue(
        loginResponse(accountB, 'token-b'),
      ),
      authenticate: () => useAuthStore.getState().telegramLogin({
        id: accountB.id,
        first_name: accountB.firstName,
        auth_date: 1_785_000_000,
        hash: 'valid-telegram-hash',
      }),
    },
  ])('$name uses the same replacement boundary', async ({
    prepare,
    authenticate,
  }) => {
    hydrateAccountA();
    const { accountBCart, accountBFavorites } =
      configureDelayedAccountBState();
    prepare();

    await expect(authenticate()).resolves.toBe(true);

    expect(useCartStore.getState().items).toEqual([]);
    expect(useFavoritesStore.getState().favoriteIds).toEqual([]);
    expect(localStorage.getItem('accessToken')).toBe('token-b');
    expect(useAuthStore.getState().user?.id).toBe(accountB.id);
    accountBCart.resolve(cartResponse(cartB));
    accountBFavorites.resolve(favoritesResponse([22]));
  });

  it('B responses leave only B cart and favorites', async () => {
    hydrateAccountA();
    const { accountBCart, accountBFavorites } =
      configureDelayedAccountBState();
    vi.mocked(authApi.login).mockResolvedValue(
      loginResponse(accountB, 'token-b'),
    );

    await useAuthStore.getState().login({
      email: accountB.email,
      password: 'Strong1!',
    });
    accountBCart.resolve(cartResponse(cartB));
    accountBFavorites.resolve(favoritesResponse([22]));

    await waitFor(() => {
      expect(useCartStore.getState().items.map(
        (item) => item.couponTitle,
      )).toEqual(['B private cart item']);
      expect(useFavoritesStore.getState().favoriteIds).toEqual([22]);
    });
  });

  it('late A responses cannot restore A state', async () => {
    hydrateAccountA();
    const lateAProfile =
      deferred<Awaited<ReturnType<typeof authApi.getMe>>>();
    const lateACart =
      deferred<Awaited<ReturnType<typeof ordersApi.getCart>>>();
    const lateAFavorites =
      deferred<Awaited<ReturnType<typeof favoritesApi.getAll>>>();
    const accountBProfile =
      deferred<Awaited<ReturnType<typeof authApi.getMe>>>();
    const accountBCart =
      deferred<Awaited<ReturnType<typeof ordersApi.getCart>>>();
    const accountBFavorites =
      deferred<Awaited<ReturnType<typeof favoritesApi.getAll>>>();
    vi.mocked(authApi.getMe)
      .mockReturnValueOnce(lateAProfile.promise)
      .mockReturnValueOnce(accountBProfile.promise);
    vi.mocked(ordersApi.getCart)
      .mockReturnValueOnce(lateACart.promise)
      .mockReturnValueOnce(accountBCart.promise);
    vi.mocked(favoritesApi.getAll)
      .mockReturnValueOnce(lateAFavorites.promise)
      .mockReturnValueOnce(accountBFavorites.promise);
    vi.mocked(authApi.login).mockResolvedValue(
      loginResponse(accountB, 'token-b'),
    );

    const oldProfileRequest = useAuthStore.getState().refreshProfile();
    const oldCartRequest = useCartStore.getState().fetchBackendCart();
    const oldFavoritesRequest =
      useFavoritesStore.getState().syncWithBackend();
    await useAuthStore.getState().login({
      email: accountB.email,
      password: 'Strong1!',
    });

    accountBProfile.resolve(profileResponse(accountB, 'Account B final'));
    accountBCart.resolve(cartResponse(cartB));
    accountBFavorites.resolve(favoritesResponse([22]));
    await waitFor(() => {
      expect(useAuthStore.getState().user?.firstName).toBe('Account B final');
      expect(useCartStore.getState().backendCartId).toBe(cartB.id);
      expect(useFavoritesStore.getState().favoriteIds).toEqual([22]);
    });

    lateAProfile.resolve(profileResponse(accountA, 'Account A late'));
    lateACart.resolve(cartResponse(cartA));
    lateAFavorites.resolve(favoritesResponse([11]));
    await Promise.all([
      oldProfileRequest,
      oldCartRequest,
      oldFavoritesRequest,
    ]);

    expect(useAuthStore.getState().user?.firstName).toBe('Account B final');
    expect(useCartStore.getState().backendCartId).toBe(cartB.id);
    expect(useFavoritesStore.getState().favoriteIds).toEqual([22]);
  });

  it('keeps account B user and cart when account A profile and cart responses arrive late', async () => {
    const lateAProfile = deferred<Awaited<ReturnType<typeof authApi.getMe>>>();
    const lateACart = deferred<Awaited<ReturnType<typeof ordersApi.getCart>>>();
    const accountBProfile = deferred<Awaited<ReturnType<typeof authApi.getMe>>>();
    const accountBCart = deferred<Awaited<ReturnType<typeof ordersApi.getCart>>>();

    vi.mocked(authApi.login)
      .mockResolvedValueOnce(loginResponse(accountA, 'token-a'))
      .mockResolvedValueOnce(loginResponse(accountB, 'token-b'));
    vi.mocked(authApi.getMe)
      .mockReturnValueOnce(lateAProfile.promise)
      .mockReturnValueOnce(accountBProfile.promise);
    vi.mocked(ordersApi.getCart)
      .mockReturnValueOnce(lateACart.promise)
      .mockReturnValueOnce(accountBCart.promise);

    await useAuthStore.getState().login({
      email: accountA.email,
      password: 'Strong1!',
    });
    expect(authApi.getMe).toHaveBeenCalledTimes(1);
    expect(ordersApi.getCart).toHaveBeenCalledTimes(1);

    useAuthStore.getState().logout();

    await useAuthStore.getState().login({
      email: accountB.email,
      password: 'Strong1!',
    });
    expect(authApi.getMe).toHaveBeenCalledTimes(2);
    expect(ordersApi.getCart).toHaveBeenCalledTimes(2);

    accountBProfile.resolve(profileResponse(accountB, 'Account B final'));
    accountBCart.resolve(cartResponse(cartB));

    await waitFor(() => {
      expect(useAuthStore.getState().user?.firstName).toBe('Account B final');
      expect(useCartStore.getState().backendCartId).toBe(cartB.id);
      expect(useCartStore.getState().items[0]?.couponTitle)
        .toBe('B private cart item');
    });

    lateAProfile.resolve(profileResponse(accountA, 'Account A late'));
    lateACart.resolve(cartResponse(cartA));
    await lateAProfile.promise;
    await lateACart.promise;
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(useCartStore.getState().backendCartId).toBe(cartB.id);
    expect(useCartStore.getState().items.map((item) => item.couponTitle))
      .toEqual(['B private cart item']);
    expect(useCartStore.getState().mode).toBe('auth');
    expect(useCartStore.getState().isLoading).toBe(false);
    expect(useAuthStore.getState().user?.firstName).toBe('Account B final');
    expect(JSON.parse(localStorage.getItem('user') ?? '{}')).toMatchObject({
      id: accountB.id,
      firstName: 'Account B final',
    });
    expect(localStorage.getItem('accessToken')).toBe('token-b');
  });

  it('resets cart loading and errors while an invalidated backend request is pending', async () => {
    const lateCart = deferred<Awaited<ReturnType<typeof ordersApi.getCart>>>();
    vi.mocked(ordersApi.getCart).mockReturnValue(lateCart.promise);
    advanceSessionGeneration();
    useCartStore.setState({
      mode: 'auth',
      backendItems: cartA.items,
      backendCartId: cartA.id,
      items: [],
      isLoading: false,
      error: 'Account A error',
    });

    const oldCartRequest = useCartStore.getState().fetchBackendCart();
    expect(useCartStore.getState().isLoading).toBe(true);

    invalidateClientSession();

    expect(useCartStore.getState().mode).toBe('guest');
    expect(useCartStore.getState().backendItems).toEqual([]);
    expect(useCartStore.getState().isLoading).toBe(false);
    expect(useCartStore.getState().error).toBeNull();

    lateCart.resolve(cartResponse(cartA));
    await oldCartRequest;

    expect(useCartStore.getState().mode).toBe('guest');
    expect(useCartStore.getState().backendItems).toEqual([]);
  });

  it('ignores a login response after that authentication attempt is invalidated', async () => {
    const lateLogin = deferred<Awaited<ReturnType<typeof authApi.login>>>();
    vi.mocked(authApi.login).mockReturnValue(lateLogin.promise);
    vi.mocked(authApi.getMe).mockResolvedValue(
      profileResponse(accountA, 'Account A late login'),
    );
    vi.mocked(ordersApi.getCart).mockResolvedValue(cartResponse(cartA));

    const loginAttempt = useAuthStore.getState().login({
      email: accountA.email,
      password: 'Strong1!',
    });
    await vi.waitFor(() => expect(authApi.login).toHaveBeenCalledOnce());

    invalidateClientSession();
    lateLogin.resolve(loginResponse(accountA, 'token-a'));
    await expect(loginAttempt).resolves.toBe(false);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(authApi.getMe).not.toHaveBeenCalled();
    expect(ordersApi.getCart).not.toHaveBeenCalled();
  });

  it('serializes overlapping authentication requests so the newest account cookie wins', async () => {
    const lateAccountALogin =
      deferred<Awaited<ReturnType<typeof authApi.login>>>();
    const accountBLoginResponse =
      deferred<Awaited<ReturnType<typeof authApi.login>>>();
    vi.mocked(authApi.login)
      .mockReturnValueOnce(lateAccountALogin.promise)
      .mockReturnValueOnce(accountBLoginResponse.promise);
    vi.mocked(authApi.getMe).mockResolvedValue(
      profileResponse(accountB, 'Account B final'),
    );
    vi.mocked(ordersApi.getCart).mockResolvedValue(cartResponse(cartB));

    const accountALogin = useAuthStore.getState().login({
      email: accountA.email,
      password: 'Strong1!',
    });
    await vi.waitFor(() => expect(authApi.login).toHaveBeenCalledOnce());

    const accountBLogin = useAuthStore.getState().login({
      email: accountB.email,
      password: 'Strong1!',
    });
    await Promise.resolve();

    expect(authApi.login).toHaveBeenCalledTimes(1);

    lateAccountALogin.resolve(loginResponse(accountA, 'token-a'));
    await expect(accountALogin).resolves.toBe(false);
    await vi.waitFor(() => expect(authApi.login).toHaveBeenCalledTimes(2));

    accountBLoginResponse.resolve(loginResponse(accountB, 'token-b'));
    await expect(accountBLogin).resolves.toBe(true);

    await waitFor(() => {
      expect(useAuthStore.getState().user?.firstName).toBe('Account B final');
      expect(useCartStore.getState().backendCartId).toBe(cartB.id);
    });
    expect(localStorage.getItem('accessToken')).toBe('token-b');
  });

  it('treats an invalidated authentication failure as cancellation', async () => {
    const lateLogin = deferred<Awaited<ReturnType<typeof authApi.login>>>();
    vi.mocked(authApi.login).mockReturnValue(lateLogin.promise);

    const loginAttempt = useAuthStore.getState().login({
      email: accountA.email,
      password: 'Strong1!',
    });
    await vi.waitFor(() => expect(authApi.login).toHaveBeenCalledOnce());

    invalidateClientSession();
    lateLogin.reject(new Error('late account A authentication failure'));

    await expect(loginAttempt).resolves.toBe(false);
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().isLoading).toBe(false);
  });

  it('waits for account A logout to settle before dispatching account B login', async () => {
    const lateLogout = deferred<Awaited<ReturnType<typeof authApi.logout>>>();
    vi.mocked(authApi.login)
      .mockResolvedValueOnce(loginResponse(accountA, 'token-a'))
      .mockResolvedValueOnce(loginResponse(accountB, 'token-b'));
    vi.mocked(authApi.getMe)
      .mockResolvedValueOnce(profileResponse(accountA, 'Account A final'))
      .mockResolvedValueOnce(profileResponse(accountB, 'Account B final'));
    vi.mocked(ordersApi.getCart)
      .mockResolvedValueOnce(cartResponse(cartA))
      .mockResolvedValueOnce(cartResponse(cartB));

    await useAuthStore.getState().login({
      email: accountA.email,
      password: 'Strong1!',
    });
    await waitFor(() => {
      expect(useAuthStore.getState().user?.firstName).toBe('Account A final');
      expect(useCartStore.getState().backendCartId).toBe(cartA.id);
    });

    vi.mocked(authApi.logout).mockReturnValue(lateLogout.promise);
    useAuthStore.getState().logout();
    await vi.waitFor(() => expect(authApi.logout).toHaveBeenCalledOnce());
    const logoutContext = (
      vi.mocked(authApi.logout).mock.calls as unknown as Array<
        [{ signal: AbortSignal }]
      >
    )[0][0];
    const accountBLogin = useAuthStore.getState().login({
      email: accountB.email,
      password: 'Strong1!',
    });
    await Promise.resolve();

    expect(logoutContext.signal.aborted).toBe(false);
    expect(authApi.login).toHaveBeenCalledTimes(1);
    expect(useAuthStore.getState().user).toBeNull();

    lateLogout.resolve(
      {} as Awaited<ReturnType<typeof authApi.logout>>,
    );
    await accountBLogin;

    await waitFor(() => {
      expect(useAuthStore.getState().user?.firstName).toBe('Account B final');
      expect(useCartStore.getState().backendCartId).toBe(cartB.id);
    });
    expect(localStorage.getItem('accessToken')).toBe('token-b');
  });

  it('captures account A token before immediate logout cleanup', async () => {
    const sessionGeneration = captureSessionGeneration();
    localStorage.setItem('accessToken', 'token-a');

    useAuthStore.getState().logout();

    expect(localStorage.getItem('accessToken')).toBeNull();
    await vi.waitFor(() => expect(authApi.logout).toHaveBeenCalledOnce());
    expect(authApi.logout).toHaveBeenCalledWith({
      accessToken: 'token-a',
      sessionGeneration,
      signal: expect.any(AbortSignal),
    });
  });

  it('aborts an in-flight authentication request when a newer attempt supersedes it', async () => {
    let accountASignal: AbortSignal | undefined;
    vi.mocked(authApi.login)
      .mockImplementationOnce(((
        _data: Parameters<typeof authApi.login>[0],
        context?: { signal?: AbortSignal },
      ) => new Promise((_, reject) => {
        accountASignal = context?.signal;
        context?.signal?.addEventListener('abort', () => {
          reject(new Error('account A request aborted'));
        });
      })) as typeof authApi.login)
      .mockResolvedValueOnce(loginResponse(accountB, 'token-b'));
    vi.mocked(authApi.getMe).mockResolvedValue(
      profileResponse(accountB, 'Account B final'),
    );
    vi.mocked(ordersApi.getCart).mockResolvedValue(cartResponse(cartB));

    const accountALogin = useAuthStore.getState().login({
      email: accountA.email,
      password: 'Strong1!',
    });
    await vi.waitFor(() => expect(authApi.login).toHaveBeenCalledOnce());
    expect(accountASignal).toBeInstanceOf(AbortSignal);

    const accountBLogin = useAuthStore.getState().login({
      email: accountB.email,
      password: 'Strong1!',
    });

    expect(accountASignal?.aborted).toBe(true);
    await expect(accountALogin).resolves.toBe(false);
    await vi.waitFor(() => expect(authApi.login).toHaveBeenCalledTimes(2));
    await expect(accountBLogin).resolves.toBe(true);

    await waitFor(() => {
      expect(useAuthStore.getState().user?.firstName).toBe('Account B final');
    });
  });

  it('does not let an intentionally aborted account A refresh invalidate account B login', async () => {
    advanceSessionGeneration();
    localStorage.setItem('accessToken', 'token-a');
    localStorage.setItem('user', JSON.stringify(accountA));
    const refreshSpy = vi.spyOn(axios, 'post').mockImplementation(
      ((_url, _data, config?: { signal?: AbortSignal }) =>
        new Promise((_, reject) => {
          config?.signal?.addEventListener('abort', () => {
            reject(new axios.CanceledError('account A refresh superseded'));
          });
        })) as typeof axios.post,
    );
    const accountARequest = apiClient.get('/account-a-private', {
      adapter: async (config) => {
        throw unauthorized(config);
      },
    }).catch((error: unknown) => error);
    await vi.waitFor(() => expect(refreshSpy).toHaveBeenCalledOnce());

    vi.mocked(authApi.login).mockResolvedValue(
      loginResponse(accountB, 'token-b'),
    );
    vi.mocked(authApi.getMe).mockResolvedValue(
      profileResponse(accountB, 'Account B final'),
    );
    vi.mocked(ordersApi.getCart).mockResolvedValue(cartResponse(cartB));

    const accountBLogin = useAuthStore.getState().login({
      email: accountB.email,
      password: 'Strong1!',
    });

    await accountARequest;
    await expect(accountBLogin).resolves.toBe(true);
    await waitFor(() => {
      expect(useAuthStore.getState().user?.firstName).toBe('Account B final');
    });
    expect(localStorage.getItem('accessToken')).toBe('token-b');
    refreshSpy.mockRestore();
  });

  it('releases a hung logout after a bounded timeout so account B can log in', async () => {
    vi.useFakeTimers();
    try {
      localStorage.setItem('accessToken', 'token-a');
      vi.mocked(authApi.logout).mockReturnValue(new Promise(() => {}));
      vi.mocked(authApi.login).mockResolvedValue(
        loginResponse(accountB, 'token-b'),
      );
      vi.mocked(authApi.getMe).mockResolvedValue(
        profileResponse(accountB, 'Account B final'),
      );
      vi.mocked(ordersApi.getCart).mockResolvedValue(cartResponse(cartB));

      useAuthStore.getState().logout();
      await vi.advanceTimersByTimeAsync(0);
      const accountBLogin = useAuthStore.getState().login({
        email: accountB.email,
        password: 'Strong1!',
      });

      await vi.advanceTimersByTimeAsync(5_000);
      await vi.waitFor(() => expect(authApi.login).toHaveBeenCalledOnce());
      await expect(accountBLogin).resolves.toBe(true);
      expect(localStorage.getItem('accessToken')).toBe('token-b');
    } finally {
      vi.useRealTimers();
    }
  });
});
