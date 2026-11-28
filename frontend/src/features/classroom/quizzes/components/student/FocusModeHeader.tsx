import React from 'react';
import { AutosaveIndicator } from '../shared/AutosaveIndicator';
import { QuizTimerDisplay } from '../shared/QuizTimerDisplay';
import { Button } from '@/components/ui/Button';
import type { AutosaveStatus } from '../../types';

interface FocusModeHeaderProps {
  quizTitle: string;
  attemptNumber: number;
  currentQuestionIndex: number;
  totalQuestions: number;
  autosaveStatus: AutosaveStatus;
  autosaveError?: string;
  onRetryAutosave?: () => void;
  remainingSeconds: number;
  isUntimed: boolean;
  isLowTime: boolean;
  isUrgent: boolean;
  isExpired: boolean;
  onSubmitClick: () => void;
  isSubmitting?: boolean;
}

export const FocusModeHeader: React.FC<FocusModeHeaderProps> = ({
  quizTitle,
  attemptNumber,
  currentQuestionIndex,
  totalQuestions,
  autosaveStatus,
  autosaveError,
  onRetryAutosave,
  remainingSeconds,
  isUntimed,
  isLowTime,
  isUrgent,
  isExpired,
  onSubmitClick,
  isSubmitting = false,
}) => {
  return (
    <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 px-4 py-3 backdrop-blur-md sm:px-8">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4">
        {/* Left: Title & Attempt info */}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="rounded bg-slate-900 px-2 py-0.5 text-[11px] font-bold text-white uppercase tracking-wider">
              Attempt #{attemptNumber}
            </span>
            <h1 className="truncate text-base font-bold text-slate-900" title={quizTitle}>
              {quizTitle}
            </h1>
          </div>
          <p className="mt-0.5 text-xs text-slate-500">
            Question {currentQuestionIndex + 1} of {totalQuestions}
          </p>
        </div>

        {/* Center: Autosave Status */}
        <div className="hidden md:flex items-center">
          <AutosaveIndicator
            status={autosaveStatus}
            errorMessage={autosaveError}
            onRetry={onRetryAutosave}
          />
        </div>

        {/* Right: Timer & Submit Action */}
        <div className="flex items-center gap-3">
          <QuizTimerDisplay
            remainingSeconds={remainingSeconds}
            isUntimed={isUntimed}
            isLowTime={isLowTime}
            isUrgent={isUrgent}
            isExpired={isExpired}
          />
          <Button
            type="button"
            variant="primary"
            size="sm"
            onClick={onSubmitClick}
            disabled={isSubmitting || isExpired}
            className="font-bold whitespace-nowrap"
          >
            {isSubmitting ? 'Submitting…' : 'Submit Quiz'}
          </Button>
        </div>
      </div>
    </header>
  );
};
