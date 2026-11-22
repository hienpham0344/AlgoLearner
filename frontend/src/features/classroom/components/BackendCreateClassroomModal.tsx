import { useEffect, useRef, useState, type FormEvent } from 'react';
import { X } from 'lucide-react';
import type { DemoRole, JoinMode } from '../types';

interface BackendCreateClassroomModalProps {
  isOpen: boolean;
  role: DemoRole;
  pending: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (input: {
    name: string;
    description: string;
    joinMode: JoinMode;
    ownerId?: string;
  }) => void;
}

/**
 * Minimalist dialog modal for creating a new classroom through the backend API.
 * Keeps creation state isolated from detail-view edit forms.
 */
export function BackendCreateClassroomModal({
  isOpen,
  role,
  pending,
  error,
  onClose,
  onSubmit,
}: BackendCreateClassroomModalProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [joinMode, setJoinMode] = useState<JoinMode>('AUTO_JOIN');
  const [ownerId, setOwnerId] = useState('');
  const nameInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!isOpen) return;
    setName('');
    setDescription('');
    setJoinMode('AUTO_JOIN');
    setOwnerId('');
    const focusTimer = window.setTimeout(() => nameInputRef.current?.focus(), 0);
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !pending) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.clearTimeout(focusTimer);
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, pending, onClose]);

  if (!isOpen) return null;

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!name.trim()) return;
    onSubmit({
      name: name.trim(),
      description: description.trim(),
      joinMode,
      ...(role === 'ADMIN' && ownerId.trim() ? { ownerId: ownerId.trim() } : {}),
    });
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="backend-create-classroom-title"
      onClick={event => {
        if (event.target === event.currentTarget && !pending) {
          onClose();
        }
      }}
    >
      <div className="w-full max-w-lg overflow-hidden rounded-lg border border-[#EAEAEA] bg-white">
        <div className="flex items-start justify-between gap-4 border-b border-[#EAEAEA] px-6 py-5">
          <div>
            <p className="font-mono text-[10px] uppercase tracking-widest text-[#787774]">
              {role} · New Space
            </p>
            <h2
              id="backend-create-classroom-title"
              className="mt-1 text-base font-semibold tracking-tight text-[#111111]"
            >
              Create classroom
            </h2>
            <p className="mt-0.5 text-xs text-[#787774]">
              Create a private teaching space with an auto-generated class code.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <kbd className="hidden rounded border border-[#EAEAEA] bg-[#F7F6F3] px-1.5 py-0.5 font-mono text-[10px] text-[#787774] sm:inline-block">
              ESC
            </kbd>
            <button
              type="button"
              disabled={pending}
              onClick={onClose}
              aria-label="Close dialog"
              className="rounded-md border border-transparent p-1 text-[#787774] transition-colors hover:border-[#EAEAEA] hover:bg-[#F7F6F3] hover:text-[#111111] disabled:opacity-50"
            >
              <X size={16} aria-hidden="true" />
            </button>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 p-6 text-xs">
          {error && (
            <p
              role="alert"
              className="rounded-md border border-[#F5C2C4] bg-[#FDEBEC] px-3 py-2 text-xs text-[#9F2F2D]"
            >
              {error}
            </p>
          )}
          {role === 'ADMIN' && (
            <div>
              <label
                htmlFor="backend-create-owner"
                className="mb-1.5 block font-medium text-[#111111]"
              >
                Teacher account UUID <span className="text-[#9F2F2D]">*</span>
              </label>
              <input
                id="backend-create-owner"
                aria-label="Teacher account UUID"
                placeholder="e.g. 11111111-1111-1111-1111-111111111111"
                required
                value={ownerId}
                onChange={event => setOwnerId(event.target.value)}
                className="w-full rounded-md border border-[#EAEAEA] bg-white px-3 py-2 font-mono text-xs text-[#111111] placeholder:text-[#A5A4A0] focus:border-[#111111] focus:outline-none"
              />
            </div>
          )}

          <div>
            <label
              htmlFor="backend-create-name"
              className="mb-1.5 block font-medium text-[#111111]"
            >
              Classroom name <span className="text-[#9F2F2D]">*</span>
            </label>
            <input
              ref={nameInputRef}
              id="backend-create-name"
              aria-label="Classroom name"
              placeholder="e.g. Data Structures & Algorithms"
              maxLength={150}
              required
              value={name}
              onChange={event => setName(event.target.value)}
              className="w-full rounded-md border border-[#EAEAEA] bg-white px-3 py-2 text-xs text-[#111111] placeholder:text-[#A5A4A0] focus:border-[#111111] focus:outline-none"
            />
          </div>

          <div>
            <label
              htmlFor="backend-create-desc"
              className="mb-1.5 block font-medium text-[#111111]"
            >
              Description <span className="font-normal text-[#787774]">(optional)</span>
            </label>
            <textarea
              id="backend-create-desc"
              rows={3}
              aria-label="Description"
              placeholder="Brief syllabus, schedule, or focus topics..."
              maxLength={2000}
              value={description}
              onChange={event => setDescription(event.target.value)}
              className="w-full resize-none rounded-md border border-[#EAEAEA] bg-white px-3 py-2 text-xs text-[#111111] placeholder:text-[#A5A4A0] focus:border-[#111111] focus:outline-none"
            />
          </div>

          <div>
            <span className="mb-1.5 block font-medium text-[#111111]">Join policy</span>
            <div className="grid grid-cols-2 gap-2.5">
              <label
                className={`cursor-pointer rounded-md border p-3 transition-colors ${
                  joinMode === 'AUTO_JOIN'
                    ? 'border-[#111111] bg-[#FBFBFA]'
                    : 'border-[#EAEAEA] bg-white hover:border-[#D1D1CF]'
                }`}
              >
                <input
                  type="radio"
                  name="backend-create-join-mode"
                  value="AUTO_JOIN"
                  checked={joinMode === 'AUTO_JOIN'}
                  onChange={() => setJoinMode('AUTO_JOIN')}
                  className="sr-only"
                />
                <div className="flex items-center justify-between">
                  <span
                    className={`font-semibold ${
                      joinMode === 'AUTO_JOIN' ? 'text-[#111111]' : 'text-[#2F3437]'
                    }`}
                  >
                    Auto join
                  </span>
                  <span
                    className={`h-2 w-2 rounded-full ${
                      joinMode === 'AUTO_JOIN' ? 'bg-[#111111]' : 'border border-[#D1D1CF]'
                    }`}
                  />
                </div>
                <p className="mt-1 text-[11px] leading-snug text-[#787774]">
                  Students with the code enter immediately.
                </p>
              </label>

              <label
                className={`cursor-pointer rounded-md border p-3 transition-colors ${
                  joinMode === 'REQUIRE_APPROVAL'
                    ? 'border-[#111111] bg-[#FBFBFA]'
                    : 'border-[#EAEAEA] bg-white hover:border-[#D1D1CF]'
                }`}
              >
                <input
                  type="radio"
                  name="backend-create-join-mode"
                  value="REQUIRE_APPROVAL"
                  checked={joinMode === 'REQUIRE_APPROVAL'}
                  onChange={() => setJoinMode('REQUIRE_APPROVAL')}
                  className="sr-only"
                />
                <div className="flex items-center justify-between">
                  <span
                    className={`font-semibold ${
                      joinMode === 'REQUIRE_APPROVAL' ? 'text-[#111111]' : 'text-[#2F3437]'
                    }`}
                  >
                    Require approval
                  </span>
                  <span
                    className={`h-2 w-2 rounded-full ${
                      joinMode === 'REQUIRE_APPROVAL' ? 'bg-[#111111]' : 'border border-[#D1D1CF]'
                    }`}
                  />
                </div>
                <p className="mt-1 text-[11px] leading-snug text-[#787774]">
                  Instructor approves each join request.
                </p>
              </label>
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 border-t border-[#EAEAEA] pt-4">
            <button
              type="button"
              disabled={pending}
              onClick={onClose}
              className="rounded-md border border-[#EAEAEA] bg-white px-3.5 py-1.5 text-xs font-medium text-[#787774] transition-colors hover:bg-[#F7F6F3] hover:text-[#111111] disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={pending}
              className="rounded-md bg-[#111111] px-4 py-1.5 text-xs font-medium text-white transition-colors hover:bg-[#2F3437] disabled:opacity-50"
            >
              Create
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
