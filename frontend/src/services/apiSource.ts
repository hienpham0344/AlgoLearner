export type ApiSource = 'unknown' | 'backend' | 'mock';
export const API_SOURCE_EVENT = 'algolearner:api-source';
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
