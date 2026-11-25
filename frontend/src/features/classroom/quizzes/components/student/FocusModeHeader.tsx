import React from 'react';
import { Send } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { QuizTimerDisplay } from '../shared/QuizTimerDisplay';
import { AutosaveIndicator } from '../shared/AutosaveIndicator';
import type { AutosaveStatus } from '../../utils/autosaveQueue';

interface FocusModeHeaderProps {
  quizTitle: string;
  totalQuestions: number;
  answeredCount: number;
  formattedTime: string;
  timerUrgency: 'normal' | 'warning' | 'critical' | 'expired';
  autosaveStatus: AutosaveStatus;
  autosaveError?: string | null;
  onRetryAutosave?: () => void;
  onSubmitClick: () => void;
  isSubmitting?: boolean;
}

export const FocusModeHeader: React.FC<FocusModeHeaderProps> = ({
  quizTitle,
  totalQuestions,
  answeredCount,
  formattedTime,
  timerUrgency,
  autosaveStatus,
  autosaveError,
  onRetryAutosave,
  onSubmitClick,
  isSubmitting = false,
}) => {
  const percentComplete = totalQuestions > 0 ? Math.round((answeredCount / totalQuestions) * 100) : 0;

  return (
    <header className="sticky top-0 z-30 bg-white/95 backdrop-blur-md border-b border-slate-200/80 shadow-xs px-4 sm:px-6 py-3 transition-all">
      <div className="max-w-7xl mx-auto flex flex-col md:flex-row md:items-center justify-between gap-3">
        {/* Left: Quiz Info & Progress */}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-indigo-700 bg-indigo-50 border border-indigo-200 px-2 py-0.5 rounded-full">
              Online Assessment
            </span>
            <span className="text-xs text-slate-400 font-mono">
              Progress: {answeredCount}/{totalQuestions} answered ({percentComplete}%)
            </span>
          </div>
          <h1 className="text-base sm:text-lg font-bold text-slate-900 truncate mt-0.5" title={quizTitle}>
            {quizTitle}
          </h1>
          {/* Progress bar */}
          <div className="w-full max-w-xs bg-slate-100 rounded-full h-1.5 mt-1.5 overflow-hidden">
            <div
              className="bg-indigo-600 h-1.5 rounded-full transition-all duration-300"
              style={{ width: `${percentComplete}%` }}
              role="progressbar"
              aria-valuenow={percentComplete}
              aria-valuemin={0}
              aria-valuemax={100}
            />
          </div>
        </div>

        {/* Right: Autosave status, Timer & Submit button */}
        <div className="flex items-center gap-3 sm:gap-4 shrink-0 justify-between md:justify-end">
          <AutosaveIndicator
            status={autosaveStatus}
            lastError={autosaveError}
            onRetry={onRetryAutosave}
          />

          <QuizTimerDisplay
            formattedTime={formattedTime}
            urgency={timerUrgency}
          />

          <Button
            type="button"
            size="sm"
            onClick={onSubmitClick}
            disabled={isSubmitting}
            className="bg-indigo-600 hover:bg-indigo-700 text-white font-semibold rounded-xl text-xs px-4 py-2 shadow-xs transition-all flex items-center gap-1.5 shrink-0"
          >
            {isSubmitting ? (
              <span>Submitting…</span>
            ) : (
              <>
                <Send size={13} className="shrink-0" />
                <span>Submit Quiz</span>
              </>
            )}
          </Button>
        </div>
      </div>
    </header>
  );
};
