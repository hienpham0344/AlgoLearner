import React from 'react';
import { Clock, AlertTriangle } from 'lucide-react';

interface QuizTimerDisplayProps {
  formattedTime: string;
  urgency: 'normal' | 'warning' | 'critical' | 'expired';
  className?: string;
}

export const QuizTimerDisplay: React.FC<QuizTimerDisplayProps> = ({
  formattedTime,
  urgency,
  className = '',
}) => {
  let badgeStyles = 'bg-slate-100 text-slate-700 border-slate-200';
  let icon = <Clock size={15} className="shrink-0 text-slate-500" />;

  if (urgency === 'warning') {
    badgeStyles = 'bg-amber-50 text-amber-800 border-amber-300';
    icon = <AlertTriangle size={15} className="shrink-0 text-amber-600 animate-bounce" />;
  } else if (urgency === 'critical') {
    badgeStyles = 'bg-rose-50 text-rose-700 border-rose-300 animate-pulse';
    icon = <AlertTriangle size={15} className="shrink-0 text-rose-600" />;
  } else if (urgency === 'expired') {
    badgeStyles = 'bg-rose-100 text-rose-800 border-rose-300';
    icon = <AlertTriangle size={15} className="shrink-0 text-rose-700" />;
  }

  return (
    <div
      role="timer"
      aria-live="polite"
      aria-atomic="true"
      className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-xl border text-xs font-mono font-bold tracking-wide transition-all shadow-xs ${badgeStyles} ${className}`}
    >
      {icon}
      <span>{urgency === 'expired' ? 'Time Expired' : formattedTime}</span>
    </div>
  );
};
