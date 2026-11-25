import React, { useState, useEffect } from 'react';
import { ClassroomAnnouncement, CreateAnnouncementInput } from '../types';
import { X, Pin, Bell, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface AnnouncementFormModalProps {
  isOpen: boolean;
  initialData?: ClassroomAnnouncement | null;
  isSubmitting?: boolean;
  serverError?: string | null;
  showPinToggle?: boolean;
  onClose: () => void;
  onSubmit: (data: CreateAnnouncementInput & { isPinned?: boolean }) => Promise<void> | void;
}

const MAX_TITLE_LENGTH = 255;
const MAX_CONTENT_LENGTH = 5000;

export const AnnouncementFormModal: React.FC<AnnouncementFormModalProps> = ({
  isOpen,
  initialData,
  isSubmitting = false,
  serverError = null,
  showPinToggle = false,
  onClose,
  onSubmit,
}) => {
  const isEditing = Boolean(initialData);

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isPinned, setIsPinned] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      if (initialData) {
        setTitle(initialData.title);
        setContent(initialData.content);
        setIsPinned(initialData.isPinned);
      } else {
        setTitle('');
        setContent('');
        setIsPinned(false);
      }
      setError(null);
    }
  }, [isOpen, initialData]);

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isSubmitting) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, isSubmitting, onClose]);

  if (!isOpen) return null;

  const displayedError = error || serverError;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;
    setError(null);

    const cleanTitle = title.trim();
    const cleanContent = content.trim();

    if (!cleanTitle) {
      setError('Please provide an announcement title.');
      return;
    }

    if (cleanTitle.length > MAX_TITLE_LENGTH) {
      setError(`Title cannot exceed ${MAX_TITLE_LENGTH} characters.`);
      return;
    }

    if (!cleanContent) {
      setError('Please provide announcement content.');
      return;
    }

    if (cleanContent.length > MAX_CONTENT_LENGTH) {
      setError(`Content cannot exceed ${MAX_CONTENT_LENGTH} characters.`);
      return;
    }

    try {
      const payload: CreateAnnouncementInput & { isPinned?: boolean } = showPinToggle
        ? { title: cleanTitle, content: cleanContent, isPinned }
        : { title: cleanTitle, content: cleanContent };
      await onSubmit(payload);
      onClose();
    } catch (submissionError) {
      if (!serverError) {
        setError(
          submissionError instanceof Error
            ? submissionError.message
            : 'Unable to save announcement. Please try again.',
        );
      }
    }
  };

  return (
    <div
      className="classroom-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="announcement-modal-title"
    >
      <div className="classroom-modal-content max-w-xl">
        <div className="classroom-modal-header">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-slate-900 text-white flex items-center justify-center shrink-0">
              <Bell size={16} aria-hidden="true" />
            </div>
            <div>
              <h3 id="announcement-modal-title" className="classroom-modal-title">
                {isEditing ? 'Edit Announcement' : 'New Classroom Announcement'}
              </h3>
              <p className="text-xs text-slate-500">
                {isEditing
                  ? 'Update announcement title and content.'
                  : 'Broadcast course notices, deadline reminders, or exam logistics to all students.'}
              </p>
            </div>
          </div>
          <button
            type="button"
            disabled={isSubmitting}
            onClick={onClose}
            className="classroom-modal-close disabled:opacity-50"
            aria-label="Close modal"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <form onSubmit={e => void handleSubmit(e)} className="p-6 space-y-4">
          {displayedError && (
            <div
              role="alert"
              className="p-3 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-xl flex items-center gap-2"
            >
              <AlertCircle size={15} className="shrink-0 text-rose-600" aria-hidden="true" />
              <span>{displayedError}</span>
            </div>
          )}

          {/* Title Field */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label htmlFor="announcement-title" className="text-xs font-bold text-slate-700">
                Title <span className="text-rose-500">*</span>
              </label>
              <span className="text-[11px] text-slate-400 font-mono">
                {title.length}/{MAX_TITLE_LENGTH}
              </span>
            </div>
            <input
              id="announcement-title"
              type="text"
              value={title}
              maxLength={MAX_TITLE_LENGTH}
              disabled={isSubmitting}
              onChange={e => setTitle(e.target.value)}
              placeholder="e.g. Midterm Examination Logistics, Coverage & Room Allocations"
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-transparent transition-all placeholder:text-slate-400 disabled:bg-slate-50 disabled:text-slate-500"
              autoFocus
            />
          </div>

          {/* Content Field (BR-ANN-001: Text-only, no attachments in MVP) */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label htmlFor="announcement-content" className="text-xs font-bold text-slate-700">
                Announcement Content <span className="text-rose-500">*</span>
              </label>
              <span className="text-[11px] text-slate-400 font-mono">
                {content.length}/{MAX_CONTENT_LENGTH}
              </span>
            </div>
            <textarea
              id="announcement-content"
              rows={8}
              value={content}
              maxLength={MAX_CONTENT_LENGTH}
              disabled={isSubmitting}
              onChange={e => setContent(e.target.value)}
              placeholder="Write the announcement details here... Use paragraphs, bullet points (- or *), bold text (**bold**), or inline code (`int x`)."
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-transparent transition-all placeholder:text-slate-400 font-sans leading-relaxed resize-y disabled:bg-slate-50 disabled:text-slate-500"
            />
            <p className="text-[11px] text-slate-500 mt-1">
              Supports plain text and safe formatting (### headings, lists, **bold**, `code`). Attachments and scheduled posting are not supported.
            </p>
          </div>

          {/* Pin Toggle Option (only rendered for prototype mock mode) */}
          {showPinToggle && (
            <div className="p-3.5 rounded-xl border border-slate-200/80 bg-slate-50/70 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-amber-100 text-amber-700 flex items-center justify-center shrink-0">
                  <Pin size={15} className="rotate-45" aria-hidden="true" />
                </div>
                <div>
                  <label htmlFor="announcement-pin" className="text-xs font-bold text-slate-900 cursor-pointer">
                    Pin to top of feed
                  </label>
                  <p className="text-[11px] text-slate-500">
                    Pinned announcements stay at the very top of the feed for all students.
                  </p>
                </div>
              </div>
              <input
                id="announcement-pin"
                type="checkbox"
                checked={isPinned}
                disabled={isSubmitting}
                onChange={e => setIsPinned(e.target.checked)}
                className="w-4 h-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900 cursor-pointer"
              />
            </div>
          )}

          {/* Modal Actions */}
          <div className="pt-2 flex items-center justify-end gap-2.5 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={isSubmitting}
              onClick={onClose}
              className="rounded-xl text-xs font-semibold text-slate-700 border-slate-300 hover:bg-slate-100"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              size="sm"
              isLoading={isSubmitting}
              disabled={isSubmitting}
              className="rounded-xl text-xs font-semibold bg-slate-900 hover:bg-slate-800 text-white"
            >
              {isSubmitting
                ? isEditing
                  ? 'Saving…'
                  : 'Publishing…'
                : isEditing
                  ? 'Save Changes'
                  : 'Publish Announcement'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
