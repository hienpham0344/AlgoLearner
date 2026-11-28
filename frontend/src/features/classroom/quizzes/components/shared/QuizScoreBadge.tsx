import React from 'react';
import { Award, CheckCircle2, Clock } from 'lucide-react';
import type { QuizKind } from '../../types';

interface QuizScoreBadgeProps {
  score?: number | null;
  kind?: QuizKind;
  completed?: boolean | null;
  className?: string;
}

export const QuizScoreBadge: React.FC<QuizScoreBadgeProps> = ({
  score,
  kind,
  completed,
  className = '',
}) => {
  if (kind === 'UNGRADED') {
    return (
      <span
        className={`inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-xs font-semibold text-slate-700 ${className}`}
      >
        {completed ? (
          <>
            <CheckCircle2 size={13} className="text-emerald-600" />
            Completed
          </>
        ) : (
          <>
            <Clock size={13} className="text-slate-400" />
            Not completed
          </>
        )}
      </span>
    );
  }

  if (score == null) {
    return (
      <span
        className={`inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-xs font-semibold text-slate-500 ${className}`}
      >
        Pending grade
      </span>
    );
  }

  const numericScore = Number(score);
  const isHigh = numericScore >= 80;
  const isMedium = numericScore >= 50 && numericScore < 80;

  const colorClass = isHigh
    ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
    : isMedium
      ? 'border-amber-200 bg-amber-50 text-amber-800'
      : 'border-rose-200 bg-rose-50 text-rose-800';

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-bold ${colorClass} ${className}`}
    >
      <Award size={13} />
      <span>{numericScore.toFixed(2)}%</span>
      {completed != null && (
        <span className="text-[10px] font-medium opacity-80">
          ({completed ? 'Passed' : 'Failed'})
        </span>
      )}
    </span>
  );
};
