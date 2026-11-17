import React, { useState } from 'react';
import { DemoPersona } from '../types';
import { X, UserPlus, ShieldAlert } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface DirectAddStudentModalProps {
  isOpen: boolean;
  students: DemoPersona[];
  onClose: () => void;
  onDirectAdd: (usernameOrEmail: string) => void;
}

export const DirectAddStudentModal: React.FC<DirectAddStudentModalProps> = ({
  isOpen,
  students,
  onClose,
  onDirectAdd,
}) => {
  const [selectedIdentifier, setSelectedIdentifier] = useState(
    students.length > 0 ? students[0].email : '',
  );
  const [customInput, setCustomInput] = useState('');
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const identifier = customInput.trim() || selectedIdentifier;
    if (!identifier) {
      setError('Please provide a student username or email.');
      return;
    }

    try {
      onDirectAdd(identifier);
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to add student.');
    }
  };

  return (
    <div
      className="classroom-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="direct-add-title"
    >
      <div className="classroom-modal-content max-w-md">
        <div className="classroom-modal-header">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-slate-900 text-white flex items-center justify-center">
              <UserPlus size={16} aria-hidden="true" />
            </div>
            <div>
              <h3 id="direct-add-title" className="classroom-modal-title">
                Direct Add Student
              </h3>
              <p className="text-xs text-slate-500">
                Enroll a registered student directly into this classroom.
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

        {error && (
          <div className="p-3 mb-4 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-700 flex items-center gap-2">
            <ShieldAlert size={15} className="shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label htmlFor="quick-student-select" className="text-xs font-bold text-slate-800 uppercase tracking-wide">
              Select Registered Student
            </label>
            <select
              id="quick-student-select"
              value={selectedIdentifier}
              onChange={e => {
                setSelectedIdentifier(e.target.value);
                setCustomInput('');
              }}
              className="w-full px-3 py-2 text-xs border border-slate-200 rounded-lg bg-white text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900"
            >
              {students.map(s => (
                <option key={s.id} value={s.email}>
                  {s.fullName} (@{s.username} · {s.email})
                </option>
              ))}
            </select>
          </div>

          <div className="relative flex py-1 items-center">
            <div className="flex-grow border-t border-slate-200" />
            <span className="flex-shrink mx-3 text-slate-400 text-[11px] font-mono">OR TYPE MANUALLY</span>
            <div className="flex-grow border-t border-slate-200" />
          </div>

          <div className="space-y-1.5">
            <label htmlFor="manual-student-input" className="text-xs font-bold text-slate-800 uppercase tracking-wide">
              Username or Email
            </label>
            <input
              id="manual-student-input"
              type="text"
              placeholder="e.g. maria_garcia or alex.chen@student.algolearn.edu"
              value={customInput}
              onChange={e => setCustomInput(e.target.value)}
              className="w-full px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900"
            />
          </div>

          <div className="classroom-modal-footer">
            <Button type="button" variant="outline" size="sm" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" size="sm" className="bg-slate-900 text-white">
              Enroll Student
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
