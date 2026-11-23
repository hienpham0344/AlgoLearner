export type ApiSource = 'unknown' | 'backend' | 'mock';
export const API_SOURCE_EVENT = 'algolearner:api-source';

export interface DemoModeInput {
  source: ApiSource;
  mode: string;
  token: string | null;
}

/** Resolve whether the app uses demo data and whether lesson views use local prototype stores. */
export function resolveDemoFlags({ source, mode, token }: DemoModeInput): {
  isDemoMode: boolean;
  isPrototypeDemo: boolean;
} {
  let isDemoMode: boolean;
  if (source === 'backend') isDemoMode = false;
  else if (source === 'mock') isDemoMode = true;
  else if (token) isDemoMode = token.startsWith('mock-');
  else isDemoMode = mode === 'auth-demo' || mode === 'lessons-demo';

  const isPrototypeDemo = mode === 'lessons-demo' ? false : isDemoMode;
  return { isDemoMode, isPrototypeDemo };
}

let source: ApiSource = 'unknown';
const listeners = new Set<() => void>();
export const getApiSource = () => source;
export const subscribeApiSource = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; };
export function observeApiSource(value: unknown) {
  if ((value !== 'backend' && value !== 'mock') || value === source) return;
  source = value;
  listeners.forEach(listener => listener());
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(API_SOURCE_EVENT));
}
