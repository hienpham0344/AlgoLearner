import React from 'react';
import type { QuestionView } from '../../types';

interface QuestionRendererProps {
  question: QuestionView;
  questionNumber: number;
  totalQuestions: number;
  selectedOptionIds: string[];
  disabled?: boolean;
  onSelectOption: (optionId: string) => void;
  className?: string;
}

export const QuestionRenderer: React.FC<QuestionRendererProps> = ({
  question,
  questionNumber,
  totalQuestions,
  selectedOptionIds,
  disabled = false,
  onSelectOption,
  className = '',
}) => {
  const isMultiple = question.type === 'MULTIPLE_CHOICE';

  const typeLabel =
    question.type === 'SINGLE_CHOICE'
      ? 'Single Choice (Select 1)'
      : question.type === 'MULTIPLE_CHOICE'
        ? 'Multiple Choice (Select all that apply)'
        : 'True / False';

  return (
    <div className={`rounded-xl border border-slate-200 bg-white p-6 shadow-xs ${className}`}>
      {/* Question Header */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
        <div className="flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-full bg-slate-900 text-xs font-bold text-white">
            {questionNumber}
          </span>
          <span className="text-xs font-bold text-slate-500">of {totalQuestions}</span>
          <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-700">
            {typeLabel}
          </span>
        </div>
        <span className="text-xs font-bold text-slate-900">
          {question.points} {question.points === 1 ? 'point' : 'points'}
        </span>
      </div>

      {/* Question Text */}
      <div className="mt-4">
        <p className="text-base font-semibold leading-relaxed text-slate-900 whitespace-pre-wrap">
          {question.text}
        </p>
      </div>

      {/* Options */}
      <fieldset className="mt-6 space-y-3" disabled={disabled}>
        <legend className="sr-only">Answer options for question {questionNumber}</legend>

        {question.options.map((option, idx) => {
          const isSelected = selectedOptionIds.includes(option.id);
          const optionLetter = String.fromCharCode(65 + idx); // A, B, C, D...

          return (
            <label
              key={option.id}
              className={`flex items-start gap-3.5 rounded-xl border p-4 cursor-pointer transition-all ${
                isSelected
                  ? 'border-indigo-600 bg-indigo-50/50 shadow-xs'
                  : 'border-slate-200 bg-slate-50/30 hover:border-slate-300 hover:bg-slate-50'
              } ${disabled ? 'cursor-not-allowed opacity-75' : ''}`}
            >
              <div className="flex h-5 items-center">
                <input
                  type={isMultiple ? 'checkbox' : 'radio'}
                  name={`question-${question.id}`}
                  value={option.id}
                  checked={isSelected}
                  disabled={disabled}
                  onChange={() => onSelectOption(option.id)}
                  className={`h-4 w-4 rounded-sm border-slate-300 text-indigo-600 focus:ring-indigo-500 ${
                    !isMultiple ? 'rounded-full' : ''
                  }`}
                />
              </div>

              <div className="flex items-baseline gap-2.5 min-w-0 flex-1 text-sm">
                <span
                  className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-md text-xs font-bold ${
                    isSelected ? 'bg-indigo-600 text-white' : 'bg-slate-200 text-slate-700'
                  }`}
                >
                  {optionLetter}
                </span>
                <span className={`text-sm leading-snug ${isSelected ? 'font-semibold text-slate-900' : 'text-slate-800'}`}>
                  {option.text}
                </span>
              </div>
            </label>
          );
        })}
      </fieldset>
    </div>
  );
};
