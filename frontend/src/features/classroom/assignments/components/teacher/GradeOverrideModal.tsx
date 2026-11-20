import React, { useState } from 'react';
import { X, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { OverrideGradeRequest } from '../../types';

interface GradeOverrideModalProps {
  isOpen: boolean;
  onClose: () => void;
  onOverride: (request: OverrideGradeRequest) => Promise<void>;
  studentName?: string;
  currentScore?: number | null;
  isLoading?: boolean;
}

export const GradeOverrideModal: React.FC<GradeOverrideModalProps> = ({
  isOpen,
  onClose,
  onOverride,
  studentName = 'Student',
  currentScore,
  isLoading = false,
}) => {
  const [overriddenScore, setOverriddenScore] = useState(
    currentScore !== undefined && currentScore !== null ? currentScore.toString() : ''
  );
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const score = parseFloat(overriddenScore);
    if (isNaN(score) || score < 0) {
      setError('Overridden score must be a non-negative number.');
      return;
    }

    if (!reason.trim()) {
      setError('An audit reason is required when overriding a final score.');
      return;
    }

    try {
      setError(null);
      await onOverride({
        overriddenScore: score,
        reason: reason.trim(),
      });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to override score.');
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="grade-override-title"
    >
      <div className="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertCircle size={18} className="text-amber-600" />
            <h3 id="grade-override-title" className="text-base font-bold text-slate-900">
              Override Final Score
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
            Override final score for <strong>{studentName}</strong>. Original scores will be preserved in the audit log.
          </p>

          <div>
            <label htmlFor="overridden-score" className="block text-xs font-bold text-slate-700 mb-1">
              New Overridden Score <span className="text-rose-500">*</span>
            </label>
            <input
              id="overridden-score"
              type="number"
              step="0.5"
              min="0"
              value={overriddenScore}
              onChange={(e) => setOverriddenScore(e.target.value)}
              required
              className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
            />
          </div>

          <div>
            <label htmlFor="override-reason" className="block text-xs font-bold text-slate-700 mb-1">
              Audit Reason <span className="text-rose-500">*</span>
            </label>
            <textarea
              id="override-reason"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Adjusted after regrading discussion or excused partial testcase failure."
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
              {isLoading ? 'Saving...' : 'Confirm Override'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
