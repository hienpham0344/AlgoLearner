import React, { useState } from 'react';
import { X, Plus, Trash2, CheckCircle2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { QuizQuestion, QuizOption, QuestionType, ScoringMode } from '../../types';
import { validateQuestion } from '../../utils/quizValidation';

interface QuestionAuthoringModalProps {
  isOpen: boolean;
  scoringMode: ScoringMode;
  question?: QuizQuestion | null;
  onSave: (question: QuizQuestion) => void;
  onClose: () => void;
}

const generateUUID = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

export const QuestionAuthoringModal: React.FC<QuestionAuthoringModalProps> = ({
  isOpen,
  scoringMode,
  question,
  onSave,
  onClose,
}) => {
  const [text, setText] = useState<string>(question?.text || '');
  const [type, setType] = useState<QuestionType>(question?.type || 'SINGLE_CHOICE');
  const [points, setPoints] = useState<number>(question?.points || 1.0);
  const [explanation, setExplanation] = useState<string>(question?.explanation || '');
  const [options, setOptions] = useState<QuizOption[]>(() => {
    if (question?.options && question.options.length >= 2) {
      return question.options.map((o) => ({ ...o }));
    }
    return [
      { id: generateUUID(), text: '', correct: true, weight: scoringMode === 'PARTIAL_CREDIT' ? 1.0 : null },
      { id: generateUUID(), text: '', correct: false, weight: scoringMode === 'PARTIAL_CREDIT' ? 0.0 : null },
    ];
  });

  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});

  if (!isOpen) return null;

  const handleTypeChange = (newType: QuestionType) => {
    setType(newType);
    if (newType === 'TRUE_FALSE') {
      setOptions([
        { id: generateUUID(), text: 'True', correct: true, weight: scoringMode === 'PARTIAL_CREDIT' ? points : null },
        { id: generateUUID(), text: 'False', correct: false, weight: scoringMode === 'PARTIAL_CREDIT' ? 0 : null },
      ]);
    } else if (options.length < 2) {
      setOptions([
        { id: generateUUID(), text: '', correct: true, weight: null },
        { id: generateUUID(), text: '', correct: false, weight: null },
      ]);
    }
  };

  const handleOptionCorrectChange = (index: number) => {
    if (type === 'SINGLE_CHOICE' || type === 'TRUE_FALSE') {
      setOptions((prev) =>
        prev.map((opt, i) => ({
          ...opt,
          correct: i === index,
          weight: scoringMode === 'PARTIAL_CREDIT' ? (i === index ? points : 0) : opt.weight,
        }))
      );
    } else {
      // MULTIPLE_CHOICE
      setOptions((prev) =>
        prev.map((opt, i) => (i === index ? { ...opt, correct: !opt.correct } : opt))
      );
    }
  };

  const handleAddOption = () => {
    if (options.length >= 20) return;
    setOptions((prev) => [
      ...prev,
      {
        id: generateUUID(),
        text: '',
        correct: false,
        weight: scoringMode === 'PARTIAL_CREDIT' ? 0 : null,
      },
    ]);
  };

  const handleRemoveOption = (index: number) => {
    if (options.length <= 2) return;
    setOptions((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSave = () => {
    const candidate: QuizQuestion = {
      id: question?.id || generateUUID(),
      text: text.trim(),
      type,
      points: Number(points),
      explanation: explanation.trim() ? explanation.trim() : null,
      options: options.map((opt) => ({
        id: opt.id,
        text: opt.text.trim(),
        correct: opt.correct,
        weight: scoringMode === 'PARTIAL_CREDIT' ? Number(opt.weight ?? 0) : null,
      })),
    };

    const res = validateQuestion(candidate, scoringMode);
    if (!res.isValid) {
      setValidationErrors(res.errors);
      return;
    }

    setValidationErrors({});
    onSave(candidate);
    onClose();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="question-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs"
    >
      <div className="flex max-h-[90vh] w-full max-w-2xl flex-col rounded-xl border border-slate-200 bg-white shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 id="question-modal-title" className="text-lg font-bold text-slate-900">
            {question ? 'Edit Question' : 'Add Question'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
          >
            <X size={18} />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {Object.keys(validationErrors).length > 0 && (
            <div
              role="alert"
              className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs font-semibold text-rose-800"
            >
              Please fix the errors below:
              <ul className="mt-1 list-disc pl-4 font-normal">
                {Object.entries(validationErrors).map(([k, err]) => (
                  <li key={k}>{err}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Type & Points */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="q-type" className="block text-xs font-bold text-slate-700 mb-1">
                Question Type
              </label>
              <select
                id="q-type"
                value={type}
                onChange={(e) => handleTypeChange(e.target.value as QuestionType)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900"
              >
                <option value="SINGLE_CHOICE">Single Choice (1 correct answer)</option>
                <option value="MULTIPLE_CHOICE">Multiple Choice (1+ correct answers)</option>
                <option value="TRUE_FALSE">True / False</option>
              </select>
            </div>

            <div>
              <label htmlFor="q-points" className="block text-xs font-bold text-slate-700 mb-1">
                Points (0.01 – 1,000)
              </label>
              <input
                id="q-points"
                type="number"
                min="0.01"
                max="1000"
                step="0.5"
                value={points}
                onChange={(e) => setPoints(parseFloat(e.target.value) || 0)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900"
                required
              />
            </div>
          </div>

          {/* Question Text */}
          <div>
            <label htmlFor="q-text" className="block text-xs font-bold text-slate-700 mb-1">
              Question Text *
            </label>
            <textarea
              id="q-text"
              rows={3}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Enter question prompt..."
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-slate-800 focus:outline-hidden"
              required
            />
          </div>

          {/* Options Section */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="block text-xs font-bold text-slate-700">
                Answer Options ({options.length}/20)
              </label>
              {type !== 'TRUE_FALSE' && options.length < 20 && (
                <button
                  type="button"
                  onClick={handleAddOption}
                  className="inline-flex items-center gap-1 text-xs font-semibold text-slate-700 hover:text-slate-900 hover:underline"
                >
                  <Plus size={14} /> Add option
                </button>
              )}
            </div>

            <div className="space-y-2">
              {options.map((opt, idx) => (
                <div
                  key={opt.id}
                  className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 p-2.5"
                >
                  {/* Correct Toggle */}
                  <button
                    type="button"
                    onClick={() => handleOptionCorrectChange(idx)}
                    title={opt.correct ? 'Marked as correct' : 'Mark as correct'}
                    className={`flex h-7 w-7 items-center justify-center rounded-full border transition-colors ${
                      opt.correct
                        ? 'border-emerald-500 bg-emerald-500 text-white'
                        : 'border-slate-300 bg-white text-slate-400 hover:border-slate-400'
                    }`}
                  >
                    <CheckCircle2 size={16} />
                  </button>

                  {/* Option Text Input */}
                  <input
                    type="text"
                    value={opt.text}
                    disabled={type === 'TRUE_FALSE'}
                    onChange={(e) => {
                      const val = e.target.value;
                      setOptions((prev) =>
                        prev.map((o, i) => (i === idx ? { ...o, text: val } : o))
                      );
                    }}
                    placeholder={`Option ${idx + 1}`}
                    className="flex-1 rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-900"
                    required
                  />

                  {/* Option Weight (if Partial Credit) */}
                  {scoringMode === 'PARTIAL_CREDIT' && (
                    <div className="w-24">
                      <input
                        type="number"
                        min="0"
                        step="0.25"
                        value={opt.weight ?? ''}
                        onChange={(e) => {
                          const w = parseFloat(e.target.value);
                          setOptions((prev) =>
                            prev.map((o, i) => (i === idx ? { ...o, weight: isNaN(w) ? null : w } : o))
                          );
                        }}
                        placeholder="Weight"
                        title="Points awarded for selecting this option"
                        className="w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-xs text-slate-900"
                      />
                    </div>
                  )}

                  {/* Delete Option (min 2 required) */}
                  {type !== 'TRUE_FALSE' && options.length > 2 && (
                    <button
                      type="button"
                      onClick={() => handleRemoveOption(idx)}
                      title="Remove option"
                      className="rounded p-1 text-slate-400 hover:text-rose-600"
                    >
                      <Trash2 size={15} />
                    </button>
                  )}
                </div>
              ))}
            </div>
            {scoringMode === 'PARTIAL_CREDIT' && (
              <p className="text-[11px] text-slate-500">
                Partial credit mode: specify points for each option. Total correct option weights must equal question points ({points}).
              </p>
            )}
          </div>

          {/* Explanation */}
          <div>
            <label htmlFor="q-expl" className="block text-xs font-bold text-slate-700 mb-1">
              Explanation (shown after grade publication)
            </label>
            <textarea
              id="q-expl"
              rows={2}
              value={explanation}
              onChange={(e) => setExplanation(e.target.value)}
              placeholder="Explain why the answer is correct..."
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-slate-800 focus:outline-hidden"
            />
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 border-t border-slate-200 px-6 py-4">
          <Button variant="outline" type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button type="button" onClick={handleSave}>
            Save Question
          </Button>
        </div>
      </div>
    </div>
  );
};
