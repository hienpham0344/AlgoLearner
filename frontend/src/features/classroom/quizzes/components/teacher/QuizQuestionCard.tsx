import React from 'react';
import { CheckCircle2, Circle, Edit2, Trash2, ArrowUp, ArrowDown } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { Question } from '../../types';

interface QuizQuestionCardProps {
  question: Question;
  index: number;
  totalQuestions: number;
  readOnly?: boolean;
  onEdit: (question: Question) => void;
  onDelete: (questionId: string) => void;
  onMoveUp?: (index: number) => void;
  onMoveDown?: (index: number) => void;
}

export const QuizQuestionCard: React.FC<QuizQuestionCardProps> = ({
  question,
  index,
  totalQuestions,
  readOnly = false,
  onEdit,
  onDelete,
  onMoveUp,
  onMoveDown,
}) => {
  const typeLabels = {
    SINGLE_CHOICE: 'Single Choice',
    MULTIPLE_CHOICE: 'Multiple Choice',
    TRUE_FALSE: 'True / False',
  };

  return (
    <div
      className="p-4 bg-white border border-slate-200 rounded-xl shadow-xs hover:border-slate-300 transition-all"
      data-testid={`question-card-${index}`}
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="inline-flex items-center justify-center w-6 h-6 rounded-md bg-slate-100 text-slate-800 text-xs font-bold">
            #{index + 1}
          </span>
          <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
            {typeLabels[question.type]}
          </span>
          <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-slate-100 text-slate-700">
            {question.scoring === 'PARTIAL' ? 'Partial Credit' : 'All-or-Nothing'}
          </span>
          <span className="text-xs font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
            {question.points} {question.points === 1 ? 'pt' : 'pts'}
          </span>
        </div>

        {!readOnly && (
          <div className="flex items-center gap-1">
            {onMoveUp && index > 0 && (
              <button
                type="button"
                onClick={() => onMoveUp(index)}
                className="p-1 text-slate-400 hover:text-slate-700 rounded transition-colors"
                title="Move up"
                aria-label={`Move question ${index + 1} up`}
              >
                <ArrowUp size={14} />
              </button>
            )}
            {onMoveDown && index < totalQuestions - 1 && (
              <button
                type="button"
                onClick={() => onMoveDown(index)}
                className="p-1 text-slate-400 hover:text-slate-700 rounded transition-colors"
                title="Move down"
                aria-label={`Move question ${index + 1} down`}
              >
                <ArrowDown size={14} />
              </button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => onEdit(question)}
              className="h-7 px-2 text-xs text-slate-700 hover:bg-slate-100"
              aria-label={`Edit question ${index + 1}`}
            >
              <Edit2 size={13} className="mr-1" />
              Edit
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => onDelete(question.id)}
              className="h-7 px-2 text-xs text-rose-600 hover:bg-rose-50 hover:border-rose-300"
              aria-label={`Delete question ${index + 1}`}
            >
              <Trash2 size={13} />
            </Button>
          </div>
        )}
      </div>

      <p className="text-sm font-medium text-slate-900 mb-3 whitespace-pre-wrap">{question.prompt}</p>

      <div className="space-y-1.5 pl-2 border-l-2 border-slate-100 mb-2">
        {question.options.map((opt, optIdx) => (
          <div
            key={opt.id}
            className={`flex items-center justify-between gap-2 p-2 rounded-lg text-xs ${
              opt.correct
                ? 'bg-emerald-50/80 text-emerald-900 border border-emerald-200 font-medium'
                : 'bg-slate-50 text-slate-700'
            }`}
          >
            <div className="flex items-center gap-2 min-w-0">
              {opt.correct ? (
                <CheckCircle2 size={14} className="text-emerald-600 shrink-0" aria-label="Correct answer" />
              ) : (
                <Circle size={14} className="text-slate-400 shrink-0" aria-hidden="true" />
              )}
              <span className="font-semibold text-slate-500 mr-1">{String.fromCharCode(65 + optIdx)}.</span>
              <span className="truncate">{opt.text}</span>
            </div>
            {opt.weight !== undefined && opt.weight !== null && (
              <span className="text-[10px] text-slate-500 shrink-0 font-mono">
                weight: {opt.weight}
              </span>
            )}
          </div>
        ))}
      </div>

      {question.explanation && (
        <div className="mt-2 p-2.5 rounded-lg bg-amber-50/60 border border-amber-200 text-xs text-amber-900">
          <strong className="font-semibold">Explanation:</strong> {question.explanation}
        </div>
      )}
    </div>
  );
};
