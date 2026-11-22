import React from 'react';
import type { QuestionView } from '../../types';

interface QuestionNavPaletteProps {
  questions: QuestionView[];
  currentIndex: number;
  onSelectIndex: (index: number) => void;
  isAnswered: (questionId: string) => boolean;
  className?: string;
}

export const QuestionNavPalette: React.FC<QuestionNavPaletteProps> = ({
  questions,
  currentIndex,
  onSelectIndex,
  isAnswered,
  className = '',
}) => {
  const answeredCount = questions.filter((q) => isAnswered(q.id)).length;

  return (
    <div className={`p-4 bg-white rounded-2xl border border-slate-200 shadow-xs space-y-3 ${className}`}>
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider">Questions</h3>
        <span className="text-xs font-semibold text-slate-500">
          <strong className="text-slate-900">{answeredCount}</strong> / {questions.length} answered
        </span>
      </div>

      <div className="grid grid-cols-5 gap-2">
        {questions.map((q, idx) => {
          const active = idx === currentIndex;
          const answered = isAnswered(q.id);

          return (
            <button
              key={q.id || idx}
              type="button"
              onClick={() => onSelectIndex(idx)}
              className={`h-9 rounded-xl font-bold text-xs flex items-center justify-center transition-all relative border ${
                active
                  ? 'border-slate-900 bg-slate-900 text-white shadow-xs'
                  : answered
                  ? 'border-emerald-300 bg-emerald-50 text-emerald-800 hover:bg-emerald-100'
                  : 'border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100'
              }`}
              aria-label={`Question ${idx + 1}: ${answered ? 'Answered' : 'Unanswered'}${active ? ', Current' : ''}`}
              aria-current={active ? 'true' : undefined}
            >
              <span>{idx + 1}</span>
              {answered && !active && (
                <span className="absolute -top-1 -right-1 w-2.5 h-2.5 bg-emerald-500 rounded-full border-2 border-white" />
              )}
            </button>
          );
        })}
      </div>

      <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-500">
        <div className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
          <span>Answered</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full bg-slate-300" />
          <span>Unanswered</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full bg-slate-900" />
          <span>Current</span>
        </div>
      </div>
    </div>
  );
};
