import { useEffect, useState } from 'react';
import { emptyLoginAttempts, nextLoginAttempt, readLoginAttempts, type LoginAttempts } from '../utils/loginAttempts';

function read(key: string): LoginAttempts {
  try { return readLoginAttempts(localStorage.getItem(key)); } catch { return emptyLoginAttempts(); }
}

export function useLoginAttempts(identity: string) {
  const key = `algolearner_login_attempts:${identity.trim().toLowerCase()}`;
  const [attempts, setAttempts] = useState(() => read(key));
  const [now, setNow] = useState(Date.now);

  useEffect(() => {
    setAttempts(read(key));
    setNow(Date.now());
    const sync = (event: StorageEvent) => { if (event.key === key || event.key === null) setAttempts(read(key)); };
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, [key]);

  useEffect(() => {
    if (!attempts.lockedUntil) return;
    const timer = setInterval(() => {
      const time = Date.now();
      setNow(time);
      if (attempts.lockedUntil && time >= attempts.lockedUntil) {
        setAttempts(emptyLoginAttempts());
        try { localStorage.removeItem(key); } catch { /* The server still validates every attempt. */ }
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [attempts.lockedUntil, key]);

  const recordFailure = () => {
    const next = nextLoginAttempt(attempts);
    setAttempts(next);
    setNow(Date.now());
    try { localStorage.setItem(key, JSON.stringify(next)); } catch { /* Storage may be unavailable. */ }
  };
  const clear = () => {
    setAttempts(emptyLoginAttempts());
    try { localStorage.removeItem(key); } catch { /* Storage may be unavailable. */ }
  };

  return { attempts: attempts.attempts, secondsLeft: Math.max(0, Math.ceil(((attempts.lockedUntil ?? now) - now) / 1000)), recordFailure, clear };
}
