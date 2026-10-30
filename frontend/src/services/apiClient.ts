import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { ApiErrorResponse } from '@/types/api';

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
      // Clear token if expired or invalid
      const currentPath = window.location.pathname;
      if (!currentPath.startsWith('/login') && !currentPath.startsWith('/register')) {
        localStorage.removeItem('algolearner_token');
        localStorage.removeItem('algolearner_user');
      }
    }
    return Promise.reject(error);
  }
);
