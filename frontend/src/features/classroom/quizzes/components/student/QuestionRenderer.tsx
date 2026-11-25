import React from 'react';
import { ArrowLeft, ArrowRight } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { StudentQuestion } from '../../types';

interface QuestionRendererProps {
  question: StudentQuestion;
  questionIndex: number;
  totalQuestions: number;
  selectedOptionIds: string[];
  onSelectOption: (optionId: string, isMultiple: boolean) => void;
  onPrevious: () => void;
  onNext: () => void;
  hasPrevious: boolean;
  hasNext: boolean;
  className?: string;
}

export const QuestionRenderer: React.FC<QuestionRendererProps> = ({
  question,
  questionIndex,
  totalQuestions,
  selectedOptionIds,
  onSelectOption,
  onPrevious,
  onNext,
  hasPrevious,
  hasNext,
  className = '',
}) => {
  const isMultiple = question.type === 'MULTIPLE_CHOICE';

  const getTypeLabel = () => {
    switch (question.type) {
      case 'SINGLE_CHOICE':
        return 'Single Choice';
      case 'MULTIPLE_CHOICE':
        return 'Multiple Choice';
      case 'TRUE_FALSE':
        return 'True / False';
      default:
        return 'Question';
    }
  };

  return (
    <article
      className={`bg-white border border-slate-200/80 rounded-2xl p-6 sm:p-8 shadow-xs flex flex-col justify-between ${className}`}
      aria-label={`Question ${questionIndex + 1}`}
    >
      <div>
        {/* Top Header: Question number & Type & Points */}
        <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold px-2.5 py-1 rounded-lg bg-indigo-50 text-indigo-700 border border-indigo-200">
              Question {questionIndex + 1} of {totalQuestions}
            </span>
            <span className="text-xs font-medium px-2 py-0.5 rounded-md bg-slate-100 text-slate-600">
              {getTypeLabel()}
            </span>
          </div>
          <span className="text-xs font-semibold text-slate-500">
            {question.points} pts
          </span>
        </div>

        {/* Question Prompt */}
        <fieldset className="border-0 p-0 m-0">
          <legend className="text-base sm:text-lg font-bold text-slate-900 leading-relaxed mb-6 block w-full">
            {question.prompt}
          </legend>

          {/* Options List */}
          <div className="space-y-3" role={isMultiple ? 'group' : 'radiogroup'} aria-label="Answer options">
            {question.options.map((opt, optIdx) => {
              const isSelected = selectedOptionIds.includes(opt.id);
              const inputId = `q-${question.id}-opt-${opt.id}`;
              const letter = String.fromCharCode(65 + optIdx); // A, B, C, D...

              return (
                <label
                  key={opt.id}
                  htmlFor={inputId}
                  className={`flex items-start gap-3.5 p-4 rounded-xl border cursor-pointer transition-all ${
                    isSelected
                      ? 'bg-indigo-50/70 border-indigo-400 text-indigo-950 shadow-xs ring-1 ring-indigo-400'
                      : 'bg-white border-slate-200 text-slate-800 hover:bg-slate-50/80 hover:border-slate-300'
                  }`}
                >
                  <input
                    type={isMultiple ? 'checkbox' : 'radio'}
                    id={inputId}
                    name={`question-${question.id}`}
                    value={opt.id}
                    checked={isSelected}
                    onChange={() => onSelectOption(opt.id, isMultiple)}
                    className="mt-0.5 w-4 h-4 text-indigo-600 focus:ring-indigo-500 rounded border-slate-300 shrink-0 cursor-pointer"
                  />

                  <div className="flex items-start gap-2.5 flex-1 min-w-0">
                    <span
                      className={`inline-flex items-center justify-center w-6 h-6 rounded-md text-xs font-bold shrink-0 transition-colors ${
                        isSelected
                          ? 'bg-indigo-600 text-white'
                          : 'bg-slate-100 text-slate-600'
                      }`}
                    >
                      {letter}
                    </span>
                    <span className="text-sm font-medium leading-relaxed break-words">
                      {opt.text}
                    </span>
                  </div>
                </label>
              );
            })}
          </div>
        </fieldset>
      </div>

      {/* Navigation Footer */}
      <footer className="flex items-center justify-between gap-4 mt-8 pt-6 border-t border-slate-100">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onPrevious}
          disabled={!hasPrevious}
          className="rounded-xl text-xs font-semibold border-slate-200 text-slate-700 hover:bg-slate-50 flex items-center gap-1.5"
        >
          <ArrowLeft size={14} className="shrink-0" />
          <span>Previous</span>
        </Button>

        <span className="text-xs text-slate-400 font-mono hidden sm:inline-block">
          {selectedOptionIds.length > 0 ? '✓ Answer selected' : 'Unselected'}
        </span>

        <Button
          type="button"
          size="sm"
          onClick={onNext}
          disabled={!hasNext}
          className="rounded-xl text-xs font-semibold bg-slate-900 text-white hover:bg-slate-800 flex items-center gap-1.5"
        >
          <span>Next</span>
          <ArrowRight size={14} className="shrink-0" />
        </Button>
      </footer>
    </article>
  );
};
