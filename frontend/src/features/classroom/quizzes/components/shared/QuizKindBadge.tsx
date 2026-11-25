import React from 'react';

interface QuizKindBadgeProps {
  graded: boolean;
  className?: string;
}

export const QuizKindBadge: React.FC<QuizKindBadgeProps> = ({ graded, className = '' }) => {
  if (graded) {
    return (
      <span
        className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200 ${className}`}
      >
        Graded
      </span>
    );
  }

  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-200 ${className}`}
    >
      Practice (Ungraded)
    </span>
  );
};
