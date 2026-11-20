import React from 'react';
import { AlertCircle, CheckCircle2, Flag } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface SubmitConfirmationDialogProps {
  isOpen: boolean;
  totalQuestions: number;
  answeredCount: number;
  unansweredCount: number;
  flaggedCount: number;
  isSubmitting?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export const SubmitConfirmationDialog: React.FC<SubmitConfirmationDialogProps> = ({
  isOpen,
  totalQuestions,
  answeredCount,
  unansweredCount,
  flaggedCount,
  isSubmitting = false,
  onConfirm,
  onCancel,
}) => {
  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="submit-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-xs"
    >
      <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl">
        <h2 id="submit-modal-title" className="text-lg font-bold text-slate-900">
          Ready to submit your quiz?
        </h2>

        {/* Warning if unanswered */}
        {unansweredCount > 0 ? (
          <div
            role="alert"
            className="mt-3.5 flex items-start gap-2.5 rounded-xl border border-amber-200 bg-amber-50 p-3.5 text-xs text-amber-900"
          >
            <AlertCircle size={18} className="shrink-0 text-amber-600 mt-0.5" />
            <div>
              <p className="font-bold">You have unanswered questions!</p>
              <p className="mt-0.5">
                {unansweredCount} of {totalQuestions} questions have not been answered. Unanswered questions receive 0 points.
              </p>
            </div>
          </div>
        ) : (
          <div className="mt-3.5 flex items-start gap-2.5 rounded-xl border border-emerald-200 bg-emerald-50 p-3.5 text-xs text-emerald-900">
            <CheckCircle2 size={18} className="shrink-0 text-emerald-600 mt-0.5" />
            <div>
              <p className="font-bold">All questions answered</p>
              <p className="mt-0.5">Great job! All {totalQuestions} questions have been answered.</p>
            </div>
          </div>
        )}

        {/* Summary counts */}
        <div className="mt-4 grid grid-cols-3 gap-2 text-center">
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-2.5">
            <p className="text-xs text-slate-500">Answered</p>
            <p className="text-lg font-bold text-slate-900">{answeredCount}</p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-2.5">
            <p className="text-xs text-slate-500">Unanswered</p>
            <p className={`text-lg font-bold ${unansweredCount > 0 ? 'text-amber-600' : 'text-slate-900'}`}>
              {unansweredCount}
            </p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-2.5">
            <div className="flex items-center justify-center gap-1 text-xs text-slate-500">
              <Flag size={11} className="text-amber-500 fill-amber-500" />
              <span>Flagged</span>
            </div>
            <p className="text-lg font-bold text-slate-900">{flaggedCount}</p>
          </div>
        </div>

        <p className="mt-4 text-xs text-slate-500">
          Once submitted, your answers will be locked and graded by the system according to the classroom rules.
        </p>

        {/* Actions */}
        <div className="mt-6 flex items-center justify-end gap-3 border-t border-slate-100 pt-4">
          <Button variant="outline" type="button" onClick={onCancel} disabled={isSubmitting}>
            Review Answers
          </Button>
          <Button type="button" onClick={onConfirm} disabled={isSubmitting}>
            {isSubmitting ? 'Submitting…' : 'Confirm Submission'}
          </Button>
        </div>
      </div>
    </div>
  );
};
