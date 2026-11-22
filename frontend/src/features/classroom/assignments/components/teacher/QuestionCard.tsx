import React from 'react';
import { Edit2, Trash2, ChevronUp, ChevronDown, Code, CheckSquare, FileText, HelpCircle } from 'lucide-react';
import type { QuestionView } from '../../types';
import { MarkdownRenderer } from '../shared/MarkdownRenderer';

interface QuestionCardProps {
  question: QuestionView;
  index: number;
  total: number;
  onEdit: (question: QuestionView) => void;
  onDelete: (questionId: string) => void;
  onMoveUp?: () => void;
  onMoveDown?: () => void;
  isReadOnly?: boolean;
}

export const QuestionCard: React.FC<QuestionCardProps> = ({
  question,
  index,
  total,
  onEdit,
  onDelete,
  onMoveUp,
  onMoveDown,
  isReadOnly = false,
}) => {
  const getIcon = () => {
    switch (question.questionType) {
      case 'CODE':
        return <Code size={14} className="text-blue-600" />;
      case 'SINGLE_CHOICE':
      case 'MULTIPLE_CHOICE':
      case 'TRUE_FALSE':
        return <CheckSquare size={14} className="text-emerald-600" />;
      case 'ESSAY':
        return <FileText size={14} className="text-amber-600" />;
      default:
        return <HelpCircle size={14} className="text-slate-600" />;
    }
  };

  const getTypeName = () => {
    switch (question.questionType) {
      case 'CODE':
        return `Code (${question.codeLanguage || 'Java'})`;
      case 'SINGLE_CHOICE':
        return 'Single Choice';
      case 'MULTIPLE_CHOICE':
        return 'Multiple Choice';
      case 'TRUE_FALSE':
        return 'True / False';
      case 'ESSAY':
        return `Essay (${question.essayFormat || 'Text/File'})`;
      default:
        return question.questionType;
    }
  };

  return (
    <div className="p-4 rounded-xl border border-slate-200 bg-white hover:border-slate-300 transition-colors shadow-xs">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="w-6 h-6 rounded-lg bg-slate-100 text-slate-800 text-xs font-bold flex items-center justify-center">
            {index + 1}
          </span>
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-slate-100 text-slate-700">
            {getIcon()}
            <span>{getTypeName()}</span>
          </span>
          <h4 className="text-sm font-bold text-slate-900 truncate">{question.title}</h4>
        </div>

        <div className="flex items-center gap-1 shrink-0">
          <span className="text-xs font-bold text-slate-700 mr-2 bg-slate-50 px-2 py-0.5 rounded border border-slate-200">
            {question.points} pts
          </span>

          {!isReadOnly && (
            <>
              {onMoveUp && (
                <button
                  type="button"
                  onClick={onMoveUp}
                  disabled={index === 0}
                  className="p-1 text-slate-400 hover:text-slate-600 disabled:opacity-30 disabled:hover:text-slate-400 rounded"
                  title="Move Up"
                  aria-label="Move question up"
                >
                  <ChevronUp size={16} />
                </button>
              )}
              {onMoveDown && (
                <button
                  type="button"
                  onClick={onMoveDown}
                  disabled={index === total - 1}
                  className="p-1 text-slate-400 hover:text-slate-600 disabled:opacity-30 disabled:hover:text-slate-400 rounded"
                  title="Move Down"
                  aria-label="Move question down"
                >
                  <ChevronDown size={16} />
                </button>
              )}
              <button
                type="button"
                onClick={() => onEdit(question)}
                className="p-1 text-slate-400 hover:text-slate-700 rounded hover:bg-slate-100"
                title="Edit Question"
                aria-label="Edit question"
              >
                <Edit2 size={14} />
              </button>
              <button
                type="button"
                onClick={() => onDelete(question.id)}
                className="p-1 text-slate-400 hover:text-rose-600 rounded hover:bg-rose-50"
                title="Delete Question"
                aria-label="Delete question"
              >
                <Trash2 size={14} />
              </button>
            </>
          )}
        </div>
      </div>

      <div className="mt-2 text-xs text-slate-600 line-clamp-2">
        <MarkdownRenderer content={question.prompt} />
      </div>

      {question.options && question.options.length > 0 && (
        <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-1.5 pt-2 border-t border-slate-100">
          {question.options.map((opt, i) => (
            <div
              key={opt.id || i}
              className={`px-2.5 py-1 rounded text-xs border flex items-center gap-1.5 ${
                opt.isCorrect
                  ? 'bg-emerald-50 text-emerald-800 border-emerald-200 font-medium'
                  : 'bg-slate-50 text-slate-600 border-slate-200'
              }`}
            >
              <span className="text-[10px] font-bold text-slate-400">{String.fromCharCode(65 + i)}.</span>
              <span className="truncate">{opt.content}</span>
              {opt.isCorrect && <span className="ml-auto text-[10px] text-emerald-600 font-bold">✓ Correct</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
