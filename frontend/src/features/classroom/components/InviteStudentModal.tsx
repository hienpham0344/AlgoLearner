import React, { useState } from 'react';
import { DemoPersona } from '../types';
import { X, Mail, ShieldAlert } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface InviteStudentModalProps {
  isOpen: boolean;
  students: DemoPersona[];
  onClose: () => void;
  onInvite: (email: string, expiresAt: string) => void;
}

export const InviteStudentModal: React.FC<InviteStudentModalProps> = ({
  isOpen,
  students,
  onClose,
  onInvite,
}) => {
  const [email, setEmail] = useState(students.length > 0 ? students[0].email : '');
  const [expiresAt, setExpiresAt] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 14);
    return d.toISOString().slice(0, 10);
  });
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!email.trim()) {
      setError('Student email is required.');
      return;
    }

    if (!expiresAt) {
      setError('Please select an expiration date for the invitation.');
      return;
    }

    try {
      const expirationIso = new Date(`${expiresAt}T23:59:59Z`).toISOString();
      onInvite(email.trim(), expirationIso);
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to send invitation.');
    }
  };

  return (
    <div
      className="classroom-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="invite-student-title"
    >
      <div className="classroom-modal-content max-w-md">
        <div className="classroom-modal-header">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-slate-900 text-white flex items-center justify-center">
              <Mail size={16} aria-hidden="true" />
            </div>
            <div>
              <h3 id="invite-student-title" className="classroom-modal-title">
                Send Class Invitation
              </h3>
              <p className="text-xs text-slate-500">
                Send an invitation directly to a registered student's email.
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
            <label htmlFor="invite-student-email-select" className="text-xs font-bold text-slate-800 uppercase tracking-wide">
              Target Registered Student
            </label>
            <select
              id="invite-student-email-select"
              value={email}
              onChange={e => setEmail(e.target.value)}
              className="w-full px-3 py-2 text-xs border border-slate-200 rounded-lg bg-white text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900"
            >
              {students.map(s => (
                <option key={s.id} value={s.email}>
                  {s.fullName} ({s.email})
                </option>
              ))}
            </select>
            <p className="text-[11px] text-slate-400">
              Invitations may only be sent to registered student accounts.
            </p>
          </div>

          <div className="space-y-1.5">
            <label htmlFor="invite-expiry-date" className="text-xs font-bold text-slate-800 uppercase tracking-wide">
              Invitation Expiration Date *
            </label>
            <input
              id="invite-expiry-date"
              type="date"
              required
              value={expiresAt}
              onChange={e => setExpiresAt(e.target.value)}
              className="w-full px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900"
            />
          </div>

          <div className="classroom-modal-footer">
            <Button type="button" variant="outline" size="sm" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" size="sm" className="bg-slate-900 text-white">
              Send Invitation
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
