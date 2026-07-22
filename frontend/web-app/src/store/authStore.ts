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

interface AuthState {
  user: UserDto | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<void>;
  telegramLogin: (data: TelegramAuthPayload) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  loadFromStorage: () => void;
  refreshProfile: () => Promise<void>;
  updateProfile: (data: UpdateProfileRequest) => Promise<UserProfileResponse>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  isLoading: false,

  login: async (data) => {
    set({ isLoading: true });
    try {
      const response = await authApi.login(data);
      const { accessToken, user } = response.data.data;
      // M4: refreshToken now in httpOnly cookie (set by backend), NOT in localStorage
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('user', JSON.stringify(user));
      set({ user, isAuthenticated: true, isLoading: false });
      // Sync guest cart → backend and switch to auth mode
      useCartStore.getState().syncLocalCartToBackend();
      void useFavoritesStore.getState().syncWithBackend();
      void get().refreshProfile();
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  telegramLogin: async (data) => {
    set({ isLoading: true });
    try {
      const response = await authApi.telegramAuth(data);
      const { accessToken, user } = response.data.data;
      // M4: refreshToken в httpOnly cookie, не в localStorage
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('user', JSON.stringify(user));
      set({ user, isAuthenticated: true, isLoading: false });
      useCartStore.getState().syncLocalCartToBackend();
      void useFavoritesStore.getState().syncWithBackend();
      void get().refreshProfile();
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  register: async (data) => {
    set({ isLoading: true });
    try {
      const response = await authApi.register(data);
      const { accessToken, user } = response.data.data;
      // M4: refreshToken now in httpOnly cookie (set by backend), NOT in localStorage
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('user', JSON.stringify(user));
      set({ user, isAuthenticated: true, isLoading: false });
      // Sync guest cart → backend and switch to auth mode
      useCartStore.getState().syncLocalCartToBackend();
      void useFavoritesStore.getState().syncWithBackend();
      void get().refreshProfile();
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  logout: () => {
    // M4: POST /logout без body — cookie удаляется бэком через Set-Cookie Max-Age=0
    authApi.logout().catch(() => {});
    localStorage.removeItem('accessToken');
    localStorage.removeItem('user');
    set({ user: null, isAuthenticated: false });
    // Switch cart back to guest mode
    useCartStore.getState().setMode('guest');
  },

  loadFromStorage: () => {
    try {
      const userStr = localStorage.getItem('user');
      const token = localStorage.getItem('accessToken');
      if (userStr && token) {
        set({ user: JSON.parse(userStr), isAuthenticated: true });
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
    try {
      const response = await authApi.getMe();
      const user = response.data.data;
      localStorage.setItem('user', JSON.stringify(user));
      set({ user });
    } catch {
      // If 401, apiClient interceptor handles auth cleanup
    }
  },

  updateProfile: async (data: UpdateProfileRequest) => {
    const response = await authApi.updateProfile(data);
    const user = response.data.data;
    localStorage.setItem('user', JSON.stringify(user));
    set({ user });
    return user;
  },
}));
