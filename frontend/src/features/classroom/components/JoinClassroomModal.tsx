import React, { useState, useRef, useEffect } from 'react';
import { JoinResultFeedback } from '../types';
import { X, KeyRound, AlertTriangle, CheckCircle2, Clock } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface JoinClassroomModalProps {
  isOpen: boolean;
  onClose: () => void;
  onJoin: (code: string) => JoinResultFeedback;
  onSuccess: (classroomId: string) => void;
}

export const JoinClassroomModal: React.FC<JoinClassroomModalProps> = ({
  isOpen,
  onClose,
  onJoin,
  onSuccess,
}) => {
  const [chars, setChars] = useState<string[]>(Array(8).fill(''));
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<JoinResultFeedback | null>(null);
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  // Reset and auto-focus first box whenever the modal opens
  useEffect(() => {
    if (isOpen) {
      setChars(Array(8).fill(''));
      setError(null);
      setResult(null);
      const timer = setTimeout(() => {
        inputRefs.current[0]?.focus();
      }, 60);
      return () => clearTimeout(timer);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const fullCode = chars.join('').toUpperCase();
  const isCodeComplete = chars.every(c => c.length === 1 && /[A-Z0-9]/.test(c));

  const fillFrom = (startIndex: number, text: string) => {
    const clean = text.toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (!clean) return;

    const next = [...chars];
    let lastFilled = startIndex;
    for (let i = 0; i < clean.length && startIndex + i < 8; i++) {
      next[startIndex + i] = clean[i];
      lastFilled = startIndex + i;
    }
    setChars(next);
    setError(null);

    const nextFocus = Math.min(lastFilled + 1, 7);
    inputRefs.current[nextFocus]?.focus();
  };

  const handlePaste = (e: React.ClipboardEvent, index: number) => {
    e.preventDefault();
    const pastedText = e.clipboardData.getData('text');
    if (!pastedText) return;

    const clean = pastedText.toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (!clean) return;

    // If pasted full 8+ characters, fill from slot 0; otherwise from the targeted slot
    const start = clean.length >= 8 ? 0 : index;
    fillFrom(start, clean);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>, index: number) => {
    if (e.key === 'Backspace') {
      e.preventDefault();
      if (!chars[index] && index > 0) {
        // Current box is empty -> clear previous box and focus it
        const next = [...chars];
        next[index - 1] = '';
        setChars(next);
        inputRefs.current[index - 1]?.focus();
      } else {
        // Clear current box
        const next = [...chars];
        next[index] = '';
        setChars(next);
      }
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      if (index > 0) {
        inputRefs.current[index - 1]?.focus();
      }
    } else if (e.key === 'ArrowRight') {
      e.preventDefault();
      if (index < 7) {
        inputRefs.current[index + 1]?.focus();
      }
    } else if (e.key === 'Delete') {
      e.preventDefault();
      const next = [...chars];
      next[index] = '';
      setChars(next);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>, index: number) => {
    const val = e.target.value;
    if (!val) {
      const next = [...chars];
      next[index] = '';
      setChars(next);
      return;
    }

    // Filter to valid alphanumeric characters
    const clean = val.toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (!clean) {
      // Non-alphanumeric character rejected
      return;
    }

    if (clean.length > 1) {
      fillFrom(index, clean);
      return;
    }

    const char = clean.charAt(clean.length - 1);
    const next = [...chars];
    next[index] = char;
    setChars(next);
    setError(null);

    // Auto-focus next box
    if (index < 7) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!isCodeComplete) {
      setError('Please enter all 8 characters of the class code.');
      return;
    }

    try {
      const feedback = onJoin(fullCode);
      setResult(feedback);
      setTimeout(() => {
        onSuccess(feedback.classroomId);
        onClose();
      }, 1800);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to join classroom.');
    }
  };

  return (
    <div
      className="classroom-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="join-class-title"
    >
      <div className="classroom-modal-content max-w-lg">
        <div className="classroom-modal-header">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-slate-900 text-white flex items-center justify-center shrink-0">
              <KeyRound size={16} aria-hidden="true" />
            </div>
            <div>
              <h3 id="join-class-title" className="classroom-modal-title">
                Join Classroom
              </h3>
              <p className="text-xs text-slate-500">
                Enter the 8-character Class Code provided by your instructor.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="classroom-modal-close"
            aria-label="Close dialog"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        {/* Error notification */}
        {error && (
          <div
            role="alert"
            className="p-3 mb-4 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-800 flex items-start gap-2.5"
          >
            <AlertTriangle size={16} className="shrink-0 mt-0.5 text-rose-600" aria-hidden="true" />
            <div className="flex-1">
              <strong className="block font-semibold">Unable to join classroom</strong>
              <span>{error}</span>
            </div>
          </div>
        )}

        {/* Result notification */}
        {result && (
          <div
            role="status"
            className={`p-3.5 mb-4 rounded-xl border text-xs flex items-start gap-2.5 ${
              result.outcome === 'PENDING_APPROVAL'
                ? 'bg-amber-50 border-amber-200 text-amber-800'
                : 'bg-emerald-50 border-emerald-200 text-emerald-800'
            }`}
          >
            {result.outcome === 'PENDING_APPROVAL' ? (
              <Clock size={16} className="shrink-0 mt-0.5 text-amber-600" aria-hidden="true" />
            ) : (
              <CheckCircle2 size={16} className="shrink-0 mt-0.5 text-emerald-600" aria-hidden="true" />
            )}
            <div>
              <strong className="block font-semibold">
                {result.outcome === 'PENDING_APPROVAL' ? 'Request Submitted' : 'Enrolled Successfully'}
              </strong>
              <span>{result.message}</span>
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* 8-Character Segmented Input */}
          <div className="space-y-2">
            <label
              id="segmented-code-label"
              className="text-xs font-bold text-slate-800 uppercase tracking-wide block"
            >
              Enter 8-character code
            </label>

            <div
              role="group"
              aria-labelledby="segmented-code-label"
              className="flex items-center justify-between gap-1.5 sm:gap-2"
            >
              {chars.map((char, index) => (
                <input
                  key={index}
                  ref={el => {
                    inputRefs.current[index] = el;
                  }}
                  type="text"
                  inputMode="text"
                  autoCapitalize="characters"
                  autoComplete="off"
                  spellCheck={false}
                  maxLength={1}
                  value={char}
                  disabled={Boolean(result)}
                  onChange={e => handleChange(e, index)}
                  onKeyDown={e => handleKeyDown(e, index)}
                  onPaste={e => handlePaste(e, index)}
                  onFocus={e => e.target.select()}
                  aria-label={`Character ${index + 1} of 8`}
                  aria-invalid={Boolean(error)}
                  className={`w-9 sm:w-11 h-12 text-center text-lg sm:text-xl font-mono font-bold uppercase rounded-xl border transition-all select-none ${
                    error
                      ? 'border-rose-300 bg-rose-50/60 text-rose-900 focus:border-rose-500 focus:ring-2 focus:ring-rose-400'
                      : 'border-slate-300 bg-slate-50 text-slate-900 hover:border-slate-400 focus:bg-white focus:border-slate-900 focus:ring-2 focus:ring-slate-900'
                  }`}
                />
              ))}
            </div>

            <p className="text-[11.5px] text-slate-500 text-center pt-0.5">
              Case-insensitive alphanumeric code provided by your teacher (e.g.{' '}
              <span className="font-mono font-semibold text-slate-700">ALGO2026</span>)
            </p>
          </div>

          <div className="classroom-modal-footer">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onClose}
              disabled={Boolean(result)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={!isCodeComplete || Boolean(result)}
              className="bg-slate-900 text-white hover:bg-slate-800"
            >
              Join Class
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
