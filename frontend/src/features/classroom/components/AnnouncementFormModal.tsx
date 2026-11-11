import React, { useState, useEffect } from 'react';
import { ClassroomAnnouncement } from '../types';
import { X, Pin, Bell, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface AnnouncementFormModalProps {
  isOpen: boolean;
  initialData?: ClassroomAnnouncement | null;
  onClose: () => void;
  onSubmit: (data: { title: string; content: string; isPinned: boolean }) => void;
}

const MAX_TITLE_LENGTH = 150;
const MAX_CONTENT_LENGTH = 5000;

export const AnnouncementFormModal: React.FC<AnnouncementFormModalProps> = ({
  isOpen,
  initialData,
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

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
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

    onSubmit({
      title: cleanTitle,
      content: cleanContent,
      isPinned,
    });
    onClose();
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
              <Bell size={16} />
            </div>
            <div>
              <h3 id="announcement-modal-title" className="classroom-modal-title">
                {isEditing ? 'Edit Announcement' : 'New Classroom Announcement'}
              </h3>
              <p className="text-xs text-slate-500">
                {isEditing
                  ? 'Update announcement details and visibility.'
                  : 'Broadcast course notices, deadline reminders, or exam logistics to all students.'}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="classroom-modal-close"
            aria-label="Close modal"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-xl flex items-center gap-2">
              <AlertCircle size={15} className="shrink-0 text-rose-600" />
              <span>{error}</span>
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
              onChange={e => setTitle(e.target.value)}
              placeholder="e.g. Midterm Examination Logistics, Coverage & Room Allocations"
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-transparent transition-all placeholder:text-slate-400"
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
              onChange={e => setContent(e.target.value)}
              placeholder="Write the announcement details here... Use paragraphs, bullet points (- or *), bold text (**bold**), or inline code (`int x`)."
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-transparent transition-all placeholder:text-slate-400 font-sans leading-relaxed resize-y"
            />
            <p className="text-[11px] text-slate-500 mt-1">
              Supports markdown headers (###), bullet lists, and code blocks. Per BR-ANN-001, attachments and scheduled posting are deferred to future releases.
            </p>
          </div>

          {/* Pin Toggle Option (FR-ANN-004 & BR-ANN-003) */}
          <div className="p-3.5 rounded-xl border border-slate-200/80 bg-slate-50/70 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-amber-100 text-amber-700 flex items-center justify-center shrink-0">
                <Pin size={15} className="rotate-45" />
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
              onChange={e => setIsPinned(e.target.checked)}
              className="w-4 h-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900 cursor-pointer"
            />
          </div>

          {/* Modal Actions */}
          <div className="pt-2 flex items-center justify-end gap-2.5 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onClose}
              className="rounded-xl text-xs font-semibold"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              size="sm"
              className="rounded-xl text-xs font-semibold bg-slate-900 hover:bg-slate-800 text-white"
            >
              {isEditing ? 'Save Changes' : 'Publish Announcement'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
