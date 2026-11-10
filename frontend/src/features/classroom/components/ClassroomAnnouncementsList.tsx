import React, { useState, useMemo } from 'react';
import { ClassroomAnnouncement } from '../types';
import { AnnouncementCard } from './AnnouncementCard';
import { AnnouncementFormModal } from './AnnouncementFormModal';
import { ConfirmationModal } from './ConfirmationModal';
import { Bell, Plus, Search, Pin, CheckCircle2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface ClassroomAnnouncementsListProps {
  classroomId: string;
  isTeacherOrAdmin: boolean;
  announcements: ClassroomAnnouncement[];
  highlightedId?: string | null;
  onCreate?: (data: { title: string; content: string; isPinned: boolean }) => void;
  onUpdate?: (id: string, data: { title: string; content: string; isPinned: boolean }) => void;
  onDelete?: (id: string) => void;
  onTogglePin?: (id: string) => void;
}

export const ClassroomAnnouncementsList: React.FC<ClassroomAnnouncementsListProps> = ({
  classroomId,
  isTeacherOrAdmin,
  announcements,
  highlightedId,
  onCreate,
  onUpdate,
  onDelete,
  onTogglePin,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [filterMode, setFilterMode] = useState<'ALL' | 'PINNED'>('ALL');

  // Modals state
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [editingAnnouncement, setEditingAnnouncement] = useState<ClassroomAnnouncement | null>(null);
  const [deletingAnnouncement, setDeletingAnnouncement] = useState<ClassroomAnnouncement | null>(null);

  // Success toast state
  const [notificationToast, setNotificationToast] = useState<string | null>(null);

  const showToast = (message: string) => {
    setNotificationToast(message);
    setTimeout(() => {
      setNotificationToast(null);
    }, 4000);
  };

  const handleCreateSubmit = (data: { title: string; content: string; isPinned: boolean }) => {
    if (onCreate) {
      onCreate(data);
      showToast('Announcement published! In-app notifications dispatched to all enrolled students.');
    }
  };

  const handleEditSubmit = (data: { title: string; content: string; isPinned: boolean }) => {
    if (onUpdate && editingAnnouncement) {
      onUpdate(editingAnnouncement.id, data);
      showToast('Announcement updated successfully.');
    }
    setEditingAnnouncement(null);
  };

  const handleConfirmDelete = () => {
    if (onDelete && deletingAnnouncement) {
      onDelete(deletingAnnouncement.id);
      showToast('Announcement removed.');
    }
    setDeletingAnnouncement(null);
  };

  // Filtered & searched announcements
  const filteredAnnouncements = useMemo(() => {
    return announcements.filter(item => {
      const matchesSearch =
        searchQuery === '' ||
        item.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        item.content.toLowerCase().includes(searchQuery.toLowerCase());

      const matchesFilter = filterMode === 'ALL' || (filterMode === 'PINNED' && item.isPinned);

      return matchesSearch && matchesFilter;
    });
  }, [announcements, searchQuery, filterMode]);

  const pinnedCount = announcements.filter(a => a.isPinned).length;
  const unpinnedCount = announcements.length - pinnedCount;

  // Separate pinned and unpinned for clear layout structure
  const pinnedAnnouncements = useMemo(
    () => filteredAnnouncements.filter(a => a.isPinned),
    [filteredAnnouncements],
  );

  const regularAnnouncements = useMemo(
    () => filteredAnnouncements.filter(a => !a.isPinned),
    [filteredAnnouncements],
  );

  return (
    <div className="space-y-6">
      {/* Dynamic Feedback Toast */}
      {notificationToast && (
        <div
          role="status"
          aria-live="polite"
          className="p-3.5 bg-slate-900 text-white rounded-xl text-xs font-semibold flex items-center justify-between shadow-lg transition-all animate-in fade-in slide-in-from-top-2"
        >
          <div className="flex items-center gap-2.5">
            <CheckCircle2 size={16} className="text-emerald-400 shrink-0" />
            <span>{notificationToast}</span>
          </div>
          <button
            type="button"
            onClick={() => setNotificationToast(null)}
            className="text-slate-400 hover:text-white text-xs px-2 py-0.5"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Control Bar: Search, Filters & Primary Action */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        <div className="flex items-center gap-2 flex-1 max-w-lg">
          <div className="relative flex-1">
            <Search size={14} className="classroom-search-icon" aria-hidden="true" />
            <input
              type="text"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              placeholder="Search announcements by title or keyword…"
              className="classroom-search-input pl-8"
              aria-label="Search announcements"
            />
          </div>

          <div className="flex items-center gap-1 bg-white border border-slate-200/80 p-0.5 rounded-xl shadow-xs">
            <button
              type="button"
              onClick={() => setFilterMode('ALL')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                filterMode === 'ALL'
                  ? 'bg-slate-900 text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              All ({announcements.length})
            </button>
            <button
              type="button"
              onClick={() => setFilterMode('PINNED')}
              className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                filterMode === 'PINNED'
                  ? 'bg-amber-500 text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <Pin size={11} className="rotate-45" />
              Pinned ({pinnedCount})
            </button>
          </div>
        </div>

        {/* Primary Action Button (Teacher/Admin only) */}
        {isTeacherOrAdmin && onCreate && (
          <Button
            size="sm"
            onClick={() => setIsCreateModalOpen(true)}
            className="bg-slate-900 text-white rounded-xl text-xs font-semibold hover:bg-slate-800 shadow-xs"
          >
            <Plus size={14} className="mr-1.5 shrink-0" />
            New Announcement
          </Button>
        )}
      </div>

      {/* Announcements Content Display */}
      {filteredAnnouncements.length === 0 ? (
        <div className="classroom-glass-card p-12 text-center">
          <div className="w-12 h-12 rounded-2xl bg-slate-100 border border-slate-200 flex items-center justify-center mx-auto mb-3.5 text-slate-500">
            <Bell size={22} />
          </div>
          <h3 className="text-base font-bold text-slate-900 mb-1">
            {searchQuery ? 'No matching announcements' : 'No announcements published yet'}
          </h3>
          <p className="text-xs text-slate-500 max-w-md mx-auto mb-5 leading-relaxed">
            {searchQuery
              ? `No announcements found matching "${searchQuery}". Try searching with different keywords or resetting filters.`
              : isTeacherOrAdmin
              ? 'Publish course notices, deadline reminders, or exam logistics to keep all enrolled students informed.'
              : 'Your instructor has not published any announcements yet. Check back later for course updates.'}
          </p>

          {searchQuery ? (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setSearchQuery('');
                setFilterMode('ALL');
              }}
              className="rounded-xl text-xs font-semibold"
            >
              Clear Search Filter
            </Button>
          ) : isTeacherOrAdmin && onCreate ? (
            <Button
              size="sm"
              onClick={() => setIsCreateModalOpen(true)}
              className="bg-slate-900 text-white rounded-xl text-xs font-semibold"
            >
              <Plus size={14} className="mr-1.5 shrink-0" />
              Publish First Announcement
            </Button>
          ) : null}
        </div>
      ) : (
        <div className="space-y-6">
          {/* FR-ANN-004 & BR-ANN-003: Pinned Section (if any match filter) */}
          {filterMode !== 'PINNED' && pinnedAnnouncements.length > 0 && (
            <div className="space-y-3">
              <div className="flex items-center gap-2 px-1">
                <span className="inline-flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-amber-800 bg-amber-50 px-2.5 py-1 rounded-full border border-amber-200/80">
                  <Pin size={12} className="rotate-45 fill-amber-500 text-amber-600" />
                  Pinned Announcements ({pinnedAnnouncements.length})
                </span>
                <div className="h-px bg-amber-200/50 flex-1" />
              </div>

              <div className="space-y-4">
                {pinnedAnnouncements.map(announcement => (
                  <AnnouncementCard
                    key={announcement.id}
                    announcement={announcement}
                    isTeacherOrAdmin={isTeacherOrAdmin}
                    isHighlighted={highlightedId === announcement.id}
                    onEdit={setEditingAnnouncement}
                    onDelete={setDeletingAnnouncement}
                    onTogglePin={onTogglePin}
                  />
                ))}
              </div>
            </div>
          )}

          {/* Regular Announcements Section */}
          {(filterMode === 'ALL' || filterMode === 'PINNED') && (
            <div className="space-y-3">
              {filterMode === 'ALL' && pinnedAnnouncements.length > 0 && regularAnnouncements.length > 0 && (
                <div className="flex items-center gap-2 px-1 pt-2">
                  <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
                    Recent Announcements ({regularAnnouncements.length})
                  </span>
                  <div className="h-px bg-slate-200 flex-1" />
                </div>
              )}

              <div className="space-y-4">
                {(filterMode === 'PINNED' ? pinnedAnnouncements : regularAnnouncements).map(announcement => (
                  <AnnouncementCard
                    key={announcement.id}
                    announcement={announcement}
                    isTeacherOrAdmin={isTeacherOrAdmin}
                    isHighlighted={highlightedId === announcement.id}
                    onEdit={setEditingAnnouncement}
                    onDelete={setDeletingAnnouncement}
                    onTogglePin={onTogglePin}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Announcement Create / Edit Modal */}
      <AnnouncementFormModal
        isOpen={isCreateModalOpen || Boolean(editingAnnouncement)}
        initialData={editingAnnouncement}
        onClose={() => {
          setIsCreateModalOpen(false);
          setEditingAnnouncement(null);
        }}
        onSubmit={editingAnnouncement ? handleEditSubmit : handleCreateSubmit}
      />

      {/* Delete Confirmation Modal */}
      <ConfirmationModal
        isOpen={Boolean(deletingAnnouncement)}
        title="Delete Announcement"
        message={`Are you sure you want to delete "${deletingAnnouncement?.title}"? This action cannot be undone.`}
        confirmLabel="Delete Announcement"
        variant="danger"
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeletingAnnouncement(null)}
      />
    </div>
  );
};
