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
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserDto;
}

export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
  avatarUrl?: string;
}

export const authApi = {
  register: (data: RegisterRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/register', data),

  login: (data: LoginRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/login', data),

  refresh: (refreshToken: string) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/refresh', { refreshToken }),

  logout: (refreshToken: string) =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/logout', { refreshToken }),

  guestAuth: (data: { phone: string; name: string }) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/guest', data),

  getMe: () =>
    apiClient.get<ApiResponse<UserDto>>('/api/v1/users/me'),

  updateProfile: (data: UpdateProfileRequest) =>
    apiClient.put<ApiResponse<UserDto>>('/api/v1/users/me', data),
};
