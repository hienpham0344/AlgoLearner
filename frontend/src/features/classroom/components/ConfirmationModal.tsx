import React, { useEffect } from 'react';
import { AlertCircle, AlertTriangle, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface ConfirmationModalProps {
  isOpen: boolean;
  title: string;
  description?: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'danger' | 'default' | 'warning';
  isConfirming?: boolean;
  errorMessage?: string | null;
  onConfirm: () => Promise<void> | void;
  onCancel: () => void;
}

export const ConfirmationModal: React.FC<ConfirmationModalProps> = ({
  isOpen,
  title,
  description,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  variant = 'default',
  isConfirming = false,
  errorMessage = null,
  onConfirm,
  onCancel,
}) => {
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isConfirming) {
        onCancel();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, isConfirming, onCancel]);

  if (!isOpen) return null;

  const contentText = description || message || '';

  return (
    <div
      className="classroom-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-modal-title"
    >
      <div className="classroom-modal-content max-w-md">
        <div className="classroom-modal-header">
          <div className="flex items-center gap-3">
            {(variant === 'danger' || variant === 'warning') && (
              <div
                className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 ${
                  variant === 'danger'
                    ? 'bg-rose-100 text-rose-700'
                    : 'bg-amber-100 text-amber-700'
                }`}
              >
                <AlertTriangle size={18} aria-hidden="true" />
              </div>
            )}
            <h3 id="confirm-modal-title" className="classroom-modal-title">
              {title}
            </h3>
          </div>
          <button
            type="button"
            disabled={isConfirming}
            onClick={onCancel}
            className="classroom-modal-close disabled:opacity-50"
            aria-label="Close dialog"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <p className="text-sm text-slate-600 leading-relaxed mb-4">{contentText}</p>

        {errorMessage && (
          <div
            role="alert"
            className="mb-4 p-3 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-xl flex items-center gap-2"
          >
            <AlertCircle size={15} className="shrink-0 text-rose-600" aria-hidden="true" />
            <span>{errorMessage}</span>
          </div>
        )}

        <div className="classroom-modal-footer">
          <Button
            variant="outline"
            size="sm"
            disabled={isConfirming}
            onClick={onCancel}
            className="text-slate-700 border-slate-300 hover:bg-slate-100"
          >
            {cancelLabel}
          </Button>
          <Button
            variant={variant === 'danger' ? 'danger' : 'primary'}
            size="sm"
            isLoading={isConfirming}
            disabled={isConfirming}
            onClick={() => void onConfirm()}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
};
