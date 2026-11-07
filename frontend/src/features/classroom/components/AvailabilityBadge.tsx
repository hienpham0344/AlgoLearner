import React from 'react';
import { CheckCircle2, MinusCircle, Clock } from 'lucide-react';

export type AvailabilityStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'SCHEDULED';

interface AvailabilityBadgeProps {
  status: AvailabilityStatus;
  className?: string;
}

export const AvailabilityBadge: React.FC<AvailabilityBadgeProps> = ({
  status,
  className = '',
}) => {
  const configs = {
    AVAILABLE: {
      label: 'Available',
      icon: <CheckCircle2 size={12} className="text-emerald-600 shrink-0" aria-hidden="true" />,
      style: 'bg-emerald-50/80 text-emerald-800 border-emerald-200/80',
    },
    UNAVAILABLE: {
      label: 'Unavailable',
      icon: <MinusCircle size={12} className="text-slate-400 shrink-0" aria-hidden="true" />,
      style: 'bg-slate-100/80 text-slate-600 border-slate-200/80',
    },
    SCHEDULED: {
      label: 'Scheduled',
      icon: <Clock size={12} className="text-amber-600 shrink-0" aria-hidden="true" />,
      style: 'bg-amber-50/80 text-amber-800 border-amber-200/80',
    },
  }[status];

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-semibold border shadow-2xs select-none ${configs.style} ${className}`}
    >
      {configs.icon}
      <span>{configs.label}</span>
    </span>
  );
};
