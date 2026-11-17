import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { ApiErrorResponse, ApiResponse } from '@/types/api';
import type { AuthResponseData } from '@/features/auth/types';

export const UNAUTHORIZED_EVENT = 'algolearner:unauthorized';
export const TOKEN_REFRESHED_EVENT = 'algolearner:token-refreshed';
let refreshInFlight: Promise<string> | null = null;
const PUBLIC_AUTH_ROUTES = new Set([
  '/auth/login', '/auth/register', '/auth/verify-otp', '/auth/resend-otp',
  '/auth/forgot-password', '/auth/reset-password', '/auth/refresh-token',
]);

function clearSession() {
  localStorage.removeItem('algolearner_token');
  localStorage.removeItem('algolearner_refresh_token');
  localStorage.removeItem('algolearner_user');
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
}

export const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('algolearner_token');
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  response => response,
  async (error: AxiosError<ApiErrorResponse>) => {
    const request = error.config as (InternalAxiosRequestConfig & { authRetried?: boolean }) | undefined;
    if (error.response?.status !== 401 || !request || PUBLIC_AUTH_ROUTES.has(request.url || '')) {
      return Promise.reject(error);
    }
    const token = localStorage.getItem('algolearner_token');
    const sentToken = request.headers.get('Authorization');
    if (!token || !sentToken || request.authRetried) {
      if (token && sentToken === `Bearer ${token}`) clearSession();
      return Promise.reject(error);
    }
    request.authRetried = true;
    if (sentToken !== `Bearer ${token}`) {
      request.headers.set('Authorization', `Bearer ${token}`);
      return apiClient(request);
    }
    const refreshToken = localStorage.getItem('algolearner_refresh_token');
    if (!refreshToken) {
      clearSession();
      return Promise.reject(error);
    }
    try {
      if (!refreshInFlight) {
        refreshInFlight = axios.post<ApiResponse<AuthResponseData>>('/api/v1/auth/refresh-token',
          { refreshToken }, { timeout: 15000 })
          .then(response => {
            if (localStorage.getItem('algolearner_refresh_token') !== refreshToken) {
              throw new Error('Session ended while token was refreshing');
            }
            const refreshed = response.data.data;
            localStorage.setItem('algolearner_token', refreshed.token);
            if (refreshed.refreshToken) localStorage.setItem('algolearner_refresh_token', refreshed.refreshToken);
            window.dispatchEvent(new Event(TOKEN_REFRESHED_EVENT));
            return refreshed.token;
          }).finally(() => { refreshInFlight = null; });
      }
      const renewedToken = await refreshInFlight;
      request.headers.set('Authorization', `Bearer ${renewedToken}`);
      return apiClient(request);
    } catch {
      if (localStorage.getItem('algolearner_token') === token) clearSession();
      return Promise.reject(error);
    }
  },
);
