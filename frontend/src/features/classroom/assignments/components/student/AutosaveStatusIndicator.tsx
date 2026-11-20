import React from 'react';
import { CheckCircle2, RefreshCw, AlertTriangle } from 'lucide-react';
import type { AutosaveStatus } from '../../utils/autosaveManager';

interface AutosaveStatusIndicatorProps {
  status: AutosaveStatus;
  lastSavedAt?: Date | null;
  errorMessage?: string | null;
  className?: string;
}

export const AutosaveStatusIndicator: React.FC<AutosaveStatusIndicatorProps> = ({
  status,
  lastSavedAt,
  errorMessage,
  className = '',
}) => {
  return (
    <div
      className={`inline-flex items-center gap-1.5 text-xs transition-opacity duration-200 ${className}`}
      role="status"
      aria-live="polite"
    >
      {status === 'SAVING' && (
        <span className="inline-flex items-center gap-1 text-slate-500">
          <RefreshCw size={12} className="animate-spin text-slate-400" />
          <span>Saving draft...</span>
        </span>
      )}

      {status === 'SAVED' && (
        <span className="inline-flex items-center gap-1 text-emerald-600 font-medium">
          <CheckCircle2 size={12} />
          <span>
            Saved {lastSavedAt ? `at ${lastSavedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}` : ''}
          </span>
        </span>
      )}

      {status === 'ERROR' && (
        <span className="inline-flex items-center gap-1 text-amber-600 font-medium" title={errorMessage || 'Error saving draft'}>
          <AlertTriangle size={12} />
          <span>Save failed. Retrying...</span>
        </span>
      )}

      {status === 'IDLE' && (
        <span className="text-slate-400 text-[11px]">Draft synced</span>
      )}
    </div>
  );
};
