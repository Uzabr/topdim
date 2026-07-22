import apiClient from './client';
import type { ApiResponse } from './client';

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
  phone?: string;
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
  register: (data: RegisterRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/register', data),

  login: (data: LoginRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/login', data),

  // M4: POST без body — refreshToken приходит из httpOnly cookie
  refresh: () =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/refresh'),

  // M4: POST без body — refreshToken приходит из httpOnly cookie
  logout: () =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/logout'),

  guestAuth: (data: { phone: string; name: string }) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/guest', data),

  telegramAuth: (data: TelegramAuthPayload) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/telegram', data),

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

  getMe: () =>
    apiClient.get<ApiResponse<UserProfileResponse>>('/api/v1/users/me'),

  updateProfile: (data: UpdateProfileRequest) =>
    apiClient.put<ApiResponse<UserProfileResponse>>('/api/v1/users/me', data),
};
