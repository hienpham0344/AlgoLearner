import { useState, useEffect, useRef, useCallback } from 'react';
import {
  calculateRemainingSeconds,
  formatCountdown,
  getTimerUrgency,
} from '../utils/quizTimeUtils';

export interface UseQuizTimerOptions {
  cutoffAt?: string | null;
  serverTime?: string | null;
  onTimeout?: () => void;
  enabled?: boolean;
}

export interface UseQuizTimerResult {
  remainingSeconds: number | null;
  formattedTime: string;
  urgency: 'normal' | 'warning' | 'critical' | 'expired';
  isExpired: boolean;
}

export function useQuizTimer({
  cutoffAt,
  serverTime,
  onTimeout,
  enabled = true,
}: UseQuizTimerOptions): UseQuizTimerResult {
  // Calculate initial clock drift offset in ms: (serverTime - clientTime)
  const driftRef = useRef<number>(0);
  const onTimeoutRef = useRef(onTimeout);
  onTimeoutRef.current = onTimeout;

  const timeoutTriggeredRef = useRef<boolean>(false);

  useEffect(() => {
    if (serverTime) {
      const serverMs = new Date(serverTime).getTime();
      if (!isNaN(serverMs)) {
        driftRef.current = serverMs - Date.now();
      }
    }
  }, [serverTime]);

  const computeRemaining = useCallback(() => {
    if (!cutoffAt || !enabled) return null;
    return calculateRemainingSeconds(cutoffAt, driftRef.current);
  }, [cutoffAt, enabled]);

  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(() => computeRemaining());

  useEffect(() => {
    if (!cutoffAt || !enabled) {
      setRemainingSeconds(null);
      return;
    }

    const updateTimer = () => {
      const rem = computeRemaining();
      setRemainingSeconds(rem);

      if (rem !== null && rem <= 0 && !timeoutTriggeredRef.current) {
        timeoutTriggeredRef.current = true;
        onTimeoutRef.current?.();
      }
    };

    // Immediate calculation
    updateTimer();

    const interval = setInterval(updateTimer, 1000);

    // Re-sync on tab visibility / focus change to prevent throttle drift
    const handleSync = () => {
      updateTimer();
    };

    window.addEventListener('visibilitychange', handleSync);
    window.addEventListener('focus', handleSync);

    return () => {
      clearInterval(interval);
      window.removeEventListener('visibilitychange', handleSync);
      window.removeEventListener('focus', handleSync);
    };
  }, [cutoffAt, enabled, computeRemaining]);

  const formattedTime = formatCountdown(remainingSeconds);
  const urgency = getTimerUrgency(remainingSeconds);
  const isExpired = remainingSeconds !== null && remainingSeconds <= 0;

  return {
    remainingSeconds,
    formattedTime,
    urgency,
    isExpired,
  };
}
