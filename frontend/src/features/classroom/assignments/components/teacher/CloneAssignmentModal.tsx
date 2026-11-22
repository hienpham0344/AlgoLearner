import React, { useState } from 'react';
import { X, Copy } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface CloneAssignmentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onClone: (targetClassroomId: string) => Promise<void>;
  isLoading?: boolean;
}

export const CloneAssignmentModal: React.FC<CloneAssignmentModalProps> = ({
  isOpen,
  onClose,
  onClone,
  isLoading = false,
}) => {
  const [targetClassroomId, setTargetClassroomId] = useState('');
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!targetClassroomId.trim()) {
      setError('Please provide a valid target Classroom ID.');
      return;
    }

    try {
      setError(null);
      await onClone(targetClassroomId.trim());
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to clone assignment.');
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="clone-assignment-title"
    >
      <div className="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Copy size={18} className="text-slate-700" />
            <h3 id="clone-assignment-title" className="text-base font-bold text-slate-900">
              Clone Assignment
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
          <p className="text-xs text-slate-600 leading-relaxed">
            This will copy the assignment settings and questions into a new <strong>Draft</strong> assignment in the
            target classroom. Student submissions and grade history will not be copied.
          </p>

          <div>
            <label htmlFor="target-class-id" className="block text-xs font-bold text-slate-700 mb-1">
              Target Classroom ID (UUID) <span className="text-rose-500">*</span>
            </label>
            <input
              id="target-class-id"
              type="text"
              value={targetClassroomId}
              onChange={(e) => setTargetClassroomId(e.target.value)}
              placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000"
              required
              className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900 font-mono"
            />
            {error && <p className="text-xs text-rose-600 mt-1">{error}</p>}
          </div>

          <div className="pt-2 flex items-center justify-end gap-3">
            <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={isLoading}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={isLoading}>
              {isLoading ? 'Cloning...' : 'Clone Assignment'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
