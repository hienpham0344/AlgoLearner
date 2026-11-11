import React, { useEffect, useRef } from 'react';
import { ClassroomAnnouncement } from '../types';
import { UserAvatar } from './UserAvatar';
import { Pin, Pencil, Trash2, Calendar } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface AnnouncementCardProps {
  announcement: ClassroomAnnouncement;
  isTeacherOrAdmin: boolean;
  isHighlighted?: boolean;
  onEdit?: (announcement: ClassroomAnnouncement) => void;
  onDelete?: (announcement: ClassroomAnnouncement) => void;
  onTogglePin?: (announcementId: string) => void;
}

/**
 * Parses markdown-like plain text safely into React nodes without dangerouslySetInnerHTML.
 * Supports paragraphs, bullet lists, bold (**text**), code snippets (`code`), and headers (###).
 */
function renderSafeContent(text: string): React.ReactNode {
  const lines = text.split('\n');
  const elements: React.ReactNode[] = [];
  let listBuffer: string[] = [];

  const flushList = () => {
    if (listBuffer.length > 0) {
      elements.push(
        <ul key={`ul-${elements.length}`} className="my-2 space-y-1 list-disc list-inside text-slate-700 text-sm">
          {listBuffer.map((item, idx) => (
            <li key={idx} className="leading-relaxed">
              {formatInlineFormatting(item)}
            </li>
          ))}
        </ul>,
      );
      listBuffer = [];
    }
  };

  lines.forEach((line, index) => {
    const trimmed = line.trim();

    // Bullet point
    if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      listBuffer.push(trimmed.slice(2));
      return;
    }

    // Numbered list item
    const numberedMatch = trimmed.match(/^\d+\.\s+(.*)$/);
    if (numberedMatch) {
      flushList();
      elements.push(
        <div key={`num-${index}`} className="flex items-start gap-2 my-1 text-sm text-slate-700 leading-relaxed">
          <span className="font-semibold text-slate-900 select-none">{trimmed.slice(0, trimmed.indexOf(' ') + 1)}</span>
          <span>{formatInlineFormatting(numberedMatch[1])}</span>
        </div>,
      );
      return;
    }

    flushList();

    if (!trimmed) {
      // Empty line / paragraph spacer
      elements.push(<div key={`space-${index}`} className="h-2" />);
      return;
    }

    // Headers
    if (trimmed.startsWith('### ')) {
      elements.push(
        <h4 key={`h4-${index}`} className="text-sm font-bold text-slate-900 mt-3 mb-1">
          {trimmed.slice(4)}
        </h4>,
      );
      return;
    }

    if (trimmed.startsWith('## ')) {
      elements.push(
        <h3 key={`h3-${index}`} className="text-base font-bold text-slate-900 mt-3.5 mb-1.5">
          {trimmed.slice(3)}
        </h3>,
      );
      return;
    }

    // Regular paragraph
    elements.push(
      <p key={`p-${index}`} className="text-sm text-slate-700 leading-relaxed">
        {formatInlineFormatting(line)}
      </p>,
    );
  });

  flushList();
  return elements;
}

function formatInlineFormatting(str: string): React.ReactNode {
  // Split on inline code `...` and bold **...**
  const parts = str.split(/(`[^`]+`|\*\*[^*]+\*\*)/g);

  return parts.map((part, i) => {
    if (part.startsWith('`') && part.endsWith('`') && part.length >= 2) {
      return (
        <code
          key={i}
          className="px-1.5 py-0.5 rounded bg-slate-100 border border-slate-200/80 font-mono text-[12px] text-slate-800"
        >
          {part.slice(1, -1)}
        </code>
      );
    }
    if (part.startsWith('**') && part.endsWith('**') && part.length >= 4) {
      return (
        <strong key={i} className="font-semibold text-slate-900">
          {part.slice(2, -2)}
        </strong>
      );
    }
    return part;
  });
}

function formatUtcTimestamp(isoString: string): string {
  try {
    const d = new Date(isoString);
    return d.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return isoString;
  }
}

export const AnnouncementCard: React.FC<AnnouncementCardProps> = ({
  announcement,
  isTeacherOrAdmin,
  isHighlighted = false,
  onEdit,
  onDelete,
  onTogglePin,
}) => {
  const cardRef = useRef<HTMLElement | null>(null);

  // Auto-scroll when targeted via deep link (FR-ANN-003)
  useEffect(() => {
    if (isHighlighted && cardRef.current) {
      cardRef.current.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }, [isHighlighted]);

  const isEdited = announcement.updatedAt !== announcement.createdAt;

  return (
    <article
      ref={cardRef}
      id={`announcement-${announcement.id}`}
      data-pinned={announcement.isPinned}
      className={`announcement-card classroom-glass-card p-6 relative transition-all duration-300 ${
        announcement.isPinned ? 'announcement-card-pinned border-amber-300/60' : ''
      } ${
        isHighlighted ? 'announcement-card-highlighted ring-2 ring-blue-500/80 bg-blue-50/20' : ''
      }`}
    >
      {/* Pinned Marker / Header Row */}
      <div className="flex items-center justify-between gap-4 mb-4">
        <div className="flex items-center gap-3">
          <UserAvatar name={announcement.authorName} size="md" />
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-sm font-bold text-slate-900">{announcement.authorName}</span>
              <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 border border-slate-200/60 uppercase tracking-wider">
                {announcement.authorRole === 'ADMIN' ? 'Administrator' : 'Instructor'}
              </span>
              {announcement.isPinned && (
                <span className="inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-full bg-amber-50 text-amber-800 border border-amber-200/80">
                  <Pin size={11} className="shrink-0 fill-amber-500 text-amber-600 rotate-45" />
                  Pinned
                </span>
              )}
            </div>

            <div className="flex items-center gap-1.5 text-xs text-slate-500 mt-0.5">
              <Calendar size={12} className="text-slate-400 shrink-0" />
              <time dateTime={announcement.createdAt} title={`UTC: ${announcement.createdAt}`}>
                {formatUtcTimestamp(announcement.createdAt)}
              </time>
              {isEdited && (
                <span className="text-slate-400 italic text-[11px]" title={`Edited at ${announcement.updatedAt}`}>
                  (edited)
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Teacher Actions Toolbar (FR-ANN-001) */}
        {isTeacherOrAdmin && (
          <div className="flex items-center gap-1 shrink-0">
            {onTogglePin && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onTogglePin(announcement.id)}
                title={announcement.isPinned ? 'Unpin this announcement' : 'Pin this announcement to top'}
                className={`h-8 px-2.5 text-xs font-semibold rounded-lg transition-colors ${
                  announcement.isPinned
                    ? 'text-amber-800 bg-amber-50 hover:bg-amber-100/80 border border-amber-200/60'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                }`}
                aria-label={announcement.isPinned ? 'Unpin announcement' : 'Pin announcement'}
              >
                <Pin
                  size={14}
                  className={`mr-1 shrink-0 ${announcement.isPinned ? 'fill-amber-500 text-amber-600 rotate-45' : ''}`}
                />
                <span>{announcement.isPinned ? 'Unpin' : 'Pin'}</span>
              </Button>
            )}

            {onEdit && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onEdit(announcement)}
                title="Edit announcement"
                className="h-8 px-2 text-xs font-semibold text-slate-600 hover:text-slate-900 hover:bg-slate-100 rounded-lg"
                aria-label="Edit announcement"
              >
                <Pencil size={13} className="mr-1 shrink-0" />
                <span>Edit</span>
              </Button>
            )}

            {onDelete && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onDelete(announcement)}
                title="Delete announcement"
                className="h-8 px-2 text-xs font-semibold text-rose-600 hover:text-rose-700 hover:bg-rose-50 rounded-lg"
                aria-label="Delete announcement"
              >
                <Trash2 size={13} className="mr-1 shrink-0" />
                <span>Delete</span>
              </Button>
            )}
          </div>
        )}
      </div>

      {/* Announcement Title */}
      <h3 className="text-base sm:text-lg font-bold text-slate-900 mb-3 tracking-tight">
        {announcement.title}
      </h3>

      {/* Safe Text Content (FR-ANN-002: XSS-safe rendering) */}
      <div className="announcement-content space-y-1 text-slate-700">
        {renderSafeContent(announcement.content)}
      </div>
    </article>
  );
};
