import React from 'react';

interface ScoreDisplayProps {
  score?: number | null;
  maxScore?: number | null;
  isPublished?: boolean;
  isOverridden?: boolean;
  className?: string;
}

export const ScoreDisplay: React.FC<ScoreDisplayProps> = ({
  score,
  maxScore,
  isPublished = true,
  isOverridden = false,
  className = '',
}) => {
  if (!isPublished) {
    return (
      <span className={`text-xs text-slate-500 italic ${className}`}>
        Pending publication
      </span>
    );
  }

  if (score === null || score === undefined) {
    return <span className={`text-xs text-slate-400 ${className}`}>—</span>;
  }

  const formattedScore = Number.isInteger(score) ? score.toString() : score.toFixed(1);
  const formattedMax =
    maxScore !== undefined && maxScore !== null
      ? Number.isInteger(maxScore)
        ? maxScore.toString()
        : maxScore.toFixed(1)
      : null;

  return (
    <span className={`inline-flex items-center gap-1 font-semibold ${className}`}>
      <span className="text-slate-900">{formattedScore}</span>
      {formattedMax !== null && <span className="text-slate-500 font-normal">/ {formattedMax}</span>}
      {isOverridden && (
        <span
          className="px-1.5 py-0.2 bg-amber-100 text-amber-800 text-[10px] font-bold rounded uppercase tracking-wide"
          title="Score manually overridden by teacher"
        >
          Overridden
        </span>
      )}
    </span>
  );
};
