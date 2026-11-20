import React, { useState } from 'react';
import { X, CheckCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { GradeEssayRequest } from '../../types';

interface GradeEssayModalProps {
  isOpen: boolean;
  onClose: () => void;
  onGrade: (request: GradeEssayRequest) => Promise<void>;
  questionTitle: string;
  questionId: string;
  maxPoints: number;
  currentScore?: number | null;
  currentFeedback?: string | null;
  isLoading?: boolean;
}

export const GradeEssayModal: React.FC<GradeEssayModalProps> = ({
  isOpen,
  onClose,
  onGrade,
  questionTitle,
  questionId,
  maxPoints,
  currentScore,
  currentFeedback,
  isLoading = false,
}) => {
  const [score, setScore] = useState(
    currentScore !== undefined && currentScore !== null ? currentScore.toString() : ''
  );
  const [feedback, setFeedback] = useState(currentFeedback || '');
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const numericScore = parseFloat(score);
    if (isNaN(numericScore) || numericScore < 0) {
      setError('Score must be a positive number or 0.');
      return;
    }

    if (numericScore > maxPoints) {
      setError(`Score cannot exceed maximum question points (${maxPoints}).`);
      return;
    }

    try {
      setError(null);
      await onGrade({
        questionId,
        score: numericScore,
        feedback: feedback.trim() || undefined,
      });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save essay grade.');
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="grade-essay-title"
    >
      <div className="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CheckCircle size={18} className="text-emerald-600" />
            <h3 id="grade-essay-title" className="text-base font-bold text-slate-900">
              Grade Essay Question
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
          <div>
            <span className="text-xs text-slate-500 block">Question</span>
            <p className="text-sm font-bold text-slate-900">{questionTitle}</p>
          </div>

          <div>
            <label htmlFor="essay-score" className="block text-xs font-bold text-slate-700 mb-1">
              Score (Max {maxPoints} pts) <span className="text-rose-500">*</span>
            </label>
            <input
              id="essay-score"
              type="number"
              step="0.5"
              min="0"
              max={maxPoints}
              value={score}
              onChange={(e) => setScore(e.target.value)}
              required
              className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
            />
          </div>

          <div>
            <label htmlFor="essay-feedback" className="block text-xs font-bold text-slate-700 mb-1">
              Teacher Feedback (Optional)
            </label>
            <textarea
              id="essay-feedback"
              rows={3}
              value={feedback}
              onChange={(e) => setFeedback(e.target.value)}
              placeholder="Provide comments, strengths, or suggestions for the student..."
              className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
            />
            {error && <p className="text-xs text-rose-600 mt-1">{error}</p>}
          </div>

          <div className="pt-2 flex items-center justify-end gap-3">
            <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={isLoading}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={isLoading}>
              {isLoading ? 'Saving...' : 'Submit Grade'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
