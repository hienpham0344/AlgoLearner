import React, { useState } from 'react';
import { X, Calendar, Settings, Shield, Award } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type {
  AssignmentDetailView,
  AssignmentType,
  CodeFeedbackLevel,
  CreateAssignmentRequest,
  GradePublishMode,
  ScoringPolicy,
  TimeExpiredAction,
  ViewAnswersMode,
} from '../../types';
import { isoUtcToLocalInput, localInputToIsoUtc } from '../../utils/timeUtils';
import { validateAssignmentForm } from '../../utils/assignmentRules';

interface AssignmentFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: CreateAssignmentRequest) => Promise<void>;
  initialData?: AssignmentDetailView | null;
  isLoading?: boolean;
}

export const AssignmentFormModal: React.FC<AssignmentFormModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  initialData,
  isLoading = false,
}) => {
  const isEditing = Boolean(initialData);

  const [activeTab, setActiveTab] = useState<'BASIC' | 'SCHEDULE' | 'ATTEMPTS' | 'POLICIES'>('BASIC');

  const [title, setTitle] = useState(initialData?.title || '');
  const [description, setDescription] = useState(initialData?.description || '');
  const [type, setType] = useState<AssignmentType>(initialData?.type || 'GRADED');
  const [startTime, setStartTime] = useState(isoUtcToLocalInput(initialData?.startTime));
  const [deadline, setDeadline] = useState(isoUtcToLocalInput(initialData?.deadline));
  const [allowLateSubmission, setAllowLateSubmission] = useState(initialData?.allowLateSubmission ?? true);
  const [durationMinutes, setDurationMinutes] = useState<string>(
    initialData?.durationMinutes !== undefined && initialData?.durationMinutes !== null
      ? initialData.durationMinutes.toString()
      : ''
  );
  const [timeExpiredAction, setTimeExpiredAction] = useState<TimeExpiredAction>(
    initialData?.timeExpiredAction || 'AUTO_SUBMIT'
  );
  const [maxAttempts, setMaxAttempts] = useState<string>(
    initialData?.maxAttempts !== undefined && initialData?.maxAttempts !== null
      ? initialData.maxAttempts.toString()
      : '1'
  );
  const [scoringPolicy, setScoringPolicy] = useState<ScoringPolicy>(initialData?.scoringPolicy || 'HIGHEST');
  const [shuffleQuestions, setShuffleQuestions] = useState(initialData?.shuffleQuestions ?? false);
  const [shuffleAnswers, setShuffleAnswers] = useState(initialData?.shuffleAnswers ?? false);
  const [gradePublishMode, setGradePublishMode] = useState<GradePublishMode>(
    initialData?.gradePublishMode || 'IMMEDIATE'
  );
  const [viewAnswersMode, setViewAnswersMode] = useState<ViewAnswersMode>(
    initialData?.viewAnswersMode || 'AFTER_SUBMIT'
  );
  const [codeFeedbackLevel, setCodeFeedbackLevel] = useState<CodeFeedbackLevel>(
    initialData?.codeFeedbackLevel || 'TESTCASE_STATUS'
  );

  const [formErrors, setFormErrors] = useState<Record<string, string>>({});

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const payload: CreateAssignmentRequest = {
      title: title.trim(),
      description: description.trim() || undefined,
      type,
      startTime: localInputToIsoUtc(startTime),
      deadline: localInputToIsoUtc(deadline),
      allowLateSubmission,
      latePenaltyPercent: 0,
      durationMinutes: durationMinutes ? parseInt(durationMinutes, 10) : null,
      timeExpiredAction,
      maxAttempts: maxAttempts ? parseInt(maxAttempts, 10) : 0,
      scoringPolicy,
      shuffleQuestions,
      shuffleAnswers,
      gradePublishMode,
      viewAnswersMode,
      codeFeedbackLevel,
    };

    const errors = validateAssignmentForm(payload);
    if (Object.keys(errors).length > 0) {
      setFormErrors(errors as Record<string, string>);
      if (errors.title) setActiveTab('BASIC');
      else if (errors.startTime || errors.deadline || errors.durationMinutes) setActiveTab('SCHEDULE');
      else if (errors.maxAttempts) setActiveTab('ATTEMPTS');
      return;
    }

    try {
      await onSubmit(payload);
      onClose();
    } catch {
      // API error handled by caller mutation
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="assignment-form-modal-title"
    >
      <div className="bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 id="assignment-form-modal-title" className="text-lg font-bold text-slate-900">
              {isEditing ? 'Edit Assignment Settings' : 'Create New Assignment'}
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Configure assignment scope, scheduling, attempts, and evaluation policies.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100 transition-colors"
            aria-label="Close dialog"
          >
            <X size={18} />
          </button>
        </div>

        {/* Tab Navigation */}
        <div className="px-6 border-b border-slate-200 flex gap-4 bg-slate-50/50">
          <button
            type="button"
            onClick={() => setActiveTab('BASIC')}
            className={`py-3 text-xs font-semibold border-b-2 transition-colors flex items-center gap-1.5 ${
              activeTab === 'BASIC'
                ? 'border-slate-900 text-slate-900'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            <Settings size={14} /> Basic Information
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('SCHEDULE')}
            className={`py-3 text-xs font-semibold border-b-2 transition-colors flex items-center gap-1.5 ${
              activeTab === 'SCHEDULE'
                ? 'border-slate-900 text-slate-900'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            <Calendar size={14} /> Schedule & Time
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('ATTEMPTS')}
            className={`py-3 text-xs font-semibold border-b-2 transition-colors flex items-center gap-1.5 ${
              activeTab === 'ATTEMPTS'
                ? 'border-slate-900 text-slate-900'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            <Award size={14} /> Attempts & Rules
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('POLICIES')}
            className={`py-3 text-xs font-semibold border-b-2 transition-colors flex items-center gap-1.5 ${
              activeTab === 'POLICIES'
                ? 'border-slate-900 text-slate-900'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            <Shield size={14} /> Feedback Policies
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-5">
          {activeTab === 'BASIC' && (
            <div className="space-y-4">
              <div>
                <label htmlFor="assignment-title" className="block text-xs font-bold text-slate-700 mb-1">
                  Assignment Title <span className="text-rose-500">*</span>
                </label>
                <input
                  id="assignment-title"
                  type="text"
                  value={title}
                  onChange={(e) => {
                    setTitle(e.target.value);
                    if (formErrors.title) {
                      setFormErrors((prev) => {
                        const next = { ...prev };
                        delete next.title;
                        return next;
                      });
                    }
                  }}
                  placeholder="e.g. Midterm Lab Assignment — Data Structures"
                  maxLength={255}
                  required
                  className={`w-full px-3.5 py-2 rounded-xl border text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900 ${
                    formErrors.title ? 'border-rose-400 bg-rose-50/20' : 'border-slate-300'
                  }`}
                />
                {formErrors.title && <p className="text-xs text-rose-600 mt-1">{formErrors.title}</p>}
              </div>

              <div>
                <label htmlFor="assignment-type" className="block text-xs font-bold text-slate-700 mb-1">
                  Assignment Type
                </label>
                <select
                  id="assignment-type"
                  value={type}
                  onChange={(e) => setType(e.target.value as AssignmentType)}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                >
                  <option value="GRADED">Graded (Includes scoring, points, and gradebook publication)</option>
                  <option value="UNGRADED">Ungraded (Practice assessment, tracks completion status)</option>
                </select>
              </div>

              <div>
                <label htmlFor="assignment-description" className="block text-xs font-bold text-slate-700 mb-1">
                  Description & Instructions (Markdown supported)
                </label>
                <textarea
                  id="assignment-description"
                  rows={4}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Provide guidance, required materials, or rules for students..."
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                />
              </div>
            </div>
          )}

          {activeTab === 'SCHEDULE' && (
            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="assignment-start-time" className="block text-xs font-bold text-slate-700 mb-1">
                    Start Time (Vietnam UTC+7)
                  </label>
                  <input
                    id="assignment-start-time"
                    type="datetime-local"
                    value={startTime}
                    onChange={(e) => setStartTime(e.target.value)}
                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                  />
                  <p className="text-[11px] text-slate-500 mt-1">Leave empty to open immediately upon publishing.</p>
                </div>

                <div>
                  <label htmlFor="assignment-deadline" className="block text-xs font-bold text-slate-700 mb-1">
                    Deadline (Vietnam UTC+7)
                  </label>
                  <input
                    id="assignment-deadline"
                    type="datetime-local"
                    value={deadline}
                    onChange={(e) => {
                      setDeadline(e.target.value);
                      if (formErrors.deadline) {
                        setFormErrors((prev) => {
                          const next = { ...prev };
                          delete next.deadline;
                          return next;
                        });
                      }
                    }}
                    className={`w-full px-3.5 py-2 rounded-xl border text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900 ${
                      formErrors.deadline ? 'border-rose-400 bg-rose-50/20' : 'border-slate-300'
                    }`}
                  />
                  {formErrors.deadline && <p className="text-xs text-rose-600 mt-1">{formErrors.deadline}</p>}
                  <p className="text-[11px] text-slate-500 mt-1">Leave empty if there is no deadline.</p>
                </div>
              </div>

              <div className="pt-2 border-t border-slate-200">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={allowLateSubmission}
                    onChange={(e) => setAllowLateSubmission(e.target.checked)}
                    className="w-4 h-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900"
                  />
                  <span className="text-xs font-bold text-slate-800">Allow Late Submissions</span>
                </label>
                <p className="text-[11px] text-slate-500 ml-6 mt-0.5">
                  Submissions after deadline will be marked with a "Late" indicator without automatic score penalty.
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
                <div>
                  <label htmlFor="assignment-duration" className="block text-xs font-bold text-slate-700 mb-1">
                    Time Limit (Minutes)
                  </label>
                  <input
                    id="assignment-duration"
                    type="number"
                    min="1"
                    value={durationMinutes}
                    onChange={(e) => setDurationMinutes(e.target.value)}
                    placeholder="e.g. 90"
                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                  />
                  <p className="text-[11px] text-slate-500 mt-1">Leave empty for untimed assignments.</p>
                </div>

                <div>
                  <label htmlFor="time-expired-action" className="block text-xs font-bold text-slate-700 mb-1">
                    Action When Time Expires
                  </label>
                  <select
                    id="time-expired-action"
                    value={timeExpiredAction}
                    onChange={(e) => setTimeExpiredAction(e.target.value as TimeExpiredAction)}
                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                  >
                    <option value="AUTO_SUBMIT">Auto-submit latest saved draft</option>
                    <option value="LOCK_WITHOUT_SUBMIT">Lock workspace without creating official submission</option>
                  </select>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'ATTEMPTS' && (
            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="assignment-max-attempts" className="block text-xs font-bold text-slate-700 mb-1">
                    Maximum Attempts Allowed
                  </label>
                  <input
                    id="assignment-max-attempts"
                    type="number"
                    min="0"
                    value={maxAttempts}
                    onChange={(e) => setMaxAttempts(e.target.value)}
                    placeholder="0 for unlimited"
                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                  />
                  <p className="text-[11px] text-slate-500 mt-1">Enter 0 for unlimited attempts.</p>
                </div>

                <div>
                  <label htmlFor="assignment-scoring-policy" className="block text-xs font-bold text-slate-700 mb-1">
                    Scoring Policy
                  </label>
                  <select
                    id="assignment-scoring-policy"
                    value={scoringPolicy}
                    onChange={(e) => setScoringPolicy(e.target.value as ScoringPolicy)}
                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                  >
                    <option value="HIGHEST">Highest score across all attempts</option>
                    <option value="LATEST">Latest submitted attempt score</option>
                  </select>
                </div>
              </div>

              <div className="space-y-2 pt-2 border-t border-slate-200">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={shuffleQuestions}
                    onChange={(e) => setShuffleQuestions(e.target.checked)}
                    className="w-4 h-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900"
                  />
                  <span className="text-xs font-bold text-slate-800">Shuffle Question Order for Each Student</span>
                </label>

                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={shuffleAnswers}
                    onChange={(e) => setShuffleAnswers(e.target.checked)}
                    className="w-4 h-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900"
                  />
                  <span className="text-xs font-bold text-slate-800">Shuffle Options for Objective Questions</span>
                </label>
              </div>
            </div>
          )}

          {activeTab === 'POLICIES' && (
            <div className="space-y-4">
              <div>
                <label htmlFor="grade-publish-mode" className="block text-xs font-bold text-slate-700 mb-1">
                  Grade Release Policy
                </label>
                <select
                  id="grade-publish-mode"
                  value={gradePublishMode}
                  onChange={(e) => setGradePublishMode(e.target.value as GradePublishMode)}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                >
                  <option value="IMMEDIATE">Immediate (Students see grades right after submission)</option>
                  <option value="AFTER_DEADLINE">After Deadline (Grades hidden until assignment deadline)</option>
                  <option value="MANUAL">Manual (Grades hidden until Teacher clicks Publish Grades)</option>
                </select>
              </div>

              <div>
                <label htmlFor="view-answers-mode" className="block text-xs font-bold text-slate-700 mb-1">
                  Answer Key & Solution Visibility
                </label>
                <select
                  id="view-answers-mode"
                  value={viewAnswersMode}
                  onChange={(e) => setViewAnswersMode(e.target.value as ViewAnswersMode)}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                >
                  <option value="AFTER_SUBMIT">After Submission (Show answers once student submits)</option>
                  <option value="AFTER_DEADLINE">After Deadline (Show answers only after deadline passes)</option>
                  <option value="ALWAYS">Always visible</option>
                  <option value="NEVER">Never (Keep answer key hidden from students)</option>
                </select>
              </div>

              <div>
                <label htmlFor="code-feedback-level" className="block text-xs font-bold text-slate-700 mb-1">
                  Code Judge Feedback Level
                </label>
                <select
                  id="code-feedback-level"
                  value={codeFeedbackLevel}
                  onChange={(e) => setCodeFeedbackLevel(e.target.value as CodeFeedbackLevel)}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                >
                  <option value="TESTCASE_STATUS">
                    Testcase Status (Shows verdict and pass/fail per sample case)
                  </option>
                  <option value="PASS_FAIL">Pass / Fail only (Overall verdict only)</option>
                  <option value="FULL_DETAILS">Full Details (Execution time, memory used, compiler output)</option>
                </select>
              </div>
            </div>
          )}

          {/* Footer Actions */}
          <div className="pt-4 border-t border-slate-200 flex items-center justify-end gap-3">
            <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={isLoading}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={isLoading}>
              {isLoading ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Assignment'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
