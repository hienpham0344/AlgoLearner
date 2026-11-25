import React from 'react';
import { CheckCircle2, Loader2, AlertCircle, RefreshCw } from 'lucide-react';
import type { AutosaveStatus } from '../../utils/autosaveQueue';

interface AutosaveIndicatorProps {
  status: AutosaveStatus;
  lastError?: string | null;
  onRetry?: () => void;
  className?: string;
}

export const AutosaveIndicator: React.FC<AutosaveIndicatorProps> = ({
  status,
  lastError,
  onRetry,
  className = '',
}) => {
  if (status === 'saving') {
    return (
      <div
        className={`inline-flex items-center gap-1.5 text-xs text-amber-700 bg-amber-50/90 border border-amber-200 px-2.5 py-1 rounded-lg ${className}`}
        aria-live="polite"
      >
        <Loader2 size={13} className="animate-spin text-amber-600 shrink-0" />
        <span>Saving answer…</span>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div
        className={`inline-flex items-center gap-2 text-xs text-rose-700 bg-rose-50 border border-rose-200 px-2.5 py-1 rounded-lg ${className}`}
        aria-live="assertive"
      >
        <AlertCircle size={13} className="text-rose-600 shrink-0" />
        <span className="font-medium">{lastError || 'Autosave failed'}</span>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="inline-flex items-center gap-1 text-[11px] font-bold text-rose-800 underline hover:text-rose-950 ml-1"
          >
            <RefreshCw size={11} className="shrink-0" />
            Retry
          </button>
        )}
      </div>
    );
  }

  if (status === 'saved') {
    return (
      <div
        className={`inline-flex items-center gap-1.5 text-xs text-emerald-700 bg-emerald-50/80 border border-emerald-200 px-2.5 py-1 rounded-lg transition-opacity duration-300 ${className}`}
        aria-live="polite"
      >
        <CheckCircle2 size={13} className="text-emerald-600 shrink-0" />
        <span>Saved</span>
      </div>
    );
  }

  return (
    <div
      className={`inline-flex items-center gap-1.5 text-xs text-slate-400 px-2.5 py-1 ${className}`}
    >
      <CheckCircle2 size={13} className="text-slate-300 shrink-0" />
      <span>Autosave active</span>
    </div>
  );
};
