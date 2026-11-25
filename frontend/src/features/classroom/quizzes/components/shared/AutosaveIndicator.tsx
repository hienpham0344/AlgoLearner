import React from 'react';
import { Check, CloudOff, RefreshCw, Loader2 } from 'lucide-react';
import type { AutosaveStatus } from '../../types';

interface AutosaveIndicatorProps {
  status: AutosaveStatus;
  errorMessage?: string;
  onRetry?: () => void;
  className?: string;
}

export const AutosaveIndicator: React.FC<AutosaveIndicatorProps> = ({
  status,
  errorMessage,
  onRetry,
  className = '',
}) => {
  if (status === 'saving') {
    return (
      <div
        role="status"
        aria-live="polite"
        className={`inline-flex items-center gap-1.5 text-xs font-medium text-slate-500 ${className}`}
      >
        <Loader2 size={13} className="animate-spin text-indigo-600" />
        <span>Saving answer…</span>
      </div>
    );
  }

  if (status === 'saved') {
    return (
      <div
        role="status"
        aria-live="polite"
        className={`inline-flex items-center gap-1.5 text-xs font-medium text-emerald-600 ${className}`}
      >
        <Check size={13} className="stroke-[2.5]" />
        <span>All answers saved</span>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div
        role="alert"
        aria-live="assertive"
        className={`inline-flex items-center gap-2 rounded-lg bg-rose-50 px-2 py-1 text-xs font-semibold text-rose-700 border border-rose-200 ${className}`}
      >
        <CloudOff size={13} className="text-rose-600" />
        <span>{errorMessage || 'Failed to save answer'}</span>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="inline-flex items-center gap-1 rounded bg-white px-1.5 py-0.5 text-[11px] font-bold text-rose-800 shadow-xs hover:bg-rose-100"
          >
            <RefreshCw size={11} />
            Retry
          </button>
        )}
      </div>
    );
  }

  // Idle
  return (
    <div
      role="status"
      className={`inline-flex items-center gap-1.5 text-xs font-medium text-slate-400 ${className}`}
    >
      <span>Answers autosaved</span>
    </div>
  );
};
