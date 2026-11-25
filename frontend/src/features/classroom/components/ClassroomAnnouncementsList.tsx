import React, { useState, useMemo, useEffect, useRef } from 'react';
import type { PaginatedData } from '@/types/api';
import { LoadingScreen } from '@/components/ui/LoadingScreen';
import { Button } from '@/components/ui/Button';
import { announcementError } from '../api/announcementApi';
import {
  ClassroomAnnouncement,
  CreateAnnouncementInput,
  UpdateAnnouncementInput,
} from '../types';
import { AnnouncementCard } from './AnnouncementCard';
import { AnnouncementFormModal } from './AnnouncementFormModal';
import { ConfirmationModal } from './ConfirmationModal';
import {
  Bell,
  Plus,
  Search,
  Pin,
  CheckCircle2,
  AlertCircle,
  RefreshCw,
} from 'lucide-react';

interface ClassroomAnnouncementsListProps {
  classroomId: string;
  isTeacherOrAdmin: boolean;
  announcements: ClassroomAnnouncement[];
  highlightedId?: string | null;
  highlightedAnnouncement?: ClassroomAnnouncement | null;
  isLoading?: boolean;
  isFetching?: boolean;
  error?: string | null;
  onRetry?: () => void;
  pagination?: PaginatedData<ClassroomAnnouncement>['pagination'];
  onPageChange?: (page: number) => void;
  isCreating?: boolean;
  isUpdating?: boolean;
  isDeleting?: boolean;
  pinningId?: string | null;
  readOnlyNotice?: string | null;
  showPinInForm?: boolean;
  onCreate?: (data: CreateAnnouncementInput & { isPinned?: boolean }) => Promise<unknown> | ClassroomAnnouncement | void;
  onUpdate?: (id: string, data: UpdateAnnouncementInput & { isPinned?: boolean }) => Promise<unknown> | ClassroomAnnouncement | void;
  onDelete?: (id: string) => Promise<unknown> | void;
  onTogglePin?: (id: string) => Promise<unknown> | void;
}

export const ClassroomAnnouncementsList: React.FC<ClassroomAnnouncementsListProps> = ({
  classroomId,
  isTeacherOrAdmin,
  announcements,
  highlightedId,
  highlightedAnnouncement = null,
  isLoading = false,
  isFetching = false,
  error = null,
  onRetry,
  pagination,
  onPageChange,
  isCreating = false,
  isUpdating = false,
  isDeleting = false,
  pinningId = null,
  readOnlyNotice = null,
  showPinInForm = false,
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

  // Mutation error & toast state
  const [formError, setFormError] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notificationToast, setNotificationToast] = useState<string | null>(null);
  const toastTimerRef = useRef<number | null>(null);

  const showToast = (message: string) => {
    if (toastTimerRef.current !== null) {
      window.clearTimeout(toastTimerRef.current);
    }
    setNotificationToast(message);
    toastTimerRef.current = window.setTimeout(() => {
      setNotificationToast(null);
      toastTimerRef.current = null;
    }, 4000);
  };

  useEffect(() => {
    return () => {
      if (toastTimerRef.current !== null) {
        window.clearTimeout(toastTimerRef.current);
      }
    };
  }, []);

  // Reset UI state when switching classrooms
  useEffect(() => {
    setSearchQuery('');
    setFilterMode('ALL');
    setIsCreateModalOpen(false);
    setEditingAnnouncement(null);
    setDeletingAnnouncement(null);
    setFormError(null);
    setDeleteError(null);
    setActionError(null);
    setNotificationToast(null);
  }, [classroomId]);

  const handleCreateSubmit = async (data: CreateAnnouncementInput & { isPinned?: boolean }) => {
    if (!onCreate || isCreating) return;
    setFormError(null);
    setActionError(null);
    try {
      await onCreate(data);
      setIsCreateModalOpen(false);
      showToast('Announcement published! In-app notifications dispatched to all enrolled students.');
    } catch (err) {
      setFormError(announcementError(err));
      throw err;
    }
  };

  const handleEditSubmit = async (data: UpdateAnnouncementInput & { isPinned?: boolean }) => {
    if (!onUpdate || !editingAnnouncement || isUpdating) return;
    setFormError(null);
    setActionError(null);
    try {
      await onUpdate(editingAnnouncement.id, data);
      setEditingAnnouncement(null);
      showToast('Announcement updated successfully.');
    } catch (err) {
      setFormError(announcementError(err));
      throw err;
    }
  };

  const handleConfirmDelete = async () => {
    if (!onDelete || !deletingAnnouncement || isDeleting) return;
    setDeleteError(null);
    setActionError(null);
    try {
      await onDelete(deletingAnnouncement.id);
      setDeletingAnnouncement(null);
      showToast('Announcement removed.');
    } catch (err) {
      setDeleteError(announcementError(err));
    }
  };

  const handleTogglePin = async (announcementId: string) => {
    if (!onTogglePin || pinningId) return;
    setActionError(null);
    const target =
      announcements.find(item => item.id === announcementId) ||
      (highlightedAnnouncement?.id === announcementId ? highlightedAnnouncement : undefined);
    try {
      await onTogglePin(announcementId);
      if (target) {
        showToast(target.isPinned ? 'Announcement unpinned.' : 'Announcement pinned to top.');
      }
    } catch (err) {
      setActionError(announcementError(err));
    }
  };

  const isPaginated = Boolean(pagination && pagination.totalPages > 1);

  // Filtered & searched announcements (within current loaded page)
  const filteredAnnouncements = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase();
    return announcements.filter(item => {
      const matchesSearch =
        normalizedQuery === '' ||
        item.title.toLowerCase().includes(normalizedQuery) ||
        item.content.toLowerCase().includes(normalizedQuery);

      const matchesFilter = filterMode === 'ALL' || (filterMode === 'PINNED' && item.isPinned);

      return matchesSearch && matchesFilter;
    });
  }, [announcements, searchQuery, filterMode]);

  const pinnedCount = useMemo(
    () => announcements.filter(a => a.isPinned).length,
    [announcements],
  );

  // Separate pinned and unpinned for clear layout structure
  const pinnedAnnouncements = useMemo(
    () => filteredAnnouncements.filter(a => a.isPinned),
    [filteredAnnouncements],
  );

  const regularAnnouncements = useMemo(
    () => filteredAnnouncements.filter(a => !a.isPinned),
    [filteredAnnouncements],
  );

  const hasLinkedFallback = Boolean(
    highlightedAnnouncement &&
      !announcements.some(item => item.id === highlightedAnnouncement.id),
  );

  const isAnyMutationBusy = isCreating || isUpdating || isDeleting || Boolean(pinningId);

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
            <CheckCircle2 size={16} className="text-emerald-400 shrink-0" aria-hidden="true" />
            <span>{notificationToast}</span>
          </div>
          <button
            type="button"
            onClick={() => setNotificationToast(null)}
            className="text-slate-300 hover:text-white text-xs px-2 py-0.5"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Mutation Action Error */}
      {actionError && (
        <div
          role="alert"
          className="p-3.5 bg-rose-50 border border-rose-200 text-rose-800 rounded-xl text-xs font-semibold flex items-center justify-between gap-3"
        >
          <div className="flex items-center gap-2">
            <AlertCircle size={16} className="text-rose-600 shrink-0" aria-hidden="true" />
            <span>{actionError}</span>
          </div>
          <button
            type="button"
            onClick={() => setActionError(null)}
            className="text-rose-700 hover:text-rose-900 underline text-xs"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Read-only classroom notice (e.g., ARCHIVED classroom) */}
      {readOnlyNotice && (
        <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs font-medium">
          {readOnlyNotice}
        </div>
      )}

      {/* Control Bar: Search, Filters & Primary Action */}
      <div className="space-y-2">
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2 flex-1 max-w-xl">
            <div className="relative flex-1">
              <Search size={14} className="classroom-search-icon" aria-hidden="true" />
              <input
                type="text"
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                placeholder={
                  isPaginated
                    ? `Filter announcements on page ${(pagination?.page ?? 0) + 1}…`
                    : 'Search announcements by title or keyword…'
                }
                className="classroom-search-input pl-8"
                aria-label="Search announcements"
              />
            </div>

            <div className="flex items-center gap-1 bg-white border border-slate-200/80 p-0.5 rounded-xl shadow-xs">
              <button
                type="button"
                aria-pressed={filterMode === 'ALL'}
                onClick={() => setFilterMode('ALL')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                  filterMode === 'ALL'
                    ? 'bg-slate-900 text-white shadow-xs'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                {isPaginated ? `All on page (${announcements.length})` : `All (${announcements.length})`}
              </button>
              <button
                type="button"
                aria-pressed={filterMode === 'PINNED'}
                onClick={() => setFilterMode('PINNED')}
                className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                  filterMode === 'PINNED'
                    ? 'bg-amber-500 text-white shadow-xs'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Pin size={11} className="rotate-45" aria-hidden="true" />
                {isPaginated ? `Pinned on page (${pinnedCount})` : `Pinned (${pinnedCount})`}
              </button>
            </div>
          </div>

          {/* Primary Action Button (Teacher owner on active classroom) */}
          {isTeacherOrAdmin && onCreate && (
            <Button
              size="sm"
              disabled={isAnyMutationBusy}
              onClick={() => {
                setFormError(null);
                setIsCreateModalOpen(true);
              }}
              className="bg-slate-900 text-white rounded-xl text-xs font-semibold hover:bg-slate-800 shadow-xs"
            >
              <Plus size={14} className="mr-1.5 shrink-0" aria-hidden="true" />
              New Announcement
            </Button>
          )}
        </div>

        {isPaginated && pagination && (
          <p className="text-[11px] text-slate-500 px-1">
            Showing page {pagination.page + 1} of {pagination.totalPages} ({pagination.totalElements} total announcements). Search and filter apply to the current page only.
          </p>
        )}
      </div>

      {/* Deep-linked announcement outside current page */}
      {hasLinkedFallback && highlightedAnnouncement && (
        <div className="space-y-2">
          <p className="text-xs font-bold uppercase tracking-wider text-blue-700 px-1">
            Linked Announcement
          </p>
          <AnnouncementCard
            announcement={highlightedAnnouncement}
            isTeacherOrAdmin={isTeacherOrAdmin}
            isHighlighted={true}
            isPinPending={pinningId === highlightedAnnouncement.id}
            isBusy={isAnyMutationBusy}
            onEdit={ann => {
              setFormError(null);
              setEditingAnnouncement(ann);
            }}
            onDelete={ann => {
              setDeleteError(null);
              setDeletingAnnouncement(ann);
            }}
            onTogglePin={onTogglePin ? handleTogglePin : undefined}
          />
        </div>
      )}

      {/* Loading State */}
      {isLoading ? (
        <div className="classroom-glass-card p-10">
          <LoadingScreen variant="inline" message="Loading classroom announcements…" />
        </div>
      ) : error ? (
        /* Error State with Retry */
        <div role="alert" className="classroom-glass-card p-8 text-center space-y-3 border-rose-200 bg-rose-50/40">
          <div className="w-11 h-11 rounded-2xl bg-rose-100 text-rose-700 flex items-center justify-center mx-auto">
            <AlertCircle size={20} aria-hidden="true" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-slate-900">Unable to load announcements</h3>
            <p className="text-xs text-rose-700 mt-1 max-w-md mx-auto">{error}</p>
          </div>
          {onRetry && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onRetry}
              className="rounded-xl text-xs font-semibold text-slate-800 border-slate-300 bg-white hover:bg-slate-50"
            >
              <RefreshCw size={13} className="mr-1.5 shrink-0" aria-hidden="true" />
              Retry
            </Button>
          )}
        </div>
      ) : filteredAnnouncements.length === 0 ? (
        /* Empty State */
        <div className="classroom-glass-card p-12 text-center">
          <div className="w-12 h-12 rounded-2xl bg-slate-100 border border-slate-200 flex items-center justify-center mx-auto mb-3.5 text-slate-500">
            <Bell size={22} aria-hidden="true" />
          </div>
          <h3 className="text-base font-bold text-slate-900 mb-1">
            {searchQuery || filterMode === 'PINNED'
              ? 'No matching announcements'
              : 'No announcements published yet'}
          </h3>
          <p className="text-xs text-slate-500 max-w-md mx-auto mb-5 leading-relaxed">
            {searchQuery || filterMode === 'PINNED'
              ? isPaginated && pagination
                ? `No announcements on page ${pagination.page + 1} of ${pagination.totalPages} match your current search or filter.`
                : searchQuery
                  ? `No announcements found matching "${searchQuery}". Try searching with different keywords or resetting filters.`
                  : 'There are no pinned announcements on this list.'
              : isTeacherOrAdmin
                ? 'Publish course notices, deadline reminders, or exam logistics to keep all enrolled students informed.'
                : 'Your instructor has not published any announcements yet. Check back later for course updates.'}
          </p>

          {searchQuery || filterMode === 'PINNED' ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                setSearchQuery('');
                setFilterMode('ALL');
              }}
              className="rounded-xl text-xs font-semibold text-slate-800 border-slate-300 hover:bg-slate-50"
            >
              Clear Search Filter
            </Button>
          ) : isTeacherOrAdmin && onCreate ? (
            <Button
              type="button"
              size="sm"
              disabled={isAnyMutationBusy}
              onClick={() => {
                setFormError(null);
                setIsCreateModalOpen(true);
              }}
              className="bg-slate-900 text-white rounded-xl text-xs font-semibold"
            >
              <Plus size={14} className="mr-1.5 shrink-0" aria-hidden="true" />
              Publish First Announcement
            </Button>
          ) : null}
        </div>
      ) : (
        <div className="space-y-6">
          {/* FR-ANN-004 & BR-ANN-003: Pinned Section */}
          {filterMode !== 'PINNED' && pinnedAnnouncements.length > 0 && (
            <div className="space-y-3">
              <div className="flex items-center gap-2 px-1">
                <span className="inline-flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-amber-800 bg-amber-50 px-2.5 py-1 rounded-full border border-amber-200/80">
                  <Pin size={12} className="rotate-45 fill-amber-500 text-amber-600" aria-hidden="true" />
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
                    isPinPending={pinningId === announcement.id}
                    isBusy={isAnyMutationBusy}
                    onEdit={ann => {
                      setFormError(null);
                      setEditingAnnouncement(ann);
                    }}
                    onDelete={ann => {
                      setDeleteError(null);
                      setDeletingAnnouncement(ann);
                    }}
                    onTogglePin={onTogglePin ? handleTogglePin : undefined}
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
                    isPinPending={pinningId === announcement.id}
                    isBusy={isAnyMutationBusy}
                    onEdit={ann => {
                      setFormError(null);
                      setEditingAnnouncement(ann);
                    }}
                    onDelete={ann => {
                      setDeleteError(null);
                      setDeletingAnnouncement(ann);
                    }}
                    onTogglePin={onTogglePin ? handleTogglePin : undefined}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Server-side Pagination Controls */}
      {pagination && pagination.totalPages > 1 && onPageChange && (
        <div className="flex items-center justify-between border-t border-slate-200 pt-4 text-xs text-slate-600">
          <span className="font-mono">
            Page {pagination.page + 1} of {pagination.totalPages}
          </span>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={pagination.isFirst || pagination.page === 0 || isFetching}
              onClick={() => onPageChange(pagination.page - 1)}
              className="rounded-lg text-xs font-semibold text-slate-800 border-slate-300 bg-white hover:bg-slate-50"
            >
              Previous
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={pagination.isLast || pagination.page + 1 >= pagination.totalPages || isFetching}
              onClick={() => onPageChange(pagination.page + 1)}
              className="rounded-lg text-xs font-semibold text-slate-800 border-slate-300 bg-white hover:bg-slate-50"
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Announcement Create / Edit Modal */}
      <AnnouncementFormModal
        isOpen={isCreateModalOpen || Boolean(editingAnnouncement)}
        initialData={editingAnnouncement}
        isSubmitting={isCreating || isUpdating}
        serverError={formError}
        showPinToggle={showPinInForm}
        onClose={() => {
          if (isCreating || isUpdating) return;
          setFormError(null);
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
        isConfirming={isDeleting}
        errorMessage={deleteError}
        onConfirm={handleConfirmDelete}
        onCancel={() => {
          if (isDeleting) return;
          setDeleteError(null);
          setDeletingAnnouncement(null);
        }}
      />
    </div>
  );
};
