import { useEffect, useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { ClassroomLayout } from '../components/ClassroomLayout';
import { BackendClassroomListView } from '../components/BackendClassroomListView';
import { LoadingScreen } from '@/components/ui/LoadingScreen';
import { classroomApi, classroomError, classroomKeys } from '../api/classroomApi';
import { announcementError } from '../api/announcementApi';
import {
  useAnnouncementDetailQuery,
  useAnnouncementsQuery,
  useCreateAnnouncementMutation,
  useDeleteAnnouncementMutation,
  useTogglePinAnnouncementMutation,
  useUpdateAnnouncementMutation,
} from '../hooks/useAnnouncements';
import { lessonApi, lessonKeys } from '../lessons/api/lessonApi';
import { AvailabilityBadge } from '../components/AvailabilityBadge';
import { ClassroomAnnouncementsList } from '../components/ClassroomAnnouncementsList';
import type { DemoPersona, DemoRole, JoinMode } from '../types';
import {
  Bell,
  BookOpen,
  BookOpenText,
  Users,
  Settings,
  Copy,
  Check,
  CheckCircle2,
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  ShieldAlert,
} from 'lucide-react';

const field = 'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900';
const button = 'rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50';
const secondary = 'rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-800 disabled:opacity-50';
const toVietnamInput = (value: string) => new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
}).format(new Date(value)).replace(' ', 'T');
const fromVietnamInput = (value: string) => new Date(`${value}:00+07:00`).toISOString();

function PageControls({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return <div className="mt-5 flex items-center gap-3 text-sm">
    <button className={secondary} type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button>
    <span>Page {page + 1} of {totalPages}</span>
    <button className={secondary} type="button" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</button>
  </div>;
}

export function BackendClassroomPage({ role }: { role: DemoRole }) {
  const { id } = useParams<{ id: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab')?.toUpperCase();
  const announcementIdParam = searchParams.get('announcementId');
  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'ANNOUNCEMENTS' | 'LESSONS' | 'MEMBERS' | 'SETTINGS'>(() => {
    if ((tabParam === 'ANNOUNCEMENTS' || announcementIdParam) && role !== 'ADMIN') return 'ANNOUNCEMENTS';
    if (tabParam === 'SETTINGS') return 'SETTINGS';
    if (tabParam === 'MEMBERS') return 'MEMBERS';
    if (tabParam === 'LESSONS') return 'LESSONS';
    return 'OVERVIEW';
  });

  useEffect(() => {
    if ((tabParam === 'ANNOUNCEMENTS' || announcementIdParam) && role !== 'ADMIN') setActiveTab('ANNOUNCEMENTS');
    else if (tabParam === 'SETTINGS') setActiveTab('SETTINGS');
    else if (tabParam === 'MEMBERS') setActiveTab('MEMBERS');
    else if (tabParam === 'LESSONS') setActiveTab('LESSONS');
    else if (tabParam === 'OVERVIEW') setActiveTab('OVERVIEW');
  }, [tabParam, announcementIdParam, role]);

  const [copiedCode, setCopiedCode] = useState(false);
  const copyCode = (code: string) => {
    navigator.clipboard.writeText(code);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const { user } = useAuth();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [pageNumber, setPageNumber] = useState(0);
  const [announcementPage, setAnnouncementPage] = useState(0);
  const [memberPage, setMemberPage] = useState(0);
  const [requestPage, setRequestPage] = useState(0);
  const [invitationPage, setInvitationPage] = useState(0);
  const [notificationPage, setNotificationPage] = useState(0);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [joinMode, setJoinMode] = useState<JoinMode>('AUTO_JOIN');
  const [ownerId, setOwnerId] = useState('');
  const [student, setStudent] = useState('');
  const [email, setEmail] = useState('');
  const [expiresAt, setExpiresAt] = useState('');
  const [codeExpiresAt, setCodeExpiresAt] = useState('');
  const [codeLocked, setCodeLocked] = useState(false);
  const [joiningOpen, setJoiningOpen] = useState(true);

  const persona: DemoPersona = {
    id: user?.id || '', username: user?.username || '', email: user?.email || '',
    fullName: user?.fullName || user?.username || role, role,
  };
  const list = useQuery({
    queryKey: classroomKeys.list(pageNumber),
    queryFn: () => classroomApi.list(pageNumber),
    enabled: !id,
  });
  const detail = useQuery({
    queryKey: classroomKeys.detail(id || ''),
    queryFn: () => classroomApi.get(id!),
    enabled: Boolean(id),
  });
  const members = useQuery({
    queryKey: classroomKeys.members(id || '', memberPage),
    queryFn: () => classroomApi.members(id!, memberPage),
    enabled: Boolean(id) && role !== 'STUDENT',
  });
  const requests = useQuery({
    queryKey: classroomKeys.requests(id || '', requestPage),
    queryFn: () => classroomApi.requests(id!, requestPage),
    enabled: Boolean(id) && role === 'TEACHER',
  });
  const invitations = useQuery({
    queryKey: classroomKeys.invitations(id || '', invitationPage),
    queryFn: () => classroomApi.invitations(id!, invitationPage),
    enabled: Boolean(id) && role === 'TEACHER',
  });
  const notifications = useQuery({
    queryKey: classroomKeys.notifications(notificationPage),
    queryFn: () => classroomApi.notifications(notificationPage),
    enabled: !id && role === 'STUDENT',
  });
  const lessonsQuery = useQuery({
    queryKey: lessonKeys.list(user?.id || '', id || '', 0),
    queryFn: ({ signal }) => lessonApi.list(id!, 0, signal),
    enabled: Boolean(id && user?.id) && role !== 'ADMIN',
    retry: false,
  });
  const announcementsQuery = useAnnouncementsQuery(
    id || '',
    announcementPage,
    20,
    Boolean(id) && role !== 'ADMIN',
  );
  const announcementInCurrentPage = Boolean(
    announcementIdParam &&
      announcementsQuery.data?.items.some(item => item.id === announcementIdParam),
  );
  const announcementDetailQuery = useAnnouncementDetailQuery(
    id || '',
    announcementIdParam,
    Boolean(id && announcementIdParam) &&
      role !== 'ADMIN' &&
      !announcementsQuery.isPending &&
      !announcementInCurrentPage,
  );
  const createAnnouncementMutation = useCreateAnnouncementMutation(id || '');
  const updateAnnouncementMutation = useUpdateAnnouncementMutation(id || '');
  const deleteAnnouncementMutation = useDeleteAnnouncementMutation(id || '');
  const togglePinAnnouncementMutation = useTogglePinAnnouncementMutation(id || '');

  useEffect(() => {
    setAnnouncementPage(0);
  }, [id]);

  useEffect(() => {
    const classroom = detail.data;
    if (!classroom) return;
    setName(classroom.name);
    setDescription(classroom.description || '');
    setJoinMode(classroom.joinMode);
    setCodeExpiresAt(classroom.codeExpiresAt ? toVietnamInput(classroom.codeExpiresAt) : '');
    setCodeLocked(classroom.codeLocked);
    setJoiningOpen(classroom.joiningOpen);
  }, [detail.data]);

  async function run<T>(action: () => Promise<T>, success: string | ((result: T) => string), next?: (result: T) => void) {
    setPending(true); setError(''); setNotice('');
    try {
      const result = await action();
      await Promise.all([
        client.invalidateQueries({ queryKey: ['classrooms'] }),
        client.invalidateQueries({ queryKey: ['notifications'] }),
        client.invalidateQueries({ queryKey: ['dashboard'] }),
      ]);
      setNotice(typeof success === 'string' ? success : success(result));
      next?.(result);
    } catch (failure) {
      setError(classroomError(failure));
    } finally {
      setPending(false);
    }
  }

  function submit<T>(event: FormEvent<HTMLFormElement>, action: () => Promise<T>, success: string | ((result: T) => string), next?: (result: T) => void) {
    event.preventDefault();
    void run(action, success, next);
  }

  const base = `/${role.toLowerCase()}/classrooms`;
  const content = id ? detail.data : undefined;

  return <ClassroomLayout role={role} persona={persona} breadcrumbChild={content?.name}>
    {!id && (
      <BackendClassroomListView
        role={role}
        base={base}
        pending={pending}
        error={error}
        notice={notice}
        listData={list.data}
        listPending={list.isPending}
        listIsError={list.isError}
        listError={list.error}
        onRetryList={() => void list.refetch()}
        pageNumber={pageNumber}
        onPageChange={setPageNumber}
        notificationsData={notifications.data}
        notificationsPending={notifications.isPending}
        notificationsIsError={notifications.isError}
        notificationsError={notifications.error}
        notificationPage={notificationPage}
        onNotificationPageChange={setNotificationPage}
        onCreateClassroom={(input, onCreated) =>
          void run(
            () =>
              classroomApi.create({
                name: input.name,
                description: input.description,
                joinMode: input.joinMode,
                codeExpiresAt: null,
                ...(input.ownerId ? { ownerId: input.ownerId } : {}),
              }),
            created => `Classroom created: ${created.name}`,
            onCreated,
          )
        }
        onJoinByCode={(code, onJoined) =>
          void run(
            () => classroomApi.join(code),
            result =>
              result.outcome === 'JOINED'
                ? 'Joined classroom.'
                : 'Request sent for teacher approval.',
            result => {
              onJoined?.();
              if (result.outcome === 'JOINED') {
                navigate(`${base}/${result.classroomId}`);
              }
            },
          )
        }
        onMarkNotificationRead={notificationId =>
          void run(() => classroomApi.markNotificationRead(notificationId), 'Đã đánh dấu đã đọc.')
        }
        onAcceptInvitation={(invitationId, classroomId) =>
          void run(
            () => classroomApi.acceptInvitation(invitationId),
            'Đã tham gia lớp.',
            () => {
              if (classroomId) navigate(`${base}/${classroomId}`);
            },
          )
        }
        onDeclineInvitation={invitationId =>
          void run(() => classroomApi.declineInvitation(invitationId), 'Đã từ chối lời mời.')
        }
        onClearFeedback={() => {
          setError('');
          setNotice('');
        }}
      />
    )}

    {id && <div className="mx-auto max-w-5xl space-y-6 p-4 sm:p-8">
      <header>
        <Link className="inline-flex items-center gap-1.5 text-sm font-semibold text-slate-600 hover:text-slate-900 transition" to={base}><ArrowLeft size={16} /> Back to classrooms</Link>
        <p className="mt-2 text-xs font-bold uppercase tracking-widest text-slate-500">{role} · Classroom workspace</p>
      </header>
      {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{error}</p>}
      {notice && <p role="status" className="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
      <>
        {detail.isPending && <LoadingScreen variant="page" message="Đang tải thông tin lớp học…" />}
        {detail.isError && <p role="alert">{classroomError(detail.error)} <button type="button" className="underline" onClick={() => detail.refetch()}>Retry</button></p>}
        {content && <>
          {/* Header Banner / Hero Card */}
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
              <div>
                <h1 className="text-3xl font-extrabold tracking-tight text-slate-900">{content.name}</h1>
                <p className="text-slate-500 text-sm mt-2 max-w-xl">
                  {content.description || 'No description provided yet.'}
                </p>
                <div className="flex flex-wrap items-center gap-5 mt-4 text-xs text-slate-600">
                  {role !== 'STUDENT' && content.classCode && (
                    <div className="flex items-center gap-1.5">
                      <span className="text-slate-400">Class code:</span>
                      <span className="font-mono font-bold bg-slate-100 text-slate-800 px-2.5 py-1 rounded-md border border-slate-200 inline-flex items-center gap-1.5">
                        {content.classCode}
                        <button
                          type="button"
                          onClick={() => copyCode(content.classCode!)}
                          className="text-slate-400 hover:text-slate-700 transition"
                          title="Copy class code"
                        >
                          {copiedCode ? <Check size={13} className="text-emerald-600" /> : <Copy size={13} />}
                        </button>
                      </span>
                    </div>
                  )}
                  <div className="flex items-center gap-1.5">
                    <span className="text-slate-400">Enrolled students:</span>
                    <span className="font-bold text-slate-800">{members.data?.pagination.totalElements ?? 0} students</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="text-slate-400">Status:</span>
                    <span className="font-semibold text-emerald-700">
                      {content.status} · {content.joinMode === 'AUTO_JOIN' ? 'Auto join' : 'Require approval'}
                    </span>
                  </div>
                </div>
              </div>

              {role !== 'ADMIN' && (
                <div className="flex items-center gap-3">
                  <Link
                    to={`${base}/${encodeURIComponent(id)}/lessons`}
                    className="inline-flex items-center justify-center gap-2 rounded-xl bg-slate-900 px-6 py-3.5 text-sm font-semibold text-white shadow-sm hover:bg-slate-800 transition"
                  >
                    <span>Open class lessons</span>
                    <ArrowRight size={16} />
                  </Link>
                  {role === 'STUDENT' && <Link
                    to={`/student/classrooms/${encodeURIComponent(id)}/practice`}
                    className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-300 bg-white px-6 py-3.5 text-sm font-semibold text-slate-900 hover:bg-slate-50 transition"
                  >Coding practice <ArrowRight size={16} /></Link>}
                </div>
              )}
            </div>
          </div>

          {/* Subnavigation Tabs */}
          <nav className="classroom-subnav mt-6" role="tablist" aria-label="Classroom sections">
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'OVERVIEW'}
              className="classroom-subnav-link"
              data-active={activeTab === 'OVERVIEW'}
              onClick={() => {
                setActiveTab('OVERVIEW');
                setSearchParams({});
              }}
            >
              <BookOpen size={16} aria-hidden="true" />
              <span>Overview</span>
            </button>

            {role !== 'ADMIN' && (
              <button
                type="button"
                role="tab"
                aria-selected={activeTab === 'LESSONS'}
                className="classroom-subnav-link"
                data-active={activeTab === 'LESSONS'}
                onClick={() => {
                  setActiveTab('LESSONS');
                  setSearchParams({ tab: 'lessons' });
                }}
              >
                <BookOpenText size={16} aria-hidden="true" />
                <span>Lessons</span>
              </button>
            )}

            {role !== 'ADMIN' && (
              <button
                type="button"
                role="tab"
                aria-selected={activeTab === 'ANNOUNCEMENTS'}
                className="classroom-subnav-link"
                data-active={activeTab === 'ANNOUNCEMENTS'}
                onClick={() => {
                  setActiveTab('ANNOUNCEMENTS');
                  setSearchParams({ tab: 'announcements' });
                }}
              >
                <Bell size={16} aria-hidden="true" />
                <span>Announcements</span>
                {announcementsQuery.data && (
                  <span className="classroom-subnav-badge">
                    {announcementsQuery.data.pagination.totalElements}
                  </span>
                )}
              </button>
            )}

            {role !== 'STUDENT' && (
              <button
                type="button"
                role="tab"
                aria-selected={activeTab === 'MEMBERS'}
                className="classroom-subnav-link"
                data-active={activeTab === 'MEMBERS'}
                onClick={() => {
                  setActiveTab('MEMBERS');
                  setSearchParams({ tab: 'members' });
                }}
              >
                <Users size={16} aria-hidden="true" />
                <span>Members & Requests</span>
                {requests.data?.items && requests.data.items.length > 0 ? (
                  <span className="classroom-subnav-badge bg-amber-100 text-amber-800">
                    {requests.data.items.length} pending
                  </span>
                ) : (
                  <span className="classroom-subnav-badge">
                    {members.data?.pagination.totalElements ?? 0}
                  </span>
                )}
              </button>
            )}

            {role !== 'STUDENT' && (
              <button
                type="button"
                role="tab"
                aria-selected={activeTab === 'SETTINGS'}
                className="classroom-subnav-link"
                data-active={activeTab === 'SETTINGS'}
                onClick={() => {
                  setActiveTab('SETTINGS');
                  setSearchParams({ tab: 'settings' });
                }}
              >
                <Settings size={16} aria-hidden="true" />
                <span>Classroom Settings</span>
              </button>
            )}
          </nav>

          {/* Tab 1: OVERVIEW */}
          {activeTab === 'OVERVIEW' && (() => {
            const allLessons = lessonsQuery.data?.items || [];
            const visibleLessons = role === 'STUDENT'
              ? allLessons.filter(lesson => lesson.status === 'PUBLISHED')
              : allLessons;
            const completedLessonsCount = visibleLessons.filter(lesson => Boolean(lesson.progress?.completed)).length;
            const nextLesson = visibleLessons.find(lesson => !lesson.progress?.completed) || null;
            const progressPercent = visibleLessons.length > 0
              ? Math.round((completedLessonsCount / visibleLessons.length) * 100)
              : 0;

            return (
              <div className="classroom-detail-grid mt-6">
                <div className="classroom-detail-main">
                  {role !== 'ADMIN' && (
                    <div className="classroom-glass-card p-6">
                      <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
                        <div>
                          <h2 className="text-base font-bold text-slate-900">Learning Modules &amp; Practice</h2>
                          <p className="text-xs text-slate-600 mt-0.5">
                            Published curriculum lessons synced with this classroom
                          </p>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="text-xs text-slate-700 font-semibold px-2.5 py-1 rounded-full bg-slate-100 border border-slate-300 font-mono">
                            {visibleLessons.length} {role === 'STUDENT' ? 'Published' : 'Total'}
                            {role === 'STUDENT' ? ` · ${completedLessonsCount} Completed` : ''}
                          </span>
                          <Link
                            to={`${base}/${encodeURIComponent(id)}/lessons`}
                            className="text-xs font-bold text-slate-900 hover:underline inline-flex items-center gap-1"
                          >
                            View all →
                          </Link>
                        </div>
                      </div>

                      {lessonsQuery.isPending ? (
                        <p className="text-xs text-slate-500 py-4">Loading classroom lessons…</p>
                      ) : visibleLessons.length === 0 ? (
                        <div className="p-5 rounded-xl border border-dashed border-slate-300 bg-slate-50 text-center">
                          <p className="text-sm font-semibold text-slate-800">No published lessons yet</p>
                          <p className="text-xs text-slate-600 mt-1">
                            Lessons published by the instructor will appear here automatically.
                          </p>
                        </div>
                      ) : (
                        <div className="space-y-3">
                          {visibleLessons.slice(0, 6).map((lesson, idx) => {
                            const isDone = Boolean(lesson.progress?.completed);
                            const materialCount = lesson.materialCount ?? lesson.attachments.length;
                            const subtitle = `${
                              lesson.completionMode === 'MANUAL'
                                ? 'Manual completion'
                                : 'Linked activity required'
                            } · ${materialCount} ${materialCount === 1 ? 'material' : 'materials'}`;

                            return (
                              <Link
                                key={lesson.id}
                                to={`${base}/${encodeURIComponent(id)}/lessons/${encodeURIComponent(lesson.id)}`}
                                className="p-4 rounded-xl border border-slate-200 bg-white/70 hover:bg-white hover:border-slate-300 flex items-center justify-between gap-3 transition-all"
                              >
                                <div className="flex items-center gap-3.5 min-w-0">
                                  <div className="classroom-module-number-card" aria-hidden="true">
                                    {String(idx + 1).padStart(2, '0')}
                                  </div>
                                  <div className="min-w-0">
                                    <h3 className="text-sm font-bold text-slate-900 truncate">
                                      {lesson.title}
                                    </h3>
                                    <p className="text-xs text-slate-600 truncate">{subtitle}</p>
                                  </div>
                                </div>
                                <div className="flex items-center gap-2 shrink-0">
                                  {role === 'STUDENT' && isDone ? (
                                    <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200 text-[11px] font-bold">
                                      <CheckCircle2 size={12} aria-hidden="true" />
                                      Completed
                                    </span>
                                  ) : (
                                    <AvailabilityBadge status="AVAILABLE" />
                                  )}
                                  <ArrowUpRight size={15} className="text-slate-500" aria-hidden="true" />
                                </div>
                              </Link>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  )}

                  <section className="classroom-glass-card p-6">
                    <h2 className="text-base font-bold text-slate-900 mb-2">Class Syllabus &amp; Description</h2>
                    <p className="text-sm text-slate-600 whitespace-pre-wrap leading-relaxed">
                      {content.description || 'No description provided yet.'}
                    </p>
                  </section>
                </div>

                <aside className="classroom-detail-rail">
                  {role === 'STUDENT' && (
                    <div className="classroom-glass-card p-5 space-y-4">
                      <div className="flex items-baseline justify-between gap-2">
                        <span className="text-[10.5px] font-mono font-bold uppercase tracking-wider text-slate-500">
                          Course Progress
                        </span>
                        <span className="text-lg font-extrabold text-slate-900 font-mono">
                          {progressPercent}%
                        </span>
                      </div>
                      <div className="w-full h-2 rounded-full bg-slate-200 overflow-hidden" aria-hidden="true">
                        <div
                          className="h-full rounded-full transition-all duration-300"
                          style={{
                            width: `${progressPercent}%`,
                            backgroundColor: progressPercent >= 100 && visibleLessons.length > 0 ? '#059669' : '#18181b',
                          }}
                        />
                      </div>
                      <p className="text-xs text-slate-600">
                        {completedLessonsCount} of {visibleLessons.length} published lessons completed
                      </p>

                      {nextLesson && (
                        <div className="pt-3 border-t border-slate-200 space-y-2.5">
                          <span className="text-[10.5px] font-mono font-bold uppercase tracking-wider text-slate-500 block">
                            Up next
                          </span>
                          <p className="text-xs font-bold text-slate-900 line-clamp-2">
                            {nextLesson.title}
                          </p>
                          <Link
                            to={`${base}/${encodeURIComponent(id)}/lessons/${encodeURIComponent(nextLesson.id)}`}
                            className="inline-flex items-center justify-center gap-1.5 w-full px-3.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold transition-colors"
                          >
                            <span>Continue next lesson</span>
                            <ArrowUpRight size={14} aria-hidden="true" />
                          </Link>
                        </div>
                      )}
                    </div>
                  )}

                  <section className="classroom-glass-card p-6">
                    <h2 className="text-base font-bold text-slate-900 mb-4">Classroom Snapshot</h2>
                    <dl className="space-y-3 text-xs">
                      <div className="flex justify-between py-1.5 border-b border-slate-100">
                        <dt className="text-slate-500">Status</dt>
                        <dd className="font-semibold text-emerald-700">{content.status}</dd>
                      </div>
                      <div className="flex justify-between py-1.5 border-b border-slate-100">
                        <dt className="text-slate-500">Join Mode</dt>
                        <dd className="font-semibold text-slate-800">{content.joinMode}</dd>
                      </div>
                      {role !== 'STUDENT' && (
                        <div className="flex justify-between py-1.5 border-b border-slate-100">
                          <dt className="text-slate-500">Class Code</dt>
                          <dd className="font-mono font-bold text-slate-800">{content.classCode || 'None'}</dd>
                        </div>
                      )}
                      <div className="flex justify-between py-1.5">
                        <dt className="text-slate-500">Enrolled</dt>
                        <dd className="font-semibold text-slate-800">{members.data?.pagination.totalElements ?? 0} students</dd>
                      </div>
                    </dl>
                    {role === 'STUDENT' && (
                      <button
                        className={`${secondary} mt-5 w-full text-rose-700 hover:bg-rose-50 border-rose-200`}
                        type="button"
                        disabled={pending || content.status !== 'ACTIVE'}
                        onClick={() => void run(() => classroomApi.leave(id), 'Left classroom.', () => navigate(base))}
                      >
                        Leave classroom
                      </button>
                    )}
                  </section>
                </aside>
              </div>
            );
          })()}

          {/* Tab: ANNOUNCEMENTS */}
          {activeTab === 'ANNOUNCEMENTS' && role !== 'ADMIN' && (() => {
            const canManageAnnouncements =
              role === 'TEACHER' &&
              Boolean(user?.id && content.ownerId === user.id) &&
              content.status === 'ACTIVE';
            const readOnlyNotice =
              role === 'TEACHER' && content.status !== 'ACTIVE'
                ? 'This classroom is archived. Announcements are read-only and cannot be created, edited, deleted, or pinned.'
                : null;
            const currentItems = announcementsQuery.data?.items ?? [];
            const linkedFallback =
              announcementIdParam && !announcementInCurrentPage
                ? announcementDetailQuery.data ?? null
                : null;

            return (
              <div className="mt-6">
                <ClassroomAnnouncementsList
                  classroomId={id}
                  isTeacherOrAdmin={canManageAnnouncements}
                  announcements={currentItems}
                  highlightedId={announcementIdParam}
                  highlightedAnnouncement={linkedFallback}
                  isLoading={announcementsQuery.isPending}
                  isFetching={announcementsQuery.isFetching}
                  error={announcementsQuery.isError ? announcementError(announcementsQuery.error) : null}
                  onRetry={() => void announcementsQuery.refetch()}
                  pagination={announcementsQuery.data?.pagination}
                  onPageChange={setAnnouncementPage}
                  isCreating={createAnnouncementMutation.isPending}
                  isUpdating={updateAnnouncementMutation.isPending}
                  isDeleting={deleteAnnouncementMutation.isPending}
                  pinningId={
                    togglePinAnnouncementMutation.isPending
                      ? togglePinAnnouncementMutation.variables?.id ?? null
                      : null
                  }
                  readOnlyNotice={readOnlyNotice}
                  onCreate={
                    canManageAnnouncements
                      ? async data => {
                          await createAnnouncementMutation.mutateAsync({
                            title: data.title,
                            content: data.content,
                          });
                          setAnnouncementPage(0);
                        }
                      : undefined
                  }
                  onUpdate={
                    canManageAnnouncements
                      ? async (annId, data) => {
                          await updateAnnouncementMutation.mutateAsync({
                            announcementId: annId,
                            input: { title: data.title, content: data.content },
                          });
                        }
                      : undefined
                  }
                  onDelete={
                    canManageAnnouncements
                      ? async annId => {
                          await deleteAnnouncementMutation.mutateAsync(annId);
                          if (announcementPage > 0 && currentItems.length <= 1) {
                            setAnnouncementPage(prev => Math.max(0, prev - 1));
                          }
                        }
                      : undefined
                  }
                  onTogglePin={
                    canManageAnnouncements
                      ? async annId => {
                          const target =
                            currentItems.find(item => item.id === annId) ||
                            (linkedFallback?.id === annId ? linkedFallback : undefined);
                          if (!target) return;
                          await togglePinAnnouncementMutation.mutateAsync({
                            id: target.id,
                            isPinned: target.isPinned,
                          });
                        }
                      : undefined
                  }
                />
              </div>
            );
          })()}

          {/* Tab 2: LESSONS */}
          {activeTab === 'LESSONS' && (
            <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm space-y-4">
              <div className="mx-auto w-12 h-12 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
                <BookOpenText size={24} />
              </div>
              <div>
                <h2 className="text-lg font-bold text-slate-900">Class Lessons & Automated Evaluations</h2>
                <p className="text-sm text-slate-500 mt-1 max-w-md mx-auto">
                  Manage interactive course lessons, assign LeetCode-style programming tasks with isolated sandbox test runners, and track student submissions.
                </p>
              </div>
              <div className="pt-2">
                <Link
                  to={`${base}/${encodeURIComponent(id)}/lessons`}
                  className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-6 py-3 text-sm font-semibold text-white shadow-sm hover:bg-slate-800 transition"
                >
                  <span>Open Class Lessons Workspace</span>
                  <ArrowRight size={16} />
                </Link>
              </div>
            </div>
          )}

          {/* Tab 3: MEMBERS */}
          {activeTab === 'MEMBERS' && (
            <div className="mt-6 space-y-6">
              {role === 'TEACHER' && content.status === 'ACTIVE' && requests.data && requests.data.items.length > 0 && (
                <section className="rounded-2xl border border-amber-200 bg-amber-50/40 p-6 shadow-sm">
                  <h2 className="mb-3 text-base font-bold text-amber-900 flex items-center gap-2">
                    Pending join requests ({requests.data.items.length})
                  </h2>
                  <ul className="divide-y divide-amber-200/50">
                    {requests.data.items.map(request => (
                      <li key={request.id} className="flex items-center justify-between gap-3 py-3 text-sm">
                        <span className="font-semibold text-slate-900">{request.username}</span>
                        <button
                          className="rounded-lg bg-emerald-600 px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-50 transition"
                          type="button"
                          disabled={pending}
                          onClick={() => void run(() => classroomApi.approve(id, request.id), 'Join request approved.')}
                        >
                          Approve
                        </button>
                      </li>
                    ))}
                  </ul>
                  <PageControls page={requestPage} totalPages={requests.data.pagination.totalPages} onPage={setRequestPage} />
                </section>
              )}

              <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                <h2 className="mb-3 text-lg font-bold text-slate-900">Members</h2>
                {members.isPending && <LoadingScreen variant="inline" message="Đang tải danh sách thành viên…" />}
                {members.isError && <p role="alert">{classroomError(members.error)}</p>}
                {members.data && (
                  <>
                    <ul className="divide-y divide-slate-100">
                      {members.data.items.map(member => (
                        <li key={member.studentId} className="flex items-center justify-between gap-3 py-3 text-sm">
                          <div>
                            <span className="font-bold text-slate-900">{member.username}</span>
                            <span className="text-slate-400 ml-2">· {member.email}</span>
                          </div>
                          {role === 'TEACHER' && content.status === 'ACTIVE' && (
                            <button
                              className="text-xs font-semibold text-rose-600 hover:text-rose-800 disabled:opacity-50"
                              type="button"
                              disabled={pending}
                              onClick={() => void run(() => classroomApi.removeMember(id, member.studentId), 'Student removed.')}
                            >
                              Remove
                            </button>
                          )}
                        </li>
                      ))}
                    </ul>
                    <PageControls page={memberPage} totalPages={members.data.pagination.totalPages} onPage={setMemberPage} />
                  </>
                )}
                {role === 'TEACHER' && content.status === 'ACTIVE' && (
                  <form
                    className="mt-5 flex gap-3 pt-4 border-t border-slate-100"
                    onSubmit={event => submit(event, () => classroomApi.directAdd(id, student.trim()), 'Student added.')}
                  >
                    <input
                      className={field}
                      aria-label="Student username or email"
                      placeholder="Student username or email"
                      required
                      value={student}
                      onChange={event => setStudent(event.target.value)}
                    />
                    <button className={button} disabled={pending}>Add</button>
                  </form>
                )}
              </section>

              {role === 'TEACHER' && content.status === 'ACTIVE' && (
                <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                  <h2 className="mb-3 text-lg font-bold text-slate-900">Invite student</h2>
                  <form
                    className="grid gap-3"
                    onSubmit={event => submit(event, () => classroomApi.invite(id, email.trim(), fromVietnamInput(expiresAt)), inviteId => `Invitation created: ${inviteId}`)}
                  >
                    <input
                      className={field}
                      type="email"
                      aria-label="Student email"
                      placeholder="Student email"
                      required
                      value={email}
                      onChange={event => setEmail(event.target.value)}
                    />
                    <label className="text-xs font-bold text-slate-600 uppercase">
                      Expires at
                      <input
                        className={`${field} mt-1`}
                        type="datetime-local"
                        required
                        value={expiresAt}
                        onChange={event => setExpiresAt(event.target.value)}
                      />
                    </label>
                    <div className="flex justify-end">
                      <button className={button} disabled={pending}>Invite</button>
                    </div>
                  </form>
                  {invitations.isPending && <LoadingScreen variant="inline" message="Đang tải danh sách lời mời…" className="mt-4" />}
                  {invitations.isError && <p role="alert" className="mt-4">{classroomError(invitations.error)}</p>}
                  {invitations.data && (
                    <>
                      <ul className="mt-4 divide-y divide-slate-100">
                        {invitations.data.items.map(invitation => (
                          <li key={invitation.id} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
                            <span>{invitation.studentEmail} · {invitation.status}</span>
                            {invitation.status === 'PENDING' && (
                              <button
                                type="button"
                                className={secondary}
                                disabled={pending}
                                onClick={() => void run(() => classroomApi.revoke(id, invitation.id), 'Đã thu hồi lời mời.')}
                              >
                                Thu hồi
                              </button>
                            )}
                          </li>
                        ))}
                      </ul>
                      <PageControls page={invitationPage} totalPages={invitations.data.pagination.totalPages} onPage={setInvitationPage} />
                    </>
                  )}
                </section>
              )}
            </div>
          )}

          {/* Tab 4: SETTINGS */}
          {activeTab === 'SETTINGS' && (
            <div className="mt-6 space-y-8">
              {/* Card 1: General Information */}
              <section className="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm">
                <div className="border-b border-slate-100 pb-3 mb-5">
                  <h2 className="text-base font-bold text-slate-900">Edit classroom</h2>
                  <p className="text-xs text-slate-500 mt-0.5">Update classroom name and public syllabus description.</p>
                </div>
                <form
                  className="grid gap-4"
                  onSubmit={event => submit(event, () => classroomApi.update(id, { name: name.trim(), description: description.trim() }), 'Classroom updated.')}
                >
                  <div>
                    <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">Classroom Name *</label>
                    <input
                      className={field}
                      aria-label="Classroom name"
                      maxLength={150}
                      required
                      value={name}
                      onChange={event => setName(event.target.value)}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">Description & Syllabus</label>
                    <textarea
                      rows={4}
                      className={field}
                      aria-label="Description"
                      maxLength={2000}
                      value={description}
                      onChange={event => setDescription(event.target.value)}
                    />
                    <div className="flex justify-between items-center text-xs text-slate-400 mt-1">
                      <span>Markdown supported.</span>
                      <span>{description.length} / 2000 characters</span>
                    </div>
                  </div>
                  <div className="flex justify-end pt-2">
                    <button className={button} disabled={pending}>Save</button>
                  </div>
                </form>
              </section>

              {/* Card 2: Join Settings & Code */}
              <section className="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm">
                <div className="border-b border-slate-100 pb-3 mb-5">
                  <h2 className="text-base font-bold text-slate-900">Join settings</h2>
                  <p className="text-xs text-slate-500 mt-0.5">Configure admission rules, code expiration, and student discovery.</p>
                </div>

                <div className="bg-slate-50 border border-slate-200/80 rounded-xl p-4 mb-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                  <div>
                    <div className="text-xs font-bold text-slate-500 uppercase tracking-wider">Active Class Code</div>
                    <div className="font-mono text-2xl font-black text-slate-900 tracking-wider mt-1">
                      {content.classCode || 'No code set'}
                    </div>
                  </div>
                  <button
                    className={secondary}
                    type="button"
                    disabled={pending}
                    onClick={() => void run(() => classroomApi.regenerateCode(id), 'Class code regenerated.')}
                  >
                    Regenerate code
                  </button>
                </div>

                <form
                  className="grid gap-4"
                  onSubmit={event => submit(event, () => classroomApi.configure(id, { codeExpiresAt: codeExpiresAt ? fromVietnamInput(codeExpiresAt) : null, codeLocked, joiningOpen, joinMode }), 'Join settings saved.')}
                >
                  <div className="grid md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">Join mode</label>
                      <select
                        className={field}
                        aria-label="Join mode"
                        value={joinMode}
                        onChange={event => setJoinMode(event.target.value as JoinMode)}
                      >
                        <option value="AUTO_JOIN">Auto join</option>
                        <option value="REQUIRE_APPROVAL">Require approval</option>
                      </select>
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">Class code expires at</label>
                      <input
                        className={field}
                        type="datetime-local"
                        value={codeExpiresAt}
                        onChange={event => setCodeExpiresAt(event.target.value)}
                      />
                    </div>
                  </div>

                  <div className="pt-3 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <div className="space-y-2">
                      <label className="flex items-center gap-2 text-sm text-slate-800 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={joiningOpen}
                          onChange={event => setJoiningOpen(event.target.checked)}
                          className="rounded border-slate-300"
                        />
                        <span>Allow joining</span>
                      </label>
                      <label className="flex items-center gap-2 text-sm text-slate-800 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={codeLocked}
                          onChange={event => setCodeLocked(event.target.checked)}
                          className="rounded border-slate-300"
                        />
                        <span>Lock class code</span>
                      </label>
                    </div>

                    <button className={button} disabled={pending}>Save settings</button>
                  </div>
                </form>
              </section>

              {/* Card 3: Danger Zone */}
              <section className="rounded-2xl border border-rose-200 bg-rose-50/20 p-7 shadow-sm">
                <div className="border-b border-rose-100 pb-3 mb-5">
                  <h2 className="text-base font-bold text-rose-900 flex items-center gap-2">
                    <ShieldAlert size={18} className="text-rose-600" />
                    Danger Zone
                  </h2>
                  <p className="text-xs text-rose-600 mt-0.5">Actions that affect the overall classroom lifecycle and access.</p>
                </div>

                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 py-2">
                  <div>
                    <div className="text-sm font-bold text-slate-900">Archive this classroom</div>
                    <div className="text-xs text-slate-500 mt-0.5">Locks the classroom into read-only mode for all students.</div>
                  </div>
                  {content.status === 'ACTIVE' && (
                    <button
                      className="rounded-lg border border-rose-300 bg-white px-4 py-2 text-xs font-semibold text-rose-700 hover:bg-rose-50 shadow-sm transition disabled:opacity-50 whitespace-nowrap"
                      type="button"
                      disabled={pending}
                      onClick={() => void run(() => classroomApi.archive(id), 'Classroom archived.')}
                    >
                      Archive classroom
                    </button>
                  )}
                </div>

                {role === 'ADMIN' && (
                  <>
                    <div className="mt-5 pt-4 border-t border-rose-100">
                      <h3 className="mb-2 text-sm font-bold text-slate-900">Transfer ownership</h3>
                      <form
                        className="flex gap-3"
                        onSubmit={event => submit(event, () => classroomApi.transferOwner(id, ownerId.trim()), 'Ownership transferred.')}
                      >
                        <input
                          className={field}
                          aria-label="New teacher account UUID"
                          placeholder="New teacher account UUID"
                          required
                          value={ownerId}
                          onChange={event => setOwnerId(event.target.value)}
                        />
                        <button className={button} disabled={pending}>Transfer</button>
                      </form>
                    </div>

                    <div className="mt-5 pt-4 border-t border-rose-100 flex items-center justify-between">
                      <div>
                        <div className="text-sm font-bold text-rose-900">Permanently delete classroom</div>
                        <div className="text-xs text-rose-600">This action cannot be undone. All lessons and submissions will be deleted.</div>
                      </div>
                      <button
                        className="rounded-lg bg-rose-600 px-4 py-2 text-xs font-semibold text-white hover:bg-rose-700 transition disabled:opacity-50"
                        type="button"
                        disabled={pending}
                        onClick={() => {
                          if (window.confirm('Delete this classroom permanently?')) {
                            void run(() => classroomApi.remove(id), 'Classroom deleted.', () => navigate(base));
                          }
                        }}
                      >
                        Delete classroom
                      </button>
                    </div>
                  </>
                )}
              </section>
            </div>
          )}
        </>}
      </>
    </div>}
  </ClassroomLayout>;
}
