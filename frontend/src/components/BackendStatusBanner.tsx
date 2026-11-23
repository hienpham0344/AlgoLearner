import { useEffect, useSyncExternalStore } from 'react';
import { getApiSource, observeApiSource, resolveDemoFlags, subscribeApiSource } from '@/services/apiSource';
import { SESSION_REFRESHED_EVENT, UNAUTHORIZED_EVENT } from '@/services/apiClient';

export const useApiSource = () => useSyncExternalStore(subscribeApiSource, getApiSource, getApiSource);

function getStoredToken(): string | null {
  try {
    if (typeof localStorage !== 'undefined') return localStorage.getItem('algolearner_token');
    if (typeof window !== 'undefined') return window.localStorage.getItem('algolearner_token');
    return null;
  } catch {
    return null;
  }
}

function subscribeStoredToken(listener: () => void): () => void {
  if (typeof window === 'undefined') return () => {};
  const handleStorage = (event: Event) => {
    if (!('key' in event) || event.key === null || event.key === 'algolearner_token') listener();
  };
  window.addEventListener(UNAUTHORIZED_EVENT, listener);
  window.addEventListener(SESSION_REFRESHED_EVENT, listener);
  window.addEventListener('storage', handleStorage);
  return () => {
    window.removeEventListener(UNAUTHORIZED_EVENT, listener);
    window.removeEventListener(SESSION_REFRESHED_EVENT, listener);
    window.removeEventListener('storage', handleStorage);
  };
}

function useDemoFlags() {
  const source = useApiSource();
  const token = useSyncExternalStore(subscribeStoredToken, getStoredToken, getStoredToken);
  return resolveDemoFlags({ source, mode: import.meta.env.MODE, token });
}

export const useIsDemoMode = (): boolean => useDemoFlags().isDemoMode;

export const useIsPrototypeDemo = (): boolean => useDemoFlags().isPrototypeDemo;

export const STATUS_POLL_INTERVAL_MS = 60_000;

export function BackendStatusBanner() {
  const source = useApiSource();
  useEffect(() => {
    const controller = new AbortController();
    const check = async () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return;
      try {
        const response = await fetch('/api/v1/status', { signal: controller.signal, cache: 'no-store' });
        const headerSource = response.headers.get('X-Algolearner-Source');
        if (headerSource) {
          observeApiSource(headerSource);
        } else if (response.ok) {
          const body = (await response.json().catch(() => null)) as { data?: { source?: string } } | null;
          observeApiSource(body?.data?.source);
        }
      } catch { /* Connection errors do not authorize mock data; only the local proxy does. */ }
    };
    const handleVisibilityOrFocus = () => {
      if (typeof document === 'undefined' || document.visibilityState === 'visible') {
        void check();
      }
    };
    void check();
    const interval = setInterval(() => { void check(); }, STATUS_POLL_INTERVAL_MS);
    window.addEventListener('focus', handleVisibilityOrFocus);
    document.addEventListener('visibilitychange', handleVisibilityOrFocus);
    return () => {
      controller.abort();
      clearInterval(interval);
      window.removeEventListener('focus', handleVisibilityOrFocus);
      document.removeEventListener('visibilitychange', handleVisibilityOrFocus);
    };
  }, []);
  if (source !== 'mock') return null;
  return <div role="status" className="sticky top-0 z-50 bg-amber-100 px-4 py-2 text-center text-sm text-amber-950">
    Backend chưa chạy — đang dùng dữ liệu mẫu, không lưu vào Supabase.
    {' '}Tài khoản mẫu: learner@example.test / DemoPass123!
  </div>;
}
