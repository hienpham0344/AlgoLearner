import React, { useState } from 'react';
import { X, RefreshCw, AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { RejudgeRequest } from '../../types';

interface RejudgeModalProps {
  isOpen: boolean;
  onClose: () => void;
  onRejudge: (request: RejudgeRequest) => Promise<void>;
  assignmentTitle: string;
  isLoading?: boolean;
}

export const RejudgeModal: React.FC<RejudgeModalProps> = ({
  isOpen,
  onClose,
  onRejudge,
  assignmentTitle,
  isLoading = false,
}) => {
  const [target, setTarget] = useState<'ORIGINAL' | 'LATEST_PUBLISHED'>('ORIGINAL');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!reason.trim()) {
      setError('An audit reason is required for initiating rejudge.');
      return;
    }

    try {
      setError(null);
      await onRejudge({
        target,
        reason: reason.trim(),
      });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to trigger rejudge.');
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="rejudge-modal-title"
    >
      <div className="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <RefreshCw size={18} className="text-slate-800" />
            <h3 id="rejudge-modal-title" className="text-base font-bold text-slate-900 truncate">
              Rejudge Submissions
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
          <p className="text-xs text-slate-500 truncate">
            Re-evaluating code solutions for <strong>{assignmentTitle}</strong>.
          </p>
          <div className="p-3 bg-amber-50 rounded-xl border border-amber-200 flex items-start gap-2 text-xs text-amber-800">
            <AlertTriangle size={16} className="shrink-0 mt-0.5 text-amber-600" />
            <span>
              Rejudge re-evaluates all official student code submissions. A new evaluation generation will be created,
              preserving past history.
            </span>
          </div>

          <div>
            <label htmlFor="rejudge-target" className="block text-xs font-bold text-slate-700 mb-1">
              Testcase & Weight Snapshot Target
            </label>
            <select
              id="rejudge-target"
              value={target}
              onChange={(e) => setTarget(e.target.value as 'ORIGINAL' | 'LATEST_PUBLISHED')}
              className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
            >
              <option value="ORIGINAL">Original Snapshot (Re-runs with the exact testcases at time of submission)</option>
              <option value="LATEST_PUBLISHED">
                Latest Published Revision (Applies updated testcases from the newest published revision)
              </option>
            </select>
          </div>

          <div>
            <label htmlFor="rejudge-reason" className="block text-xs font-bold text-slate-700 mb-1">
              Audit Reason <span className="text-rose-500">*</span>
            </label>
            <textarea
              id="rejudge-reason"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Fixed faulty sample testcase #3; regrading all student submissions."
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
              {isLoading ? 'Rejudging...' : 'Initiate Rejudge'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
