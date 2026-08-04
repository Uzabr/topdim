import { create } from 'zustand';
import { authApi } from '../api/auth';
import type {
  UserDto,
  UserProfileResponse,
  LoginRequest,
  RegisterRequest,
  UpdateProfileRequest,
  TelegramAuthPayload,
} from '../api/auth';
import { useCartStore } from './cartStore';
import { useFavoritesStore } from './favoritesStore';
import {
  abortSessionAuthenticationTransport,
  advanceSessionGeneration,
  captureSessionGeneration,
  establishAuthenticatedSession,
  invalidateClientSession,
  isSessionGenerationCurrent,
  registerSessionReset,
  runSessionAuthenticationTransport,
  SESSION_AUTH_TRANSPORT_TIMEOUT_MS,
  SESSION_LOGOUT_TRANSPORT_TIMEOUT_MS,
} from '../sessionCleanup';

interface AuthState {
  user: UserDto | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<boolean>;
  telegramLogin: (data: TelegramAuthPayload) => Promise<boolean>;
  phoneLogin: (phone: string, code: string) => Promise<boolean>;
  register: (data: RegisterRequest) => Promise<boolean>;
  logout: () => void;
  loadFromStorage: () => void;
  refreshProfile: () => Promise<void>;
  updateProfile: (data: UpdateProfileRequest) => Promise<UserProfileResponse>;
}

let authenticationAttempt = 0;
let pendingLogout: Promise<void> | null = null;

function beginAuthenticationAttempt() {
  abortSessionAuthenticationTransport();
  authenticationAttempt += 1;
  return authenticationAttempt;
}

function isAuthenticationAttemptCurrent(
  attempt: number,
  sessionGeneration: number,
) {
  return attempt === authenticationAttempt
    && isSessionGenerationCurrent(sessionGeneration);
}

async function waitForPendingLogout() {
  if (pendingLogout) {
    await pendingLogout;
  }
}

async function runCurrentAuthenticationRequest<T>(
  attempt: number,
  sessionGeneration: number,
  request: (signal: AbortSignal) => Promise<T>,
): Promise<T | null> {
  await waitForPendingLogout();
  if (!isAuthenticationAttemptCurrent(attempt, sessionGeneration)) {
    return null;
  }
  return runSessionAuthenticationTransport(
    (signal) => {
      if (!isAuthenticationAttemptCurrent(attempt, sessionGeneration)) {
        return null;
      }
      return request(signal);
    },
    { timeoutMs: SESSION_AUTH_TRANSPORT_TIMEOUT_MS },
  );
}

function trackLogoutRequest(
  accessToken: string | null,
  sessionGeneration: number,
) {
  const request = runSessionAuthenticationTransport((signal) =>
    authApi.logout({
      accessToken: accessToken ?? undefined,
      sessionGeneration,
      signal,
    }), {
      abortable: false,
      timeoutMs: SESSION_LOGOUT_TRANSPORT_TIMEOUT_MS,
    });
  const settledRequest = request.then(
    () => undefined,
    () => undefined,
  );
  const previousLogout = pendingLogout;
  const allPendingLogouts = Promise.all([
    previousLogout,
    settledRequest,
  ]).then(() => undefined);
  pendingLogout = allPendingLogouts;
  void allPendingLogouts.then(() => {
    if (pendingLogout === allPendingLogouts) {
      pendingLogout = null;
    }
  });
}

function applyAuthenticatedSession(accessToken: string, user: UserDto) {
  const replacingAuthenticatedSession =
    useAuthStore.getState().isAuthenticated
    || localStorage.getItem('accessToken') != null;
  const establishedSession = establishAuthenticatedSession(
    replacingAuthenticatedSession,
    () => {
      // M4: refreshToken is stored in an httpOnly cookie by the backend.
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('user', JSON.stringify(user));
      useAuthStore.setState({
        user,
        isAuthenticated: true,
        isLoading: false,
      });
    },
  );

  const cartStore = useCartStore.getState();
  if (establishedSession.replacedAuthenticatedSession) {
    cartStore.setMode('auth');
    void cartStore.fetchBackendCart();
  } else {
    void cartStore.syncLocalCartToBackend();
  }
  void useFavoritesStore.getState().syncWithBackend();
  void useAuthStore.getState().refreshProfile();
  return establishedSession.generation;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  isLoading: false,

  login: async (data) => {
    const attempt = beginAuthenticationAttempt();
    let sessionGeneration = captureSessionGeneration();
    set({ isLoading: true });
    try {
      const response = await runCurrentAuthenticationRequest(
        attempt,
        sessionGeneration,
        (signal) => authApi.login(data, { signal }),
      );
      if (response === null || !isAuthenticationAttemptCurrent(
        attempt,
        sessionGeneration,
      )) return false;
      const { accessToken, user } = response.data.data;
      sessionGeneration = applyAuthenticatedSession(accessToken, user);
      return true;
    } catch (error) {
      if (!isAuthenticationAttemptCurrent(
        attempt,
        sessionGeneration,
      )) {
        return false;
      }
      set({ isLoading: false });
      throw error;
    }
  },

  telegramLogin: async (data) => {
    const attempt = beginAuthenticationAttempt();
    let sessionGeneration = captureSessionGeneration();
    set({ isLoading: true });
    try {
      const response = await runCurrentAuthenticationRequest(
        attempt,
        sessionGeneration,
        (signal) => authApi.telegramAuth(data, { signal }),
      );
      if (response === null || !isAuthenticationAttemptCurrent(
        attempt,
        sessionGeneration,
      )) return false;
      const { accessToken, user } = response.data.data;
      sessionGeneration = applyAuthenticatedSession(accessToken, user);
      return true;
    } catch (error) {
      if (!isAuthenticationAttemptCurrent(
        attempt,
        sessionGeneration,
      )) {
        return false;
      }
      set({ isLoading: false });
      throw error;
    }
  },

  phoneLogin: async (phone, code) => {
    const attempt = beginAuthenticationAttempt();
    let sessionGeneration = captureSessionGeneration();
    set({ isLoading: true });
    try {
      const response = await runCurrentAuthenticationRequest(
        attempt,
        sessionGeneration,
        (signal) => authApi.confirmPhoneOtp({ phone, code }, { signal }),
      );
      if (response === null || !isAuthenticationAttemptCurrent(
        attempt,
        sessionGeneration,
      )) return false;
      const { accessToken, user } = response.data.data;
      sessionGeneration = applyAuthenticatedSession(accessToken, user);
      return true;
    } catch (error) {
      if (!isAuthenticationAttemptCurrent(
        attempt,
        sessionGeneration,
      )) {
        return false;
      }
      set({ isLoading: false });
      throw error;
    }
  },

  register: async (data) => {
    const attempt = beginAuthenticationAttempt();
    let sessionGeneration = captureSessionGeneration();
    set({ isLoading: true });
    try {
      const response = await runCurrentAuthenticationRequest(
        attempt,
        sessionGeneration,
        (signal) => authApi.register(data, { signal }),
      );
      if (response === null || !isAuthenticationAttemptCurrent(
        attempt,
        sessionGeneration,
      )) return false;
      const { accessToken, user } = response.data.data;
      sessionGeneration = applyAuthenticatedSession(accessToken, user);
      return true;
    } catch (error) {
      if (!isAuthenticationAttemptCurrent(
        attempt,
        sessionGeneration,
      )) {
        return false;
      }
      set({ isLoading: false });
      throw error;
    }
  },

  logout: () => {
    // M4: POST /logout без body — cookie удаляется бэком через Set-Cookie Max-Age=0
    const accessToken = localStorage.getItem('accessToken');
    const sessionGeneration = captureSessionGeneration();
    beginAuthenticationAttempt();
    trackLogoutRequest(accessToken, sessionGeneration);
    invalidateClientSession();
  },

  loadFromStorage: () => {
    try {
      const userStr = localStorage.getItem('user');
      const token = localStorage.getItem('accessToken');
      if (userStr && token) {
        const user = JSON.parse(userStr);
        beginAuthenticationAttempt();
        advanceSessionGeneration();
        set({ user, isAuthenticated: true });
        // User already authenticated — switch cart to auth mode
        const cartStore = useCartStore.getState();
        cartStore.setMode('auth');
        cartStore.fetchBackendCart();
        void useFavoritesStore.getState().syncWithBackend();
        void get().refreshProfile();
      }
    } catch {
      // Ignore
    }
  },

  refreshProfile: async () => {
    const sessionGeneration = captureSessionGeneration();
    try {
      const response = await authApi.getMe();
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const user = response.data.data;
      localStorage.setItem('user', JSON.stringify(user));
      set({ user });
    } catch {
      // If 401, apiClient interceptor handles auth cleanup
    }
  },

  updateProfile: async (data: UpdateProfileRequest) => {
    const sessionGeneration = captureSessionGeneration();
    const response = await authApi.updateProfile(data);
    const user = response.data.data;
    if (isSessionGenerationCurrent(sessionGeneration)) {
      localStorage.setItem('user', JSON.stringify(user));
      set({ user });
    }
    return user;
  },
}));

registerSessionReset(() => {
  useAuthStore.setState({
    user: null,
    isAuthenticated: false,
    isLoading: false,
  });
  useCartStore.getState().setMode('guest');
  useFavoritesStore.getState().reset();
});
