import React from 'react';
import { Edit2, Trash2, ArrowUp, ArrowDown, Check, HelpCircle } from 'lucide-react';
import type { QuizQuestion, ScoringMode } from '../../types';

interface QuestionCardProps {
  question: QuizQuestion;
  index: number;
  totalQuestions: number;
  scoringMode: ScoringMode;
  readOnly?: boolean;
  onEdit?: () => void;
  onDelete?: () => void;
  onMoveUp?: () => void;
  onMoveDown?: () => void;
}

export const QuestionCard: React.FC<QuestionCardProps> = ({
  question,
  index,
  totalQuestions,
  scoringMode,
  readOnly = false,
  onEdit,
  onDelete,
  onMoveUp,
  onMoveDown,
}) => {
  const typeLabel =
    question.type === 'SINGLE_CHOICE'
      ? 'Single Choice'
      : question.type === 'MULTIPLE_CHOICE'
        ? 'Multiple Choice'
        : 'True / False';

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-2">
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-slate-900 text-xs font-bold text-white">
              {index + 1}
            </span>
            <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-700">
              {typeLabel}
            </span>
            <span className="text-xs font-bold text-slate-900">
              {question.points} {question.points === 1 ? 'pt' : 'pts'}
            </span>
          </div>
          <p className="font-semibold text-slate-900 whitespace-pre-wrap">{question.text}</p>
        </div>

        {!readOnly && (
          <div className="flex items-center gap-1">
            {onMoveUp && (
              <button
                type="button"
                disabled={index === 0}
                onClick={onMoveUp}
                aria-label={`Move question ${index + 1} up`}
                className="rounded p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-30"
              >
                <ArrowUp size={16} />
              </button>
            )}
            {onMoveDown && (
              <button
                type="button"
                disabled={index === totalQuestions - 1}
                onClick={onMoveDown}
                aria-label={`Move question ${index + 1} down`}
                className="rounded p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-30"
              >
                <ArrowDown size={16} />
              </button>
            )}
            {onEdit && (
              <button
                type="button"
                onClick={onEdit}
                aria-label={`Edit question ${index + 1}`}
                className="rounded p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
              >
                <Edit2 size={16} />
              </button>
            )}
            {onDelete && (
              <button
                type="button"
                onClick={onDelete}
                aria-label={`Delete question ${index + 1}`}
                className="rounded p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600"
              >
                <Trash2 size={16} />
              </button>
            )}
          </div>
        )}
      </div>

      {/* Options List */}
      <div className="mt-4 grid gap-2">
        {question.options.map((opt) => (
          <div
            key={opt.id}
            className={`flex items-center justify-between rounded-lg border px-3 py-2 text-sm ${
              opt.correct
                ? 'border-emerald-300 bg-emerald-50/50 text-slate-900 font-medium'
                : 'border-slate-200 bg-slate-50/50 text-slate-700'
            }`}
          >
            <div className="flex items-center gap-2">
              <span
                className={`flex h-4 w-4 items-center justify-center rounded-full text-[10px] ${
                  opt.correct ? 'bg-emerald-600 text-white' : 'border border-slate-300 bg-white'
                }`}
              >
                {opt.correct && <Check size={11} className="stroke-[3]" />}
              </span>
              <span>{opt.text}</span>
            </div>

            {scoringMode === 'PARTIAL_CREDIT' && opt.weight != null && (
              <span className="text-xs font-mono font-semibold text-slate-500">
                Weight: {opt.weight}
              </span>
            )}
          </div>
        ))}
      </div>

      {/* Explanation */}
      {question.explanation && (
        <div className="mt-3 flex items-start gap-1.5 text-xs text-slate-500 bg-slate-50 p-2.5 rounded-lg border border-slate-200">
          <HelpCircle size={14} className="mt-0.5 shrink-0 text-slate-400" />
          <div>
            <strong className="font-semibold text-slate-700">Explanation: </strong>
            <span>{question.explanation}</span>
          </div>
        </div>
      )}
    </div>
  );
};
