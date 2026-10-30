import React, { createContext, useEffect, useState } from 'react';
import { AuthUser, LoginRequest, RegisterRequest, VerifyOtpRequest } from '../features/auth/types';
import { authApi } from '../features/auth/api/authApi';

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

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(() => {
    const saved = localStorage.getItem('algolearner_user');
    return saved ? JSON.parse(saved) : null;
  });
  const [token, setToken] = useState<string | null>(() => {
    return localStorage.getItem('algolearner_token');
  });
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const initAuth = async () => {
      const savedToken = localStorage.getItem('algolearner_token');
      if (savedToken) {
        try {
          // If token exists, try fetching profile from live backend
          const me = await authApi.getCurrentUser();
          setUser(me);
          localStorage.setItem('algolearner_user', JSON.stringify(me));
        } catch {
          // If profile fetch fails but token is valid mock token
          if (savedToken.startsWith('mock-')) {
            const role = savedToken.replace('mock-', '').toUpperCase() as 'STUDENT' | 'TEACHER' | 'ADMIN';
            const fallbackUser: AuthUser = {
              id: 'mock-user-id',
              username: role.toLowerCase(),
              email: `${role.toLowerCase()}@example.test`,
              role,
              fullName: `Demo ${role}`,
              lifecycleStatus: 'ACTIVE',
            };
            setUser(fallbackUser);
          } else {
            localStorage.removeItem('algolearner_token');
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

  const handleAuthSuccess = (newToken: string, newUser: AuthUser) => {
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

  const logout = () => {
    setUser(null);
    setToken(null);
    localStorage.removeItem('algolearner_token');
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
