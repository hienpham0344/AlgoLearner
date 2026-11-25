import React from 'react';
import { Flag } from 'lucide-react';
import type { StudentQuestion } from '../../types';

interface QuestionNavPaletteProps {
  questions: StudentQuestion[];
  currentIndex: number;
  answers: Record<string, string[]>;
  flaggedQuestionIds: Set<string>;
  onSelectQuestion: (index: number) => void;
  onToggleFlag: (questionId: string) => void;
  className?: string;
}

export const QuestionNavPalette: React.FC<QuestionNavPaletteProps> = ({
  questions,
  currentIndex,
  answers,
  flaggedQuestionIds,
  onSelectQuestion,
  onToggleFlag,
  className = '',
}) => {
  const currentQuestion = questions[currentIndex];
  const isCurrentFlagged = currentQuestion ? flaggedQuestionIds.has(currentQuestion.id) : false;

  return (
    <aside
      className={`bg-white border border-slate-200/80 rounded-2xl p-4 sm:p-5 shadow-xs flex flex-col gap-4 ${className}`}
      aria-label="Question Navigator"
    >
      <div className="flex items-center justify-between gap-2 border-b border-slate-100 pb-3">
        <h2 className="text-xs font-bold uppercase tracking-wider text-slate-700">
          Questions ({questions.length})
        </h2>
        {currentQuestion && (
          <button
            type="button"
            onClick={() => onToggleFlag(currentQuestion.id)}
            className={`inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-lg border transition-all ${
              isCurrentFlagged
                ? 'bg-amber-50 text-amber-800 border-amber-300'
                : 'bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100'
            }`}
            title="Flag this question to review before submitting"
          >
            <Flag size={12} className={isCurrentFlagged ? 'fill-amber-500 text-amber-600' : 'text-slate-400'} />
            <span>{isCurrentFlagged ? 'Unflag' : 'Flag for review'}</span>
          </button>
        )}
      </div>

      {/* Grid of question buttons */}
      <nav aria-label="Assessment Questions">
        <div className="grid grid-cols-5 sm:grid-cols-6 md:grid-cols-5 gap-2">
          {questions.map((q, idx) => {
            const isAnswered = Boolean(answers[q.id] && answers[q.id].length > 0);
            const isFlagged = flaggedQuestionIds.has(q.id);
            const isCurrent = currentIndex === idx;

            let btnStyle = 'bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100';

            if (isAnswered) {
              btnStyle = 'bg-emerald-50 text-emerald-800 border-emerald-300 font-bold';
            }

            if (isFlagged) {
              btnStyle = isAnswered
                ? 'bg-amber-100 text-amber-900 border-amber-400 font-bold'
                : 'bg-amber-50 text-amber-800 border-amber-300 font-medium';
            }

            if (isCurrent) {
              btnStyle += ' ring-2 ring-indigo-600 ring-offset-1 border-indigo-600 font-extrabold';
            }

            return (
              <button
                key={q.id}
                type="button"
                onClick={() => onSelectQuestion(idx)}
                aria-current={isCurrent ? 'true' : undefined}
                aria-label={`Question ${idx + 1}: ${isAnswered ? 'Answered' : 'Unanswered'}${
                  isFlagged ? ', Flagged' : ''
                }`}
                className={`relative h-10 rounded-xl border text-xs flex items-center justify-center transition-all ${btnStyle}`}
              >
                <span>{idx + 1}</span>
                {isFlagged && (
                  <span className="absolute -top-1 -right-1 w-2.5 h-2.5 bg-amber-500 rounded-full ring-2 ring-white" />
                )}
              </button>
            );
          })}
        </div>
      </nav>

      {/* Palette Legend */}
      <div className="text-[11px] text-slate-500 border-t border-slate-100 pt-3 flex flex-wrap gap-x-4 gap-y-2">
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm bg-emerald-100 border border-emerald-300 inline-block" />
          <span>Answered</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm bg-slate-100 border border-slate-200 inline-block" />
          <span>Unanswered</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm bg-amber-100 border border-amber-300 inline-block" />
          <span>Flagged</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm border-2 border-indigo-600 inline-block" />
          <span>Current</span>
        </div>
      </div>
    </aside>
  );
};
