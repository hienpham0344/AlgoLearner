import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, ArrowUpRight, BookOpen, CheckCircle2, ChevronRight, Clock, FileText, Plus, Search } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { ClassroomLayout } from '../../components/ClassroomLayout';
import { classroomApi } from '../../api/classroomApi';
import { readStoredPersona } from '../../mock/useClassroomStore';
import type { Classroom } from '../../types';
import { lessonApi, lessonError, lessonKeys } from '../api/lessonApi';
import { getSynchronizedClassroom, lessonMockStore } from '../mock/lessonMockStore';
import {
  estimateReadingMinutes,
  extractLessonOutline,
  readSavedLessonProgress,
  saveLessonReadProgress,
} from '../lessonReading';
import { activityPath, canCompleteLesson, lessonDate, visibleLessons } from '../lessonRules';
import type { Lesson, LessonInput, LessonRole } from '../types';
import { LessonEditor } from '../components/LessonEditor';
import { LessonContent } from '../components/LessonContent';
import { LessonAttachments } from '../components/LessonAttachments';
import { LessonProgress } from '../components/LessonProgress';
import { LessonConfirm, LessonErrorState, LessonPagination } from '../components/LessonState';
import { InteractiveCourseBackground } from '@/features/dashboard/components/InteractiveCourseBackground';
import 'katex/dist/katex.min.css';
import '../styles/lessons.css';

const IS_PROTOTYPE_DEMO = import.meta.env.MODE === 'auth-demo';

export function LessonWorkspacePage({ role }: { role: LessonRole }) {
  const { user, isLoading } = useAuth();
  if (isLoading) return <p role="status" className="p-8">Loading account…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== role) return <div className="p-8" role="alert">This lesson workspace is for {role.toLowerCase()} accounts. <Link to="/classrooms">Open my classrooms</Link></div>;
  return <LessonScope role={role} userId={user.id} />;
}

function LessonScope({ role, userId }: { role: LessonRole; userId: string }) {
  const { id = '', lessonId } = useParams<{ id: string; lessonId: string }>();
  const { user } = useAuth();

  useLayoutEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }, [id, lessonId]);

  const demoPersona = IS_PROTOTYPE_DEMO ? readStoredPersona(role) : null;
  const effectiveUserId = demoPersona ? demoPersona.id : userId;

  const classroom = useQuery({
    queryKey: ['lesson-classroom', effectiveUserId, id],
    queryFn: async () => {
      if (IS_PROTOTYPE_DEMO) {
        const match = getSynchronizedClassroom(id);
        if (!match) throw new Error('Classroom not found.');
        return match;
      }
      return classroomApi.get(id);
    },
    retry: false,
    staleTime: 0,
    refetchInterval: IS_PROTOTYPE_DEMO ? false : 15_000,
  });

  const base = `/${role.toLowerCase()}/classrooms/${encodeURIComponent(id)}`;
  const lessonsBase = `${base}/lessons`;
  const layoutPersona = demoPersona || {
    id: userId,
    username: user?.username || '',
    fullName: user?.fullName || user?.username || '',
    email: user?.email || '',
    role,
  };

  const syncClassroom = IS_PROTOTYPE_DEMO ? (classroom.data as Classroom | undefined) : undefined;
  const isMember =
    !IS_PROTOTYPE_DEMO
    || role !== 'STUDENT'
    || Boolean(syncClassroom?.members.some(m => m.studentId === effectiveUserId && m.status === 'ACTIVE'));

  const breadcrumbNode = classroom.data ? (
    <span className="inline-flex flex-wrap items-center gap-1.5 min-w-0 max-w-full">
      <Link to={base} className="hover:underline font-medium text-slate-600 truncate max-w-[200px] sm:max-w-[280px]">
        {classroom.data.name}
      </Link>
      <ChevronRight size={13} className="shrink-0 text-slate-400" aria-hidden="true" />
      {lessonId ? (
        <>
          <Link to={lessonsBase} className="hover:underline font-medium text-slate-600 shrink-0">
            Class lessons
          </Link>
          <ChevronRight size={13} className="shrink-0 text-slate-400" aria-hidden="true" />
          <span className="text-slate-900 font-bold truncate">
            {lessonId === 'new' ? 'New lesson' : 'Lesson detail'}
          </span>
        </>
      ) : (
        <span className="text-slate-900 font-bold shrink-0">Class lessons</span>
      )}
    </span>
  ) : (
    'Class lessons'
  );

  return <ClassroomLayout className="lesson-shell" role={role} persona={layoutPersona} breadcrumbChild={breadcrumbNode}>
    <div className="lesson-workspace" id="lesson-main">
      {classroom.isPending ? <p role="status">Loading classroom…</p> : classroom.isError ? <LessonErrorState error={classroom.error} retry={() => void classroom.refetch()} />
        : classroom.data && (
          role === 'TEACHER' && classroom.data.ownerId !== effectiveUserId
            ? <LessonErrorState error={new Error('Only the classroom owner can manage these lessons.')} />
            : !isMember
              ? <LessonErrorState error={new Error('You must be an active member of this classroom to access its lessons.')} />
              : <>
                  {!lessonId && (
                    <header className="lesson-page-header">
                      <div className="min-w-0">
                        <div className="lesson-header-crumb">
                          <Link className="lesson-back" to={base}>
                            <ArrowLeft size={14} aria-hidden="true" />
                            <span>Back to classroom</span>
                          </Link>
                          <span className="text-slate-300" aria-hidden="true">/</span>
                          <p className="lesson-eyebrow">{classroom.data.name} · Learning space</p>
                        </div>
                        <h1>Class lessons<span className="lesson-heading-dot">.</span></h1>
                        <p className="lesson-muted">{role === 'TEACHER' ? 'Structure drafts, published lessons, and class materials in one workspace.' : 'Follow lessons in sequence, track reading progress, and continue where you left off.'}</p>
                      </div>
                      {role === 'TEACHER' && classroom.data.status === 'ACTIVE' && (
                        <Link className="lesson-button shrink-0" to="new">
                          <Plus size={16} aria-hidden="true" />
                          <span>Create lesson</span>
                        </Link>
                      )}
                    </header>
                  )}
                  {classroom.data.status === 'ARCHIVED' && <p className="lesson-alert" role="status">This classroom is archived. Lessons are read-only.</p>}
                  {!lessonId ? (
                    <LessonList
                      key={id}
                      classId={id}
                      classroomBase={base}
                      classroomName={classroom.data.name}
                      userId={effectiveUserId}
                      role={role}
                      writable={classroom.data.status === 'ACTIVE'}
                    />
                  ) : lessonId === 'new' ? role === 'TEACHER' && classroom.data.status === 'ACTIVE' ? <LessonDetail key="new" classId={id} classroomName={classroom.data.name} userId={effectiveUserId} role={role} writable create /> : <LessonErrorState error={new Error('You cannot create lessons in this classroom.')} />
                    : <LessonDetail key={lessonId} classId={id} classroomName={classroom.data.name} userId={effectiveUserId} role={role} lessonId={lessonId} writable={classroom.data.status === 'ACTIVE'} />}
                </>
        )}
    </div>
  </ClassroomLayout>;
}

type LessonFilter = 'ALL' | 'TODO' | 'COMPLETED' | 'PUBLISHED' | 'DRAFT' | 'ARCHIVED';

function LessonList({
  classId,
  classroomBase,
  classroomName,
  userId,
  role,
  writable,
}: {
  classId: string;
  classroomBase: string;
  classroomName: string;
  userId: string;
  role: LessonRole;
  writable: boolean;
}) {
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<LessonFilter>('ALL');
  const [search, setSearch] = useState('');

  const query = useQuery({
    queryKey: lessonKeys.list(userId, classId, page),
    queryFn: ({ signal }) => IS_PROTOTYPE_DEMO
      ? Promise.resolve(lessonMockStore.list(classId, role, userId, page))
      : lessonApi.list(classId, page, signal),
    retry: false,
    staleTime: 0,
    refetchInterval: !IS_PROTOTYPE_DEMO && role === 'STUDENT' ? 15_000 : false,
  });

  const items = visibleLessons(query.data?.items || [], role);
  const publishedItems = items.filter(item => item.status === 'PUBLISHED');
  const completedCount = publishedItems.filter(item => Boolean(item.progress?.completed)).length;
  const remainingItems = publishedItems.filter(item => !item.progress?.completed);
  const nextLesson = remainingItems[0] || null;
  const coursePercent = publishedItems.length > 0 ? Math.round((completedCount / publishedItems.length) * 100) : 0;
  const remainingMinutes = remainingItems.reduce((sum, item) => sum + estimateReadingMinutes(item), 0);
  const draftCount = items.filter(item => item.status === 'DRAFT').length;
  const archivedCount = items.filter(item => item.status === 'ARCHIVED').length;

  const filteredItems = useMemo(() => {
    const q = search.trim().toLowerCase();
    return items.filter(lesson => {
      if (filter === 'TODO' && lesson.progress?.completed) return false;
      if (filter === 'COMPLETED' && !lesson.progress?.completed) return false;
      if (filter === 'PUBLISHED' && lesson.status !== 'PUBLISHED') return false;
      if (filter === 'DRAFT' && lesson.status !== 'DRAFT') return false;
      if (filter === 'ARCHIVED' && lesson.status !== 'ARCHIVED') return false;
      if (!q) return true;
      return lesson.title.toLowerCase().includes(q);
    });
  }, [items, filter, search]);

  const filterOptions: Array<{ id: LessonFilter; label: string; count: number }> =
    role === 'STUDENT'
      ? [
          { id: 'ALL', label: 'All lessons', count: items.length },
          { id: 'TODO', label: 'Remaining', count: remainingItems.length },
          { id: 'COMPLETED', label: 'Completed', count: completedCount },
        ]
      : [
          { id: 'ALL', label: 'All', count: items.length },
          { id: 'PUBLISHED', label: 'Published', count: publishedItems.length },
          { id: 'DRAFT', label: 'Drafts', count: draftCount },
          { id: 'ARCHIVED', label: 'Archived', count: archivedCount },
        ];

  return <>
    {query.isPending ? <div className="lesson-panel lesson-empty" role="status">Loading lessons…</div> : query.isError ? <LessonErrorState error={query.error} retry={() => void query.refetch()} /> : <>
      {items.length === 0 ? (
        <section className="lesson-panel lesson-empty">
          <BookOpen size={32} strokeWidth={1.4} aria-hidden="true" />
          <h2>{role === 'TEACHER' ? 'A good lesson starts with an idea.' : 'Your next lesson is on its way.'}</h2>
          <p className="lesson-muted">{role === 'TEACHER' ? 'Create a draft, add your notes and publish when it is ready.' : 'Published lessons will appear here when your teacher shares them.'}</p>
          {role === 'TEACHER' && writable && <Link className="lesson-button" to="new"><Plus size={16} aria-hidden="true" />Create first lesson</Link>}
        </section>
      ) : (
        <div className="lesson-list-layout">
          {/* Left Primary Column: Curriculum Filter + Lesson Cards */}
          <div className="lesson-list-main">
            <div className="lesson-library-bar">
              <div>
                <h2>Your curriculum</h2>
                <p className="lesson-muted">{role === 'TEACHER' ? 'Drafts, published lessons and your archive.' : 'Published lessons from your teacher.'}</p>
              </div>
            </div>

            <div className="lesson-filter-bar">
              <div className="lesson-filter-tabs" role="tablist" aria-label="Filter lessons">
                {filterOptions.map(option => (
                  <button
                    key={option.id}
                    type="button"
                    role="tab"
                    aria-selected={filter === option.id}
                    data-active={filter === option.id}
                    className="lesson-filter-pill"
                    onClick={() => setFilter(option.id)}
                  >
                    <span>{option.label}</span>
                    <span className="lesson-filter-count">{option.count}</span>
                  </button>
                ))}
              </div>

              <div className="lesson-search">
                <Search size={14} className="lesson-search-icon" aria-hidden="true" />
                <input
                  type="search"
                  value={search}
                  onChange={e => setSearch(e.target.value)}
                  placeholder="Search lessons..."
                  aria-label="Search curriculum lessons"
                />
              </div>
            </div>

            {filteredItems.length === 0 ? (
              <div className="lesson-panel text-center py-10 space-y-3">
                <p className="text-sm font-semibold text-slate-900">No matching lessons found</p>
                <p className="lesson-muted">Adjust the status filter or search query to see all lessons.</p>
                <button
                  type="button"
                  className="lesson-button secondary"
                  onClick={() => { setFilter('ALL'); setSearch(''); }}
                >
                  Clear filters
                </button>
              </div>
            ) : (
              <ol className="lesson-list">
                {filteredItems.map((lesson) => {
                  const originalIdx = items.findIndex(item => item.id === lesson.id);
                  const completed = role === 'STUDENT' && Boolean(lesson.progress?.completed);
                  const readMinutes = estimateReadingMinutes(lesson);

                  return (
                    <li key={lesson.id}>
                      <Link
                        to={encodeURIComponent(lesson.id)}
                        className="lesson-list-item"
                        data-completed={completed}
                        data-status={lesson.status}
                      >
                        <span className="lesson-number">{String(page * 20 + originalIdx + 1).padStart(2, '0')}</span>
                        <div className="lesson-list-copy">
                          <div className="lesson-item-meta">
                            <StatusBadge status={lesson.status} />
                            {completed && <span className="lesson-completed"><CheckCircle2 size={13} aria-hidden="true" />Completed</span>}
                            <span className="lesson-duration">
                              <Clock size={12} aria-hidden="true" />
                              ~{readMinutes} min read
                            </span>
                          </div>
                          <h3>{lesson.title}</h3>
                          <p>{lesson.completionMode === 'MANUAL' ? 'Mark complete when you are ready' : 'Complete linked assignments or quizzes'} · {lesson.attachments.length} materials</p>
                          <small>Updated {lessonDate(lesson.updatedAt)} · UTC+7</small>
                        </div>
                        <span className="lesson-list-cta" aria-hidden="true">
                          {role === 'TEACHER' ? 'Manage' : completed ? 'Review' : 'Open lesson'}
                          <ArrowUpRight size={15} />
                        </span>
                      </Link>
                    </li>
                  );
                })}
              </ol>
            )}
            <LessonPagination page={page} totalPages={query.data?.pagination.totalPages || 0} onPage={setPage} />
          </div>

          {/* Right Sticky Context Rail: Progress / Up Next / Class Navigation */}
          <aside className="lesson-list-rail" aria-label="Curriculum overview">
            {role === 'STUDENT' ? (
              <div className="lesson-rail-card" data-completed={publishedItems.length > 0 && completedCount === publishedItems.length}>
                <div className="lesson-rail-head">
                  <span className="lesson-rail-eyebrow">COURSE PROGRESS</span>
                  <span className="lesson-rail-pct">{coursePercent}%</span>
                </div>
                <div className="lesson-meter-track" role="progressbar" aria-label="Course completion" aria-valuemin={0} aria-valuemax={100} aria-valuenow={coursePercent}>
                  <div className="lesson-meter-fill" style={{ width: `${coursePercent}%` }} />
                </div>
                <p className="lesson-rail-meta">
                  {completedCount} of {publishedItems.length} published lessons completed
                  {remainingMinutes > 0 ? ` · ~${remainingMinutes} min remaining` : ''}
                </p>

                {nextLesson ? (
                  <div className="mt-4 pt-4 border-t border-slate-200 space-y-2.5">
                    <span className="lesson-rail-eyebrow block">UP NEXT</span>
                    <p className="text-sm font-bold text-slate-900 leading-snug">{nextLesson.title}</p>
                    <Link to={encodeURIComponent(nextLesson.id)} className="lesson-button w-full">
                      <span>Continue next lesson</span>
                      <ArrowRight size={15} aria-hidden="true" />
                    </Link>
                  </div>
                ) : publishedItems.length > 0 ? (
                  <div className="mt-4 pt-4 border-t border-slate-200 space-y-2.5">
                    <p className="lesson-completed"><CheckCircle2 size={14} aria-hidden="true" />All lessons completed</p>
                    <Link to={encodeURIComponent(publishedItems[0].id)} className="lesson-button secondary w-full">
                      <span>Review from lesson 01</span>
                    </Link>
                  </div>
                ) : null}
              </div>
            ) : (
              <div className="lesson-rail-card space-y-3">
                <div className="lesson-rail-head">
                  <span className="lesson-rail-eyebrow">CURRICULUM STATUS</span>
                  <span className="text-xs font-mono font-bold text-slate-700">{items.length} total</span>
                </div>
                <div className="grid grid-cols-3 gap-2 text-center">
                  <div className="p-2.5 rounded-lg bg-emerald-50 border border-emerald-200">
                    <strong className="block text-base font-extrabold text-emerald-800">{publishedItems.length}</strong>
                    <span className="text-[11px] font-semibold text-emerald-700">Published</span>
                  </div>
                  <div className="p-2.5 rounded-lg bg-amber-50 border border-amber-200">
                    <strong className="block text-base font-extrabold text-amber-800">{draftCount}</strong>
                    <span className="text-[11px] font-semibold text-amber-700">Drafts</span>
                  </div>
                  <div className="p-2.5 rounded-lg bg-slate-100 border border-slate-200">
                    <strong className="block text-base font-extrabold text-slate-700">{archivedCount}</strong>
                    <span className="text-[11px] font-semibold text-slate-600">Archived</span>
                  </div>
                </div>
                {writable && (
                  <Link className="lesson-button w-full" to="new">
                    <Plus size={15} aria-hidden="true" />
                    <span>Create new lesson</span>
                  </Link>
                )}
              </div>
            )}

            <div className="lesson-rail-card space-y-2.5">
              <span className="lesson-rail-eyebrow block">CLASSROOM SHORTCUTS</span>
              <p className="text-xs font-semibold text-slate-800 truncate">{classroomName}</p>
              <div className="flex flex-col gap-1.5 pt-1">
                <Link to={classroomBase} className="lesson-rail-shortcut">
                  <span>Classroom overview</span>
                  <ArrowUpRight size={14} aria-hidden="true" />
                </Link>
                <Link to={`${classroomBase}?tab=announcements`} className="lesson-rail-shortcut">
                  <span>Announcements</span>
                  <ArrowUpRight size={14} aria-hidden="true" />
                </Link>
                {role === 'TEACHER' && (
                  <Link to={`${classroomBase}?tab=members`} className="lesson-rail-shortcut">
                    <span>Members &amp; access</span>
                    <ArrowUpRight size={14} aria-hidden="true" />
                  </Link>
                )}
              </div>
            </div>
          </aside>
        </div>
      )}
    </>}
  </>;
}

const lifecycle = {
  publish: { title: 'Publish lesson?', message: 'Students in this classroom will be able to read this lesson and its available materials.' },
  draft: { title: 'Move lesson to draft?', message: 'This hides the lesson from students. Previously saved progress and learning history are preserved.' },
  archive: { title: 'Archive lesson?', message: 'The lesson and affected linked activities become unavailable to students. Learning history is preserved.' },
  restore: { title: 'Restore lesson?', message: 'The server restores the lesson and linked items to their states before archiving. Progress is preserved.' },
  delete: { title: 'Delete lesson permanently?', message: 'This cannot be undone. The server allows deletion only when no learning data or linked activities need to be preserved.' },
};
type Action = keyof typeof lifecycle;

function LessonDetail({
  classId,
  classroomName,
  userId,
  role,
  lessonId = '',
  writable,
  create = false,
}: {
  classId: string;
  classroomName: string;
  userId: string;
  role: LessonRole;
  lessonId?: string;
  writable: boolean;
  create?: boolean;
}) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const classroomBase = `/${role.toLowerCase()}/classrooms/${encodeURIComponent(classId)}`;
  const base = `${classroomBase}/lessons`;
  const [editing, setEditing] = useState(create);
  const [action, setAction] = useState<Action | null>(null);
  const [notice, setNotice] = useState('');
  const readingRef = useRef<HTMLElement>(null);
  const [readPercent, setReadPercent] = useState<number>(() =>
    create || !lessonId ? 0 : readSavedLessonProgress(userId, lessonId),
  );
  const [activeSectionId, setActiveSectionId] = useState<string>('');

  const listQuery = useQuery({
    queryKey: lessonKeys.list(userId, classId, 0),
    queryFn: ({ signal }) => IS_PROTOTYPE_DEMO
      ? Promise.resolve(lessonMockStore.list(classId, role, userId, 0))
      : lessonApi.list(classId, 0, signal),
    enabled: !create,
    retry: false,
    staleTime: 0,
  });
  const orderedLessons = visibleLessons(listQuery.data?.items || [], role);
  const currentIdx = orderedLessons.findIndex(item => item.id === lessonId);
  const prevLesson = currentIdx > 0 ? orderedLessons[currentIdx - 1] : null;
  const nextLesson = currentIdx >= 0 && currentIdx + 1 < orderedLessons.length ? orderedLessons[currentIdx + 1] : null;

  const detail = useQuery({
    queryKey: lessonKeys.detail(userId, classId, lessonId),
    queryFn: ({ signal }) => IS_PROTOTYPE_DEMO
      ? Promise.resolve(lessonMockStore.get(classId, lessonId, role, userId))
      : lessonApi.get(classId, lessonId, signal),
    enabled: !create,
    retry: false,
    staleTime: 0,
    refetchInterval: !IS_PROTOTYPE_DEMO && role === 'STUDENT' ? 15_000 : false,
  });
  const policy = useQuery({
    queryKey: lessonKeys.capabilities(userId, classId),
    queryFn: () => IS_PROTOTYPE_DEMO ? Promise.resolve(lessonMockStore.getCapabilities()) : lessonApi.capabilities(classId),
    enabled: role === 'TEACHER',
    retry: false,
  });
  const options = useQuery({
    queryKey: lessonKeys.activities(userId, classId),
    queryFn: () => IS_PROTOTYPE_DEMO ? Promise.resolve(lessonMockStore.getActivities(classId)) : lessonApi.activities(classId),
    enabled: role === 'TEACHER' && editing && policy.data?.activityLinkingAvailable === true,
    retry: false,
  });
  const mutation = useMutation({ mutationFn: async (operation: () => Promise<void>) => operation(), onError: () => { if (!create) void client.invalidateQueries({ queryKey: lessonKeys.detail(userId, classId, lessonId) }); } });
  const refresh = async () => { await client.invalidateQueries({ queryKey: lessonKeys.root(userId, classId) }); };
  const lesson = detail.data;
  const editable = role === 'TEACHER' && writable && lesson?.status !== 'ARCHIVED';

  const outline = useMemo(
    () => (lesson ? extractLessonOutline(lesson, true) : []),
    [lesson],
  );
  const totalMinutes = useMemo(
    () => (lesson ? estimateReadingMinutes(lesson) : 1),
    [lesson],
  );
  const isCompleted = Boolean(lesson?.progress?.completed);
  const displayPercent = isCompleted ? 100 : readPercent;
  const remainingMinutes = displayPercent >= 95 ? 0 : Math.max(1, Math.ceil(totalMinutes * (1 - displayPercent / 100)));
  const activeOutlineIndex = Math.max(0, outline.findIndex(item => item.id === activeSectionId));

  useEffect(() => {
    if (create || editing || !lesson) return;
    if (lesson.progress?.completed) {
      setReadPercent(100);
      saveLessonReadProgress(userId, lesson.id, 100);
    }

    const measure = () => {
      const container = readingRef.current;
      if (!container) return;
      const rect = container.getBoundingClientRect();
      const vh = window.innerHeight;
      let computed = 0;

      if (rect.bottom <= vh - 32) {
        computed = 100;
      } else {
        const scrollableSpan = Math.max(rect.height - vh * 0.45, 1);
        const passed = Math.max(0, vh * 0.55 - rect.top);
        computed = Math.min(100, Math.round((passed / scrollableSpan) * 100));
      }

      setReadPercent(prev => {
        const next = Math.max(prev, computed);
        if (next > prev) {
          saveLessonReadProgress(userId, lesson.id, next);
        }
        return next;
      });

      let currentSection = outline[0]?.id || '';
      for (const item of outline) {
        const target = document.getElementById(item.id);
        if (target && target.getBoundingClientRect().top <= vh * 0.35) {
          currentSection = item.id;
        }
      }
      if (currentSection) {
        setActiveSectionId(currentSection);
      }
    };

    const raf = requestAnimationFrame(measure);
    window.addEventListener('scroll', measure, { passive: true });
    window.addEventListener('resize', measure, { passive: true });
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener('scroll', measure);
      window.removeEventListener('resize', measure);
    };
  }, [create, editing, lesson, outline, userId]);

  async function save(input: LessonInput) {
    await mutation.mutateAsync(async () => {
      const saved = IS_PROTOTYPE_DEMO
        ? (create ? lessonMockStore.create(classId, input, userId) : lessonMockStore.update(classId, lessonId, input, userId))
        : (create ? await lessonApi.create(classId, input) : await lessonApi.update(classId, lessonId, input));
      await refresh(); setEditing(false); setNotice('Lesson saved.');
      if (create) navigate(`${base}/${encodeURIComponent(saved.id)}`, { replace: true });
    });
  }
  async function transition() {
    if (!action) return;
    setNotice('');
    try {
      await mutation.mutateAsync(async () => {
        if (IS_PROTOTYPE_DEMO) {
          if (action === 'delete') lessonMockStore.remove(classId, lessonId);
          else lessonMockStore.transition(classId, lessonId, action, userId);
        } else {
          if (action === 'delete') await lessonApi.remove(classId, lessonId);
          else await lessonApi.transition(classId, lessonId, action);
        }
        await refresh(); setAction(null); setNotice('Lesson updated.');
        if (action === 'delete') navigate(base, { replace: true });
      });
    } catch { setAction(null); }
  }
  async function complete() {
    setNotice('');
    try { await mutation.mutateAsync(async () => {
      const saved = IS_PROTOTYPE_DEMO
        ? lessonMockStore.complete(classId, lessonId, userId)
        : await lessonApi.complete(classId, lessonId);
      if (!saved.progress?.completed) throw new Error('The server did not confirm completion. Refresh and try again.');
      saveLessonReadProgress(userId, lessonId, 100);
      setReadPercent(100);
      await refresh(); setNotice('Your completion has been saved.');
    }); } catch { /* Mutation feedback is rendered below. */ }
  }

  if (!create && detail.isPending) return <p role="status">Loading lesson…</p>;
  if (!create && detail.isError) return <LessonErrorState error={detail.error} retry={() => void detail.refetch()} />;
  if (!create && (!lesson || lesson.classroomId !== classId || (role === 'STUDENT' && lesson.status !== 'PUBLISHED'))) return <LessonErrorState error={new Error('This lesson is not available to you.')} />;
  const activities = [...(options.data || []), ...(lesson?.activities || []).filter(activity => !options.data?.some(option => option.id === activity.id))];

  const compactBar = (
    <div className="lesson-compact-bar">
      <div className="flex flex-wrap items-center gap-3 min-w-0">
        <Link className="lesson-back" to={base}>
          <ArrowLeft size={15} aria-hidden="true" />
          <span>All lessons</span>
        </Link>
        <span className="text-slate-300" aria-hidden="true">·</span>
        <Link to={classroomBase} className="text-xs font-medium text-slate-600 hover:text-slate-900 hover:underline truncate max-w-[260px]">
          {classroomName}
        </Link>
      </div>
      {!create && currentIdx >= 0 && (
        <div className="lesson-top-nav">
          {prevLesson ? (
            <Link to={`${base}/${encodeURIComponent(prevLesson.id)}`} className="lesson-top-nav-btn" aria-label={`Previous lesson: ${prevLesson.title}`}>
              <ArrowLeft size={13} aria-hidden="true" />
              <span>Prev</span>
            </Link>
          ) : (
            <span className="lesson-top-nav-btn" aria-disabled="true">
              <ArrowLeft size={13} aria-hidden="true" />
              <span>Prev</span>
            </span>
          )}
          <span className="text-xs font-mono font-semibold text-slate-600 px-1">
            Lesson {currentIdx + 1} of {orderedLessons.length}
          </span>
          {nextLesson ? (
            <Link to={`${base}/${encodeURIComponent(nextLesson.id)}`} className="lesson-top-nav-btn" aria-label={`Next lesson: ${nextLesson.title}`}>
              <span>Next</span>
              <ArrowRight size={13} aria-hidden="true" />
            </Link>
          ) : (
            <span className="lesson-top-nav-btn" aria-disabled="true">
              <span>Next</span>
              <ArrowRight size={13} aria-hidden="true" />
            </span>
          )}
        </div>
      )}
    </div>
  );

  return <>
    {compactBar}
    {mutation.isError && <p className="lesson-alert" role="alert">{lessonError(mutation.error)}</p>}
    {notice && <p className="lesson-notice" role="status">{notice}</p>}
    {role === 'TEACHER' && (policy.isError || options.isError) && <div className="lesson-dependency" role="status"><p>File uploads or activity linking are currently unavailable. You can continue editing lesson content.</p><button className="lesson-button text" type="button" onClick={() => { void policy.refetch(); if (policy.data?.activityLinkingAvailable) void options.refetch(); }}>Retry available services</button></div>}
    {editing && (create || editable) ? <LessonEditor lesson={lesson} activities={activities} activityLinkingAvailable={policy.data?.activityLinkingAvailable === true && options.isSuccess} pending={mutation.isPending} onSave={save} onCancel={() => create ? navigate(base) : setEditing(false)} /> : lesson && (
      <div className="lesson-detail-layout">
        <div className="lesson-detail-main">
          <section ref={readingRef} className="lesson-panel lesson-reading">
            <div className="lesson-card-meter" aria-hidden="true">
              <span style={{ width: `${displayPercent}%` }} />
            </div>
            <div className="lesson-section-head">
              <div className="flex items-center gap-2.5 flex-wrap">
                <StatusBadge status={lesson.status} />
                <span className="inline-flex items-center gap-1 text-xs font-medium text-slate-500">
                  <Clock size={13} aria-hidden="true" />
                  ~{totalMinutes} min read
                </span>
              </div>
              <span className="lesson-muted">Updated {lessonDate(lesson.updatedAt)} · UTC+7</span>
            </div>
            <h2 className="lesson-title">{lesson.title}</h2>
            {role === 'TEACHER' && writable && <div className="lesson-actions lesson-lifecycle">
              {editable && <button className="lesson-button secondary" type="button" disabled={mutation.isPending} onClick={() => { mutation.reset(); setNotice(''); setEditing(true); }}>Edit lesson</button>}
              {lesson.status === 'DRAFT' && <button className="lesson-button" type="button" disabled={mutation.isPending} onClick={() => setAction('publish')}>Publish lesson</button>}
              {lesson.status === 'PUBLISHED' && <button className="lesson-button secondary" type="button" disabled={mutation.isPending} onClick={() => setAction('draft')}>Move to draft</button>}
              {lesson.status === 'ARCHIVED' ? <button className="lesson-button" type="button" disabled={mutation.isPending} onClick={() => setAction('restore')}>Restore lesson</button> : <button className="lesson-button secondary" type="button" disabled={mutation.isPending} onClick={() => setAction('archive')}>Archive lesson</button>}
              <button className="lesson-button text danger" type="button" disabled={mutation.isPending || !lesson.canDelete} aria-describedby={!lesson.canDelete ? 'lesson-delete-reason' : undefined} onClick={() => setAction('delete')}>Delete lesson</button>
              {!lesson.canDelete && <p id="lesson-delete-reason" className="lesson-muted">This lesson has related learning data. Archive it to preserve history.</p>}
            </div>}
            <LessonContent lesson={lesson} />
          </section>

          <div id="lesson-materials-completion" className="lesson-support-grid">
            <LessonAttachments lesson={lesson} editable={editable} policy={policy.data || null} onChanged={refresh} />
            <section className="lesson-panel"><h2><FileText size={18} aria-hidden="true" />Linked activities</h2>
              {lesson.activities.filter(activity => role === 'TEACHER' || activity.available).length === 0 && <p className="lesson-muted">No available activities linked yet.</p>}
              <ul className="lesson-activity-list">{lesson.activities.filter(activity => role === 'TEACHER' || activity.available).map(activity => {
                const path = activity.available ? activityPath(activity.path, classId, role) : null;
                return <li key={activity.id}><div><small>{activity.type.toLowerCase()}{lesson.requiredActivityIds.includes(activity.id) ? ' · Required for completion' : ''}</small><strong>{activity.title}</strong></div>{path ? <Link className="lesson-button text" to={path}>Open <ArrowUpRight size={15} aria-hidden="true" /><span className="sr-only">{activity.title}</span></Link> : <span className="lesson-muted">Unavailable</span>}</li>;
              })}</ul>
              <div className="lesson-completion"><h3>{role === 'TEACHER' ? 'Completion mode' : 'Your completion'}</h3><p>{lesson.completionMode === 'MANUAL' ? 'Students mark this lesson as completed.' : 'Completion depends on the configured requirements of linked assignments or quizzes.'}</p>
                {role === 'STUDENT' && (lesson.progress?.completed ? <p className="lesson-completed"><CheckCircle2 size={17} aria-hidden="true" />Completed · {lessonDate(lesson.progress.completedAt)} · UTC+7</p>
                  : lesson.completionMode === 'MANUAL' ? <button className="lesson-button" type="button" disabled={!writable || !canCompleteLesson(lesson) || mutation.isPending} onClick={() => void complete()}>Mark as completed</button>
                  : <><p className="lesson-muted">Not completed yet. Return here after completing the required activities.</p><button className="lesson-button secondary" type="button" disabled={detail.isFetching} onClick={() => void detail.refetch()}>Refresh completion</button></>)}
              </div>
            </section>
          </div>

          {role === 'TEACHER' && <LessonProgress userId={userId} classId={classId} lessonId={lesson.id} />}

          {(prevLesson || nextLesson) && (
            <nav className="mt-6 flex flex-wrap items-center justify-between gap-3 pt-5 border-t border-slate-200" aria-label="Lesson sequence">
              {prevLesson ? (
                <Link
                  to={`${base}/${encodeURIComponent(prevLesson.id)}`}
                  className="lesson-button secondary"
                >
                  <ArrowLeft size={15} aria-hidden="true" />
                  <span>Previous: {prevLesson.title}</span>
                </Link>
              ) : (
                <span />
              )}
              {nextLesson && (
                <Link
                  to={`${base}/${encodeURIComponent(nextLesson.id)}`}
                  className="lesson-button"
                >
                  <span>Next: {nextLesson.title}</span>
                  <ArrowRight size={15} aria-hidden="true" />
                </Link>
              )}
            </nav>
          )}
        </div>

        <aside className="lesson-rail" aria-label="Lesson reading progress and outline">
          <div className="lesson-console" data-completed={isCompleted}>
            {/* 1. Dashboard-Style Constellation Progress Head */}
            <div className="lesson-console-head">
              <InteractiveCourseBackground />
              <div className="lesson-console-head-inner">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <span className="lesson-console-eyebrow">
                      READ · <span aria-live="polite">{displayPercent}%</span>
                    </span>
                    <div className="mt-1.5 flex items-center gap-2 flex-wrap">
                      {isCompleted ? (
                        <span className="lesson-console-badge is-done">
                          <CheckCircle2 size={12} aria-hidden="true" />
                          <span>Completed</span>
                        </span>
                      ) : lesson.completionMode === 'CONDITION_BASED' ? (
                        <span className="lesson-console-badge is-quiz">Quiz required</span>
                      ) : (
                        <span className="lesson-console-badge">Self-paced</span>
                      )}
                    </div>
                  </div>

                  <div className="lesson-ring" aria-hidden="true">
                    <svg width="52" height="52" viewBox="0 0 52 52">
                      <circle cx="26" cy="26" r="21" className="lesson-ring-track" />
                      <circle
                        cx="26"
                        cy="26"
                        r="21"
                        className="lesson-ring-fill"
                        strokeDasharray={131.95}
                        strokeDashoffset={131.95 * (1 - displayPercent / 100)}
                      />
                    </svg>
                    <span>{displayPercent}%</span>
                  </div>
                </div>

                <div
                  className="lesson-console-meter"
                  role="progressbar"
                  aria-label="Lesson reading progress"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={displayPercent}
                >
                  <div className="lesson-console-meter-fill" style={{ width: `${displayPercent}%` }} />
                </div>

                <p className="lesson-console-meta">
                  {displayPercent >= 100
                    ? isCompleted
                      ? 'Reached end of lesson.'
                      : lesson.completionMode === 'CONDITION_BASED'
                        ? 'Finished reading · Complete linked activity below.'
                        : 'Reached the end of this lesson.'
                    : `~${remainingMinutes} min left · ${100 - displayPercent}% remaining`}
                </p>

                {role === 'STUDENT' && !isCompleted && lesson.completionMode === 'MANUAL' && (
                  <button
                    type="button"
                    className="lesson-console-cta"
                    disabled={!writable || !canCompleteLesson(lesson) || mutation.isPending}
                    onClick={() => void complete()}
                  >
                    Complete this lesson
                  </button>
                )}
              </div>
            </div>

            {/* 2. Vertical Timeline Outline */}
            {outline.length > 0 && (
              <div className="lesson-console-section">
                <div className="lesson-rail-head">
                  <span className="lesson-rail-eyebrow">ON THIS PAGE</span>
                  <span className="text-[11px] font-mono text-slate-500">
                    {activeOutlineIndex + 1} / {outline.length}
                  </span>
                </div>
                <nav aria-label="Lesson outline">
                  <ul className="lesson-timeline-outline">
                    {outline.map((item, idx) => {
                      const isActive = activeSectionId === item.id || (!activeSectionId && idx === 0);
                      const state = isActive ? 'active' : idx < activeOutlineIndex ? 'past' : 'upcoming';
                      return (
                        <li key={item.id}>
                          <a
                            href={`#${item.id}`}
                            className="lesson-outline-node"
                            data-level={item.level}
                            data-active={isActive}
                            data-state={state}
                            onClick={event => {
                              event.preventDefault();
                              setActiveSectionId(item.id);
                              document.getElementById(item.id)?.scrollIntoView({
                                behavior: 'smooth',
                                block: 'start',
                              });
                            }}
                          >
                            {item.label}
                          </a>
                        </li>
                      );
                    })}
                  </ul>
                </nav>
              </div>
            )}

            {/* 3. Stepper Course Lessons Switcher */}
            {orderedLessons.length > 0 && (
              <div className="lesson-console-section">
                <div className="lesson-rail-head">
                  <span className="lesson-rail-eyebrow">COURSE LESSONS</span>
                  <span className="text-[11px] font-mono text-slate-500">
                    {orderedLessons.length}
                  </span>
                </div>
                <nav aria-label="Course lessons">
                  <ul className="lesson-stepper-list">
                    {orderedLessons.map((item, idx) => {
                      const isCurrent = item.id === lesson.id;
                      const done = role === 'STUDENT' && Boolean(item.progress?.completed);
                      return (
                        <li key={item.id}>
                          <Link
                            to={`${base}/${encodeURIComponent(item.id)}`}
                            className="lesson-stepper-link"
                            data-current={isCurrent}
                            data-done={done}
                          >
                            <span className="lesson-stepper-badge">
                              {done && !isCurrent ? (
                                <CheckCircle2 size={13} aria-hidden="true" />
                              ) : (
                                String(idx + 1).padStart(2, '0')
                              )}
                            </span>
                            <span className="lesson-mini-title">{item.title}</span>
                            {isCurrent && <span className="lesson-stepper-tag">Active</span>}
                          </Link>
                        </li>
                      );
                    })}
                  </ul>
                </nav>
              </div>
            )}
          </div>
        </aside>
      </div>
    )}
    {action && <LessonConfirm {...lifecycle[action]} pending={mutation.isPending} onConfirm={() => void transition()} onCancel={() => setAction(null)} />}
  </>;
}

function StatusBadge({ status }: { status: Lesson['status'] }) {
  return <span className={`lesson-status ${status.toLowerCase()}`}>{status === 'DRAFT' ? 'Draft' : status === 'PUBLISHED' ? 'Published' : 'Archived'}</span>;
}
