import React, { useState } from 'react';
import { Classroom, DemoPersona } from '../types';
import { X, Share2, ShieldAlert } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface TransferOwnershipModalProps {
  isOpen: boolean;
  classroom: Classroom;
  teachers: DemoPersona[];
  onClose: () => void;
  onTransfer: (targetTeacherId: string) => void;
}

export const TransferOwnershipModal: React.FC<TransferOwnershipModalProps> = ({
  isOpen,
  classroom,
  teachers,
  onClose,
  onTransfer,
}) => {
  const availableTeachers = teachers.filter(t => t.id !== classroom.ownerId);
  const [targetTeacherId, setTargetTeacherId] = useState(
    availableTeachers.length > 0 ? availableTeachers[0].id : '',
  );
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!targetTeacherId) {
      setError('Please select a target Teacher.');
      return;
    }
    try {
      onTransfer(targetTeacherId);
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Transfer failed.');
    }
  };

  return (
    <div
      className="classroom-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="transfer-modal-title"
    >
      <div className="classroom-modal-content max-w-md">
        <div className="classroom-modal-header">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-indigo-100 text-indigo-700 flex items-center justify-center">
              <Share2 size={16} aria-hidden="true" />
            </div>
            <div>
              <h3 id="transfer-modal-title" className="classroom-modal-title">
                Transfer Ownership
              </h3>
              <p className="text-xs text-slate-500">
                Reassign primary course instructor
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

        <div className="p-3 mb-4 rounded-xl bg-slate-50 border border-slate-200 text-xs space-y-1">
          <div className="text-slate-500">Current Classroom Owner:</div>
          <div className="font-bold text-slate-800 text-sm">
            {classroom.ownerName} <span className="text-slate-400 font-mono font-normal">({classroom.ownerEmail})</span>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label htmlFor="target-teacher-select" className="text-xs font-bold text-slate-800 uppercase tracking-wide">
              Select New Teacher Owner *
            </label>
            {availableTeachers.length === 0 ? (
              <p className="text-xs text-slate-500 italic">No other registered teachers available.</p>
            ) : (
              <select
                id="target-teacher-select"
                value={targetTeacherId}
                onChange={e => setTargetTeacherId(e.target.value)}
                className="w-full px-3 py-2 text-xs border border-slate-200 rounded-lg bg-white text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900"
              >
                {availableTeachers.map(t => (
                  <option key={t.id} value={t.id}>
                    {t.fullName} ({t.email})
                  </option>
                ))}
              </select>
            )}
            <p className="text-[11px] text-slate-400">
              The selected teacher will become the primary owner and receive full administrative access to this classroom.
            </p>
          </div>

          <div className="classroom-modal-footer">
            <Button type="button" variant="outline" size="sm" onClick={onClose}>
              Cancel
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={availableTeachers.length === 0}
              className="bg-indigo-600 hover:bg-indigo-700 text-white"
            >
              Confirm Transfer
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
