import React from 'react';
import { Flag } from 'lucide-react';

interface QuestionNavPaletteProps {
  totalQuestions: number;
  currentIndex: number;
  answeredIndices: Set<number>;
  flaggedIndices: Set<number>;
  onSelectQuestion: (index: number) => void;
  className?: string;
}

export const QuestionNavPalette: React.FC<QuestionNavPaletteProps> = ({
  totalQuestions,
  currentIndex,
  answeredIndices,
  flaggedIndices,
  onSelectQuestion,
  className = '',
}) => {
  return (
    <nav
      aria-label="Question Navigation"
      className={`rounded-xl border border-slate-200 bg-white p-4 shadow-xs ${className}`}
    >
      <div className="flex items-center justify-between pb-3 border-b border-slate-100">
        <h2 className="text-xs font-bold uppercase tracking-wider text-slate-500">
          Question Navigator
        </h2>
        <span className="text-xs font-semibold text-slate-700">
          {answeredIndices.size} / {totalQuestions} answered
        </span>
      </div>

      {/* Grid of buttons */}
      <div className="mt-4 grid grid-cols-5 gap-2 sm:grid-cols-5">
        {Array.from({ length: totalQuestions }, (_, i) => {
          const isCurrent = i === currentIndex;
          const isAnswered = answeredIndices.has(i);
          const isFlagged = flaggedIndices.has(i);

          let buttonStyle = 'border-slate-300 bg-white text-slate-700 hover:border-slate-400';
          if (isCurrent) {
            buttonStyle = 'border-indigo-600 bg-indigo-50 text-indigo-700 ring-2 ring-indigo-600 ring-offset-1 font-bold';
          } else if (isAnswered) {
            buttonStyle = 'border-slate-800 bg-slate-900 text-white font-medium hover:bg-slate-800';
          }

          return (
            <button
              key={i}
              type="button"
              onClick={() => onSelectQuestion(i)}
              aria-current={isCurrent ? 'true' : undefined}
              aria-label={`Question ${i + 1}${isAnswered ? ', answered' : ', unanswered'}${isFlagged ? ', flagged for review' : ''}`}
              className={`relative flex h-9 w-full items-center justify-center rounded-lg border text-xs transition-all ${buttonStyle}`}
            >
              <span>{i + 1}</span>
              {isFlagged && (
                <span
                  title="Flagged for review"
                  className="absolute -top-1 -right-1 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-amber-500 text-white"
                >
                  <Flag size={8} className="fill-white" />
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Legend */}
      <div className="mt-5 space-y-1.5 border-t border-slate-100 pt-3 text-[11px] text-slate-500">
        <div className="flex items-center gap-2">
          <span className="h-3 w-3 rounded-sm bg-slate-900" />
          <span>Answered</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="h-3 w-3 rounded-sm border border-slate-300 bg-white" />
          <span>Unanswered</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="h-3 w-3 rounded-sm border border-indigo-600 bg-indigo-50" />
          <span>Current question</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="flex h-3 w-3 items-center justify-center rounded-full bg-amber-500 text-white">
            <Flag size={6} className="fill-white" />
          </span>
          <span>Flagged for review</span>
        </div>
      </div>
    </nav>
  );
};
