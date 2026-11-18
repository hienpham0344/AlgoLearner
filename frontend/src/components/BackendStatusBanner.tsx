import { useEffect, useSyncExternalStore } from 'react';
import { getApiSource, observeApiSource, resolveDemoFlags, subscribeApiSource } from '@/services/apiSource';

export const useApiSource = () => useSyncExternalStore(subscribeApiSource, getApiSource);

function useDemoFlags() {
  const source = useApiSource();
  const token = typeof window !== 'undefined' ? window.localStorage.getItem('algolearner_token') : null;
  return resolveDemoFlags({ source, mode: import.meta.env.MODE, token });
}

export const useIsDemoMode = (): boolean => useDemoFlags().isDemoMode;

export const useIsPrototypeDemo = (): boolean => useDemoFlags().isPrototypeDemo;

export function BackendStatusBanner() {
  const source = useApiSource();
  useEffect(() => {
    const controller = new AbortController();
    const check = async () => {
      try {
        const response = await fetch('/api/v1/status', { signal: controller.signal, cache: 'no-store' });
        observeApiSource(response.headers.get('X-Algolearner-Source'));
      } catch { /* Connection errors do not authorize mock data; only the local proxy does. */ }
    };
    void check();
    const interval = setInterval(() => { void check(); }, 5000);
    return () => { controller.abort(); clearInterval(interval); };
  }, []);
  if (source !== 'mock') return null;
  return <div role="status" className="sticky top-0 z-50 bg-amber-100 px-4 py-2 text-center text-sm text-amber-950">
    Backend chưa chạy — đang dùng dữ liệu mẫu, không lưu vào Supabase.
    {' '}Tài khoản mẫu: learner@example.test / DemoPass123!
  </div>;
}
