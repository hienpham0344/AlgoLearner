import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowRight,
  Bell,
  BookOpen,
  ChevronRight,
  Code2,
  Flame,
  LayoutDashboard,
  LogOut,
  Menu,
  UserRound,
  X,
} from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { profileApi } from '@/features/profile/api/profileApi';
import logoMark from '@/assets/logo-mark.svg';
import byteMascot from '@/assets/mascot-byte.svg';
import { dashboardDemoData } from '../dashboardDemoData';
import type { EnrolledClass, LearningDeadline, PracticeStats, StudentDashboardData } from '../types';
import { dashboardApi, dashboardKeys, profileKeys } from '../api/dashboardApi';
import { mapStudentDashboard } from '../mapStudentDashboard';
import { RoleDashboardPage } from './RoleDashboardPage';
import { InteractiveCourseBackground } from '../components/InteractiveCourseBackground';
import { PracticePulse } from '../components/PracticePulse';
import '../styles/dashboard.css';

const IS_DEMO = import.meta.env.MODE === 'auth-demo';
const DISPLAY_TIME_ZONE = 'Asia/Bangkok';
const BANGKOK_OFFSET_MS = 7 * 60 * 60 * 1000;

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

const NAV_ITEMS = [
  { label: 'Dashboard', href: '/dashboard', icon: LayoutDashboard, current: true },
  { label: 'My classes', href: '#classes', icon: BookOpen, current: false },
  { label: 'Public courses', href: '/courses', icon: Code2, current: false },
  { label: 'Coding practice', href: '#practice', icon: Flame, current: false },
  { label: 'Notifications', href: '#notifications', icon: Bell, current: false },
  { label: 'My profile', href: '/profile', icon: UserRound, current: false },
] as const;

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

const toBangkokDateKey = (date: Date) =>
  new Date(date.getTime() + BANGKOK_OFFSET_MS).toISOString().slice(0, 10);

const getDeadlineUrgency = (dueAt: string): DeadlineUrgency => {
  const due = new Date(dueAt);
  if (due.getTime() < Date.now()) return 'Overdue';
  return toBangkokDateKey(due) === toBangkokDateKey(new Date()) ? 'Due today' : 'Upcoming';
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
  if (IS_DEMO) return 'ready';
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
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [navOpen, setNavOpen] = useState(false);

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

  useEffect(() => {
    const isUnauthorized = [profileQuery.error, dashboardQuery.error].some(
      error => isAxiosError(error) && error.response?.status === 401,
    );
    if (isUnauthorized) {
      logout();
      navigate('/login', { replace: true });
    }
  }, [profileQuery.error, dashboardQuery.error, logout, navigate]);

  const signOut = () => {
    logout();
    navigate('/login', { replace: true });
  };

  if (user?.role !== 'STUDENT') {
    return user?.role === 'TEACHER' || user?.role === 'ADMIN' ? <RoleDashboardPage role={user.role} /> : null;
  }

  const data = IS_DEMO
    ? dashboardDemoData
    : dashboardQuery.data
      ? mapStudentDashboard(dashboardQuery.data)
      : EMPTY_DASHBOARD_DATA;

  const practice: PracticeStats = data.practice;
  const loadState = resolveLoadState(dashboardQuery.isPending, dashboardQuery.isError, dashboardQuery.error);

  const rawDisplayName = profileQuery.data?.fullName || user.fullName || user.username;
  const displayName = rawDisplayName?.replace(/^Demo\s+/i, '') || user.username;
  const firstName = displayName?.split(' ')[0] || 'Learner';

  const featuredCourse = data.courses[0];
  const secondaryCourse = data.courses[1];
  const unreadCount = data.notifications.filter(notification => !notification.read).length;
  const sortedDeadlines = [...data.deadlines].sort((a, b) => Date.parse(a.dueAt) - Date.parse(b.dueAt));

  return (
    <div className="student-dashboard">
      <aside className="dashboard-sidebar" data-open={navOpen}>
        <div className="dashboard-sidebar-top">
          <Link
            className="dashboard-brand"
            to="/dashboard"
            onClick={() => setNavOpen(false)}
            aria-label="AlgoLearn dashboard"
          >
            <img src={logoMark} alt="" width="38" height="40" />
            <span>AlgoLearn</span>
          </Link>
          <button
            className="dashboard-menu-toggle"
            type="button"
            aria-label={navOpen ? 'Close navigation' : 'Open navigation'}
            aria-expanded={navOpen}
            onClick={() => setNavOpen(open => !open)}
          >
            {navOpen ? <X size={21} /> : <Menu size={21} />}
          </button>
        </div>

        <nav className="dashboard-nav" aria-label="Student workspace">
          <span className="dashboard-nav-label">WORKSPACE</span>
          {NAV_ITEMS.map(({ label, href, icon: Icon, current }) => (
            <Link
              key={label}
              to={href}
              className="dashboard-nav-link"
              aria-current={current ? 'page' : undefined}
              onClick={() => setNavOpen(false)}
              title={label}
            >
              <Icon size={18} strokeWidth={1.8} aria-hidden="true" />
              <span>{label}</span>
              {current && <span className="dashboard-nav-current" aria-hidden="true" />}
            </Link>
          ))}
        </nav>

        <div className="dashboard-sidebar-foot">
          <span>THINK. CODE. REPEAT.</span>
          <p>Small steps. Real progress.</p>
        </div>
      </aside>

      <div className="dashboard-page">
        <header className="dashboard-topbar">
          <span className="dashboard-topbar-path">
            Workspace <ChevronRight size={13} aria-hidden="true" /> Dashboard
          </span>

          <div className="dashboard-topbar-actions">
            <a
              className="dashboard-icon-link"
              href="#notifications"
              aria-label={unreadCount ? `${unreadCount} unread notifications` : 'Notifications'}
            >
              <Bell size={18} strokeWidth={1.8} aria-hidden="true" />
              {unreadCount > 0 && <span className="dashboard-notification-dot" />}
            </a>

            <Link className="dashboard-topbar-profile-link" to="/profile">
              <UserRound size={16} aria-hidden="true" />
              <span>My profile</span>
            </Link>

            <details className="dashboard-profile">
              <summary aria-label="Account menu">
                <span className="dashboard-avatar">{firstName.slice(0, 1).toUpperCase()}</span>
                <span className="dashboard-profile-name">{displayName}</span>
              </summary>
              <div className="dashboard-profile-menu">
                <strong>{displayName}</strong>
                <span>{user.email}</span>
                <Link to="/profile" className="dashboard-profile-setting">
                  Profile settings
                </Link>
                <button type="button" onClick={signOut}>
                  <LogOut size={15} aria-hidden="true" /> Sign out
                </button>
              </div>
            </details>
          </div>
        </header>

        <main className="dashboard-content">
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

          {!IS_DEMO && (dashboardQuery.isPending || dashboardQuery.isError) ? (
            <div className="dashboard-fetch-state" role={dashboardQuery.isError ? 'alert' : 'status'}>
              <span className="dashboard-eyebrow">YOUR LEARNING SPACE</span>
              <h2>
                {dashboardQuery.isPending
                  ? 'Loading your workspace…'
                  : loadState === 'restricted'
                    ? 'Access restricted.'
                    : 'Learning activity unavailable.'}
              </h2>
              {dashboardQuery.isError && (
                <>
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
                </>
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

                  <div className="dashboard-learning-card">
                    {featuredCourse ? (
                      <>
                        <InteractiveCourseBackground />
                        <div className="dashboard-learning-content">
                          <span className="dashboard-learning-label">
                            CURRENT COURSE · {featuredCourse.topic}
                          </span>
                          <h3>{featuredCourse.title}</h3>
                          {featuredCourse.currentLesson && <p>Up next: {featuredCourse.currentLesson}</p>}
                          <div className="dashboard-progress-info">
                            <span>Course progress</span>
                            <strong>{featuredCourse.progressPercent}%</strong>
                          </div>
                          <div
                            className="dashboard-progress-track"
                            role="progressbar"
                            aria-label={`${featuredCourse.title} progress`}
                            aria-valuemin={0}
                            aria-valuemax={100}
                            aria-valuenow={featuredCourse.progressPercent}
                          >
                            <span style={{ width: `${featuredCourse.progressPercent}%` }} />
                          </div>
                          {featuredCourse.href && (
                            <Link className="dashboard-learning-action" to={featuredCourse.href}>
                              Continue learning <ArrowRight size={15} aria-hidden="true" />
                            </Link>
                          )}
                        </div>
                      </>
                    ) : (
                      <>
                        <div className="dashboard-learning-content">
                          <span className="dashboard-learning-label">YOUR NEXT CHAPTER</span>
                          <h3>Your next chapter starts here.</h3>
                          <p>Choose a topic that interests you and start learning at your pace.</p>
                          <Link className="dashboard-learning-action" to="/courses">
                            Explore courses <ArrowRight size={16} aria-hidden="true" />
                          </Link>
                        </div>
                        <img className="dashboard-learning-mascot" src={byteMascot} alt="" width="280" height="280" />
                      </>
                    )}
                  </div>

                  {secondaryCourse && (
                    <div className="dashboard-secondary-course">
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
                    </div>
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
                                {item.href ? (
                                  <Link className="dashboard-timeline-title" to={item.href}>
                                    {item.title}
                                  </Link>
                                ) : (
                                  <strong className="dashboard-timeline-title">{item.title}</strong>
                                )}
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
                          Assignments and quizzes from your enrolled classes will appear here in chronological order.
                        </p>
                      </div>
                    </div>
                  )}
                </section>

                <section aria-labelledby="classes-title" id="classes" className="dashboard-section">
                  <div className="dashboard-section-heading">
                    <span className="dashboard-eyebrow">03 / YOUR LEARNING</span>
                    <h2 id="classes-title">My classes</h2>
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
                                  {moduleCounts.completed} / {moduleCounts.total} modules
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

                        return classroom.href ? (
                          <Link className="dashboard-class-row" key={classroom.id} to={classroom.href}>
                            {content}
                          </Link>
                        ) : (
                          <article className="dashboard-class-row" key={classroom.id} tabIndex={0}>
                            {content}
                          </article>
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
                  <div className="dashboard-section-heading">
                    <span className="dashboard-eyebrow">04 / KEEP THE RHYTHM</span>
                    <h2 id="practice-title">Practice pulse</h2>
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
        </main>

        <footer className="dashboard-footer">
          <span>© {new Date().getFullYear()} AlgoLearn</span>
          <span>Think. Code. Repeat.</span>
        </footer>
      </div>
    </div>
  );
};
