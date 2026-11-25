import React, { useState, useEffect } from 'react';
import { X, Plus, Trash2, CheckCircle2, Circle, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { Option, Question, QuestionType, ScoringPolicy } from '../../types';
import { generateClientUuid, validateQuestion } from '../../utils/quizRules';

interface QuizQuestionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (question: Question) => void;
  initialQuestion?: Question | null;
}

export const QuizQuestionModal: React.FC<QuizQuestionModalProps> = ({
  isOpen,
  onClose,
  onSave,
  initialQuestion,
}) => {
  const isEditing = Boolean(initialQuestion);

  const [prompt, setPrompt] = useState('');
  const [type, setType] = useState<QuestionType>('SINGLE_CHOICE');
  const [scoring, setScoring] = useState<ScoringPolicy>('ALL_OR_NOTHING');
  const [points, setPoints] = useState<string>('1');
  const [options, setOptions] = useState<Option[]>([]);
  const [explanation, setExplanation] = useState('');
  const [useCustomWeights, setUseCustomWeights] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (initialQuestion) {
      setPrompt(initialQuestion.prompt || '');
      setType(initialQuestion.type || 'SINGLE_CHOICE');
      setScoring(initialQuestion.scoring || 'ALL_OR_NOTHING');
      setPoints(String(initialQuestion.points ?? 1));
      setOptions(initialQuestion.options ? JSON.parse(JSON.stringify(initialQuestion.options)) : []);
      setExplanation(initialQuestion.explanation || '');
      setUseCustomWeights(initialQuestion.options?.some((o) => o.weight !== null && o.weight !== undefined) ?? false);
    } else {
      // Default empty question
      setPrompt('');
      setType('SINGLE_CHOICE');
      setScoring('ALL_OR_NOTHING');
      setPoints('1');
      setOptions([
        { id: generateClientUuid(), text: '', correct: true, weight: null },
        { id: generateClientUuid(), text: '', correct: false, weight: null },
        { id: generateClientUuid(), text: '', correct: false, weight: null },
        { id: generateClientUuid(), text: '', correct: false, weight: null },
      ]);
      setExplanation('');
      setUseCustomWeights(false);
    }
    setErrors({});
  }, [initialQuestion, isOpen]);

  if (!isOpen) return null;

  const handleTypeChange = (newType: QuestionType) => {
    setType(newType);
    if (newType === 'TRUE_FALSE') {
      setOptions([
        { id: generateClientUuid(), text: 'True', correct: true, weight: null },
        { id: generateClientUuid(), text: 'False', correct: false, weight: null },
      ]);
      setUseCustomWeights(false);
    } else if (type === 'TRUE_FALSE') {
      // Switching back to single or multiple choice
      setOptions([
        { id: generateClientUuid(), text: '', correct: true, weight: null },
        { id: generateClientUuid(), text: '', correct: false, weight: null },
      ]);
    } else if (newType === 'SINGLE_CHOICE') {
      // Ensure only one option is correct
      let foundFirst = false;
      setOptions((prev) =>
        prev.map((opt) => {
          if (opt.correct) {
            if (!foundFirst) {
              foundFirst = true;
              return opt;
            }
            return { ...opt, correct: false };
          }
          return opt;
        })
      );
    }
  };

  const handleToggleCorrect = (index: number) => {
    if (type === 'SINGLE_CHOICE' || type === 'TRUE_FALSE') {
      setOptions((prev) =>
        prev.map((opt, i) => ({
          ...opt,
          correct: i === index,
        }))
      );
    } else {
      // MULTIPLE_CHOICE
      setOptions((prev) =>
        prev.map((opt, i) => (i === index ? { ...opt, correct: !opt.correct } : opt))
      );
    }
  };

  const handleOptionTextChange = (index: number, text: string) => {
    setOptions((prev) =>
      prev.map((opt, i) => (i === index ? { ...opt, text } : opt))
    );
  };

  const handleOptionWeightChange = (index: number, weightStr: string) => {
    const val = weightStr === '' ? null : parseFloat(weightStr);
    setOptions((prev) =>
      prev.map((opt, i) => (i === index ? { ...opt, weight: isNaN(val as number) ? null : val } : opt))
    );
  };

  const handleAddOption = () => {
    setOptions((prev) => [
      ...prev,
      { id: generateClientUuid(), text: '', correct: false, weight: useCustomWeights ? 0 : null },
    ]);
  };

  const handleRemoveOption = (index: number) => {
    if (options.length <= 2) return;
    setOptions((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    const parsedPoints = parseFloat(points);
    const sanitizedOptions: Option[] = options.map((opt) => ({
      id: opt.id || generateClientUuid(),
      text: opt.text.trim(),
      correct: opt.correct,
      weight: useCustomWeights ? (opt.weight ?? 0) : null,
    }));

    const validation = validateQuestion({
      prompt: prompt.trim(),
      type,
      scoring,
      points: parsedPoints,
      options: sanitizedOptions,
    });

    if (Object.keys(validation).length > 0) {
      setErrors(validation);
      return;
    }

    const savedQuestion: Question = {
      id: initialQuestion?.id || generateClientUuid(),
      prompt: prompt.trim(),
      type,
      scoring,
      points: parsedPoints,
      options: sanitizedOptions,
      explanation: explanation.trim() || null,
    };

    onSave(savedQuestion);
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="question-modal-title"
    >
      <div className="bg-white rounded-2xl border border-slate-200 shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden animate-in fade-in-50 zoom-in-95">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50">
          <h2 id="question-modal-title" className="text-base font-bold text-slate-900">
            {isEditing ? 'Edit Question' : 'Add New Question'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-200/50 transition-colors"
            aria-label="Close modal"
          >
            <X size={18} />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-5">
          {Object.keys(errors).length > 0 && (
            <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-start gap-2">
              <AlertCircle size={16} className="shrink-0 mt-0.5" />
              <div className="space-y-1">
                {Object.values(errors).map((err, i) => (
                  <p key={i}>{err}</p>
                ))}
              </div>
            </div>
          )}

          {/* Prompt */}
          <div>
            <label htmlFor="q-prompt" className="block text-xs font-semibold text-slate-700 mb-1">
              Question Prompt <span className="text-rose-500">*</span>
            </label>
            <textarea
              id="q-prompt"
              rows={3}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Enter the question text or problem statement..."
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 placeholder:text-slate-400"
              required
            />
          </div>

          {/* Row: Type, Scoring, Points */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label htmlFor="q-type" className="block text-xs font-semibold text-slate-700 mb-1">
                Question Type
              </label>
              <select
                id="q-type"
                value={type}
                onChange={(e) => handleTypeChange(e.target.value as QuestionType)}
                className="w-full px-3 py-2 text-xs border border-slate-300 rounded-xl bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
              >
                <option value="SINGLE_CHOICE">Single Choice (1 correct)</option>
                <option value="MULTIPLE_CHOICE">Multiple Choice (1+ correct)</option>
                <option value="TRUE_FALSE">True / False</option>
              </select>
            </div>

            <div>
              <label htmlFor="q-scoring" className="block text-xs font-semibold text-slate-700 mb-1">
                Scoring Policy
              </label>
              <select
                id="q-scoring"
                value={scoring}
                onChange={(e) => setScoring(e.target.value as ScoringPolicy)}
                className="w-full px-3 py-2 text-xs border border-slate-300 rounded-xl bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
              >
                <option value="ALL_OR_NOTHING">All-or-Nothing</option>
                <option value="PARTIAL">Partial Credit</option>
              </select>
            </div>

            <div>
              <label htmlFor="q-points" className="block text-xs font-semibold text-slate-700 mb-1">
                Points <span className="text-rose-500">*</span>
              </label>
              <input
                id="q-points"
                type="number"
                min="0.1"
                step="0.5"
                value={points}
                onChange={(e) => setPoints(e.target.value)}
                className="w-full px-3 py-2 text-xs border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                required
              />
            </div>
          </div>

          {/* Partial Credit Weights Toggle */}
          {scoring === 'PARTIAL' && (
            <div className="flex items-center gap-2 pt-1 pb-1">
              <input
                type="checkbox"
                id="custom-weights"
                checked={useCustomWeights}
                onChange={(e) => {
                  const checked = e.target.checked;
                  setUseCustomWeights(checked);
                  if (checked) {
                    const parsed = parseFloat(points) || 1;
                    const correctOpts = options.filter((o) => o.correct);
                    const perOption = correctOpts.length > 0 ? parsed / correctOpts.length : 0;
                    setOptions((prev) =>
                      prev.map((o) => ({
                        ...o,
                        weight: o.correct ? perOption : 0,
                      }))
                    );
                  } else {
                    setOptions((prev) => prev.map((o) => ({ ...o, weight: null })));
                  }
                }}
                className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
              />
              <label htmlFor="custom-weights" className="text-xs text-slate-700 font-medium cursor-pointer">
                Configure explicit custom weights per option (sum of correct option weights must equal points)
              </label>
            </div>
          )}

          {/* Options */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <label className="block text-xs font-semibold text-slate-700">
                Answer Options <span className="text-rose-500">*</span>
              </label>
              {type !== 'TRUE_FALSE' && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleAddOption}
                  className="h-7 text-xs text-[#d64527] border-[#d64527]/30 hover:bg-[#d64527]/10"
                >
                  <Plus size={13} className="mr-1" />
                  Add Option
                </Button>
              )}
            </div>

            <p className="text-[11px] text-slate-500">
              Click the circle next to an option to mark it as correct.
              {type === 'SINGLE_CHOICE' && ' Exactly one must be correct.'}
              {type === 'MULTIPLE_CHOICE' && ' One or more can be correct.'}
            </p>

            <div className="space-y-2">
              {options.map((opt, idx) => (
                <div
                  key={opt.id}
                  className={`flex items-center gap-2 p-2.5 rounded-xl border transition-colors ${
                    opt.correct ? 'bg-emerald-50/50 border-emerald-300' : 'bg-slate-50 border-slate-200'
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => handleToggleCorrect(idx)}
                    className="p-1 text-slate-400 hover:text-emerald-600 transition-colors shrink-0"
                    title={opt.correct ? 'Correct answer' : 'Mark as correct'}
                    aria-label={`Mark option ${String.fromCharCode(65 + idx)} as correct`}
                  >
                    {opt.correct ? (
                      <CheckCircle2 size={18} className="text-emerald-600" />
                    ) : (
                      <Circle size={18} className="text-slate-400" />
                    )}
                  </button>

                  <span className="text-xs font-bold text-slate-600 w-4 shrink-0">
                    {String.fromCharCode(65 + idx)}.
                  </span>

                  <input
                    type="text"
                    value={opt.text}
                    onChange={(e) => handleOptionTextChange(idx, e.target.value)}
                    placeholder={`Option ${String.fromCharCode(65 + idx)} text...`}
                    disabled={type === 'TRUE_FALSE'}
                    className="flex-1 px-3 py-1.5 text-xs bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 disabled:bg-slate-100 disabled:text-slate-600"
                    required
                  />

                  {useCustomWeights && (
                    <div className="flex items-center gap-1 shrink-0">
                      <span className="text-[10px] text-slate-500">wt:</span>
                      <input
                        type="number"
                        min="0"
                        step="0.1"
                        value={opt.weight ?? ''}
                        onChange={(e) => handleOptionWeightChange(idx, e.target.value)}
                        className="w-16 px-2 py-1 text-xs border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                        title="Weight for this option"
                      />
                    </div>
                  )}

                  {type !== 'TRUE_FALSE' && options.length > 2 && (
                    <button
                      type="button"
                      onClick={() => handleRemoveOption(idx)}
                      className="p-1 text-slate-400 hover:text-rose-600 rounded transition-colors shrink-0"
                      aria-label={`Remove option ${String.fromCharCode(65 + idx)}`}
                    >
                      <Trash2 size={15} />
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Explanation */}
          <div>
            <label htmlFor="q-explanation" className="block text-xs font-semibold text-slate-700 mb-1">
              Explanation (Optional)
            </label>
            <textarea
              id="q-explanation"
              rows={2}
              value={explanation}
              onChange={(e) => setExplanation(e.target.value)}
              placeholder="Explain why this is the correct answer. Displayed to students when explanations are released."
              className="w-full px-3 py-2 text-xs border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 placeholder:text-slate-400"
            />
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-200">
            <Button type="button" variant="outline" size="sm" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" size="sm" className="bg-[#d64527] hover:bg-[#be3b20] text-white">
              {isEditing ? 'Save Changes' : 'Add Question'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
