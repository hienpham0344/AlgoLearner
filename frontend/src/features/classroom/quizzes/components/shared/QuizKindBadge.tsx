import React from 'react';
import type { QuizKind } from '../../types';

interface QuizKindBadgeProps {
  kind: QuizKind;
  className?: string;
}

export const QuizKindBadge: React.FC<QuizKindBadgeProps> = ({ kind, className = '' }) => {
  if (kind === 'GRADED') {
    return (
      <span
        className={`inline-flex items-center rounded-full border border-indigo-200 bg-indigo-50 px-2.5 py-0.5 text-xs font-semibold text-indigo-700 ${className}`}
      >
        Graded
      </span>
    );
  }

  return (
    <span
      className={`inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-2.5 py-0.5 text-xs font-semibold text-amber-700 ${className}`}
    >
      Ungraded
    </span>
  );
};
