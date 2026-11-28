import React from 'react';
import type { QuizStatus } from '../../types';

interface QuizStatusBadgeProps {
  status: QuizStatus;
  className?: string;
}

export const QuizStatusBadge: React.FC<QuizStatusBadgeProps> = ({ status, className = '' }) => {
  const config: Record<QuizStatus, { label: string; bg: string; text: string; border: string }> = {
    DRAFT: {
      label: 'Draft',
      bg: 'bg-slate-100',
      text: 'text-slate-700',
      border: 'border-slate-300',
    },
    PUBLISHED: {
      label: 'Published',
      bg: 'bg-emerald-50',
      text: 'text-emerald-700',
      border: 'border-emerald-200',
    },
    CLOSED: {
      label: 'Closed',
      bg: 'bg-rose-50',
      text: 'text-rose-700',
      border: 'border-rose-200',
    },
  };

  const current = config[status] || config.DRAFT;

  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${current.bg} ${current.text} ${current.border} ${className}`}
    >
      {current.label}
    </span>
  );
};
