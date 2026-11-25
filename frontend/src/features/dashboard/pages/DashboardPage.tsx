import { useIsDemoMode } from '@/components/BackendStatusBanner';
import { clearSession } from '@/services/apiClient';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { useQueries, useQuery } from '@tanstack/react-query';
import { ArrowRight } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { WorkspaceLayout } from '@/components/layout/WorkspaceLayout';
import { DashboardSkeleton } from '../components/DashboardSkeleton';
import { profileApi } from '@/features/profile/api/profileApi';
import byteMascot from '@/assets/mascot-byte.svg';
import { dashboardDemoData } from '../dashboardDemoData';
import type { CourseProgress, EnrolledClass, LearningDeadline, PracticeStats, StudentDashboardData } from '../types';
import { dashboardApi, dashboardKeys, profileKeys } from '../api/dashboardApi';
import { mapStudentDashboard } from '../mapStudentDashboard';
import { RoleDashboardPage } from './RoleDashboardPage';
import { InteractiveCourseBackground } from '../components/InteractiveCourseBackground';
import { PracticePulse } from '../components/PracticePulse';
import { readStoredClassrooms, readStoredPersona } from '@/features/classroom/mock/useClassroomStore';
import { getClassroomLessonSummary } from '@/features/classroom/lessons/mock/lessonMockStore';
import { lessonApi, lessonKeys } from '@/features/classroom/lessons/api/lessonApi';
import '../styles/dashboard.css';

const DISPLAY_TIME_ZONE = 'Asia/Ho_Chi_Minh';
const VIETNAM_OFFSET_MS = 7 * 60 * 60 * 1000;

const EMPTY_DASHBOARD_DATA: StudentDashboardData = {
  courses: [],
  deadlines: [],
  classes: [],
  practice: {
    currentStreak: null,
    totalSolved: null,
    easy: null,
    medium: null,
    hard: null,
  },
  notifications: [],
};

const shortDateFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  month: 'short',
  day: 'numeric',
});

const dayNumberFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  day: '2-digit',
});

const monthFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  month: 'short',
});

const timeFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  hour: 'numeric',
  minute: '2-digit',
});

const todayFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  weekday: 'long',
  month: 'short',
  day: 'numeric',
});

type DeadlineUrgency = 'Overdue' | 'Due today' | 'Upcoming';
type DashboardLoadState = 'ready' | 'loading' | 'restricted' | 'unavailable';

const toVietnamDateKey = (date: Date) =>
  new Date(date.getTime() + VIETNAM_OFFSET_MS).toISOString().slice(0, 10);

const getDeadlineUrgency = (dueAt: string): DeadlineUrgency => {
  const due = new Date(dueAt);
  if (due.getTime() < Date.now()) return 'Overdue';
  return toVietnamDateKey(due) === toVietnamDateKey(new Date()) ? 'Due today' : 'Upcoming';
};

const formatDayNumber = (iso: string) => dayNumberFormatter.format(new Date(iso));
const formatMonth = (iso: string) => monthFormatter.format(new Date(iso)).toUpperCase();
const formatScheduleTime = (iso: string) => timeFormatter.format(new Date(iso));

const formatNotificationTimestamp = (iso: string): string => {
  const createdMs = new Date(iso).getTime();
  const diffHours = Math.round((Date.now() - createdMs) / (60 * 60 * 1000));
  if (diffHours >= 0 && diffHours < 1) return 'Just now';
  if (diffHours >= 1 && diffHours < 24) return `${diffHours}h ago`;
  return shortDateFormatter.format(new Date(iso));
};

const resolveLoadState = (isPending: boolean, isError: boolean, error: unknown): DashboardLoadState => {
  if (isPending) return 'loading';
  if (!isError) return 'ready';
  return isAxiosError(error) && error.response?.status === 403 ? 'restricted' : 'unavailable';
};

const resolveModuleCounts = (classroom: EnrolledClass) => {
  if (typeof classroom.totalModules !== 'number' || classroom.totalModules <= 0) {
    return null;
  }
  if (typeof classroom.completedModules !== 'number') {
    return null;
  }
  return {
    total: classroom.totalModules,
    completed: Math.min(Math.max(0, classroom.completedModules), classroom.totalModules),
  };
};

const getSegmentState = (index: number, completed: number): 'completed' | 'current' | 'upcoming' => {
  if (index < completed) return 'completed';
  if (index === completed) return 'current';
  return 'upcoming';
};

export const DashboardPage = () => {
  const IS_DEMO = useIsDemoMode();
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const isStudent = user?.role === 'STUDENT';

  const profileQuery = useQuery({
    queryKey: profileKeys.own(),
    queryFn: ({ signal }) => profileApi.own(signal),
    enabled: isStudent && !IS_DEMO,
    retry: false,
  });

  const dashboardQuery = useQuery({
    queryKey: dashboardKeys.student(),
    queryFn: ({ signal }) => dashboardApi.student(signal),
    enabled: isStudent && !IS_DEMO,
    retry: false,
  });

  const mappedBackendData = !IS_DEMO && dashboardQuery.data
    ? mapStudentDashboard(dashboardQuery.data)
    : EMPTY_DASHBOARD_DATA;

  const classroomLessonQueries = useQueries({
    queries: (isStudent && !IS_DEMO ? mappedBackendData.classes : []).map(cls => ({
      queryKey: lessonKeys.list(user?.id || '', cls.id, 0),
      queryFn: ({ signal }: { signal: AbortSignal }) => lessonApi.list(cls.id, 0, signal),
      enabled: Boolean(user?.id && cls.id),
      retry: false,
      staleTime: 30_000,
    })),
  });

  useEffect(() => {
    const isUnauthorized = [profileQuery.error, dashboardQuery.error].some(
      error => isAxiosError(error) && error.response?.status === 401,
    );
    if (isUnauthorized) {
      clearSession();
      navigate('/login', { replace: true });
    }
  }, [profileQuery.error, dashboardQuery.error, logout, navigate]);

  if (user?.role !== 'STUDENT') {
    return user?.role === 'TEACHER' || user?.role === 'ADMIN' ? <RoleDashboardPage role={user.role} /> : null;
  }

  const { syncedDemoClasses, syncedDemoCourses, primaryClassroomHref } = (() => {
    if (!IS_DEMO) {
      return {
        syncedDemoClasses: dashboardDemoData.classes,
        syncedDemoCourses: dashboardDemoData.courses,
        primaryClassroomHref: null as string | null,
      };
    }
    const studentPersona = readStoredPersona('STUDENT');
    const activeClasses = readStoredClassrooms().filter(
      c =>
        c.status === 'ACTIVE'
        && c.members.some(m => m.studentId === studentPersona.id && m.status === 'ACTIVE'),
    );

    const summaries = activeClasses.map(c => ({
      classroom: c,
      summary: getClassroomLessonSummary(c.id, 'STUDENT', studentPersona.id),
    }));

    const classes: EnrolledClass[] = summaries.map(({ classroom: c, summary }) => ({
      id: c.id,
      name: c.name,
      code: c.classCode,
      teacher: c.ownerName,
      currentActivity: c.description,
      nextAction:
        summary.nextLesson && summary.completedCount < summary.publishedCount
          ? `Up next: ${summary.nextLesson.title}`
          : summary.publishedCount > 0
            ? `All ${summary.publishedCount} lessons completed`
            : 'No published lessons yet',
      completedModules: summary.completedCount,
      totalModules: summary.publishedCount,
      href: `/student/classrooms/${encodeURIComponent(c.id)}`,
    }));

    const orderedForLearning = [...summaries].sort((a, b) => {
      const aScore = a.summary.publishedCount > a.summary.completedCount ? 2 : a.summary.publishedCount > 0 ? 1 : 0;
      const bScore = b.summary.publishedCount > b.summary.completedCount ? 2 : b.summary.publishedCount > 0 ? 1 : 0;
      return bScore - aScore;
    });

    const courses: CourseProgress[] = orderedForLearning.map(({ classroom: c, summary }) => {
      const pct =
        summary.publishedCount > 0
          ? Math.round((summary.completedCount / summary.publishedCount) * 100)
          : 0;
      return {
        id: c.id,
        title: c.name,
        topic: c.classCode,
        currentModule: `${summary.completedCount} / ${summary.publishedCount} lessons`,
        currentLesson: summary.nextLesson?.title || 'View classroom materials',
        completedUnits: summary.completedCount,
        totalUnits: summary.publishedCount,
        progressPercent: pct,
        href: summary.nextLesson
          ? `/student/classrooms/${encodeURIComponent(c.id)}/lessons/${encodeURIComponent(summary.nextLesson.id)}`
          : `/student/classrooms/${encodeURIComponent(c.id)}`,
      };
    });

    const primaryClass = orderedForLearning[0]?.classroom;
    return {
      syncedDemoClasses: classes,
      syncedDemoCourses: courses.length > 0 ? courses : dashboardDemoData.courses,
      primaryClassroomHref: primaryClass ? `/student/classrooms/${encodeURIComponent(primaryClass.id)}` : null,
    };
  })();

  const { backendClasses, backendCourses, backendPrimaryClassroomHref } = (() => {
    if (IS_DEMO || !dashboardQuery.data) {
      return {
        backendClasses: mappedBackendData.classes,
        backendCourses: mappedBackendData.courses,
        backendPrimaryClassroomHref: null as string | null,
      };
    }
    const summaries = mappedBackendData.classes.map((cls, index) => {
      const lessonQuery = classroomLessonQueries[index];
      const items = lessonQuery?.data?.items || [];
      const published = items.filter(lesson => lesson.status === 'PUBLISHED');
      const completedCount = published.filter(lesson => Boolean(lesson.progress?.completed)).length;
      const nextLesson = published.find(lesson => !lesson.progress?.completed) || null;
      return {
        classroom: cls,
        isPending: Boolean(lessonQuery?.isPending),
        publishedCount: published.length,
        completedCount,
        nextLesson,
      };
    });

    const enrichedClasses: EnrolledClass[] = summaries.map(({ classroom, isPending, publishedCount, completedCount, nextLesson }) => ({
      ...classroom,
      nextAction: isPending
        ? 'Loading lessons…'
        : nextLesson && completedCount < publishedCount
          ? `Up next: ${nextLesson.title}`
          : publishedCount > 0
            ? `All ${publishedCount} lessons completed`
            : 'No published lessons yet',
      completedModules: publishedCount > 0 ? completedCount : undefined,
      totalModules: publishedCount > 0 ? publishedCount : undefined,
      href: `/student/classrooms/${encodeURIComponent(classroom.id)}`,
    }));

    if (mappedBackendData.courses.length > 0) {
      return {
        backendClasses: enrichedClasses,
        backendCourses: mappedBackendData.courses,
        backendPrimaryClassroomHref: null as string | null,
      };
    }

    const orderedForLearning = [...summaries]
      .sort((a, b) => {
        const aScore = a.publishedCount > a.completedCount ? 3 : a.publishedCount > 0 ? 2 : 1;
        const bScore = b.publishedCount > b.completedCount ? 3 : b.publishedCount > 0 ? 2 : 1;
        return bScore - aScore;
      });

    const derivedCourses: CourseProgress[] = orderedForLearning.map(({ classroom, isPending, publishedCount, completedCount, nextLesson }) => ({
      id: classroom.id,
      title: classroom.name,
      topic: 'CLASSROOM',
      currentModule: isPending ? 'Syncing lessons…' : `${completedCount} / ${publishedCount} lessons`,
      currentLesson: isPending
        ? 'Loading next lesson…'
        : nextLesson?.title || (publishedCount > 0 ? 'View classroom materials' : 'No published lessons yet — open classroom'),
      completedUnits: completedCount,
      totalUnits: publishedCount,
      progressPercent: publishedCount > 0 ? Math.round((completedCount / publishedCount) * 100) : 0,
      href: nextLesson
        ? `/student/classrooms/${encodeURIComponent(classroom.id)}/lessons/${encodeURIComponent(nextLesson.id)}`
        : `/student/classrooms/${encodeURIComponent(classroom.id)}`,
    }));

    const primaryClass = orderedForLearning[0]?.classroom;
    return {
      backendClasses: enrichedClasses,
      backendCourses: derivedCourses,
      backendPrimaryClassroomHref: primaryClass ? `/student/classrooms/${encodeURIComponent(primaryClass.id)}` : null,
    };
  })();

  const data: StudentDashboardData = IS_DEMO
    ? { ...dashboardDemoData, classes: syncedDemoClasses, courses: syncedDemoCourses }
    : { ...mappedBackendData, classes: backendClasses, courses: backendCourses };
  const resolvedPrimaryClassroomHref = IS_DEMO ? primaryClassroomHref : backendPrimaryClassroomHref;

  const practice: PracticeStats = data.practice;
  const loadState = IS_DEMO ? 'ready' : resolveLoadState(dashboardQuery.isPending, dashboardQuery.isError, dashboardQuery.error);

  const rawDisplayName = profileQuery.data?.fullName || user.fullName || user.username;
  const displayName = rawDisplayName?.replace(/^Demo\s+/i, '') || user.username;
  const firstName = displayName?.split(' ')[0] || 'Learner';

  const featuredCourse = data.courses[0];
  const secondaryCourse = data.courses[1];
  const isLessonCoursesLoading = !IS_DEMO && classroomLessonQueries.some(query => query.isPending);
  const [progressEntered, setProgressEntered] = useState(false);

  useEffect(() => {
    if (!featuredCourse || (!IS_DEMO && dashboardQuery.isPending)) {
      setProgressEntered(false);
      return;
    }
    const frame = window.requestAnimationFrame(() => setProgressEntered(true));
    return () => window.cancelAnimationFrame(frame);
  }, [featuredCourse, IS_DEMO, dashboardQuery.isPending]);

  const unreadCount = data.notifications.filter(notification => !notification.read).length;
  const sortedDeadlines = [...data.deadlines].sort((a, b) => Date.parse(a.dueAt) - Date.parse(b.dueAt));

  return (
    <WorkspaceLayout role="STUDENT" unreadCount={unreadCount} displayNameOverride={displayName}>
      <div className="dashboard-intro">
        <div>
          <span className="dashboard-eyebrow">YOUR LEARNING SPACE</span>
          <h1>Keep building.</h1>
          <p>Pick up where you left off, {firstName}.</p>
        </div>
        <span className="dashboard-today">{todayFormatter.format(new Date())}</span>
      </div>

      {!IS_DEMO && dashboardQuery.data && (
        <p className="dashboard-availability-note" role="status">
          {profileQuery.isError
            ? 'Profile details could not be loaded; your learning data is still available.'
            : 'Your learning activity is up to date.'}
        </p>
      )}

      {!IS_DEMO && dashboardQuery.isPending ? (
        <DashboardSkeleton role="STUDENT" />
      ) : !IS_DEMO && dashboardQuery.isError ? (
        <div className="dashboard-fetch-state" role="alert">
          <span className="dashboard-eyebrow">YOUR LEARNING SPACE</span>
          <h2>
            {loadState === 'restricted'
              ? 'Access restricted.'
              : 'Learning activity unavailable.'}
          </h2>
          <p>
            {loadState === 'restricted'
              ? 'This account cannot access the student dashboard.'
              : 'We could not load your learning data.'}
          </p>
          {loadState !== 'restricted' && (
            <button type="button" onClick={() => dashboardQuery.refetch()}>
              Try again
            </button>
          )}
        </div>
      ) : (
        <div className="dashboard-grid">
          <div className="dashboard-primary">
            <section aria-labelledby="learning-title" id="learning" className="dashboard-learning-section">
              <div className="dashboard-section-heading">
                <span className="dashboard-eyebrow">01 / THE NEXT STEP</span>
                <h2 id="learning-title">Continue learning</h2>
              </div>

              <div className="dashboard-learning-card" data-entered={progressEntered ? 'true' : 'false'}>
                <InteractiveCourseBackground />
                {featuredCourse ? (
                  <>
                    <div className="dashboard-learning-content">
                      <span className="dashboard-learning-label">
                        CURRENT COURSE · {featuredCourse.topic}
                      </span>
                      <h3>{featuredCourse.title}</h3>
                      {featuredCourse.currentLesson && <p>Up next: {featuredCourse.currentLesson}</p>}
                      <div className="dashboard-progress-info">
                        <span>Course progress</span>
                        <strong>{isLessonCoursesLoading ? 'Syncing…' : `${featuredCourse.progressPercent}%`}</strong>
                      </div>
                      <div
                        className="dashboard-progress-track"
                        data-loading={isLessonCoursesLoading ? 'true' : 'false'}
                        role="progressbar"
                        aria-label={`${featuredCourse.title} progress`}
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-valuenow={featuredCourse.progressPercent}
                      >
                        <span style={{ width: `${featuredCourse.progressPercent}%` }} />
                      </div>
                      <div className="dashboard-learning-actions">
                        {featuredCourse.href && (
                          <Link className="dashboard-learning-action" to={featuredCourse.href}>
                            {resolvedPrimaryClassroomHref ? 'Continue lesson' : 'Continue learning'} <ArrowRight size={15} aria-hidden="true" />
                          </Link>
                        )}
                        {resolvedPrimaryClassroomHref && (
                          <Link
                            to={resolvedPrimaryClassroomHref}
                            className="dashboard-learning-secondary"
                          >
                            Open classroom →
                          </Link>
                        )}
                      </div>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="dashboard-learning-content">
                      <span className="dashboard-learning-label">YOUR NEXT CHAPTER</span>
                      <h3>Your next chapter starts here.</h3>
                      <p>
                        {IS_DEMO
                          ? 'Choose a topic that interests you and start learning at your pace.'
                          : 'Join a classroom with a class code from your instructor to start learning.'}
                      </p>
                      <Link
                        className="dashboard-learning-action"
                        to={IS_DEMO ? '/student/courses' : '/student/classrooms'}
                      >
                        {IS_DEMO ? 'Explore courses' : 'Join a classroom'} <ArrowRight size={16} aria-hidden="true" />
                      </Link>
                    </div>
                    <img className="dashboard-learning-mascot" src={byteMascot} alt="" width="280" height="280" />
                  </>
                )}
              </div>

              {secondaryCourse && (
                <Link
                  to={secondaryCourse.href || '/student/classrooms'}
                  className="dashboard-secondary-course hover:border-slate-300 transition-colors"
                >
                  <span>ALSO IN PROGRESS</span>
                  <div className="dashboard-secondary-course-main">
                    <strong>{secondaryCourse.title}</strong>
                    {(secondaryCourse.currentModule || secondaryCourse.currentLesson) && (
                      <span>
                        {[
                          secondaryCourse.currentModule,
                          secondaryCourse.currentLesson && `Next: ${secondaryCourse.currentLesson}`,
                        ]
                          .filter(Boolean)
                          .join(' · ')}
                      </span>
                    )}
                  </div>
                  <span className="dashboard-secondary-course-progress">
                    {secondaryCourse.progressPercent}% complete
                  </span>
                </Link>
              )}
            </section>

            <section aria-labelledby="deadlines-title" id="deadlines" className="dashboard-section">
              <div className="dashboard-section-heading">
                <span className="dashboard-eyebrow">02 / WHAT'S AHEAD</span>
                <h2 id="deadlines-title">Upcoming deadlines</h2>
              </div>

              {sortedDeadlines.length ? (
                <div className="dashboard-timeline" role="list">
                  {sortedDeadlines.map((item: LearningDeadline) => {
                    const urgency = getDeadlineUrgency(item.dueAt);
                    const isImmediate = urgency === 'Overdue' || urgency === 'Due today';

                    return (
                      <div
                        className="dashboard-timeline-item"
                        key={item.id}
                        role="listitem"
                        data-urgency={urgency}
                      >
                        <time className="dashboard-timeline-date" dateTime={item.dueAt}>
                          <strong>{formatDayNumber(item.dueAt)}</strong>
                          <span>{formatMonth(item.dueAt)}</span>
                        </time>
                        <div className="dashboard-timeline-axis" aria-hidden="true">
                          <span className="dashboard-timeline-node" data-immediate={isImmediate} />
                        </div>
                        <div className="dashboard-timeline-body">
                          <div className="dashboard-timeline-head">
                            <Link
                              className="dashboard-timeline-title"
                              to={item.href && !item.href.startsWith('#') ? item.href : '/student/classrooms'}
                            >
                              {item.title}
                            </Link>
                            <span className="dashboard-timeline-urgency" data-immediate={isImmediate}>
                              {urgency}
                            </span>
                          </div>
                          <p className="dashboard-timeline-meta">
                            <span>{item.className}</span>
                            <span>{item.kind}</span>
                            <span>Due {formatScheduleTime(item.dueAt)}</span>
                            {item.status && <span className="dashboard-timeline-status">{item.status}</span>}
                          </p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="dashboard-empty dashboard-empty-line">
                  <div>
                    <strong>No deadlines to show yet.</strong>
                    <p>
                      {IS_DEMO
                        ? 'Assignments and quizzes from your enrolled classes will appear here in chronological order.'
                        : 'Assignments and quizzes will appear here once their classroom modules are connected.'}
                    </p>
                  </div>
                </div>
              )}
            </section>

            <section aria-labelledby="classes-title" id="classes" className="dashboard-section">
              <div className="dashboard-section-heading flex items-center justify-between">
                <div>
                  <span className="dashboard-eyebrow">03 / YOUR LEARNING</span>
                  <h2 id="classes-title">My classes</h2>
                </div>
                <Link to="/student/classrooms" className="text-xs font-semibold text-slate-700 hover:text-slate-900 underline">
                  View all classrooms →
                </Link>
              </div>

              {data.classes.length ? (
                <div className="dashboard-class-list">
                  {data.classes.map(classroom => {
                    const nextClassDeadline = sortedDeadlines.find(item => item.className === classroom.name);
                    const moduleText =
                      classroom.currentActivity || (classroom.teacher ? `Instructor: ${classroom.teacher}` : undefined);
                    const nextActionText =
                      classroom.nextAction
                      || (nextClassDeadline
                        ? `Complete ${nextClassDeadline.kind.toLowerCase()}: ${nextClassDeadline.title}`
                        : undefined);
                    const moduleCounts = resolveModuleCounts(classroom);

                    const content = (
                      <>
                        <div className="dashboard-class-main">
                          <div className="dashboard-class-header">
                            <strong>{classroom.name}</strong>
                            {classroom.code && <span className="dashboard-class-code">{classroom.code}</span>}
                          </div>
                          {moduleText && <p className="dashboard-class-module">{moduleText}</p>}
                          {nextActionText && <span className="dashboard-class-next">Next: {nextActionText}</span>}
                        </div>
                        {moduleCounts && (
                          <div
                            className="dashboard-class-segments"
                            role="img"
                            aria-label={`${classroom.name}: ${moduleCounts.completed} of ${moduleCounts.total} modules completed`}
                          >
                            <span className="dashboard-class-segments-label">
                              {moduleCounts.completed} / {moduleCounts.total} lessons
                            </span>
                            <div className="dashboard-class-segments-track" aria-hidden="true">
                              {Array.from({ length: moduleCounts.total }, (_, idx) => (
                                <span key={idx} data-state={getSegmentState(idx, moduleCounts.completed)} />
                              ))}
                            </div>
                          </div>
                        )}
                      </>
                    );

                    const classroomLink = classroom.href && !classroom.href.startsWith('#')
                      ? classroom.href
                      : `/student/classrooms/${encodeURIComponent(classroom.id)}`;

                    return (
                      <Link className="dashboard-class-row" key={classroom.id} to={classroomLink}>
                        {content}
                      </Link>
                    );
                  })}
                </div>
              ) : (
                <div className="dashboard-empty dashboard-empty-line">
                  <div>
                    <strong>Your classes will show up here.</strong>
                    <p>
                      Enrolled classrooms, active modules, and coursework steps will appear once you join a class.
                    </p>
                  </div>
                </div>
              )}
            </section>
          </div>

          <aside className="dashboard-secondary">
            <section aria-labelledby="practice-title" id="practice" className="dashboard-practice">
              <div className="dashboard-section-heading flex items-center justify-between">
                <div>
                  <span className="dashboard-eyebrow">04 / KEEP THE RHYTHM</span>
                  <h2 id="practice-title">Practice pulse</h2>
                </div>
                <Link to="/practice" className="text-xs font-semibold text-slate-700 hover:text-slate-900 underline">
                  Open practice studio →
                </Link>
              </div>

              <PracticePulse practice={practice} loading={loadState === 'loading'} />
            </section>

            <section aria-labelledby="notifications-title" id="notifications" className="dashboard-notifications">
              <div className="dashboard-section-heading">
                <span className="dashboard-eyebrow">05 / STAY IN THE LOOP</span>
                <h2 id="notifications-title">Recent notifications</h2>
              </div>

              {data.notifications.length ? (
                <div className="dashboard-notification-list" role="list">
                  {data.notifications.map(notification => {
                    const isInternalRoute = Boolean(notification.href && notification.href.startsWith('/'));

                    const inner = (
                      <>
                        <span
                          className="dashboard-notification-indicator"
                          data-unread={!notification.read}
                          aria-label={notification.read ? 'Read' : 'Unread'}
                        />
                        <div className="dashboard-notification-body">
                          <div className="dashboard-notification-head">
                            <strong>{notification.title}</strong>
                            <time dateTime={notification.createdAt}>
                              {formatNotificationTimestamp(notification.createdAt)}
                            </time>
                          </div>
                          {(notification.content || notification.course || notification.detail) && (
                            <p className="dashboard-notification-sub">
                              {notification.content ? (
                                <>
                                  <span className="dashboard-notification-topic">{notification.content}</span>
                                  {notification.course && (
                                    <span className="dashboard-notification-course">{notification.course}</span>
                                  )}
                                </>
                              ) : (
                                <span>{notification.course || notification.detail}</span>
                              )}
                            </p>
                          )}
                        </div>
                      </>
                    );

                    if (isInternalRoute && notification.href) {
                      return (
                        <Link
                          className="dashboard-notification-row"
                          key={notification.id}
                          to={notification.href}
                          role="listitem"
                          data-unread={!notification.read}
                        >
                          {inner}
                        </Link>
                      );
                    }

                    return notification.href ? (
                      <a
                        className="dashboard-notification-row"
                        key={notification.id}
                        href={notification.href}
                        role="listitem"
                        data-unread={!notification.read}
                      >
                        {inner}
                      </a>
                    ) : (
                      <div
                        className="dashboard-notification-row"
                        key={notification.id}
                        role="listitem"
                        data-unread={!notification.read}
                      >
                        {inner}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="dashboard-empty dashboard-empty-notifications">
                  <strong>All caught up.</strong>
                  <p>New lessons and classroom updates will appear here.</p>
                </div>
              )}
            </section>
          </aside>
        </div>
      )}
    </WorkspaceLayout>
  );
};
