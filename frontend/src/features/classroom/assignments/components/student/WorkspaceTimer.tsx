import React, { useEffect, useState } from 'react';
import { Clock, AlertCircle } from 'lucide-react';
import { formatRemainingTime } from '../../utils/timeUtils';

interface WorkspaceTimerProps {
  initialRemainingSeconds: number;
  onTimeout?: () => void;
  className?: string;
}

export const WorkspaceTimer: React.FC<WorkspaceTimerProps> = ({
  initialRemainingSeconds,
  onTimeout,
  className = '',
}) => {
  const [secondsLeft, setSecondsLeft] = useState(initialRemainingSeconds);

  useEffect(() => {
    setSecondsLeft(initialRemainingSeconds);
  }, [initialRemainingSeconds]);

  useEffect(() => {
    if (secondsLeft <= 0) {
      if (onTimeout) onTimeout();
      return;
    }

    const timer = setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          if (onTimeout) onTimeout();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [secondsLeft, onTimeout]);

  const isLowTime = secondsLeft <= 300 && secondsLeft > 0; // <= 5 minutes
  const isUrgent = secondsLeft <= 60 && secondsLeft > 0; // <= 1 minute

  return (
    <div
      className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-xl text-xs font-bold border transition-colors ${
        isUrgent
          ? 'bg-rose-100 text-rose-800 border-rose-300 animate-pulse'
          : isLowTime
          ? 'bg-amber-50 text-amber-800 border-amber-300'
          : 'bg-slate-100 text-slate-800 border-slate-200'
      } ${className}`}
      role="timer"
      aria-label={`Time remaining: ${formatRemainingTime(secondsLeft)}`}
    >
      {isLowTime ? <AlertCircle size={14} className="text-amber-600 animate-bounce" /> : <Clock size={14} />}
      <span className="font-mono tracking-tight">{formatRemainingTime(secondsLeft)}</span>
      {isLowTime && <span className="text-[10px] uppercase font-extrabold ml-1">Ending soon</span>}
    </div>
  );
};
