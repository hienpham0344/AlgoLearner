import React from 'react';
import { Clock, AlertTriangle } from 'lucide-react';
import { formatVietnamDateTime, isPastDeadline } from '../../utils/timeUtils';

interface DeadlineBadgeProps {
  deadline?: string | null;
  allowLate?: boolean;
  className?: string;
}

export const DeadlineBadge: React.FC<DeadlineBadgeProps> = ({
  deadline,
  allowLate = false,
  className = '',
}) => {
  if (!deadline) {
    return (
      <span className={`inline-flex items-center gap-1 text-xs text-slate-500 ${className}`}>
        <Clock size={12} aria-hidden="true" />
        No deadline
      </span>
    );
  }

  const past = isPastDeadline(deadline);
  const formatted = formatVietnamDateTime(deadline);

  if (past) {
    return (
      <span
        className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium border ${
          allowLate
            ? 'bg-amber-50 text-amber-800 border-amber-200'
            : 'bg-rose-50 text-rose-800 border-rose-200'
        } ${className}`}
        role="status"
      >
        <AlertTriangle size={12} aria-hidden="true" />
        {allowLate ? `Ended (${formatted}) - Late accepted` : `Ended (${formatted})`}
      </span>
    );
  }

  return (
    <span className={`inline-flex items-center gap-1 text-xs text-slate-600 ${className}`}>
      <Clock size={12} aria-hidden="true" />
      <span>Due: {formatted}</span>
    </span>
  );
};
