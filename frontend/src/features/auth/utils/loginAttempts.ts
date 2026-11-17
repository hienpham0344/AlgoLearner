export interface LoginAttempts {
  attempts: number;
  lockedUntil: number | null;
}

// SRS BR-ACC-011: five rejected credential attempts trigger a 15-minute restriction.
export const LOCKOUT_DURATION_MS = 15 * 60 * 1000;
export const emptyLoginAttempts = (): LoginAttempts => ({ attempts: 0, lockedUntil: null });

export function readLoginAttempts(raw: string | null, now = Date.now()): LoginAttempts {
  if (!raw) return emptyLoginAttempts();
  try {
    const state: unknown = JSON.parse(raw);
    if (typeof state !== 'object' || state === null || !('attempts' in state) || !('lockedUntil' in state)) return emptyLoginAttempts();
    const { attempts, lockedUntil } = state;
    if (typeof attempts !== 'number' || !Number.isInteger(attempts) || attempts < 0 || attempts > 5) return emptyLoginAttempts();
    if (lockedUntil !== null && (typeof lockedUntil !== 'number' || !Number.isFinite(lockedUntil))) return emptyLoginAttempts();
    if (typeof lockedUntil === 'number' && lockedUntil <= now) return emptyLoginAttempts();
    if (attempts === 5 && lockedUntil === null) return emptyLoginAttempts();
    return { attempts, lockedUntil };
  } catch {
    return emptyLoginAttempts();
  }
}

export function nextLoginAttempt(state: LoginAttempts, now = Date.now()): LoginAttempts {
  if (state.lockedUntil && state.lockedUntil > now) return state;
  const attempts = Math.min(5, (state.lockedUntil ? 0 : state.attempts) + 1);
  return { attempts, lockedUntil: attempts === 5 ? now + LOCKOUT_DURATION_MS : null };
}

export function formatLockTime(seconds: number): string {
  const total = Math.max(0, Math.ceil(seconds));
  return `${Math.floor(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}`;
}
