import React from 'react';
import type { QuizStatus } from '../../types';
import { getQuizStatusBadgeConfig } from '../../utils/quizRules';

interface QuizStatusBadgeProps {
  status: QuizStatus;
  className?: string;
}

export const QuizStatusBadge: React.FC<QuizStatusBadgeProps> = ({ status, className = '' }) => {
  const config = getQuizStatusBadgeConfig(status);

  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold border ${config.badgeClass} ${className}`}
      title={config.description}
      aria-label={`Trạng thái bài kiểm tra: ${config.label}`}
    >
      {config.label}
    </span>
  );
};
