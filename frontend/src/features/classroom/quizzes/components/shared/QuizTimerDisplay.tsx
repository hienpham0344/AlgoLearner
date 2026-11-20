import React from 'react';
import { Clock, AlertTriangle } from 'lucide-react';
import { formatRemainingTime } from '../../utils/timeUtils';

interface QuizTimerDisplayProps {
  remainingSeconds: number;
  isUntimed: boolean;
  isLowTime: boolean;
  isUrgent: boolean;
  isExpired: boolean;
  className?: string;
}

export const QuizTimerDisplay: React.FC<QuizTimerDisplayProps> = ({
  remainingSeconds,
  isUntimed,
  isLowTime,
  isUrgent,
  isExpired,
  className = '',
}) => {
  if (isUntimed) {
    return (
      <div
        className={`inline-flex items-center gap-1.5 rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-700 ${className}`}
        aria-label="Untimed quiz"
      >
        <Clock size={14} className="text-slate-500" />
        <span>No time limit</span>
      </div>
    );
  }

  if (isExpired) {
    return (
      <div
        role="alert"
        aria-live="assertive"
        className={`inline-flex items-center gap-1.5 rounded-lg bg-rose-100 px-3 py-1.5 text-xs font-bold text-rose-800 border border-rose-300 ${className}`}
      >
        <AlertTriangle size={14} className="text-rose-600" />
        <span>Time Expired</span>
      </div>
    );
  }

  const urgentClasses = isUrgent
    ? 'bg-rose-50 text-rose-800 border-rose-300 animate-pulse'
    : isLowTime
      ? 'bg-amber-50 text-amber-800 border-amber-300'
      : 'bg-slate-100 text-slate-800 border-slate-200';

  const iconColor = isUrgent
    ? 'text-rose-600'
    : isLowTime
      ? 'text-amber-600'
      : 'text-slate-500';

  return (
    <div
      role="timer"
      aria-live={isLowTime ? 'assertive' : 'off'}
      aria-label={`Time remaining: ${formatRemainingTime(remainingSeconds)}`}
      className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 font-mono text-sm font-bold transition-colors ${urgentClasses} ${className}`}
    >
      <Clock size={15} className={iconColor} aria-hidden="true" />
      <span>{formatRemainingTime(remainingSeconds)}</span>
    </div>
  );
};
