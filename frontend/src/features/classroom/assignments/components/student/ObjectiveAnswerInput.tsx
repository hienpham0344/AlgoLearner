import React from 'react';
import type { OptionView, QuestionType } from '../../types';

interface ObjectiveAnswerInputProps {
  questionType: QuestionType;
  options: OptionView[];
  selectedOptionIds: string[];
  onChange: (selectedIds: string[]) => void;
  disabled?: boolean;
}

export const ObjectiveAnswerInput: React.FC<ObjectiveAnswerInputProps> = ({
  questionType,
  options,
  selectedOptionIds,
  onChange,
  disabled = false,
}) => {
  const isMultiple = questionType === 'MULTIPLE_CHOICE';

  const handleSelect = (optionId: string) => {
    if (disabled) return;

    if (isMultiple) {
      if (selectedOptionIds.includes(optionId)) {
        onChange(selectedOptionIds.filter((id) => id !== optionId));
      } else {
        onChange([...selectedOptionIds, optionId]);
      }
    } else {
      onChange([optionId]);
    }
  };

  return (
    <div
      className="space-y-2.5"
      role={isMultiple ? 'group' : 'radiogroup'}
      aria-label="Answer options"
    >
      {options.map((option, index) => {
        const isSelected = selectedOptionIds.includes(option.id);
        const letter = String.fromCharCode(65 + index);

        return (
          <button
            key={option.id}
            type="button"
            role={isMultiple ? 'checkbox' : 'radio'}
            aria-checked={isSelected}
            disabled={disabled}
            onClick={() => handleSelect(option.id)}
            className={`w-full p-3.5 rounded-xl border text-left flex items-center gap-3 transition-all ${
              isSelected
                ? 'border-slate-900 bg-slate-900/5 text-slate-900 ring-1 ring-slate-900/20 shadow-xs'
                : 'border-slate-200 bg-white hover:border-slate-300 text-slate-800'
            } ${disabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'}`}
          >
            <span
              className={`w-7 h-7 rounded-lg text-xs font-bold flex items-center justify-center shrink-0 border transition-colors ${
                isSelected
                  ? 'bg-slate-900 border-slate-900 text-white'
                  : 'bg-slate-50 border-slate-200 text-slate-600'
              }`}
            >
              {isSelected && !isMultiple ? '✓' : letter}
            </span>

            <span className="text-sm flex-1 leading-snug">{option.content}</span>
          </button>
        );
      })}
    </div>
  );
};
