import React from 'react';
import { AlertTriangle, CheckCircle2, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface SubmitConfirmationDialogProps {
  isOpen: boolean;
  totalQuestions: number;
  answeredCount: number;
  unansweredCount: number;
  flaggedCount: number;
  isSubmitting: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export const SubmitConfirmationDialog: React.FC<SubmitConfirmationDialogProps> = ({
  isOpen,
  totalQuestions,
  answeredCount,
  unansweredCount,
  flaggedCount,
  isSubmitting,
  onConfirm,
  onCancel,
}) => {
  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="submit-dialog-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-in fade-in"
    >
      <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200 flex flex-col gap-5">
        {/* Header */}
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-amber-100 text-amber-800 flex items-center justify-center shrink-0">
              <AlertTriangle size={18} />
            </div>
            <div>
              <h2 id="submit-dialog-title" className="text-base font-bold text-slate-900">
                Confirm Quiz Submission
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Please review your answer summary before submitting
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onCancel}
            disabled={isSubmitting}
            className="text-slate-400 hover:text-slate-600 p-1 rounded-lg hover:bg-slate-100 transition-colors"
            aria-label="Close dialog"
          >
            <X size={18} />
          </button>
        </div>

        {/* Breakdown Stats */}
        <div className="grid grid-cols-3 gap-2.5 p-3 rounded-xl bg-slate-50 border border-slate-100 text-center">
          <div className="p-2 bg-white rounded-lg border border-slate-100 shadow-2xs">
            <span className="text-[11px] font-medium text-slate-500 block">Answered</span>
            <span className="text-base font-bold text-emerald-600">{answeredCount}</span>
            <span className="text-[10px] text-slate-400 block">of {totalQuestions}</span>
          </div>
          <div className="p-2 bg-white rounded-lg border border-slate-100 shadow-2xs">
            <span className="text-[11px] font-medium text-slate-500 block">Unanswered</span>
            <span className={`text-base font-bold ${unansweredCount > 0 ? 'text-rose-600' : 'text-slate-700'}`}>
              {unansweredCount}
            </span>
            <span className="text-[10px] text-slate-400 block">questions</span>
          </div>
          <div className="p-2 bg-white rounded-lg border border-slate-100 shadow-2xs">
            <span className="text-[11px] font-medium text-slate-500 block">Flagged</span>
            <span className={`text-base font-bold ${flaggedCount > 0 ? 'text-amber-600' : 'text-slate-700'}`}>
              {flaggedCount}
            </span>
            <span className="text-[10px] text-slate-400 block">to review</span>
          </div>
        </div>

        {/* Warning messages */}
        {unansweredCount > 0 ? (
          <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-800 flex items-start gap-2.5">
            <AlertTriangle size={15} className="text-rose-600 shrink-0 mt-0.5" />
            <p className="leading-relaxed">
              You still have <strong className="font-bold">{unansweredCount} unanswered question(s)</strong>. Unanswered questions will receive 0 points. Are you sure you want to proceed?
            </p>
          </div>
        ) : (
          <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800 flex items-start gap-2.5">
            <CheckCircle2 size={15} className="text-emerald-600 shrink-0 mt-0.5" />
            <p className="leading-relaxed">
              You have answered all questions! Once submitted, you cannot modify your answers.
            </p>
          </div>
        )}

        {/* Action Buttons */}
        <div className="flex items-center justify-end gap-3 pt-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onCancel}
            disabled={isSubmitting}
            className="rounded-xl text-xs font-semibold border-slate-200 text-slate-700"
          >
            Back to Quiz
          </Button>

          <Button
            type="button"
            size="sm"
            onClick={onConfirm}
            disabled={isSubmitting}
            className="rounded-xl text-xs font-semibold bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs"
          >
            {isSubmitting ? 'Submitting…' : 'Confirm & Submit'}
          </Button>
        </div>
      </div>
    </div>
  );
};
