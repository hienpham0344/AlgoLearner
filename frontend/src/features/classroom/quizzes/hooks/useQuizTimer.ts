/**
 * Server-Synchronized Quiz Countdown Timer Hook
 * Backend is the single source of truth.
 * Handles client clock drift, tab focus/blur, network reconnects, and expiry callbacks.
 */

import { useState, useEffect, useRef, useCallback } from 'react';

interface UseQuizTimerOptions {
  serverDeadline?: string | null; // ISO-8601 UTC
  serverTime?: string | null; // ISO-8601 UTC from backend response
  isSubmitted?: boolean;
  onExpire?: () => void;
}

export function useQuizTimer({
  serverDeadline,
  serverTime,
  isSubmitted = false,
  onExpire,
}: UseQuizTimerOptions) {
  const [remainingSeconds, setRemainingSeconds] = useState<number>(() => {
    if (!serverDeadline) return -1; // untimed
    const deadlineMs = new Date(serverDeadline).getTime();
    const serverMs = serverTime ? new Date(serverTime).getTime() : Date.now();
    return Math.max(0, Math.floor((deadlineMs - serverMs) / 1000));
  });

  const [isExpired, setIsExpired] = useState(false);
  const onExpireRef = useRef(onExpire);
  onExpireRef.current = onExpire;

  // Calculate clock drift between client and server:
  // drift = Date.now() - serverTimeMs
  const driftMsRef = useRef<number>(0);
  useEffect(() => {
    if (serverTime) {
      const serverMs = new Date(serverTime).getTime();
      if (!isNaN(serverMs)) {
        driftMsRef.current = Date.now() - serverMs;
      }
    }
  }, [serverTime]);

  const updateRemaining = useCallback(() => {
    if (!serverDeadline || isSubmitted) return;

    const deadlineMs = new Date(serverDeadline).getTime();
    if (isNaN(deadlineMs)) return;

    // Current estimated server time = Date.now() - driftMs
    const currentServerTime = Date.now() - driftMsRef.current;
    const remainingMs = deadlineMs - currentServerTime;
    const remainingSecs = Math.max(0, Math.floor(remainingMs / 1000));

    setRemainingSeconds(remainingSecs);

    if (remainingSecs <= 0 && !isExpired) {
      setIsExpired(true);
      onExpireRef.current?.();
    }
  }, [serverDeadline, isSubmitted, isExpired]);

  // Main countdown interval (every 1 second)
  useEffect(() => {
    if (!serverDeadline || isSubmitted || isExpired) return;

    updateRemaining();
    const interval = setInterval(updateRemaining, 1000);

    return () => clearInterval(interval);
  }, [serverDeadline, isSubmitted, isExpired, updateRemaining]);

  // Revalidate on visibilitychange (tab refocus) and online event
  useEffect(() => {
    if (!serverDeadline || isSubmitted) return;

    const handleVisibilityOrFocus = () => {
      if (document.visibilityState === 'visible') {
        updateRemaining();
      }
    };

    const handleOnline = () => {
      updateRemaining();
    };

    window.addEventListener('visibilitychange', handleVisibilityOrFocus);
    window.addEventListener('focus', handleVisibilityOrFocus);
    window.addEventListener('online', handleOnline);

    return () => {
      window.removeEventListener('visibilitychange', handleVisibilityOrFocus);
      window.removeEventListener('focus', handleVisibilityOrFocus);
      window.removeEventListener('online', handleOnline);
    };
  }, [serverDeadline, isSubmitted, updateRemaining]);

  const isUntimed = !serverDeadline;
  const isLowTime = !isUntimed && remainingSeconds > 0 && remainingSeconds <= 300; // <= 5 minutes
  const isUrgent = !isUntimed && remainingSeconds > 0 && remainingSeconds <= 60; // <= 1 minute

  return {
    remainingSeconds,
    isUntimed,
    isExpired: isExpired || (remainingSeconds === 0 && !isUntimed),
    isLowTime,
    isUrgent,
    revalidateTime: updateRemaining,
  };
}
