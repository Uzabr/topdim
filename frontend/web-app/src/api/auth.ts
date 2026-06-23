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

  getMe: () =>
    apiClient.get<ApiResponse<UserDto>>('/api/v1/users/me'),

  updateProfile: (data: UpdateProfileRequest) =>
    apiClient.put<ApiResponse<UserDto>>('/api/v1/users/me', data),
};
