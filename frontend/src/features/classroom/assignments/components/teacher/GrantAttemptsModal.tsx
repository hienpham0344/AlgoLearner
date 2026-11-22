import React, { useState } from 'react';
import { X, Award } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { AttemptGrantRequest } from '../../types';

interface GrantAttemptsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onGrant: (request: AttemptGrantRequest) => Promise<void>;
  studentName?: string;
  isLoading?: boolean;
}

export const GrantAttemptsModal: React.FC<GrantAttemptsModalProps> = ({
  isOpen,
  onClose,
  onGrant,
  studentName = 'Student',
  isLoading = false,
}) => {
  const [extraAttempts, setExtraAttempts] = useState('1');
  const [reset, setReset] = useState(false);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!reason.trim()) {
      setError('An audit reason is required for granting attempts.');
      return;
    }

    const extra = parseInt(extraAttempts, 10);
    if (isNaN(extra) || extra < 0) {
      setError('Extra attempts must be a non-negative number.');
      return;
    }

    try {
      setError(null);
      await onGrant({
        extraAttempts: extra,
        reset,
        reason: reason.trim(),
      });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to grant attempts.');
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="grant-attempts-title"
    >
      <div className="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Award size={18} className="text-slate-700" />
            <h3 id="grant-attempts-title" className="text-base font-bold text-slate-900">
              Grant Attempt Allowance
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100"
            aria-label="Close dialog"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <p className="text-xs text-slate-600">
            Grant additional attempts to <strong>{studentName}</strong>. Past submissions will be preserved.
          </p>

          <div>
            <label htmlFor="extra-attempts" className="block text-xs font-bold text-slate-700 mb-1">
              Extra Attempts to Grant <span className="text-rose-500">*</span>
            </label>
            <input
              id="extra-attempts"
              type="number"
              min="0"
              value={extraAttempts}
              onChange={(e) => setExtraAttempts(e.target.value)}
              required
              className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
            />
          </div>

          <div>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={reset}
                onChange={(e) => setReset(e.target.checked)}
                className="w-4 h-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900"
              />
              <span className="text-xs font-bold text-slate-800">Reset Attempt Counter</span>
            </label>
            <p className="text-[11px] text-slate-500 ml-6 mt-0.5">
              Resets used count while retaining all historical submissions.
            </p>
          </div>

          <div>
            <label htmlFor="grant-reason" className="block text-xs font-bold text-slate-700 mb-1">
              Audit Reason <span className="text-rose-500">*</span>
            </label>
            <textarea
              id="grant-reason"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Granted due to verified medical excuse or technical issue during attempt."
              maxLength={500}
              required
              className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
            />
            {error && <p className="text-xs text-rose-600 mt-1">{error}</p>}
          </div>

          <div className="pt-2 flex items-center justify-end gap-3">
            <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={isLoading}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={isLoading}>
              {isLoading ? 'Saving...' : 'Confirm Grant'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
