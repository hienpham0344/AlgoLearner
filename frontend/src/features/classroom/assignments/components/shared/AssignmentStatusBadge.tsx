import React from 'react';
import type { AssignmentStatus } from '../../types';
import { getStatusBadgeConfig } from '../../utils/assignmentRules';

interface AssignmentStatusBadgeProps {
  status: AssignmentStatus;
  startTime?: string | null;
  deadline?: string | null;
  className?: string;
}

export const AssignmentStatusBadge: React.FC<AssignmentStatusBadgeProps> = ({
  status,
  startTime,
  deadline,
  className = '',
}) => {
  const { label, variant } = getStatusBadgeConfig(status, startTime, deadline);

  const variantStyles = {
    draft: 'bg-slate-100 text-slate-700 border-slate-300',
    scheduled: 'bg-amber-50 text-amber-800 border-amber-300',
    published: 'bg-emerald-50 text-emerald-800 border-emerald-300',
    closed: 'bg-rose-50 text-rose-800 border-rose-300',
    late: 'bg-orange-50 text-orange-800 border-orange-300',
  };

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${variantStyles[variant]} ${className}`}
      role="status"
    >
      {label}
    </span>
  );
};
