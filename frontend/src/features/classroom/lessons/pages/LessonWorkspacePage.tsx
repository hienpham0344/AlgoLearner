import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, ArrowUpRight, BookOpen, CheckCircle2, ChevronRight, FileText, Plus } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { ClassroomLayout } from '../../components/ClassroomLayout';
import { classroomApi } from '../../api/classroomApi';
import { readStoredPersona } from '../../mock/useClassroomStore';
import type { Classroom } from '../../types';
import { lessonApi, lessonError, lessonKeys } from '../api/lessonApi';
import { getSynchronizedClassroom, lessonMockStore } from '../mock/lessonMockStore';
import { activityPath, canCompleteLesson, lessonDate, visibleLessons } from '../lessonRules';
import type { Lesson, LessonInput, LessonRole } from '../types';
import { LessonEditor } from '../components/LessonEditor';
import { LessonContent } from '../components/LessonContent';
import { LessonAttachments } from '../components/LessonAttachments';
import { LessonProgress } from '../components/LessonProgress';
import { LessonConfirm, LessonErrorState, LessonPagination } from '../components/LessonState';
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
    <span className="inline-flex items-center gap-1.5 min-w-0">
      <Link to={base} className="hover:underline font-medium text-slate-600 truncate max-w-[180px]">
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
      <Link className="lesson-back" to={base}><ArrowLeft size={15} aria-hidden="true" />Back to classroom</Link>
      {classroom.isPending ? <p role="status">Loading classroom…</p> : classroom.isError ? <LessonErrorState error={classroom.error} retry={() => void classroom.refetch()} />
        : classroom.data && (
          role === 'TEACHER' && classroom.data.ownerId !== effectiveUserId
            ? <LessonErrorState error={new Error('Only the classroom owner can manage these lessons.')} />
            : !isMember
              ? <LessonErrorState error={new Error('You must be an active member of this classroom to access its lessons.')} />
              : <>
                  <header className="lesson-page-header"><div><p className="lesson-eyebrow">{classroom.data.name} / Learning space</p><h1>Class lessons<span className="lesson-heading-dot">.</span></h1><p className="lesson-muted">{role === 'TEACHER' ? 'Turn the next concept into a clear path forward.' : 'Read, explore, and put the next idea into practice.'}</p></div><BookOpen size={36} strokeWidth={1.3} aria-hidden="true" /></header>
                  {classroom.data.status === 'ARCHIVED' && <p className="lesson-alert" role="status">This classroom is archived. Lessons are read-only.</p>}
                  {!lessonId ? <LessonList key={id} classId={id} userId={effectiveUserId} role={role} writable={classroom.data.status === 'ACTIVE'} />
                    : lessonId === 'new' ? role === 'TEACHER' && classroom.data.status === 'ACTIVE' ? <LessonDetail key="new" classId={id} userId={effectiveUserId} role={role} writable create /> : <LessonErrorState error={new Error('You cannot create lessons in this classroom.')} />
                    : <LessonDetail key={lessonId} classId={id} userId={effectiveUserId} role={role} lessonId={lessonId} writable={classroom.data.status === 'ACTIVE'} />}
                </>
        )}
    </div>
  </ClassroomLayout>;
}

function LessonList({ classId, userId, role, writable }: { classId: string; userId: string; role: LessonRole; writable: boolean }) {
  const [page, setPage] = useState(0);
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
  return <>
    <div className="lesson-library-bar"><div><h2>Your curriculum</h2><p className="lesson-muted">{role === 'TEACHER' ? 'Drafts, published lessons and your archive.' : 'Published lessons from your teacher.'}</p></div>{role === 'TEACHER' && writable && <Link className="lesson-button" to="new"><Plus size={16} aria-hidden="true" />Create lesson</Link>}</div>
    {query.isPending ? <div className="lesson-panel lesson-empty" role="status">Loading lessons…</div> : query.isError ? <LessonErrorState error={query.error} retry={() => void query.refetch()} /> : <>
      {items.length === 0 ? <section className="lesson-panel lesson-empty"><BookOpen size={32} strokeWidth={1.4} aria-hidden="true" /><h2>{role === 'TEACHER' ? 'A good lesson starts with an idea.' : 'Your next lesson is on its way.'}</h2><p className="lesson-muted">{role === 'TEACHER' ? 'Create a draft, add your notes and publish when it is ready.' : 'Published lessons will appear here when your teacher shares them.'}</p></section>
        : <ol className="lesson-list">{items.map((lesson, index) => <li key={lesson.id}><Link to={encodeURIComponent(lesson.id)} className="lesson-list-item">
          <span className="lesson-number">{String(page * 20 + index + 1).padStart(2, '0')}</span><div className="lesson-list-copy"><div className="lesson-item-meta"><StatusBadge status={lesson.status} />{role === 'STUDENT' && lesson.progress?.completed && <span className="lesson-completed"><CheckCircle2 size={13} aria-hidden="true" />Completed</span>}</div><h3>{lesson.title}</h3><p>{lesson.completionMode === 'MANUAL' ? 'Mark complete when you are ready' : 'Complete linked assignments or quizzes'} · {lesson.attachments.length} materials</p><small>Updated {lessonDate(lesson.updatedAt)} · UTC+7</small></div><ArrowUpRight size={20} aria-hidden="true" />
        </Link></li>)}</ol>}
      <LessonPagination page={page} totalPages={query.data?.pagination.totalPages || 0} onPage={setPage} />
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

function LessonDetail({ classId, userId, role, lessonId = '', writable, create = false }: { classId: string; userId: string; role: LessonRole; lessonId?: string; writable: boolean; create?: boolean }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const base = `/${role.toLowerCase()}/classrooms/${encodeURIComponent(classId)}/lessons`;
  const [editing, setEditing] = useState(create);
  const [action, setAction] = useState<Action | null>(null);
  const [notice, setNotice] = useState('');
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
      await refresh(); setNotice('Your completion has been saved.');
    }); } catch { /* Mutation feedback is rendered below. */ }
  }

  if (!create && detail.isPending) return <p role="status">Loading lesson…</p>;
  if (!create && detail.isError) return <LessonErrorState error={detail.error} retry={() => void detail.refetch()} />;
  if (!create && (!lesson || lesson.classroomId !== classId || (role === 'STUDENT' && lesson.status !== 'PUBLISHED'))) return <LessonErrorState error={new Error('This lesson is not available to you.')} />;
  const activities = [...(options.data || []), ...(lesson?.activities || []).filter(activity => !options.data?.some(option => option.id === activity.id))];
  return <>
    {!editing && <Link className="lesson-back" to={base}><ArrowLeft size={15} aria-hidden="true" />All lessons</Link>}
    {mutation.isError && <p className="lesson-alert" role="alert">{lessonError(mutation.error)}</p>}
    {notice && <p className="lesson-notice" role="status">{notice}</p>}
    {role === 'TEACHER' && (policy.isError || options.isError) && <div className="lesson-dependency" role="status"><p>File uploads or activity linking are currently unavailable. You can continue editing lesson content.</p><button className="lesson-button text" type="button" onClick={() => { void policy.refetch(); if (policy.data?.activityLinkingAvailable) void options.refetch(); }}>Retry available services</button></div>}
    {editing && (create || editable) ? <LessonEditor lesson={lesson} activities={activities} activityLinkingAvailable={policy.data?.activityLinkingAvailable === true && options.isSuccess} pending={mutation.isPending} onSave={save} onCancel={() => create ? navigate(base) : setEditing(false)} /> : lesson && <>
      <section className="lesson-panel lesson-reading"><div className="lesson-section-head"><StatusBadge status={lesson.status} /><span className="lesson-muted">Updated {lessonDate(lesson.updatedAt)} · UTC+7</span></div>
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
      <div className="lesson-support-grid"><LessonAttachments lesson={lesson} editable={editable} policy={policy.data || null} onChanged={refresh} />
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
    </>}
    {action && <LessonConfirm {...lifecycle[action]} pending={mutation.isPending} onConfirm={() => void transition()} onCancel={() => setAction(null)} />}
  </>;
}

function StatusBadge({ status }: { status: Lesson['status'] }) {
  return <span className={`lesson-status ${status.toLowerCase()}`}>{status === 'DRAFT' ? 'Draft' : status === 'PUBLISHED' ? 'Published' : 'Archived'}</span>;
}
