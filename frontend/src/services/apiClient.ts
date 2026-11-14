import { observeApiSource } from './apiSource';
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { ApiErrorResponse, ApiResponse } from '@/types/api';
import type { AuthResponseData } from '@/features/auth/types';

export const UNAUTHORIZED_EVENT = 'algolearner:unauthorized';
export const SESSION_REFRESHED_EVENT = 'algolearner:session-refreshed';
const publicAuth = /^\/auth\/(login|register|verify-otp|resend-otp|forgot-password|reset-password|refresh-token|logout)$/;
const sessionClient = axios.create({ baseURL: '/api/v1', timeout: 15000, withCredentials: true,
  headers: { 'X-Requested-With': 'XMLHttpRequest' } });
export const apiClient = axios.create({ baseURL: '/api/v1', timeout: 15000, headers: { 'Content-Type': 'application/json' } });
sessionClient.interceptors.response.use(response => { observeApiSource(response.headers['x-algolearner-source']); return response; }, (error: unknown) => {
  if (axios.isAxiosError(error)) observeApiSource(error.response?.headers['x-algolearner-source']);
  return Promise.reject(error);
});
let pendingRefresh: Promise<AuthResponseData> | null = null;
let generation = 0;

export function clearSession() {
  generation++;
  localStorage.removeItem('algolearner_token');
  localStorage.removeItem('algolearner_user');
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
}

export function refreshSession(): Promise<AuthResponseData> {
  if (!pendingRefresh) {
    const started = generation;
    pendingRefresh = sessionClient.post<ApiResponse<AuthResponseData>>('/auth/refresh-token').then(response => {
      if (started !== generation) throw new Error('Session changed while refreshing.');
      const data = response.data.data;
      localStorage.setItem('algolearner_token', data.token);
      localStorage.setItem('algolearner_user', JSON.stringify(data.user));
      window.dispatchEvent(new Event(SESSION_REFRESHED_EVENT));
      return data;
    }).catch((error: unknown) => {
      if (axios.isAxiosError(error) && error.response?.status === 401 && started === generation) clearSession();
      throw error;
    }).finally(() => { pendingRefresh = null; });
  }
  return pendingRefresh;
}

export async function logoutSession() {
  // Revoke the rotated cookie if an in-flight refresh finishes during sign-out.
  if (pendingRefresh) await pendingRefresh.catch(() => undefined);
  const token = localStorage.getItem('algolearner_token');
  if (token && !token.startsWith('mock-')) {
    try {
      await sessionClient.post('/auth/logout', undefined, { headers: { Authorization: `Bearer ${token}` } });
    } catch (error) {
      // An expired access token must not prevent the refresh cookie from being revoked.
      if (!axios.isAxiosError(error) || error.response?.status !== 401) throw error;
      await sessionClient.post('/auth/logout');
    }
  } else {
    await sessionClient.post('/auth/logout');
  }
  clearSession();
}

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('algolearner_token');
  if (publicAuth.test(config.url || '')) config.headers.delete('Authorization');
  else if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(response => { observeApiSource(response.headers['x-algolearner-source']); return response; }, async (error: AxiosError<ApiErrorResponse>) => {
  observeApiSource(error.response?.headers['x-algolearner-source']);
  const config = error.config as (InternalAxiosRequestConfig & { sessionRetried?: boolean }) | undefined;
  if (error.response?.status !== 401 || !config || publicAuth.test(config.url || '') || config.sessionRetried) throw error;
  const token = localStorage.getItem('algolearner_token');
  if (token?.startsWith('mock-')) { clearSession(); throw error; }
  const sent = config.headers.get('Authorization');
  config.sessionRetried = true;
  if (!token || sent === `Bearer ${token}`) await refreshSession();
  return apiClient.request(config);
});
