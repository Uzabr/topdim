import apiClient from './client';
import type { ApiResponse } from './client';
import type { AxiosRequestConfig } from 'axios';
import {
  SESSION_AUTH_TRANSPORT_TIMEOUT_MS,
  SESSION_LOGOUT_TRANSPORT_TIMEOUT_MS,
} from '../sessionCleanup';

interface SessionAxiosRequestConfig extends AxiosRequestConfig {
  _sessionGeneration?: number;
  _skipAuthRefresh?: boolean;
}

export interface AuthenticationRequestContext {
  signal?: AbortSignal;
}

export interface LogoutRequestContext extends AuthenticationRequestContext {
  accessToken?: string;
  sessionGeneration?: number;
}

function authRequestConfig(
  context?: AuthenticationRequestContext,
): SessionAxiosRequestConfig {
  return {
    _skipAuthRefresh: true,
    signal: context?.signal,
    timeout: SESSION_AUTH_TRANSPORT_TIMEOUT_MS,
  };
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  phone?: string;
  password: string;
  firstName: string;
  lastName?: string;
}

export interface UserDto {
  id: number;
  email: string;
  phone?: string;
  firstName: string;
  lastName?: string;
  role: string;
  avatarUrl?: string;
  emailVerified?: boolean;
  phoneVerified?: boolean;
}

export interface UserProfileResponse extends UserDto {
  emailVerified: boolean;
  phoneVerified: boolean;
  createdAt?: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserDto;
  // M4: refreshToken больше не в JSON body — передаётся через httpOnly cookie
}

export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  avatarUrl?: string;
}

/** Payload Telegram Login Widget (snake_case — отдаётся виджетом как есть на бэкенд). */
export interface TelegramAuthPayload {
  id: number;
  first_name?: string;
  last_name?: string;
  username?: string;
  photo_url?: string;
  auth_date: number;
  hash: string;
}

export const authApi = {
  register: (
    data: RegisterRequest,
    context?: AuthenticationRequestContext,
  ) =>
    apiClient.post<ApiResponse<AuthResponse>>(
      '/api/v1/auth/register',
      data,
      authRequestConfig(context),
    ),

  login: (
    data: LoginRequest,
    context?: AuthenticationRequestContext,
  ) =>
    apiClient.post<ApiResponse<AuthResponse>>(
      '/api/v1/auth/login',
      data,
      authRequestConfig(context),
    ),

  // M4: POST без body — refreshToken приходит из httpOnly cookie
  refresh: () =>
    apiClient.post<ApiResponse<AuthResponse>>(
      '/api/v1/auth/refresh',
      undefined,
      authRequestConfig(),
    ),

  // M4: POST без body — refreshToken приходит из httpOnly cookie
  logout: (context?: LogoutRequestContext) => {
    const config: SessionAxiosRequestConfig = {
      ...authRequestConfig(context),
      _sessionGeneration: context?.sessionGeneration,
      timeout: SESSION_LOGOUT_TRANSPORT_TIMEOUT_MS,
      ...(context?.accessToken
        ? {
            headers: {
              Authorization: `Bearer ${context.accessToken}`,
            },
          }
        : {}),
    };
    return apiClient.post<ApiResponse<void>>(
      '/api/v1/auth/logout',
      undefined,
      config,
    );
  },

  guestAuth: (data: { phone: string; name: string }) =>
    apiClient.post<ApiResponse<AuthResponse>>(
      '/api/v1/auth/guest',
      data,
      authRequestConfig(),
    ),

  telegramAuth: (
    data: TelegramAuthPayload,
    context?: AuthenticationRequestContext,
  ) =>
    apiClient.post<ApiResponse<AuthResponse>>(
      '/api/v1/auth/telegram',
      data,
      authRequestConfig(context),
    ),

  /** idToken — ID-token, полученный от Google Identity Services (GIS) на клиенте. */
  googleAuth: (
    idToken: string,
    context?: AuthenticationRequestContext,
  ) =>
    apiClient.post<ApiResponse<AuthResponse>>(
      '/api/v1/auth/google',
      { idToken },
      authRequestConfig(context),
    ),

  /** Всегда 202 — бэкенд не раскрывает, существует ли номер (anti-enumeration). */
  requestPhoneOtp: (phone: string) =>
    apiClient.post<ApiResponse<void>>(
      '/api/v1/auth/phone/request',
      { phone },
      authRequestConfig(),
    ),

  confirmPhoneOtp: (
    data: { phone: string; code: string },
    context?: AuthenticationRequestContext,
  ) =>
    apiClient.post<ApiResponse<AuthResponse>>(
      '/api/v1/auth/phone/confirm',
      data,
      authRequestConfig(context),
    ),

  /**
   * Привязка телефона к текущему (авторизованному) аккаунту (T8b, backend T8a).
   * Заменяет легаси-смену телефона через PUT /users/me (была в обход OTP-подтверждения).
   * Authenticated — БЕЗ authRequestConfig, как changePassword/requestEmailChange, чтобы
   * при истёкшем access-токене сработал silent-refresh интерцептора apiClient.
   * Неверный код → 401, номер занят другим аккаунтом → 409.
   * Отвечает 200 без тела — после успеха обязательно перечитать профиль (refreshProfile()),
   * иначе UI покажет устаревшие phone/phoneVerified.
   */
  linkPhone: (data: { phone: string; code: string }) =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/phone/link', data),

  /** Всегда 202 — бэкенд не раскрывает, зарегистрирован ли email. */
  requestPasswordReset: (email: string) =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/password-reset/request', { email }),

  /** token приходит пользователю письмом; пароль — по правилам @StrongPassword. */
  confirmPasswordReset: (data: { token: string; newPassword: string; confirmPassword: string }) =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/password-reset/confirm', data),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiClient.put<ApiResponse<void>>('/api/v1/auth/change-password', {
      currentPassword,
      newPassword,
      confirmPassword: newPassword,
    }),

  requestEmailConfirm: () =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/confirm/request'),

  confirmEmail: (token: string) =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/confirm/email', { token }),

  /** Authenticated: письмо со ссылкой подтверждения уходит на newEmail (доказательство владения). */
  requestEmailChange: (newEmail: string) =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/email-change/request', { newEmail }),

  /** Публичный — переход по ссылке из письма на новый адрес. Занят → 409, битый/просроченный токен → 401. */
  confirmEmailChange: (token: string) =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/email-change/confirm', { token }),

  getMe: () =>
    apiClient.get<ApiResponse<UserProfileResponse>>('/api/v1/users/me'),

  updateProfile: (data: UpdateProfileRequest) =>
    apiClient.put<ApiResponse<UserProfileResponse>>('/api/v1/users/me', data),
};
