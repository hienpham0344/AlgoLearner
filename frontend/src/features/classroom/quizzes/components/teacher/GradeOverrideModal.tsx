import React, { useState } from 'react';
import { X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { GradeOverrideRequest } from '../../types';

interface GradeOverrideModalProps {
  isOpen: boolean;
  studentId: string;
  studentName?: string;
  currentScore?: number | null;
  onSave: (request: GradeOverrideRequest) => Promise<void>;
  onClose: () => void;
}

export const GradeOverrideModal: React.FC<GradeOverrideModalProps> = ({
  isOpen,
  studentName,
  currentScore,
  onSave,
  onClose,
}) => {
  const [score, setScore] = useState<number>(currentScore != null ? Number(currentScore) : 0);
  const [reason, setReason] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string>('');

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reason.trim()) {
      setError('An audit reason is required for grade overrides.');
      return;
    }
    if (score < 0 || score > 100) {
      setError('Score must be between 0 and 100%.');
      return;
    }

    setError('');
    setIsSubmitting(true);

    try {
      await onSave({
        score: Number(score),
        reason: reason.trim(),
      });
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to override grade');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="grade-override-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs"
    >
      <div className="w-full max-w-md rounded-xl border border-slate-200 bg-white p-6 shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-200 pb-3">
          <h2 id="grade-override-title" className="text-base font-bold text-slate-900">
            Override Final Grade
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            className="rounded p-1 text-slate-400 hover:text-slate-600"
          >
            <X size={18} />
          </button>
        </div>

        {studentName && (
          <p className="mt-2 text-xs font-medium text-slate-600">
            Student: <strong className="text-slate-900">{studentName}</strong>
            {currentScore != null && (
              <span> · Current score: {Number(currentScore).toFixed(2)}%</span>
            )}
          </p>
        )}

        {error && (
          <div
            role="alert"
            className="mt-3 rounded-lg border border-rose-200 bg-rose-50 p-2.5 text-xs text-rose-800"
          >
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-4 space-y-4 text-sm">
          <div>
            <label htmlFor="final-score" className="block text-xs font-bold text-slate-700 mb-1">
              New Final Score (%) (0 – 100) *
            </label>
            <input
              id="final-score"
              type="number"
              min="0"
              max="100"
              step="0.01"
              required
              value={score}
              onChange={(e) => setScore(parseFloat(e.target.value) || 0)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900"
            />
          </div>

          <div>
            <label htmlFor="override-reason" className="block text-xs font-bold text-slate-700 mb-1">
              Reason / Justification * (Audit log required)
            </label>
            <textarea
              id="override-reason"
              rows={3}
              required
              maxLength={2000}
              placeholder="e.g. Granted partial credit for alternative solution during dispute resolution."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-slate-800 focus:outline-hidden"
            />
          </div>

          <p className="text-[11px] text-slate-500">
            The original auto-graded score will be preserved in audit logs alongside this modification.
          </p>

          <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-200">
            <Button variant="outline" type="button" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Submitting…' : 'Apply Grade Override'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
