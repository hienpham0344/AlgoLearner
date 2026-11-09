import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { ApiErrorResponse } from '@/types/api';

export const UNAUTHORIZED_EVENT = 'algolearner:unauthorized';

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
  (response) => response,
  (error: AxiosError<ApiErrorResponse>) => {
    if (error.response?.status === 401) {
      const currentPath = window.location.pathname;
      const token = localStorage.getItem('algolearner_token');
      const requestAuthorization = error.config?.headers?.get('Authorization');
      if (token && requestAuthorization === `Bearer ${token}`
        && !currentPath.startsWith('/login') && !currentPath.startsWith('/register')) {
        localStorage.removeItem('algolearner_token');
        localStorage.removeItem('algolearner_user');
        window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
      }
    }
    return Promise.reject(error);
  }
);
