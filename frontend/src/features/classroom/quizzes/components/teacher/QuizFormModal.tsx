import React, { useState } from 'react';
import { X, Calendar, Settings, ShieldCheck, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type {
  Question,
  QuizConfigureRequest,
  ReleasePolicy,
  TimeoutMode,
} from '../../types';
import { isoUtcToLocalInput, localInputToIsoUtc } from '../../utils/quizTimeUtils';
import { generateClientUuid, validateQuizConfig } from '../../utils/quizRules';

interface QuizFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: QuizConfigureRequest) => Promise<void>;
  initialData?: QuizConfigureRequest | null;
  isLoading?: boolean;
}

export const QuizFormModal: React.FC<QuizFormModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  initialData,
  isLoading = false,
}) => {
  const isEditing = Boolean(initialData);

  const [activeTab, setActiveTab] = useState<'BASIC' | 'SCHEDULE' | 'RULES'>('BASIC');

  // Form states
  const [title, setTitle] = useState(initialData?.title || '');
  const [graded, setGraded] = useState(initialData?.graded ?? true);
  const [passingScore, setPassingScore] = useState<string>(
    initialData?.passingScore !== undefined && initialData?.passingScore !== null
      ? String(initialData.passingScore)
      : ''
  );

  const [opensAt, setOpensAt] = useState(isoUtcToLocalInput(initialData?.opensAt));
  const [closesAt, setClosesAt] = useState(isoUtcToLocalInput(initialData?.closesAt));
  const [durationMinutes, setDurationMinutes] = useState<string>(
    initialData?.durationSeconds !== undefined && initialData?.durationSeconds !== null
      ? String(Math.floor(initialData.durationSeconds / 60))
      : ''
  );
  const [allowLate, setAllowLate] = useState(initialData?.allowLate ?? false);

  const [maxAttempts, setMaxAttempts] = useState<string>(
    initialData?.maxAttempts !== undefined && initialData?.maxAttempts !== null
      ? String(initialData.maxAttempts)
      : '1'
  );
  const [timeoutMode, setTimeoutMode] = useState<TimeoutMode>(initialData?.timeoutMode || 'AUTO_SUBMIT');
  const [shuffleQuestions, setShuffleQuestions] = useState(initialData?.shuffleQuestions ?? false);
  const [shuffleAnswers, setShuffleAnswers] = useState(initialData?.shuffleAnswers ?? false);
  const [releasePolicy, setReleasePolicy] = useState<ReleasePolicy>(initialData?.releasePolicy || 'MANUAL');
  const [showCorrectAnswers, setShowCorrectAnswers] = useState(initialData?.showCorrectAnswers ?? false);
  const [showExplanations, setShowExplanations] = useState(initialData?.showExplanations ?? false);

  const [questions] = useState<Question[]>(() => {
    if (initialData?.questions && initialData.questions.length > 0) {
      return initialData.questions;
    }
    return [
      {
        id: generateClientUuid(),
        prompt: 'Sample Question 1',
        type: 'SINGLE_CHOICE',
        scoring: 'ALL_OR_NOTHING',
        points: 1,
        options: [
          { id: generateClientUuid(), text: 'Option A', correct: true, weight: null },
          { id: generateClientUuid(), text: 'Option B', correct: false, weight: null },
        ],
      },
    ];
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const parsedAttempts = parseInt(maxAttempts, 10);
    const parsedDurationMin = durationMinutes.trim() ? parseInt(durationMinutes, 10) : null;
    const durationSeconds = parsedDurationMin !== null ? parsedDurationMin * 60 : null;
    const parsedPassing = passingScore.trim() ? parseFloat(passingScore) : null;

    const finalQuestions: Question[] =
      questions && questions.length > 0
        ? questions
        : [
            {
              id: generateClientUuid(),
              prompt: 'Sample Question 1',
              type: 'SINGLE_CHOICE',
              scoring: 'ALL_OR_NOTHING',
              points: 1,
              options: [
                { id: generateClientUuid(), text: 'Option A', correct: true, weight: null },
                { id: generateClientUuid(), text: 'Option B', correct: false, weight: null },
              ],
            },
          ];

    const validation = validateQuizConfig({
      title: title.trim(),
      graded,
      opensAt: localInputToIsoUtc(opensAt),
      closesAt: localInputToIsoUtc(closesAt),
      durationSeconds,
      maxAttempts: isNaN(parsedAttempts) ? 0 : parsedAttempts,
      passingScore: parsedPassing,
      questions: finalQuestions,
    });

    if (Object.keys(validation).length > 0) {
      setErrors(validation);
      return;
    }

    const payload: QuizConfigureRequest = {
      title: title.trim(),
      graded,
      opensAt: localInputToIsoUtc(opensAt),
      closesAt: localInputToIsoUtc(closesAt),
      durationSeconds,
      maxAttempts: parsedAttempts,
      allowLate,
      shuffleQuestions,
      shuffleAnswers,
      releasePolicy,
      timeoutMode,
      questions: finalQuestions,
      showCorrectAnswers,
      showExplanations,
      passingScore: parsedPassing,
    };

    setErrors({});
    await onSubmit(payload);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="quiz-form-modal-title"
    >
      <div className="bg-white rounded-2xl border border-slate-200 shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden animate-in fade-in-50 zoom-in-95">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50">
          <div>
            <h2 id="quiz-form-modal-title" className="text-base font-bold text-slate-900">
              {isEditing ? 'Configure Quiz Settings' : 'Create New Quiz Draft'}
            </h2>
            <p className="text-xs text-slate-500">
              Define quiz parameters, schedule limits, and release policies.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Tab Nav */}
        <div className="flex border-b border-slate-200 px-6 bg-slate-50/50">
          <button
            type="button"
            onClick={() => setActiveTab('BASIC')}
            className={`py-2.5 px-4 text-xs font-semibold border-b-2 flex items-center gap-1.5 transition-colors ${
              activeTab === 'BASIC'
                ? 'border-[#d64527] text-[#d64527]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Settings size={14} />
            Basic Info &amp; Grading
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('SCHEDULE')}
            className={`py-2.5 px-4 text-xs font-semibold border-b-2 flex items-center gap-1.5 transition-colors ${
              activeTab === 'SCHEDULE'
                ? 'border-[#d64527] text-[#d64527]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Calendar size={14} />
            Schedule &amp; Time
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('RULES')}
            className={`py-2.5 px-4 text-xs font-semibold border-b-2 flex items-center gap-1.5 transition-colors ${
              activeTab === 'RULES'
                ? 'border-[#d64527] text-[#d64527]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <ShieldCheck size={14} />
            Attempts &amp; Visibility
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

          {activeTab === 'BASIC' && (
            <div className="space-y-4">
              <div>
                <label htmlFor="quiz-title" className="block text-xs font-semibold text-slate-700 mb-1">
                  Quiz Title <span className="text-rose-500">*</span>
                </label>
                <input
                  id="quiz-title"
                  type="text"
                  maxLength={255}
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="e.g., Module 3 Checkpoint: Graph Algorithms"
                  className="w-full px-3 py-2 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 placeholder:text-slate-400"
                  required
                />
              </div>

              <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <strong className="text-xs font-bold text-slate-800 block">
                      Quiz Type: {graded ? 'Graded Evaluation' : 'Ungraded Practice'}
                    </strong>
                    <span className="text-[11px] text-slate-500">
                      {graded
                        ? 'Scores are recorded and contribute to official student gradebook.'
                        : 'Practice only. No scores contribute to overall gradebook.'}
                    </span>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={graded}
                      onChange={(e) => setGraded(e.target.checked)}
                      className="sr-only peer"
                    />
                    <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#d64527]"></div>
                  </label>
                </div>

                {graded && (
                  <div className="pt-2 border-t border-slate-200">
                    <label htmlFor="passing-score" className="block text-xs font-semibold text-slate-700 mb-1">
                      Passing Score Threshold (0 - 100%, Optional)
                    </label>
                    <input
                      id="passing-score"
                      type="number"
                      min="0"
                      max="100"
                      step="1"
                      value={passingScore}
                      onChange={(e) => setPassingScore(e.target.value)}
                      placeholder="Leave empty for 'Submit is Enough'"
                      className="w-full sm:w-1/2 px-3 py-1.5 text-xs border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 bg-white"
                    />
                    <span className="text-[10px] text-slate-500 mt-1 block">
                      If configured, students must score at least this percentage to mark completion.
                    </span>
                  </div>
                )}
              </div>
            </div>
          )}

          {activeTab === 'SCHEDULE' && (
            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="opens-at" className="block text-xs font-semibold text-slate-700 mb-1">
                    Opening Time (Start Time, UTC+7)
                  </label>
                  <input
                    id="opens-at"
                    type="datetime-local"
                    value={opensAt}
                    onChange={(e) => setOpensAt(e.target.value)}
                    className="w-full px-3 py-2 text-xs border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 bg-white"
                  />
                  <span className="text-[10px] text-slate-500 mt-1 block">
                    Students cannot begin before this date/time.
                  </span>
                </div>

                <div>
                  <label htmlFor="closes-at" className="block text-xs font-semibold text-slate-700 mb-1">
                    Closing Time (Deadline, UTC+7)
                  </label>
                  <input
                    id="closes-at"
                    type="datetime-local"
                    value={closesAt}
                    onChange={(e) => setClosesAt(e.target.value)}
                    className="w-full px-3 py-2 text-xs border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 bg-white"
                  />
                  <span className="text-[10px] text-slate-500 mt-1 block">
                    Strict deadline for submissions.
                  </span>
                </div>
              </div>

              <div>
                <label htmlFor="duration-min" className="block text-xs font-semibold text-slate-700 mb-1">
                  Time Limit (in Minutes, Optional)
                </label>
                <input
                  id="duration-min"
                  type="number"
                  min="1"
                  value={durationMinutes}
                  onChange={(e) => setDurationMinutes(e.target.value)}
                  placeholder="Leave empty for unlimited duration"
                  className="w-full sm:w-1/2 px-3 py-2 text-xs border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                />
                <span className="text-[10px] text-slate-500 mt-1 block">
                  Per-attempt timer enforced by server timestamp.
                </span>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-between">
                <div>
                  <strong className="text-xs font-bold text-slate-800 block">
                    Allow Late Submissions
                  </strong>
                  <span className="text-[11px] text-slate-500">
                    If enabled, attempts started or submitted past deadline are accepted but flagged as LATE.
                  </span>
                </div>
                <input
                  type="checkbox"
                  checked={allowLate}
                  onChange={(e) => setAllowLate(e.target.checked)}
                  className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 h-4 w-4"
                />
              </div>
            </div>
          )}

          {activeTab === 'RULES' && (
            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="max-attempts" className="block text-xs font-semibold text-slate-700 mb-1">
                    Max Attempts Allowed <span className="text-rose-500">*</span>
                  </label>
                  <input
                    id="max-attempts"
                    type="number"
                    min="1"
                    value={maxAttempts}
                    onChange={(e) => setMaxAttempts(e.target.value)}
                    className="w-full px-3 py-2 text-xs border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                    required
                  />
                  <span className="text-[10px] text-slate-500 mt-1 block">
                    Highest score across all attempts becomes official grade.
                  </span>
                </div>

                <div>
                  <label htmlFor="timeout-mode" className="block text-xs font-semibold text-slate-700 mb-1">
                    On Timer Expiration (Timeout Mode)
                  </label>
                  <select
                    id="timeout-mode"
                    value={timeoutMode}
                    onChange={(e) => setTimeoutMode(e.target.value as TimeoutMode)}
                    className="w-full px-3 py-2 text-xs border border-slate-300 rounded-xl bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  >
                    <option value="AUTO_SUBMIT">Auto Submit saved answers</option>
                    <option value="LOCK">Lock without Submit</option>
                  </select>
                </div>
              </div>

              {/* Shuffling */}
              <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
                <strong className="text-xs font-bold text-slate-800 block">Randomization &amp; Shuffling</strong>
                <div className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    id="shuffle-q"
                    checked={shuffleQuestions}
                    onChange={(e) => setShuffleQuestions(e.target.checked)}
                    className="rounded border-slate-300 text-[#d64527] focus:ring-[#d64527]"
                  />
                  <label htmlFor="shuffle-q" className="text-xs text-slate-700 cursor-pointer">
                    Shuffle Question order for each student
                  </label>
                </div>
                <div className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    id="shuffle-a"
                    checked={shuffleAnswers}
                    onChange={(e) => setShuffleAnswers(e.target.checked)}
                    className="rounded border-slate-300 text-[#d64527] focus:ring-[#d64527]"
                  />
                  <label htmlFor="shuffle-a" className="text-xs text-slate-700 cursor-pointer">
                    Shuffle Option choices within questions
                  </label>
                </div>
              </div>

              {/* Release & Visibility */}
              <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
                <strong className="text-xs font-bold text-slate-800 block">
                  Results &amp; Answers Visibility
                </strong>

                <div>
                  <label htmlFor="release-policy" className="block text-[11px] font-semibold text-slate-700 mb-1">
                    Grade Release Policy
                  </label>
                  <select
                    id="release-policy"
                    value={releasePolicy}
                    onChange={(e) => setReleasePolicy(e.target.value as ReleasePolicy)}
                    className="w-full px-3 py-1.5 text-xs border border-slate-300 rounded-lg bg-white"
                  >
                    <option value="MANUAL">Manual (Grades released only when Teacher clicks Publish)</option>
                    <option value="IMMEDIATE">Immediate (Students see score immediately after submission)</option>
                  </select>
                </div>

                <div className="space-y-2 pt-1 border-t border-slate-200">
                  <div className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      id="show-correct"
                      checked={showCorrectAnswers}
                      onChange={(e) => setShowCorrectAnswers(e.target.checked)}
                      className="rounded border-slate-300 text-[#d64527] focus:ring-[#d64527]"
                    />
                    <label htmlFor="show-correct" className="text-xs text-slate-700 cursor-pointer">
                      Show Correct Answers to students (when review is released)
                    </label>
                  </div>

                  <div className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      id="show-explanations"
                      checked={showExplanations}
                      onChange={(e) => setShowExplanations(e.target.checked)}
                      className="rounded border-slate-300 text-[#d64527] focus:ring-[#d64527]"
                    />
                    <label htmlFor="show-explanations" className="text-xs text-slate-700 cursor-pointer">
                      Show Explanations to students (when review is released)
                    </label>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Footer Actions */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-200">
            <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={isLoading}>
              Cancel
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={isLoading}
              className="bg-[#d64527] hover:bg-[#be3b20] text-white disabled:opacity-50"
            >
              {isLoading ? 'Saving...' : isEditing ? 'Save Settings' : 'Create Draft'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
