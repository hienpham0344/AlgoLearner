import React from 'react';
import type { AssignmentType } from '../../types';

interface AssignmentTypeBadgeProps {
  type: AssignmentType;
  className?: string;
}

export const AssignmentTypeBadge: React.FC<AssignmentTypeBadgeProps> = ({ type, className = '' }) => {
  const isGraded = type === 'GRADED';

  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${
        isGraded
          ? 'bg-blue-50 text-blue-700 border-blue-200'
          : 'bg-purple-50 text-purple-700 border-purple-200'
      } ${className}`}
    >
      {isGraded ? 'Graded' : 'Ungraded'}
    </span>
  );
};
