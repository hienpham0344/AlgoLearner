import { API_SOURCE_EVENT, getApiSource } from '../services/apiSource';
import React, { createContext, useEffect, useRef, useState } from 'react';
import { AuthUser, LoginRequest, RegisterRequest, VerifyOtpRequest } from '../features/auth/types';
import { authApi } from '../features/auth/api/authApi';
import { useQueryClient } from '@tanstack/react-query';
import { UNAUTHORIZED_EVENT, SESSION_REFRESHED_EVENT, refreshSession, clearSession } from '../services/apiClient';

export interface AuthContextType {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<{ email: string; message: string }>;
  verifyOtp: (data: VerifyOtpRequest) => Promise<void>;
  quickLogin: (role: 'STUDENT' | 'TEACHER' | 'ADMIN') => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

const savedAuthUser = (): AuthUser | null => {
  try {
    const saved = localStorage.getItem('algolearner_user');
    if (!saved) return null;
    const value: unknown = JSON.parse(saved);
    if (typeof value !== 'object' || value === null || !('role' in value) || !('username' in value)
      || typeof value.role !== 'string' || typeof value.username !== 'string') return null;
    return value as AuthUser;
  } catch { return null; }
};

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const queryClient = useQueryClient();
  const lastApiSource = useRef(getApiSource());
  const sessionRestoreStarted = useRef(false);
  const [user, setUser] = useState<AuthUser | null>(savedAuthUser);
  const [token, setToken] = useState<string | null>(() => {
    return localStorage.getItem('algolearner_token');
  });
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const expireSession = () => {
      queryClient.clear();
      setUser(null);
      setToken(null);
    };
    window.addEventListener(UNAUTHORIZED_EVENT, expireSession);
    const sourceChanged = () => {
      const source = getApiSource();
      if (lastApiSource.current !== 'unknown' && lastApiSource.current !== source) queryClient.clear();
      lastApiSource.current = source;
      const saved = localStorage.getItem('algolearner_token');
      if (saved && (saved.startsWith('mock-') !== (source === 'mock'))) clearSession();
    };
    window.addEventListener(API_SOURCE_EVENT, sourceChanged);
    const refreshed = () => {
      setToken(localStorage.getItem('algolearner_token'));
      setUser(savedAuthUser());
    };
    window.addEventListener(SESSION_REFRESHED_EVENT, refreshed);
    return () => {
      window.removeEventListener(UNAUTHORIZED_EVENT, expireSession);
      window.removeEventListener(API_SOURCE_EVENT, sourceChanged);
      window.removeEventListener(SESSION_REFRESHED_EVENT, refreshed);
    };
  }, [queryClient]);

  useEffect(() => {
    if (sessionRestoreStarted.current) return;
    sessionRestoreStarted.current = true;
    const initAuth = async () => {
      let savedToken = localStorage.getItem('algolearner_token');
      if (!savedToken) {
        try { savedToken = (await refreshSession()).token; } catch { /* No active cookie or server unavailable. */ }
      }
      if (savedToken) {
        try {
          const currentUser = await authApi.getCurrentUser();
          setUser(currentUser);
          setToken(savedToken);
          localStorage.setItem('algolearner_user', JSON.stringify(currentUser));
        } catch {
          clearSession();
        }
      }
      setIsLoading(false);
    };

    initAuth();
  }, []);

  const handleAuthSuccess = (newToken: string, newUser: AuthUser) => {
    queryClient.clear();
    setToken(newToken);
    setUser(newUser);
    localStorage.setItem('algolearner_token', newToken);
    localStorage.setItem('algolearner_user', JSON.stringify(newUser));
  };

  const login = async (credentials: LoginRequest) => {
    setIsLoading(true);
    try {
      const res = await authApi.login(credentials);
      handleAuthSuccess(res.token, res.user);
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (data: RegisterRequest) => {
    return await authApi.register(data);
  };

  const verifyOtp = async (data: VerifyOtpRequest) => {
    setIsLoading(true);
    try {
      const res = await authApi.verifyOtp(data);
      handleAuthSuccess(res.token, res.user);
    } finally {
      setIsLoading(false);
    }
  };

  const quickLogin = async (role: 'STUDENT' | 'TEACHER' | 'ADMIN') => {
    setIsLoading(true);
    try {
      const res = await authApi.quickLogin(role);
      handleAuthSuccess(res.token, res.user);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    await authApi.logout();
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: Boolean(token && user),
        isLoading,
        login,
        register,
        verifyOtp,
        quickLogin,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
