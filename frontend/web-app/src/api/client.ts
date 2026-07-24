import axios from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';
import {
  captureSessionGeneration,
  invalidateClientSession,
  isSessionGenerationCurrent,
  runSessionAuthenticationTransport,
  SESSION_AUTH_TRANSPORT_TIMEOUT_MS,
  SessionAuthenticationTransportTimeoutError,
} from '../sessionCleanup';

const API_BASE_URL = import.meta.env.VITE_API_URL || '';

interface SessionRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
  _sessionGeneration?: number;
  _skipAuthRefresh?: boolean;
}

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
    'ngrok-skip-browser-warning': 'true',
  },
  withCredentials: true, // M4: send httpOnly cookies (refreshToken) with requests
});

// Request interceptor — add JWT token from memory
apiClient.interceptors.request.use(
  (config) => {
    const sessionConfig = config as SessionRequestConfig;
    sessionConfig._sessionGeneration ??= captureSessionGeneration();
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    throw error;
  },
  { synchronous: true },
);

// Response interceptor — handle 401 and token refresh
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config as SessionRequestConfig | undefined;

    if (
      error.response?.status === 401
      && originalRequest
      && !originalRequest._retry
      && !originalRequest._skipAuthRefresh
      && Boolean(originalRequest.headers.get('Authorization'))
    ) {
      const requestGeneration = originalRequest._sessionGeneration;
      if (
        requestGeneration == null
        || !isSessionGenerationCurrent(requestGeneration)
      ) {
        return Promise.reject(error);
      }
      originalRequest._retry = true;

      let refreshSignal: AbortSignal | undefined;
      try {
        // M4: POST /refresh без body — refreshToken приходит из httpOnly cookie
        const response = await runSessionAuthenticationTransport((signal) => {
          refreshSignal = signal;
          if (!isSessionGenerationCurrent(requestGeneration)) {
            throw error;
          }
          return axios.post(`${API_BASE_URL}/api/v1/auth/refresh`, null, {
            signal,
            timeout: SESSION_AUTH_TRANSPORT_TIMEOUT_MS,
            withCredentials: true,
          });
        }, { timeoutMs: SESSION_AUTH_TRANSPORT_TIMEOUT_MS });

        if (!isSessionGenerationCurrent(requestGeneration)) {
          return Promise.reject(error);
        }
        const { accessToken } = response.data.data;
        localStorage.setItem('accessToken', accessToken);

        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        const transportTimedOut =
          refreshSignal?.reason
          instanceof SessionAuthenticationTransportTimeoutError;
        if (axios.isCancel(refreshError) && !transportTimedOut) {
          return Promise.reject(error);
        }
        if (!isSessionGenerationCurrent(requestGeneration)) {
          return Promise.reject(error);
        }
        // Only redirect to login if user was previously authenticated
        const hadToken = localStorage.getItem('accessToken');
        invalidateClientSession();
        if (hadToken) {
          window.location.href = '/login';
        }
        return Promise.reject(error);
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}
