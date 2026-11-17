import React, { useState } from 'react';
import { CreateClassroomInput, DemoPersona, DemoRole, JoinMode } from '../types';
import { X, ShieldAlert } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface CreateClassroomModalProps {
  isOpen: boolean;
  currentRole: DemoRole;
  currentPersona?: DemoPersona;
  teachers: DemoPersona[];
  onClose: () => void;
  onSubmit: (input: CreateClassroomInput) => void;
}

export const CreateClassroomModal: React.FC<CreateClassroomModalProps> = ({
  isOpen,
  currentRole,
  teachers,
  onClose,
  onSubmit,
}) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [joinMode, setJoinMode] = useState<JoinMode>('AUTO_JOIN');
  const [codeExpiresAt, setCodeExpiresAt] = useState('');
  const [targetTeacherId, setTargetTeacherId] = useState(
    teachers.length > 0 ? teachers[0].id : '',
  );
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError('Classroom name is required.');
      return;
    }

    if (currentRole === 'ADMIN' && !targetTeacherId) {
      setError('Admin must assign an active Teacher as the classroom owner.');
      return;
    }

    try {
      onSubmit({
        name: name.trim(),
        description: description.trim(),
        joinMode,
        codeExpiresAt: codeExpiresAt ? new Date(`${codeExpiresAt}T23:59:59Z`).toISOString() : null,
        targetTeacherId: currentRole === 'ADMIN' ? targetTeacherId : undefined,
      });
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to create classroom.');
    }
  };

  return (
    <div
      className="classroom-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-class-title"
    >
      <div className="classroom-modal-content">
        <div className="classroom-modal-header">
          <div>
            <h3 id="create-class-title" className="classroom-modal-title">
              {currentRole === 'ADMIN' ? 'Create Classroom for Teacher' : 'Create New Classroom'}
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Set up a private learning space with automated code generation.
            </p>
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
            <ShieldAlert size={15} className="shrink-0" aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Admin Owner Assignment per FR-CLS-001 (AC-02) */}
          {currentRole === 'ADMIN' && (
            <div className="space-y-1.5 p-3 rounded-xl bg-amber-50 border border-amber-200">
              <label
                htmlFor="assigned-teacher-select"
                className="text-xs font-bold text-amber-900 block"
              >
                Assign Instructor
              </label>
              <select
                id="assigned-teacher-select"
                value={targetTeacherId}
                onChange={e => setTargetTeacherId(e.target.value)}
                className="w-full px-3 py-2 text-xs border border-amber-300 rounded-lg bg-white text-slate-800 focus:outline-none focus:ring-1 focus:ring-amber-500"
              >
                {teachers.map(t => (
                  <option key={t.id} value={t.id}>
                    {t.fullName} ({t.email})
                  </option>
                ))}
              </select>
              <p className="text-[11px] text-amber-700 mt-0.5">
                Each classroom must be assigned an active instructor as owner.
              </p>
            </div>
          )}

          <div className="space-y-1.5">
            <label htmlFor="class-name-input" className="text-xs font-bold text-slate-800 uppercase tracking-wide">
              Classroom Name *
            </label>
            <input
              id="class-name-input"
              type="text"
              required
              maxLength={150}
              placeholder="e.g. Advanced Graph Theory & Network Flow"
              value={name}
              onChange={e => setName(e.target.value)}
              className="w-full px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900"
            />
          </div>

          <div className="space-y-1.5">
            <label htmlFor="class-desc-input" className="text-xs font-bold text-slate-800 uppercase tracking-wide">
              Description
            </label>
            <textarea
              id="class-desc-input"
              rows={3}
              maxLength={2000}
              placeholder="Outline the course objectives, prerequisites, and automated problem topics..."
              value={description}
              onChange={e => setDescription(e.target.value)}
              className="w-full px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900 resize-none"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-bold text-slate-800 uppercase tracking-wide block">
              Enrollment Mode
            </label>
            <div className="grid grid-cols-2 gap-2">
              <label
                className={`p-2.5 rounded-lg border text-xs cursor-pointer flex items-center gap-2 ${
                  joinMode === 'AUTO_JOIN'
                    ? 'border-slate-900 bg-slate-900 text-white font-semibold'
                    : 'border-slate-200 bg-slate-50 text-slate-700'
                }`}
              >
                <input
                  type="radio"
                  name="joinMode"
                  value="AUTO_JOIN"
                  checked={joinMode === 'AUTO_JOIN'}
                  onChange={() => setJoinMode('AUTO_JOIN')}
                  className="sr-only"
                />
                <span>Auto Join</span>
              </label>

              <label
                className={`p-2.5 rounded-lg border text-xs cursor-pointer flex items-center gap-2 ${
                  joinMode === 'REQUIRE_APPROVAL'
                    ? 'border-slate-900 bg-slate-900 text-white font-semibold'
                    : 'border-slate-200 bg-slate-50 text-slate-700'
                }`}
              >
                <input
                  type="radio"
                  name="joinMode"
                  value="REQUIRE_APPROVAL"
                  checked={joinMode === 'REQUIRE_APPROVAL'}
                  onChange={() => setJoinMode('REQUIRE_APPROVAL')}
                  className="sr-only"
                />
                <span>Require Approval</span>
              </label>
            </div>
          </div>

          <div className="space-y-1.5">
            <label htmlFor="class-code-expiry" className="text-xs font-bold text-slate-800 uppercase tracking-wide">
              Initial Code Expiration Date (Optional)
            </label>
            <input
              id="class-code-expiry"
              type="date"
              value={codeExpiresAt}
              onChange={e => setCodeExpiresAt(e.target.value)}
              className="w-full px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900"
            />
            <p className="text-[11px] text-slate-400">
              Leave empty for an evergreen code that does not expire.
            </p>
          </div>

          <div className="classroom-modal-footer">
            <Button type="button" variant="outline" size="sm" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" size="sm" className="bg-slate-900 text-white">
              Create Classroom
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
