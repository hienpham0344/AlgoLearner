import React, { createContext, useEffect, useState } from 'react';
import { AuthUser, LoginRequest, RegisterRequest, VerifyOtpRequest } from '../features/auth/types';
import { authApi } from '../features/auth/api/authApi';
import { useQueryClient } from '@tanstack/react-query';
import { TOKEN_REFRESHED_EVENT, UNAUTHORIZED_EVENT } from '../services/apiClient';

export interface AuthContextType {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<{ email: string; message: string }>;
  verifyOtp: (data: VerifyOtpRequest) => Promise<void>;
  quickLogin: (role: 'STUDENT' | 'TEACHER' | 'ADMIN') => Promise<void>;
  logout: () => void;
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
    const updateToken = () => setToken(localStorage.getItem('algolearner_token'));
    window.addEventListener(UNAUTHORIZED_EVENT, expireSession);
    window.addEventListener(TOKEN_REFRESHED_EVENT, updateToken);
    return () => {
      window.removeEventListener(UNAUTHORIZED_EVENT, expireSession);
      window.removeEventListener(TOKEN_REFRESHED_EVENT, updateToken);
    };
  }, [queryClient]);

  useEffect(() => {
    const initAuth = async () => {
      const savedToken = localStorage.getItem('algolearner_token');
      if (savedToken) {
        if (['auth-demo', 'backend-mock'].includes(import.meta.env.MODE) && savedToken.startsWith('mock-')) {
          const storedUser = savedAuthUser();
          if (storedUser) { setUser(storedUser); setIsLoading(false); return; }
        }
        try {
          // 4.1 identity works even if 4.2 profile persistence is unavailable.
          const me = await authApi.getCurrentUser();
          setUser(me);
          setToken(localStorage.getItem('algolearner_token'));
          localStorage.setItem('algolearner_user', JSON.stringify(me));
        } catch {
          // If profile fetch fails but token is valid mock token
          if (import.meta.env.DEV && savedToken.startsWith('mock-')) {
            const role = savedToken.replace('mock-', '').toUpperCase() as 'STUDENT' | 'TEACHER' | 'ADMIN';
            const fallbackUser: AuthUser = {
              id: 'mock-user-id',
              username: role.toLowerCase(),
              email: `${role.toLowerCase()}@example.test`,
              role,
              fullName: role.charAt(0) + role.slice(1).toLowerCase(),
              lifecycleStatus: 'ACTIVE',
            };
            setUser(fallbackUser);
          } else {
            localStorage.removeItem('algolearner_token');
            localStorage.removeItem('algolearner_refresh_token');
            localStorage.removeItem('algolearner_user');
            setUser(null);
            setToken(null);
          }
        }
      }
      setIsLoading(false);
    };

    initAuth();
  }, []);

  const handleAuthSuccess = (newToken: string, newUser: AuthUser, refreshToken?: string) => {
    queryClient.clear();
    setToken(newToken);
    setUser(newUser);
    localStorage.setItem('algolearner_token', newToken);
    if (refreshToken) localStorage.setItem('algolearner_refresh_token', refreshToken);
    else localStorage.removeItem('algolearner_refresh_token');
    localStorage.setItem('algolearner_user', JSON.stringify(newUser));
  };

  const login = async (credentials: LoginRequest) => {
    setIsLoading(true);
    try {
      const res = await authApi.login(credentials);
      handleAuthSuccess(res.token, res.user, res.refreshToken);
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
      handleAuthSuccess(res.token, res.user, res.refreshToken);
    } finally {
      setIsLoading(false);
    }
  };

  const quickLogin = async (role: 'STUDENT' | 'TEACHER' | 'ADMIN') => {
    setIsLoading(true);
    try {
      const res = await authApi.quickLogin(role);
      handleAuthSuccess(res.token, res.user, res.refreshToken);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    const currentToken = localStorage.getItem('algolearner_token');
    const refreshToken = localStorage.getItem('algolearner_refresh_token');
    if (currentToken && refreshToken && !currentToken.startsWith('mock-')) {
      void authApi.logout(currentToken, refreshToken).catch(() => {
        // The local session is cleared even when the server is unavailable.
      });
    }
    queryClient.clear();
    setUser(null);
    setToken(null);
    localStorage.removeItem('algolearner_token');
    localStorage.removeItem('algolearner_refresh_token');
    localStorage.removeItem('algolearner_user');
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
