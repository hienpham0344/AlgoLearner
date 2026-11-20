import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, BookOpen, Check, CheckCircle2, Clock, Copy, Lock, Plus, Search } from 'lucide-react';
import type { PaginatedData } from '@/types/api';
import { LoadingScreen } from '@/components/ui/LoadingScreen';
import { Button } from '@/components/ui/Button';
import { classroomError, type ClassroomRecord, type InvitationNotificationRecord } from '../api/classroomApi';
import type { DemoRole, JoinMode } from '../types';
import { BackendCreateClassroomModal } from './BackendCreateClassroomModal';
import { ClassroomPageHeader } from './ClassroomPageHeader';
import { ClassroomStatsGrid, type StatCardItem } from './ClassroomStatsGrid';

type StatusFilter = 'ALL' | 'ACTIVE' | 'ARCHIVED';

interface BackendClassroomListViewProps {
  role: DemoRole;
  base: string;
  pending: boolean;
  error: string;
  notice: string;
  listData?: PaginatedData<ClassroomRecord>;
  listPending: boolean;
  listIsError: boolean;
  listError: unknown;
  onRetryList: () => void;
  pageNumber: number;
  onPageChange: (page: number) => void;
  notificationsData?: PaginatedData<InvitationNotificationRecord>;
  notificationsPending: boolean;
  notificationsIsError: boolean;
  notificationsError: unknown;
  notificationPage: number;
  onNotificationPageChange: (page: number) => void;
  onCreateClassroom: (
    input: { name: string; description: string; joinMode: JoinMode; ownerId?: string },
    onCreated: (created: ClassroomRecord) => void,
  ) => void;
  onJoinByCode: (classCode: string, onJoined?: () => void) => void;
  onMarkNotificationRead: (notificationId: string) => void;
  onAcceptInvitation: (invitationId: string, classroomId: string | null) => void;
  onDeclineInvitation: (invitationId: string) => void;
  onClearFeedback: () => void;
}

function formatVietnamDate(value?: string | null): string {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return '—';
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(parsed);
}

function MinimalPageControls({
  page,
  totalPages,
  onPage,
}: {
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-4 flex items-center justify-between border-t border-[#EAEAEA] pt-4 text-xs text-[#787774]">
      <span className="font-mono text-[11px]">
        Page {page + 1} of {totalPages}
      </span>
      <div className="flex items-center gap-2">
        <button
          type="button"
          disabled={page === 0}
          onClick={() => onPage(page - 1)}
          className="rounded-md border border-[#EAEAEA] bg-white px-3 py-1.5 text-xs font-medium text-[#2F3437] transition-colors hover:bg-[#F7F6F3] disabled:opacity-40"
        >
          Previous
        </button>
        <button
          type="button"
          disabled={page + 1 >= totalPages}
          onClick={() => onPage(page + 1)}
          className="rounded-md border border-[#EAEAEA] bg-white px-3 py-1.5 text-xs font-medium text-[#2F3437] transition-colors hover:bg-[#F7F6F3] disabled:opacity-40"
        >
          Next
        </button>
      </div>
    </div>
  );
}

/**
 * Utilitarian Minimalist (Linear-style) classroom list workspace connected to real backend APIs.
 */
export function BackendClassroomListView({
  role,
  base,
  pending,
  error,
  notice,
  listData,
  listPending,
  listIsError,
  listError,
  onRetryList,
  pageNumber,
  onPageChange,
  notificationsData,
  notificationsPending,
  notificationsIsError,
  notificationsError,
  notificationPage,
  onNotificationPageChange,
  onCreateClassroom,
  onJoinByCode,
  onMarkNotificationRead,
  onAcceptInvitation,
  onDeclineInvitation,
  onClearFeedback,
}: BackendClassroomListViewProps) {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isJoinOpen, setIsJoinOpen] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [classCode, setClassCode] = useState('');
  const searchInputRef = useRef<HTMLInputElement>(null);
  const joinInputRef = useRef<HTMLInputElement>(null);

  const items = listData?.items ?? [];
  const totalElements = listData?.pagination.totalElements ?? items.length;
  const activeCount = useMemo(
    () => items.filter(c => c.status === 'ACTIVE').length,
    [items],
  );
  const archivedCount = useMemo(
    () => items.filter(c => c.status === 'ARCHIVED').length,
    [items],
  );

  const filteredItems = useMemo(() => {
    const q = search.trim().toLowerCase();
    return items.filter(classroom => {
      if (statusFilter !== 'ALL' && classroom.status !== statusFilter) {
        return false;
      }
      if (!q) return true;
      return (
        classroom.name.toLowerCase().includes(q) ||
        (classroom.description || '').toLowerCase().includes(q) ||
        (classroom.classCode || '').toLowerCase().includes(q)
      );
    });
  }, [items, search, statusFilter]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (isCreateOpen) return;
      const target = event.target as HTMLElement | null;
      const isEditable =
        target instanceof HTMLInputElement ||
        target instanceof HTMLTextAreaElement ||
        target instanceof HTMLSelectElement ||
        Boolean(target?.isContentEditable);

      if (event.key === 'Escape' && search) {
        setSearch('');
        return;
      }
      if (isEditable || event.metaKey || event.ctrlKey || event.altKey) return;

      if (event.key === '/') {
        event.preventDefault();
        searchInputRef.current?.focus();
      } else if ((event.key === 'n' || event.key === 'N') && role !== 'STUDENT') {
        event.preventDefault();
        onClearFeedback();
        setIsCreateOpen(true);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isCreateOpen, onClearFeedback, role, search]);

  const copyClassCode = (classroomId: string, code: string) => {
    navigator.clipboard?.writeText(code);
    setCopiedId(classroomId);
    window.setTimeout(() => {
      setCopiedId(current => (current === classroomId ? null : current));
    }, 1800);
  };

  const handleJoinSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = classCode.trim();
    if (!trimmed) return;
    onJoinByCode(trimmed, () => {
      setClassCode('');
      setIsJoinOpen(false);
    });
  };

  return (
    <div className={role === 'STUDENT' ? 'classroom-workspace-shell' : 'mx-auto max-w-5xl px-4 py-6 sm:px-8 sm:py-8'}>
      {role === 'STUDENT' ? (
        <>
          <ClassroomPageHeader
            eyebrow="STUDENT WORKSPACE"
            title="My Classrooms & Learning Spaces"
            description="Access your enrolled courses, view lessons, and track your learning progress."
            primaryAction={
              <Button
                size="sm"
                onClick={() => {
                  onClearFeedback();
                  setIsJoinOpen(true);
                }}
                className="h-10 px-4 bg-slate-900 text-white hover:bg-slate-800 rounded-xl font-semibold text-xs shadow-xs"
              >
                <Plus size={15} className="mr-1.5 shrink-0" aria-hidden="true" />
                Join Class
              </Button>
            }
          />
          <ClassroomStatsGrid
            stats={[
              {
                id: 'enrolled-classes',
                label: 'Enrolled Workspaces',
                value: totalElements,
                helperText: 'Active course cohorts & problem sets',
                icon: <BookOpen size={16} />,
                badge: { text: 'Enrolled', variant: 'active' },
              },
              {
                id: 'active-classes',
                label: 'Active Classes',
                value: activeCount,
                helperText: 'Currently open learning spaces',
                icon: <CheckCircle2 size={16} />,
              },
              {
                id: 'archived-classes',
                label: 'Archived',
                value: archivedCount,
                helperText: 'Completed or archived cohorts',
                icon: <Clock size={16} />,
              },
            ] satisfies StatCardItem[]}
          />
        </>
      ) : (
        <header className="flex flex-col justify-between gap-4 border-b border-[#EAEAEA] pb-6 sm:flex-row sm:items-end">
          <div>
            <p className="font-mono text-[10px] font-medium uppercase tracking-widest text-[#787774]">
              {role} · Classroom workspace
            </p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight text-[#111111]">
              Classrooms
            </h1>
            <p className="mt-1 text-xs text-[#787774]">
              {listData ? (
                <>
                  <span className="font-medium text-[#111111]">{totalElements}</span>{' '}
                  {role === 'TEACHER'
                    ? totalElements === 1
                      ? 'classroom owned'
                      : 'classrooms owned'
                    : totalElements === 1
                      ? 'classroom'
                      : 'classrooms'}
                  <span className="mx-2 text-[#D1D1CF]">·</span>
                  <span>{activeCount} active</span>
                  <span className="mx-2 text-[#D1D1CF]">·</span>
                  <span>{archivedCount} archived</span>
                </>
              ) : (
                'Manage teaching spaces, rosters, and class access codes.'
              )}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="relative">
              <input
                ref={searchInputRef}
                type="text"
                aria-label="Search classrooms"
                placeholder="Search classrooms..."
                value={search}
                onChange={event => setSearch(event.target.value)}
                className="w-56 rounded-md border border-[#EAEAEA] bg-white px-2.5 py-1.5 pr-8 text-xs text-[#111111] placeholder:text-[#A5A4A0] transition-colors focus:border-[#111111] focus:outline-none"
              />
              {!search ? (
                <kbd className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 rounded border border-[#EAEAEA] bg-[#F7F6F3] px-1 font-mono text-[10px] text-[#A5A4A0]">
                  /
                </kbd>
              ) : (
                <button
                  type="button"
                  aria-label="Clear search"
                  onClick={() => setSearch('')}
                  className="absolute right-2 top-1/2 -translate-y-1/2 font-mono text-[11px] text-[#787774] hover:text-[#111111]"
                >
                  ×
                </button>
              )}
            </div>

            <button
              type="button"
              onClick={() => {
                onClearFeedback();
                setIsCreateOpen(true);
              }}
              className="inline-flex items-center gap-1.5 rounded-md bg-[#111111] px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-[#2F3437]"
            >
              <span>+ New classroom</span>
              <kbd className="hidden rounded bg-white/15 px-1 font-mono text-[10px] text-white/90 sm:inline-block">
                N
              </kbd>
            </button>
          </div>
        </header>
      )}

      {/* Alerts & Status Feedback */}
      {error && !isCreateOpen && (
        <p
          role="alert"
          className="mt-4 rounded-md border border-[#F5C2C4] bg-[#FDEBEC] px-3.5 py-2.5 text-xs text-[#9F2F2D]"
        >
          {error}
        </p>
      )}
      {notice && (
        <p
          role="status"
          className="mt-4 rounded-md border border-[#C6DEC3] bg-[#EDF3EC] px-3.5 py-2.5 text-xs text-[#346538]"
        >
          {notice}
        </p>
      )}

      {/* Loading & Error States */}
      {listPending && (
        <div className="mt-6">
          <LoadingScreen variant="page" message="Đang tải danh sách lớp học…" />
        </div>
      )}

      {listIsError && (
        <p
          role="alert"
          className="mt-6 rounded-md border border-[#F5C2C4] bg-[#FDEBEC] px-4 py-3 text-xs text-[#9F2F2D]"
        >
          {classroomError(listError)}{' '}
          <button
            type="button"
            className="ml-2 font-semibold underline"
            onClick={onRetryList}
          >
            Retry
          </button>
        </p>
      )}

      {/* Filter Bar & Classroom List */}
      {listData && (
        <section className="mt-5">
          {role === 'STUDENT' ? (
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
              <div className="classroom-tabs mb-0" role="tablist" aria-label="Classroom status filter">
                {(
                  [
                    { id: 'ALL', label: `Enrolled Classes (${items.length})` },
                    { id: 'ACTIVE', label: `Active (${activeCount})` },
                    { id: 'ARCHIVED', label: `Archived (${archivedCount})` },
                  ] as const
                ).map(tabItem => {
                  const isSelected = statusFilter === tabItem.id;
                  return (
                    <button
                      key={tabItem.id}
                      type="button"
                      role="tab"
                      aria-selected={isSelected}
                      data-active={isSelected}
                      onClick={() => setStatusFilter(tabItem.id)}
                      className="classroom-tab-btn"
                    >
                      {tabItem.label}
                    </button>
                  );
                })}
              </div>

              <div className="relative w-full sm:w-72">
                <Search size={14} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" aria-hidden="true" />
                <input
                  ref={searchInputRef}
                  type="text"
                  aria-label="Search classrooms"
                  placeholder="Search classrooms..."
                  value={search}
                  onChange={event => setSearch(event.target.value)}
                  className="w-full h-10 rounded-xl border border-slate-200 bg-white pl-9 pr-8 text-xs text-slate-900 placeholder:text-slate-400 focus:border-slate-900 focus:outline-none shadow-xs"
                />
                {search && (
                  <button
                    type="button"
                    aria-label="Clear search"
                    onClick={() => setSearch('')}
                    className="absolute right-3 top-1/2 -translate-y-1/2 font-mono text-xs text-slate-400 hover:text-slate-900"
                  >
                    ×
                  </button>
                )}
              </div>
            </div>
          ) : (
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#EAEAEA] text-xs">
              <div className="flex items-center gap-6" role="tablist" aria-label="Classroom status filter">
                {(
                  [
                    { id: 'ALL', label: `All Classes (${items.length})` },
                    { id: 'ACTIVE', label: `Active (${activeCount})` },
                    { id: 'ARCHIVED', label: `Archived (${archivedCount})` },
                  ] as const
                ).map(tabItem => {
                  const isSelected = statusFilter === tabItem.id;
                  return (
                    <button
                      key={tabItem.id}
                      type="button"
                      role="tab"
                      aria-selected={isSelected}
                      onClick={() => setStatusFilter(tabItem.id)}
                      className={`-mb-px border-b-2 pb-2.5 text-xs transition-colors ${
                        isSelected
                          ? 'border-[#111111] font-semibold text-[#111111]'
                          : 'border-transparent text-[#787774] hover:text-[#111111]'
                      }`}
                    >
                      {tabItem.label}
                    </button>
                  );
                })}
              </div>
              <span className="pb-2 font-mono text-[11px] text-[#A5A4A0]">
                Showing {filteredItems.length} of {items.length}
              </span>
            </div>
          )}

          {/* Empty State or Data Table */}
          {items.length === 0 ? (
            <div className="mt-5 rounded-md border border-dashed border-[#D1D1CF] bg-white px-6 py-12 text-center">
              <p className="text-sm font-medium text-[#111111]">No classrooms yet</p>
              <p className="mt-1 text-xs text-[#787774]">
                {role === 'STUDENT'
                  ? 'Use an instructor class code above or accept an invitation below.'
                  : 'Create your first classroom to manage lessons, assignments, and students.'}
              </p>
              {role !== 'STUDENT' && (
                <button
                  type="button"
                  onClick={() => {
                    onClearFeedback();
                    setIsCreateOpen(true);
                  }}
                  className="mt-4 rounded-md bg-[#111111] px-4 py-1.5 text-xs font-medium text-white hover:bg-[#2F3437]"
                >
                  Create first classroom
                </button>
              )}
            </div>
          ) : filteredItems.length === 0 ? (
            <div className="mt-5 rounded-md border border-[#EAEAEA] bg-white px-6 py-10 text-center">
              <p className="text-xs text-[#787774]">
                No classrooms match your current filter
                {search.trim() ? ` "${search.trim()}"` : ''}.
              </p>
              <button
                type="button"
                onClick={() => {
                  setSearch('');
                  setStatusFilter('ALL');
                }}
                className="mt-2 text-xs font-medium text-[#111111] underline underline-offset-2"
              >
                Reset filters
              </button>
            </div>
          ) : role === 'STUDENT' ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {filteredItems.map(classroom => {
                const destination = `${base}/${classroom.id}`;
                const isArchived = classroom.status === 'ARCHIVED';
                const isJoiningClosed = classroom.joiningOpen === false;
                const joinLabel = isJoiningClosed
                  ? 'Closed'
                  : classroom.joinMode === 'REQUIRE_APPROVAL'
                    ? 'Approval Required'
                    : 'Auto Join';

                return (
                  <article
                    key={classroom.id}
                    className={`classroom-glass-card p-6 flex flex-col justify-between transition-all ${
                      isArchived ? 'opacity-80' : ''
                    }`}
                  >
                    <div>
                      <div className="flex items-center justify-between gap-2 mb-3">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span
                            className={`px-2 py-0.5 rounded-md font-mono text-[10px] font-bold uppercase tracking-wider border ${
                              classroom.status === 'ACTIVE'
                                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                : 'bg-slate-100 text-slate-600 border-slate-200'
                            }`}
                          >
                            {classroom.status}
                          </span>
                          <span className="px-2 py-0.5 rounded-md font-mono text-[10px] font-medium bg-slate-100 text-slate-600 border border-slate-200">
                            {joinLabel}
                          </span>
                          <span className="px-2 py-0.5 rounded-md font-mono text-[10px] font-bold bg-slate-900 text-white">
                            Enrolled
                          </span>
                        </div>
                      </div>

                      <Link to={destination} className="block group">
                        <h2 className="text-base font-extrabold text-slate-900 group-hover:underline decoration-slate-400 underline-offset-4">
                          {classroom.name}
                        </h2>
                      </Link>
                      <p className="mt-2 text-xs text-slate-600 line-clamp-2 leading-relaxed">
                        {classroom.description || 'No description provided yet.'}
                      </p>
                    </div>

                    <div className="mt-6 pt-4 border-t border-slate-200/80 flex items-center justify-between gap-3">
                      <span className="font-mono text-[11px] text-slate-500">
                        Updated {formatVietnamDate(classroom.updatedAt ?? classroom.createdAt)}
                      </span>
                      <Link
                        to={destination}
                        className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-3.5 py-2 text-xs font-semibold text-white hover:bg-slate-800 transition-colors"
                      >
                        <span>Open Classroom</span>
                        <ArrowRight size={13} aria-hidden="true" />
                      </Link>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <div className="mt-5 overflow-hidden rounded-md border border-[#EAEAEA] bg-white">
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left text-xs">
                  <thead className="border-b border-[#EAEAEA] bg-[#FBFBFA] font-mono text-[11px] uppercase tracking-wider text-[#787774]">
                    <tr>
                      <th className="px-4 py-2.5 font-medium">Classroom</th>
                      <th className="px-4 py-2.5 font-medium">Code</th>
                      <th className="px-4 py-2.5 font-medium">Join Policy</th>
                      <th className="px-4 py-2.5 font-medium">Status</th>
                      <th className="px-4 py-2.5 font-medium">Updated</th>
                      <th className="px-4 py-2.5 text-right font-medium">
                        <span className="sr-only">Actions</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#EAEAEA]">
                    {filteredItems.map(classroom => {
                      const destination = `${base}/${classroom.id}`;
                      const isArchived = classroom.status === 'ARCHIVED';
                      const isJoiningClosed = classroom.joiningOpen === false;
                      const isCopied = copiedId === classroom.id;

                      return (
                        <tr
                          key={classroom.id}
                          onClick={() => navigate(destination)}
                          className={`group cursor-pointer transition-colors hover:bg-[#FBFBFA] ${
                            isArchived ? 'opacity-70' : ''
                          }`}
                        >
                          <td className="max-w-md px-4 py-3.5">
                            <Link
                              to={destination}
                              onClick={event => event.stopPropagation()}
                              className="block"
                            >
                              <h2 className="text-[13px] font-semibold text-[#111111] decoration-[#D1D1CF] underline-offset-4 group-hover:underline">
                                {classroom.name}
                              </h2>
                            </Link>
                            <p className="mt-0.5 line-clamp-1 text-[11px] text-[#787774]">
                              {classroom.description || 'No description'}
                            </p>
                          </td>

                          <td className="whitespace-nowrap px-4 py-3.5 font-mono text-[11px]">
                              {classroom.classCode ? (
                                <button
                                  type="button"
                                  title="Copy class code"
                                  aria-label={`Copy class code ${classroom.classCode}`}
                                  onClick={event => {
                                    event.stopPropagation();
                                    copyClassCode(classroom.id, classroom.classCode!);
                                  }}
                                  className="inline-flex items-center gap-1.5 rounded border border-[#EAEAEA] bg-[#F7F6F3] px-2 py-0.5 text-[#2F3437] transition-colors hover:border-[#D1D1CF] hover:bg-[#F2F1EE]"
                                >
                                  {classroom.codeLocked && (
                                    <Lock size={11} className="text-[#787774]" aria-hidden="true" />
                                  )}
                                  <span>{classroom.classCode}</span>
                                  {isCopied ? (
                                    <Check size={11} className="text-[#346538]" aria-hidden="true" />
                                  ) : (
                                    <Copy size={11} className="text-[#A5A4A0]" aria-hidden="true" />
                                  )}
                                </button>
                              ) : (
                                <span className="text-[#A5A4A0]">—</span>
                              )}
                            </td>

                          <td className="whitespace-nowrap px-4 py-3.5">
                            {isJoiningClosed ? (
                              <span className="rounded bg-[#F2F1EE] px-2 py-0.5 font-mono text-[10px] uppercase tracking-wider text-[#787774]">
                                Closed
                              </span>
                            ) : classroom.joinMode === 'REQUIRE_APPROVAL' ? (
                              <span className="rounded bg-[#FBF3DB] px-2 py-0.5 font-mono text-[10px] uppercase tracking-wider text-[#956400]">
                                Approval Required
                              </span>
                            ) : (
                              <span className="rounded bg-[#F2F1EE] px-2 py-0.5 font-mono text-[10px] uppercase tracking-wider text-[#6A6965]">
                                Auto Join
                              </span>
                            )}
                          </td>

                          <td className="whitespace-nowrap px-4 py-3.5">
                            {classroom.status === 'ACTIVE' ? (
                              <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-[#346538]">
                                <span className="h-1.5 w-1.5 rounded-full bg-[#346538]" />
                                Active
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-[#787774]">
                                <span className="h-1.5 w-1.5 rounded-full bg-[#A5A4A0]" />
                                Archived
                              </span>
                            )}
                          </td>

                          <td className="whitespace-nowrap px-4 py-3.5 font-mono text-[11px] text-[#787774]">
                            {formatVietnamDate(classroom.updatedAt ?? classroom.createdAt)}
                          </td>

                          <td className="whitespace-nowrap px-4 py-3.5 text-right">
                            <div className="inline-flex items-center gap-3">
                              {classroom.status === 'ACTIVE' && (
                                <Link
                                  to={`${destination}?tab=settings`}
                                  onClick={event => event.stopPropagation()}
                                  className="text-[11px] font-medium text-[#787774] opacity-0 transition-opacity hover:text-[#111111] group-hover:opacity-100"
                                >
                                  Settings
                                </Link>
                              )}
                              <Link
                                to={destination}
                                onClick={event => event.stopPropagation()}
                                className="text-xs font-medium text-[#787774] transition-colors group-hover:text-[#111111]"
                              >
                                {isArchived ? 'View' : 'Open →'}
                              </Link>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          <MinimalPageControls
            page={pageNumber}
            totalPages={listData.pagination.totalPages}
            onPage={onPageChange}
          />
        </section>
      )}

      {/* Student Notifications / Invitations Section */}
      {role === 'STUDENT' && (
        <section id="invitations" className="mt-8 rounded-md border border-[#EAEAEA] bg-white p-5">
          <div className="flex items-center justify-between border-b border-[#EAEAEA] pb-3">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-[#111111]">
              Thông báo
            </h2>
            {notificationsData && (
              <span className="font-mono text-[11px] text-[#A5A4A0]">
                {notificationsData.pagination.totalElements}
              </span>
            )}
          </div>

          {notificationsPending && (
            <LoadingScreen variant="inline" message="Đang tải thông báo…" className="mt-3" />
          )}
          {notificationsIsError && (
            <p role="alert" className="mt-3 text-xs text-[#9F2F2D]">
              {classroomError(notificationsError)}
            </p>
          )}
          {notificationsData && (
            <>
              {notificationsData.items.length === 0 && (
                <p className="pt-4 text-xs text-[#787774]">Chưa có thông báo.</p>
              )}
              <ul className="divide-y divide-[#EAEAEA]">
                {notificationsData.items.map(item => (
                  <li
                    key={item.id}
                    className="flex flex-col justify-between gap-3 py-3.5 text-xs sm:flex-row sm:items-center"
                  >
                    <div className="space-y-1">
                      <strong className="font-semibold text-[#111111]">
                        {item.title ?? item.classroomName}
                      </strong>
                      {item.message && <p className="text-[#787774]">{item.message}</p>}
                      {item.expiresAt && (
                        <p className="font-mono text-[11px] text-[#787774]">
                          Trạng thái: {item.status} · Hết hạn:{' '}
                          {new Intl.DateTimeFormat('vi-VN', {
                            dateStyle: 'short',
                            timeStyle: 'short',
                            timeZone: 'Asia/Ho_Chi_Minh',
                          }).format(new Date(item.expiresAt))}
                        </p>
                      )}
                      {(item.status === 'REVOKED' || item.status === 'EXPIRED') && (
                        <p className="text-[11px] text-[#9F2F2D]">Lời mời đã hết hạn.</p>
                      )}
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      {!item.readAt && (
                        <button
                          type="button"
                          disabled={pending}
                          onClick={() => onMarkNotificationRead(item.id)}
                          className="text-xs font-medium text-[#787774] underline underline-offset-2 hover:text-[#111111]"
                        >
                          Đánh dấu đã đọc
                        </button>
                      )}
                      {item.status === 'PENDING' && item.invitationId && (
                        <>
                          <button
                            type="button"
                            disabled={pending}
                            onClick={() => onAcceptInvitation(item.invitationId!, item.classroomId)}
                            className="rounded-md bg-[#111111] px-3 py-1.5 text-xs font-medium text-white hover:bg-[#2F3437] disabled:opacity-50"
                          >
                            Chấp nhận
                          </button>
                          <button
                            type="button"
                            disabled={pending}
                            onClick={() => onDeclineInvitation(item.invitationId!)}
                            className="rounded-md border border-[#EAEAEA] bg-white px-3 py-1.5 text-xs font-medium text-[#787774] hover:bg-[#F7F6F3] hover:text-[#111111] disabled:opacity-50"
                          >
                            Từ chối
                          </button>
                        </>
                      )}
                      {item.type !== 'CLASS_INVITATION' && item.actionUrl && (
                        <Link
                          to={item.actionUrl}
                          className="text-xs font-medium text-[#111111] underline underline-offset-2"
                        >
                          Xem chi tiết
                        </Link>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
              <MinimalPageControls
                page={notificationPage}
                totalPages={notificationsData.pagination.totalPages}
                onPage={onNotificationPageChange}
              />
            </>
          )}
        </section>
      )}

      {/* Keyboard Shortcuts Footer Hint */}
      <div className="mt-5 flex flex-wrap items-center justify-between gap-2 font-mono text-[11px] text-[#A5A4A0]">
        <span>
          {role !== 'STUDENT' ? (
            <>
              Press{' '}
              <kbd className="rounded border border-[#EAEAEA] bg-[#F7F6F3] px-1 text-[#787774]">
                N
              </kbd>{' '}
              to create classroom ·{' '}
            </>
          ) : null}
          Press{' '}
          <kbd className="rounded border border-[#EAEAEA] bg-[#F7F6F3] px-1 text-[#787774]">
            /
          </kbd>{' '}
          to search ·{' '}
          <kbd className="rounded border border-[#EAEAEA] bg-[#F7F6F3] px-1 text-[#787774]">
            Esc
          </kbd>{' '}
          to clear
        </span>
        <span>AlgoLearner Minimal Workspace</span>
      </div>

      {/* Join Classroom Modal (Student) */}
      {role === 'STUDENT' && isJoinOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/45 p-4 backdrop-blur-xs"
          role="dialog"
          aria-modal="true"
          aria-labelledby="backend-join-modal-title"
          onClick={() => {
            onClearFeedback();
            setIsJoinOpen(false);
          }}
        >
          <div
            className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl"
            onClick={event => event.stopPropagation()}
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 id="backend-join-modal-title" className="text-base font-bold text-slate-900">
                  Join a Classroom
                </h2>
                <p className="mt-1 text-xs text-slate-500">
                  Enter the access code provided by your instructor to enroll.
                </p>
              </div>
              <button
                type="button"
                aria-label="Close modal"
                onClick={() => {
                  onClearFeedback();
                  setIsJoinOpen(false);
                }}
                className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
              >
                ×
              </button>
            </div>

            {error && (
              <p role="alert" className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                {error}
              </p>
            )}

            <form onSubmit={handleJoinSubmit} className="mt-5 space-y-4">
              <div>
                <label htmlFor="student-join-class-code" className="block text-xs font-semibold text-slate-700 mb-1.5">
                  Class Code
                </label>
                <input
                  id="student-join-class-code"
                  ref={joinInputRef}
                  autoFocus
                  placeholder="Enter class code"
                  maxLength={32}
                  required
                  value={classCode}
                  onChange={event => setClassCode(event.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 font-mono text-sm text-slate-900 placeholder:text-slate-400 focus:border-slate-900 focus:bg-white focus:outline-none"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => {
                    onClearFeedback();
                    setIsJoinOpen(false);
                  }}
                  className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={pending || !classCode.trim()}
                  className="rounded-xl bg-slate-900 px-4 py-2 text-xs font-semibold text-white hover:bg-slate-800 disabled:opacity-50"
                >
                  {pending ? 'Joining…' : 'Join Class'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Create Classroom Modal (Teacher / Admin) */}
      <BackendCreateClassroomModal
        isOpen={isCreateOpen}
        role={role}
        pending={pending}
        error={error}
        onClose={() => {
          onClearFeedback();
          setIsCreateOpen(false);
        }}
        onSubmit={input =>
          onCreateClassroom(input, created => {
            setIsCreateOpen(false);
            navigate(`${base}/${created.id}`);
          })
        }
      />
    </div>
  );
}
