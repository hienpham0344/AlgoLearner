import React, { useState } from 'react';
import { X, Award, History, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { GradeChange, GradeOverrideRequest } from '../../types';
import { formatVietnamDateTime } from '../../utils/quizTimeUtils';

interface GradeOverrideModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: GradeOverrideRequest) => Promise<void>;
  studentId: string;
  studentName?: string;
  currentScore?: number | null;
  history?: GradeChange[];
  isLoadingHistory?: boolean;
  isLoading?: boolean;
}

export const GradeOverrideModal: React.FC<GradeOverrideModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  studentName = 'Student',
  currentScore,
  history = [],
  isLoadingHistory = false,
  isLoading = false,
}) => {
  const [newScore, setNewScore] = useState<string>(
    currentScore !== undefined && currentScore !== null ? String(currentScore) : ''
  );
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const scoreVal = parseFloat(newScore);
    if (isNaN(scoreVal) || scoreVal < 0 || scoreVal > 100) {
      setError('Score must be a number between 0 and 100.');
      return;
    }
    if (!reason.trim()) {
      setError('A reason is required to override official grades.');
      return;
    }
    if (reason.length > 1000) {
      setError('Reason cannot exceed 1000 characters.');
      return;
    }

    setError(null);
    await onSubmit({
      score: scoreVal,
      reason: reason.trim(),
    });
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="override-grade-title"
    >
      <div className="bg-white rounded-2xl border border-slate-200 shadow-xl w-full max-w-lg max-h-[90vh] flex flex-col overflow-hidden">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-800 flex items-center justify-center">
              <Award size={16} />
            </div>
            <div>
              <h2 id="override-grade-title" className="text-base font-bold text-slate-900">
                Override Quiz Grade
              </h2>
              <p className="text-xs text-slate-500">
                Audited grade change for student {studentName}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4 overflow-y-auto flex-1">
          {error && (
            <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-2">
              <AlertCircle size={15} className="shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs flex items-center justify-between">
            <span className="text-slate-600">Current Recorded Score:</span>
            <strong className="text-slate-900 font-bold">
              {currentScore !== undefined && currentScore !== null ? `${currentScore} / 100` : 'None / Not submitted'}
            </strong>
          </div>

          <div>
            <label htmlFor="new-score" className="block text-xs font-semibold text-slate-700 mb-1">
              New Official Score (0 - 100) <span className="text-rose-500">*</span>
            </label>
            <input
              id="new-score"
              type="number"
              min="0"
              max="100"
              step="0.01"
              value={newScore}
              onChange={(e) => setNewScore(e.target.value)}
              placeholder="e.g., 85.5"
              className="w-full px-3 py-2 text-xs border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
              required
            />
          </div>

          <div>
            <label htmlFor="override-reason" className="block text-xs font-semibold text-slate-700 mb-1">
              Audit Reason <span className="text-rose-500">*</span>
            </label>
            <textarea
              id="override-reason"
              rows={3}
              maxLength={1000}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Provide a clear justification for this grade override..."
              className="w-full px-3 py-2 text-xs border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
              required
            />
          </div>

          {/* Audit History */}
          <div className="pt-2">
            <div className="flex items-center gap-1.5 mb-2 text-xs font-bold text-slate-800">
              <History size={14} />
              Grade Override History
            </div>
            {isLoadingHistory ? (
              <p className="text-xs text-slate-400">Loading history...</p>
            ) : history.length === 0 ? (
              <p className="text-xs text-slate-400 italic">No previous overrides recorded for this student.</p>
            ) : (
              <div className="space-y-1.5 max-h-36 overflow-y-auto">
                {history.map((item) => (
                  <div key={item.id} className="p-2 rounded-lg bg-slate-50 border border-slate-200 text-xs">
                    <div className="flex items-center justify-between font-semibold text-slate-800">
                      <span>
                        {item.originalScore !== null && item.originalScore !== undefined
                          ? `${item.originalScore} → `
                          : ''}
                        {item.finalScore} pts
                      </span>
                      <span className="text-[10px] text-slate-400">{formatVietnamDateTime(item.createdAt)}</span>
                    </div>
                    <p className="text-[11px] text-slate-600 mt-0.5">{item.reason}</p>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="flex items-center justify-end gap-2 pt-4 border-t border-slate-200">
            <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={isLoading}>
              Cancel
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={isLoading}
              className="bg-[#d64527] hover:bg-[#be3b20] text-white"
            >
              {isLoading ? 'Saving...' : 'Submit Override'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
