import React, { useState } from 'react';
import { X, UserPlus, RotateCcw, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { AdjustAttemptsRequest } from '../../types';

interface AdjustAttemptsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: AdjustAttemptsRequest) => Promise<void>;
  studentId: string;
  studentName?: string;
  isLoading?: boolean;
}

export const AdjustAttemptsModal: React.FC<AdjustAttemptsModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  studentId,
  studentName = 'Student',
  isLoading = false,
}) => {
  const [action, setAction] = useState<'GRANT' | 'RESET'>('GRANT');
  const [additionalAttempts, setAdditionalAttempts] = useState<number>(1);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reason.trim()) {
      setError('An audit reason is required for adjusting attempt rights.');
      return;
    }
    if (reason.length > 1000) {
      setError('Reason cannot exceed 1000 characters.');
      return;
    }

    setError(null);
    await onSubmit({
      studentId,
      action,
      additionalAttempts: action === 'GRANT' ? additionalAttempts : 0,
      reason: reason.trim(),
    });
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="adjust-attempts-title"
    >
      <div className="bg-white rounded-2xl border border-slate-200 shadow-xl w-full max-w-md overflow-hidden">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50">
          <h2 id="adjust-attempts-title" className="text-base font-bold text-slate-900">
            Adjust Attempt Allowance
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-2">
              <AlertCircle size={15} className="shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-700">
            Target student: <strong className="font-semibold text-slate-900">{studentName}</strong>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Action</label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setAction('GRANT')}
                className={`py-2 px-3 rounded-xl border text-xs font-medium flex items-center justify-center gap-1.5 transition-colors ${
                  action === 'GRANT'
                    ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                    : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                }`}
              >
                <UserPlus size={14} />
                Grant Extra
              </button>
              <button
                type="button"
                onClick={() => setAction('RESET')}
                className={`py-2 px-3 rounded-xl border text-xs font-medium flex items-center justify-center gap-1.5 transition-colors ${
                  action === 'RESET'
                    ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                    : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                }`}
              >
                <RotateCcw size={14} />
                Reset Attempts
              </button>
            </div>
          </div>

          {action === 'GRANT' && (
            <div>
              <label htmlFor="extra-attempts" className="block text-xs font-semibold text-slate-700 mb-1">
                Additional Attempts to Grant
              </label>
              <input
                id="extra-attempts"
                type="number"
                min="1"
                max="10"
                value={additionalAttempts}
                onChange={(e) => setAdditionalAttempts(parseInt(e.target.value, 10) || 1)}
                className="w-full px-3 py-1.5 text-xs border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                required
              />
            </div>
          )}

          <div>
            <label htmlFor="adjust-reason" className="block text-xs font-semibold text-slate-700 mb-1">
              Audit Reason <span className="text-rose-500">*</span>
            </label>
            <textarea
              id="adjust-reason"
              rows={3}
              maxLength={1000}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g., Medical absence exception approved or network interruption during submission..."
              className="w-full px-3 py-2 text-xs border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
              required
            />
            <span className="text-[10px] text-slate-400 mt-1 block">
              Required for compliance and audit log tracking.
            </span>
          </div>

          <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-200">
            <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={isLoading}>
              Cancel
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={isLoading}
              className="bg-[#d64527] hover:bg-[#be3b20] text-white"
            >
              {isLoading ? 'Applying...' : 'Apply Allowance'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
