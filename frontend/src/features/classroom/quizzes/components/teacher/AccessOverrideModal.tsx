import React, { useState } from 'react';
import { X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { AccessOverrideRequest } from '../../types';
import { localInputToIsoUtc } from '../../utils/timeUtils';

interface AccessOverrideModalProps {
  isOpen: boolean;
  studentId: string;
  studentName?: string;
  onSave: (request: AccessOverrideRequest) => Promise<void>;
  onClose: () => void;
}

export const AccessOverrideModal: React.FC<AccessOverrideModalProps> = ({
  isOpen,
  studentName,
  onSave,
  onClose,
}) => {
  const [resetAttempts, setResetAttempts] = useState(false);
  const [extraAttempts, setExtraAttempts] = useState<number>(0);
  const [overrideDeadlineLocal, setOverrideDeadlineLocal] = useState<string>('');
  const [overrideTimeLimitMins, setOverrideTimeLimitMins] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string>('');

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      const deadlineIso = overrideDeadlineLocal ? localInputToIsoUtc(overrideDeadlineLocal) : null;
      const timeLimitSeconds = overrideTimeLimitMins
        ? parseInt(overrideTimeLimitMins, 10) * 60
        : null;

      await onSave({
        resetAttempts,
        extraAttempts: Number(extraAttempts) || 0,
        deadline: deadlineIso,
        timeLimitSeconds,
      });
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update access override');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="access-override-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs"
    >
      <div className="w-full max-w-lg rounded-xl border border-slate-200 bg-white p-6 shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-200 pb-3">
          <h2 id="access-override-title" className="text-base font-bold text-slate-900">
            Student Access Override
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
            Applying overrides for student: <strong className="text-slate-900">{studentName}</strong>
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
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-2">
            <label className="flex items-center gap-2 font-medium text-slate-800 cursor-pointer">
              <input
                type="checkbox"
                checked={resetAttempts}
                onChange={(e) => setResetAttempts(e.target.checked)}
                className="rounded border-slate-300 text-slate-900"
              />
              Reset used attempt count
            </label>
            <p className="text-xs text-slate-500">
              Allows the student to start fresh with a full allowance of attempts without deleting their past attempt history.
            </p>
          </div>

          <div>
            <label htmlFor="extra-attempts" className="block text-xs font-bold text-slate-700 mb-1">
              Give Extra Attempts (0 – 10,000)
            </label>
            <input
              id="extra-attempts"
              type="number"
              min="0"
              max="10000"
              value={extraAttempts}
              onChange={(e) => setExtraAttempts(parseInt(e.target.value) || 0)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900"
            />
          </div>

          <div>
            <label htmlFor="override-deadline" className="block text-xs font-bold text-slate-700 mb-1">
              Individual Deadline Extension (Optional)
            </label>
            <input
              id="override-deadline"
              type="datetime-local"
              value={overrideDeadlineLocal}
              onChange={(e) => setOverrideDeadlineLocal(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900"
            />
          </div>

          <div>
            <label htmlFor="override-time-limit" className="block text-xs font-bold text-slate-700 mb-1">
              Individual Time Limit Override in Minutes (Optional)
            </label>
            <input
              id="override-time-limit"
              type="number"
              min="1"
              placeholder="Leave blank to use quiz default"
              value={overrideTimeLimitMins}
              onChange={(e) => setOverrideTimeLimitMins(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900"
            />
          </div>

          <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-200">
            <Button variant="outline" type="button" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : 'Save Overrides'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
