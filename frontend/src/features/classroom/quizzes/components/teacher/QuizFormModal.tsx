import React, { useState } from 'react';
import { X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type {
  QuizConfig,
  QuizKind,
  ScoringMode,
  TimeoutMode,
  CompletionMode,
  PublicationMode,
} from '../../types';
import { localInputToIsoUtc, isoUtcToLocalInput } from '../../utils/timeUtils';

interface QuizFormModalProps {
  isOpen: boolean;
  initialTitle?: string;
  initialConfig?: Partial<QuizConfig>;
  onSave: (title: string, config: Partial<QuizConfig>) => void;
  onClose: () => void;
  isAssessmentFrozen?: boolean;
}

export const QuizFormModal: React.FC<QuizFormModalProps> = ({
  isOpen,
  initialTitle = '',
  initialConfig,
  onSave,
  onClose,
  isAssessmentFrozen = false,
}) => {
  const [title, setTitle] = useState(initialTitle);
  const [kind, setKind] = useState<QuizKind>(initialConfig?.kind || 'GRADED');
  const [maxAttempts, setMaxAttempts] = useState<number>(initialConfig?.maxAttempts || 1);
  const [startTimeLocal, setStartTimeLocal] = useState<string>(
    initialConfig?.startTime ? isoUtcToLocalInput(initialConfig.startTime) : ''
  );
  const [deadlineLocal, setDeadlineLocal] = useState<string>(
    initialConfig?.deadline ? isoUtcToLocalInput(initialConfig.deadline) : ''
  );
  const [allowLateAttempts, setAllowLateAttempts] = useState<boolean>(
    initialConfig?.allowLateAttempts || false
  );
  const [enableTimeLimit, setEnableTimeLimit] = useState<boolean>(
    Boolean(initialConfig?.timeLimitSeconds)
  );
  const [timeLimitMinutes, setTimeLimitMinutes] = useState<number>(() => {
    if (initialConfig?.timeLimitSeconds) {
      return Math.round(initialConfig.timeLimitSeconds / 60);
    }
    return 60;
  });
  const [timeout, setTimeout] = useState<TimeoutMode>(initialConfig?.timeout || 'AUTO_SUBMIT');
  const [shuffleQuestions, setShuffleQuestions] = useState<boolean>(
    initialConfig?.shuffleQuestions || false
  );
  const [shuffleAnswers, setShuffleAnswers] = useState<boolean>(
    initialConfig?.shuffleAnswers || false
  );
  const [scoring, setScoring] = useState<ScoringMode>(initialConfig?.scoring || 'ALL_OR_NOTHING');
  const [completion, setCompletion] = useState<CompletionMode>(
    initialConfig?.completion || 'SUBMIT_IS_ENOUGH'
  );
  const [passingScore, setPassingScore] = useState<number>(
    initialConfig?.passingScore != null ? Number(initialConfig.passingScore) : 80
  );
  const [publication, setPublication] = useState<PublicationMode>(
    initialConfig?.publication || 'AUTO'
  );
  const [showCorrectAnswers, setShowCorrectAnswers] = useState<boolean>(
    initialConfig?.showCorrectAnswers ?? true
  );
  const [showExplanations, setShowExplanations] = useState<boolean>(
    initialConfig?.showExplanations ?? true
  );

  const [validationError, setValidationError] = useState<string>('');

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setValidationError('Quiz title is required.');
      return;
    }

    const startIso = startTimeLocal ? localInputToIsoUtc(startTimeLocal) : null;
    const deadlineIso = deadlineLocal ? localInputToIsoUtc(deadlineLocal) : null;

    if (startIso && deadlineIso) {
      if (new Date(deadlineIso).getTime() <= new Date(startIso).getTime()) {
        setValidationError('Deadline must be strictly after start time.');
        return;
      }
    }

    if (completion === 'PASSING_SCORE' && (passingScore < 0 || passingScore > 100)) {
      setValidationError('Passing score must be between 0 and 100%.');
      return;
    }

    const config: Partial<QuizConfig> = {
      kind,
      maxAttempts: Number(maxAttempts),
      startTime: startIso,
      deadline: deadlineIso,
      allowLateAttempts,
      timeLimitSeconds: enableTimeLimit ? timeLimitMinutes * 60 : null,
      timeout,
      shuffleQuestions,
      shuffleAnswers,
      scoring,
      completion,
      passingScore: completion === 'PASSING_SCORE' ? passingScore : null,
      publication,
      showCorrectAnswers,
      showExplanations,
    };

    onSave(title.trim(), config);
    onClose();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="quiz-form-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs"
    >
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col rounded-xl border border-slate-200 bg-white shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 id="quiz-form-modal-title" className="text-lg font-bold text-slate-900">
            Quiz Settings & Rules
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

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-5">
          {validationError && (
            <div
              role="alert"
              className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs font-semibold text-rose-800"
            >
              {validationError}
            </div>
          )}

          {isAssessmentFrozen && (
            <div
              role="alert"
              className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs font-semibold text-amber-800"
            >
              Assessment configuration is frozen because students have already attempted this quiz. Only title and visibility flags can be modified.
            </div>
          )}

          {/* Title */}
          <div>
            <label htmlFor="quiz-title" className="block text-xs font-bold text-slate-700 mb-1">
              Quiz Title *
            </label>
            <input
              id="quiz-title"
              type="text"
              required
              maxLength={200}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Midterm Quiz: Arrays & Linked Lists"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-slate-800 focus:outline-hidden"
            />
          </div>

          <fieldset disabled={isAssessmentFrozen} className="space-y-5">
            {/* Kind & Max Attempts */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label htmlFor="quiz-kind" className="block text-xs font-bold text-slate-700 mb-1">
                  Quiz Kind
                </label>
                <select
                  id="quiz-kind"
                  value={kind}
                  onChange={(e) => setKind(e.target.value as QuizKind)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900"
                >
                  <option value="GRADED">Graded (Official score contributing to class)</option>
                  <option value="UNGRADED">Ungraded (Practice only)</option>
                </select>
              </div>

              <div>
                <label htmlFor="quiz-max-attempts" className="block text-xs font-bold text-slate-700 mb-1">
                  Maximum Attempts (1 – 10,000)
                </label>
                <input
                  id="quiz-max-attempts"
                  type="number"
                  min="1"
                  max="10000"
                  value={maxAttempts}
                  onChange={(e) => setMaxAttempts(parseInt(e.target.value) || 1)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900"
                  required
                />
              </div>
            </div>

            {/* Schedule (Start Time & Deadline) */}
            <div className="rounded-lg border border-slate-200 bg-slate-50/50 p-4 space-y-3">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Schedule & Deadline (Vietnam UTC+7)
              </h3>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label htmlFor="quiz-start-time" className="block text-xs font-semibold text-slate-700 mb-1">
                    Start Time (Optional)
                  </label>
                  <input
                    id="quiz-start-time"
                    type="datetime-local"
                    value={startTimeLocal}
                    onChange={(e) => setStartTimeLocal(e.target.value)}
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
                  />
                </div>

                <div>
                  <label htmlFor="quiz-deadline" className="block text-xs font-semibold text-slate-700 mb-1">
                    Deadline (Optional)
                  </label>
                  <input
                    id="quiz-deadline"
                    type="datetime-local"
                    value={deadlineLocal}
                    onChange={(e) => setDeadlineLocal(e.target.value)}
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
                  />
                </div>
              </div>

              <div className="flex items-center gap-2 pt-1">
                <input
                  id="quiz-late-attempts"
                  type="checkbox"
                  checked={allowLateAttempts}
                  onChange={(e) => setAllowLateAttempts(e.target.checked)}
                  className="rounded border-slate-300 text-slate-900"
                />
                <label htmlFor="quiz-late-attempts" className="text-xs text-slate-700 font-medium">
                  Allow late attempts after deadline (marked as Late)
                </label>
              </div>
            </div>

            {/* Timer & Timeout Mode */}
            <div className="rounded-lg border border-slate-200 bg-slate-50/50 p-4 space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  Time Limit & Expiry Behavior
                </h3>
                <label className="flex items-center gap-2 text-xs font-semibold text-slate-700 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={enableTimeLimit}
                    onChange={(e) => setEnableTimeLimit(e.target.checked)}
                    className="rounded border-slate-300 text-slate-900"
                  />
                  Enable time limit
                </label>
              </div>

              {enableTimeLimit && (
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 pt-2">
                  <div>
                    <label htmlFor="quiz-duration" className="block text-xs font-semibold text-slate-700 mb-1">
                      Duration in Minutes (1 – 10,080)
                    </label>
                    <input
                      id="quiz-duration"
                      type="number"
                      min="1"
                      max="10080"
                      value={timeLimitMinutes}
                      onChange={(e) => setTimeLimitMinutes(parseInt(e.target.value) || 1)}
                      className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
                      required={enableTimeLimit}
                    />
                  </div>

                  <div>
                    <label htmlFor="quiz-timeout-mode" className="block text-xs font-semibold text-slate-700 mb-1">
                      Timeout Action
                    </label>
                    <select
                      id="quiz-timeout-mode"
                      value={timeout}
                      onChange={(e) => setTimeout(e.target.value as TimeoutMode)}
                      className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
                    >
                      <option value="AUTO_SUBMIT">Auto-submit saved answers</option>
                      <option value="LOCK_WITHOUT_SUBMIT">Lock attempt without submitting</option>
                    </select>
                  </div>
                </div>
              )}
            </div>

            {/* Randomization */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="flex items-center gap-2">
                <input
                  id="quiz-shuffle-q"
                  type="checkbox"
                  checked={shuffleQuestions}
                  onChange={(e) => setShuffleQuestions(e.target.checked)}
                  className="rounded border-slate-300 text-slate-900"
                />
                <label htmlFor="quiz-shuffle-q" className="text-xs text-slate-700 font-semibold">
                  Shuffle questions for each student
                </label>
              </div>

              <div className="flex items-center gap-2">
                <input
                  id="quiz-shuffle-a"
                  type="checkbox"
                  checked={shuffleAnswers}
                  onChange={(e) => setShuffleAnswers(e.target.checked)}
                  className="rounded border-slate-300 text-slate-900"
                />
                <label htmlFor="quiz-shuffle-a" className="text-xs text-slate-700 font-semibold">
                  Shuffle answer options
                </label>
              </div>
            </div>

            {/* Scoring & Completion */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label htmlFor="quiz-scoring-mode" className="block text-xs font-bold text-slate-700 mb-1">
                  Scoring Mode
                </label>
                <select
                  id="quiz-scoring-mode"
                  value={scoring}
                  onChange={(e) => setScoring(e.target.value as ScoringMode)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900"
                >
                  <option value="ALL_OR_NOTHING">All or Nothing</option>
                  <option value="PARTIAL_CREDIT">Partial Credit (Weighted options)</option>
                </select>
              </div>

              <div>
                <label htmlFor="quiz-completion-mode" className="block text-xs font-bold text-slate-700 mb-1">
                  Completion Rule
                </label>
                <select
                  id="quiz-completion-mode"
                  value={completion}
                  onChange={(e) => setCompletion(e.target.value as CompletionMode)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900"
                >
                  <option value="SUBMIT_IS_ENOUGH">Submit is enough</option>
                  <option value="PASSING_SCORE">Must achieve Passing Score</option>
                </select>
              </div>
            </div>

            {completion === 'PASSING_SCORE' && (
              <div>
                <label htmlFor="quiz-passing-score" className="block text-xs font-bold text-slate-700 mb-1">
                  Passing Score Threshold (%)
                </label>
                <input
                  id="quiz-passing-score"
                  type="number"
                  min="0"
                  max="100"
                  step="1"
                  value={passingScore}
                  onChange={(e) => setPassingScore(parseFloat(e.target.value) || 0)}
                  className="w-full sm:w-1/2 rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900"
                  required
                />
              </div>
            )}
          </fieldset>

          {/* Visibility and Publication Flags (Always editable) */}
          <div className="rounded-lg border border-slate-200 bg-slate-50/50 p-4 space-y-3">
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Grade Publication & Student Visibility
            </h3>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label htmlFor="quiz-pub-mode" className="block text-xs font-semibold text-slate-700 mb-1">
                  Grade Publication Mode
                </label>
                <select
                  id="quiz-pub-mode"
                  value={publication}
                  onChange={(e) => setPublication(e.target.value as PublicationMode)}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
                >
                  <option value="AUTO">Auto Publish (Immediately upon submission)</option>
                  <option value="MANUAL">Manual Publish (Only when Teacher publishes)</option>
                </select>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 pt-2">
              <div className="flex items-center gap-2">
                <input
                  id="quiz-show-correct"
                  type="checkbox"
                  checked={showCorrectAnswers}
                  onChange={(e) => setShowCorrectAnswers(e.target.checked)}
                  className="rounded border-slate-300 text-slate-900"
                />
                <label htmlFor="quiz-show-correct" className="text-xs text-slate-700 font-medium">
                  Show correct answers to students after grades are released
                </label>
              </div>

              <div className="flex items-center gap-2">
                <input
                  id="quiz-show-expl"
                  type="checkbox"
                  checked={showExplanations}
                  onChange={(e) => setShowExplanations(e.target.checked)}
                  className="rounded border-slate-300 text-slate-900"
                />
                <label htmlFor="quiz-show-expl" className="text-xs text-slate-700 font-medium">
                  Show explanations to students after grades are released
                </label>
              </div>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 border-t border-slate-200 pt-4">
            <Button variant="outline" type="button" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit">
              Save Settings
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
