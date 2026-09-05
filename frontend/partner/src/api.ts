import axios from 'axios';
import { clearPartnerSession } from './authSession';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  // НЕ задаём Content-Type по умолчанию: axios сам ставит application/json для
  // объектов и multipart/form-data (+boundary) для FormData. Явно заданный здесь
  // дефолт json «прилипал» и не снимался для FormData → загрузка логотипа уходила
  // как application/json, media отвечал 415 (HttpMediaTypeNotSupported).
  withCredentials: true, // M4: send httpOnly cookies (refreshToken)
});

// Inject auth token from localStorage
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Redirect to login on 401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearPartnerSession();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
