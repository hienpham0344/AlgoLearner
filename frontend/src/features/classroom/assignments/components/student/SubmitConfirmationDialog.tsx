import React from 'react';
import { X, Send, AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface SubmitConfirmationDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void>;
  totalQuestions: number;
  answeredCount: number;
  isSubmitting?: boolean;
}

export const SubmitConfirmationDialog: React.FC<SubmitConfirmationDialogProps> = ({
  isOpen,
  onClose,
  onConfirm,
  totalQuestions,
  answeredCount,
  isSubmitting = false,
}) => {
  if (!isOpen) return null;

  const unansweredCount = Math.max(0, totalQuestions - answeredCount);
  const allAnswered = unansweredCount === 0;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="submit-modal-title"
    >
      <div className="bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Send size={18} className="text-slate-900" />
            <h3 id="submit-modal-title" className="text-base font-bold text-slate-900">
              Submit Assignment
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="p-1 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100"
            aria-label="Close dialog"
          >
            <X size={18} />
          </button>
        </div>

        <div className="p-6 space-y-4">
          <div className="flex items-center gap-4 p-4 rounded-xl bg-slate-50 border border-slate-200">
            <div className="w-12 h-12 rounded-xl bg-white border border-slate-200 flex items-center justify-center font-bold text-slate-900 shadow-xs">
              {answeredCount}/{totalQuestions}
            </div>
            <div>
              <span className="text-xs font-bold text-slate-800 block">Questions Completed</span>
              <span className="text-xs text-slate-500">
                {allAnswered
                  ? 'All questions have been answered.'
                  : `${unansweredCount} question${unansweredCount > 1 ? 's are' : ' is'} currently unanswered.`}
              </span>
            </div>
          </div>

          {!allAnswered && (
            <div className="p-3 bg-amber-50 rounded-xl border border-amber-200 flex items-start gap-2.5 text-xs text-amber-800">
              <AlertTriangle size={16} className="shrink-0 mt-0.5 text-amber-600" />
              <span>
                You still have <strong>{unansweredCount} unanswered questions</strong>. You can still submit now, but
                unanswered questions will receive 0 points.
              </span>
            </div>
          )}

          <p className="text-xs text-slate-600 leading-relaxed">
            Once submitted, your official attempt will be finalized and submitted for grading. Answers cannot be changed
            after confirmation.
          </p>

          <div className="pt-2 flex items-center justify-end gap-3">
            <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={isSubmitting}>
              Back to Questions
            </Button>
            <Button
              type="button"
              size="sm"
              onClick={onConfirm}
              disabled={isSubmitting}
              className="bg-emerald-600 hover:bg-emerald-700 text-white font-bold"
            >
              {isSubmitting ? 'Submitting...' : 'Confirm & Submit'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};
