import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { ArrowRight, Bell, BookOpen, CalendarDays, ChevronRight, Code2, Flame, LayoutDashboard, LogOut, Menu, X } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { apiClient } from '@/services/apiClient';
import type { ApiResponse } from '@/types/api';
import logoMark from '@/assets/logo-mark.svg';
import byteMascot from '@/assets/mascot-byte.svg';
import { dashboardDemoData } from '../dashboardDemoData';
import type { LearningDeadline, PracticeStats, StudentDashboardData } from '../types';
import '../styles/dashboard.css';

interface UserProfileData {
  fullName?: string;
  publicPracticeStatistics?: { solved?: number };
  streak?: { currentStreak?: number };
}

const isDemo = import.meta.env.MODE === 'auth-demo';
const emptyData: StudentDashboardData = {
  courses: [], deadlines: [], classes: [],
  practice: { currentStreak: null, totalSolved: null, easy: null, medium: null, hard: null },
  notifications: [],
};

const dayKey = (date: Date) => new Date(date.getTime() + 7 * 60 * 60 * 1000).toISOString().slice(0, 10);
const deadlineGroup = (dueAt: string): 'Overdue' | 'Due today' | 'Upcoming' => {
  const due = new Date(dueAt);
  if (due.getTime() < Date.now()) return 'Overdue';
  return dayKey(due) === dayKey(new Date()) ? 'Due today' : 'Upcoming';
};
const dateLabel = (iso: string) => new Intl.DateTimeFormat('en', {
  timeZone: 'Asia/Bangkok', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
}).format(new Date(iso));

export const DashboardPage = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [navOpen, setNavOpen] = useState(false);
  const [profile, setProfile] = useState<UserProfileData | null>(null);
  const [profileState, setProfileState] = useState<'loading' | 'ready' | 'unavailable' | 'restricted'>(isDemo ? 'ready' : 'loading');
  const [profileRetry, setProfileRetry] = useState(0);

  useEffect(() => {
    if (isDemo || user?.role !== 'STUDENT') return;
    const controller = new AbortController();
    setProfileState('loading');
    apiClient.get<ApiResponse<UserProfileData>>('/users/me/profile', { signal: controller.signal })
      .then(response => { setProfile(response.data.data); setProfileState('ready'); })
      .catch(error => {
        if (controller.signal.aborted) return;
        if (isAxiosError(error) && error.response?.status === 401) {
          logout();
          navigate('/login', { replace: true });
          return;
        }
        setProfileState(isAxiosError(error) && error.response?.status === 403 ? 'restricted' : 'unavailable');
      });
    return () => controller.abort();
  }, [user?.role, profileRetry]);

  const signOut = () => { logout(); navigate('/login', { replace: true }); };
  if (user?.role !== 'STUDENT') {
    return <main className="dashboard-role-guard"><div><span className="dashboard-eyebrow">ALGOLEARN WORKSPACE</span><h1>Student workspace</h1><p>This dashboard is available to student accounts.</p><button type="button" onClick={signOut}>Sign out <ArrowRight size={16} aria-hidden="true" /></button></div></main>;
  }

  const data = isDemo ? dashboardDemoData : emptyData;
  const practice: PracticeStats = isDemo ? data.practice : {
    currentStreak: profile?.streak?.currentStreak ?? null,
    totalSolved: profile?.publicPracticeStatistics?.solved ?? null,
    easy: null, medium: null, hard: null,
  };
  const displayName = profile?.fullName || user.fullName || user.username;
  const firstName = displayName?.split(' ')[0] || 'Learner';
  const featuredCourse = data.courses[0];
  const unreadCount = data.notifications.filter(notification => !notification.read).length;
  const sortedDeadlines = [...data.deadlines].sort((a, b) => Date.parse(a.dueAt) - Date.parse(b.dueAt));
  const groupedDeadlines = (['Overdue', 'Due today', 'Upcoming'] as const)
    .map(label => ({ label, items: sortedDeadlines.filter(item => deadlineGroup(item.dueAt) === label) }))
    .filter(group => group.items.length > 0);

  const navItems = [
    { label: 'Dashboard', href: '/dashboard', icon: LayoutDashboard, current: true },
    { label: 'My classes', href: '#classes', icon: BookOpen },
    { label: 'Public courses', href: '/courses', icon: Code2 },
    { label: 'Coding practice', href: '#practice', icon: Flame },
    { label: 'Notifications', href: '#notifications', icon: Bell },
  ];

  return (
    <div className="student-dashboard">
      <aside className="dashboard-sidebar" data-open={navOpen}>
        <div className="dashboard-sidebar-top">
          <Link className="dashboard-brand" to="/dashboard" onClick={() => setNavOpen(false)} aria-label="AlgoLearn dashboard"><img src={logoMark} alt="" width="38" height="40" /><span>AlgoLearn</span></Link>
          <button className="dashboard-menu-toggle" type="button" aria-label={navOpen ? 'Close navigation' : 'Open navigation'} aria-expanded={navOpen} onClick={() => setNavOpen(open => !open)}>{navOpen ? <X size={21} /> : <Menu size={21} />}</button>
        </div>
        <nav className="dashboard-nav" aria-label="Student workspace">
          <span className="dashboard-nav-label">WORKSPACE</span>
          {navItems.map(({ label, href, icon: Icon, current }) => (
            <Link key={label} to={href} className="dashboard-nav-link" aria-current={current ? 'page' : undefined} onClick={() => setNavOpen(false)} title={label}>
              <Icon size={18} strokeWidth={1.8} aria-hidden="true" /><span>{label}</span>{current && <span className="dashboard-nav-current" aria-hidden="true" />}
            </Link>
          ))}
        </nav>
        <div className="dashboard-sidebar-foot"><span>THINK. CODE. REPEAT.</span><p>Small steps. Real progress.</p></div>
      </aside>

      <div className="dashboard-page">
        <header className="dashboard-topbar">
          <span className="dashboard-topbar-path">Workspace <ChevronRight size={13} aria-hidden="true" /> Dashboard</span>
          <div className="dashboard-topbar-actions">
            <a className="dashboard-icon-link" href="#notifications" aria-label={unreadCount ? `${unreadCount} unread notifications` : 'Notifications'}><Bell size={18} strokeWidth={1.8} aria-hidden="true" />{unreadCount > 0 && <span className="dashboard-notification-dot" />}</a>
            <details className="dashboard-profile"><summary aria-label="Account menu"><span className="dashboard-avatar">{firstName.slice(0, 1).toUpperCase()}</span><span className="dashboard-profile-name">{displayName}</span></summary><div className="dashboard-profile-menu"><strong>{displayName}</strong><span>{user.email}</span><button type="button" onClick={signOut}><LogOut size={15} aria-hidden="true" /> Sign out</button></div></details>
          </div>
        </header>

        <main className="dashboard-content">
          <div className="dashboard-intro"><div><span className="dashboard-eyebrow">YOUR LEARNING SPACE</span><h1>Keep building.</h1><p>Pick up where you left off, {firstName}.</p></div><span className="dashboard-today">{new Intl.DateTimeFormat('en', { timeZone: 'Asia/Bangkok', weekday: 'long', month: 'short', day: 'numeric' }).format(new Date())}</span></div>
          {isDemo ? <p className="dashboard-mode-note"><span /> Demo workspace · sample learning data</p> : <p className="dashboard-availability-note">{profileState === 'unavailable' ? <>Practice details could not be loaded. <button type="button" onClick={() => setProfileRetry(value => value + 1)}>Try again</button></> : profileState === 'restricted' ? 'Practice details are unavailable for this account.' : 'Learning activity will appear here as your workspace becomes available.'}</p>}

          <div className="dashboard-grid">
            <div className="dashboard-primary">
              <section aria-labelledby="learning-title" id="learning" className="dashboard-learning-section">
                <div className="dashboard-section-heading"><span className="dashboard-eyebrow">01 / THE NEXT STEP</span><h2 id="learning-title">Continue learning</h2></div>
                <div className="dashboard-learning-card">
                  {featuredCourse ? <>
                    <div className="dashboard-learning-content"><span className="dashboard-learning-label">CURRENT COURSE · {featuredCourse.topic}</span><h3>{featuredCourse.title}</h3><p>Up next: {featuredCourse.currentLesson}</p><div className="dashboard-progress-info"><span>Course progress</span><strong>{featuredCourse.progressPercent}%</strong></div><div className="dashboard-progress-track" role="progressbar" aria-label={`${featuredCourse.title} progress`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={featuredCourse.progressPercent}><span style={{ width: `${featuredCourse.progressPercent}%` }} /></div>{featuredCourse.href && <Link className="dashboard-learning-action" to={featuredCourse.href}>Explore course preview <ArrowRight size={16} aria-hidden="true" /></Link>}</div>
                    <span className="dashboard-learning-index" aria-hidden="true">&lt; / &gt;</span>
                  </> : <>
                    <div className="dashboard-learning-content"><span className="dashboard-learning-label">YOUR NEXT CHAPTER</span><h3>Your next chapter starts here.</h3><p>Choose a topic that interests you and start learning at your pace.</p><Link className="dashboard-learning-action" to="/courses">Explore courses <ArrowRight size={16} aria-hidden="true" /></Link></div>
                    <img className="dashboard-learning-mascot" src={byteMascot} alt="" width="280" height="280" />
                  </>}
                </div>
                {data.courses.length > 1 && <div className="dashboard-secondary-course"><span>ALSO IN PROGRESS</span><strong>{data.courses[1].title}</strong><span>{data.courses[1].progressPercent}% complete</span></div>}
              </section>

              <section aria-labelledby="deadlines-title" id="deadlines" className="dashboard-section">
                <div className="dashboard-section-heading"><span className="dashboard-eyebrow">02 / WHAT'S AHEAD</span><h2 id="deadlines-title">Upcoming deadlines</h2></div>
                {groupedDeadlines.length ? <div className="dashboard-agenda">{groupedDeadlines.map(group => <div className="dashboard-agenda-group" key={group.label}><h3>{group.label}</h3>{group.items.map((item: LearningDeadline) => <div className="dashboard-agenda-row" key={item.id}><span className="dashboard-agenda-mark" data-urgency={group.label} /><div className="dashboard-agenda-main"><strong>{item.title}</strong><span>{item.className} · {item.kind} · {item.status}</span></div><time dateTime={item.dueAt}>{dateLabel(item.dueAt)}</time>{item.href && <Link to={item.href} aria-label={`Open ${item.title}`}><ArrowRight size={16} /></Link>}</div>)}</div>)}</div> : <div className="dashboard-empty dashboard-empty-line"><CalendarDays size={19} strokeWidth={1.5} aria-hidden="true" /><div><strong>No deadlines to show yet.</strong><p>Assignments and quizzes will appear here when available.</p></div></div>}
              </section>

              <section aria-labelledby="classes-title" id="classes" className="dashboard-section">
                <div className="dashboard-section-heading"><span className="dashboard-eyebrow">03 / YOUR COMMUNITY</span><h2 id="classes-title">My classes</h2></div>
                {data.classes.length ? <div className="dashboard-class-list">{data.classes.map(classroom => <div className="dashboard-class-row" key={classroom.id}><span className="dashboard-class-initial" aria-hidden="true">{classroom.name.slice(0, 1)}</span><div><strong>{classroom.name}</strong><span>{classroom.teacher ? `${classroom.teacher} · ` : ''}{classroom.code}</span></div>{classroom.href && <Link to={classroom.href} aria-label={`Open ${classroom.name}`}><ArrowRight size={16} /></Link>}</div>)}</div> : <div className="dashboard-empty dashboard-empty-line"><BookOpen size={19} strokeWidth={1.5} aria-hidden="true" /><div><strong>Your classes will show up here.</strong><p>Class information is not available yet.</p></div></div>}
              </section>
            </div>

            <aside className="dashboard-secondary">
              <section aria-labelledby="practice-title" id="practice" className="dashboard-practice">
                <div className="dashboard-section-heading"><span className="dashboard-eyebrow">04 / KEEP THE RHYTHM</span><h2 id="practice-title">Practice pulse</h2></div>
                <div className="dashboard-practice-body">
                  {profileState === 'loading' ? <div className="dashboard-practice-loading" aria-label="Loading practice statistics"><span /><span /><span /></div> : <><div className="dashboard-streak"><Flame size={22} strokeWidth={1.7} aria-hidden="true" /><strong>{practice.currentStreak ?? '—'}</strong><span>{practice.currentStreak === null ? 'Streak unavailable' : `${practice.currentStreak === 1 ? 'day' : 'days'} in a row`}</span></div>{practice.totalSolved !== null && <p className="dashboard-total-solved">{practice.totalSolved} problems solved in public practice</p>}<div className="dashboard-difficulty"><span>PROBLEMS SOLVED</span>{([['Easy', practice.easy], ['Medium', practice.medium], ['Hard', practice.hard]] as const).map(([label, count]) => <div key={label}><span>{label}</span><strong>{count ?? '—'}</strong></div>)}</div>{!isDemo && <p className="dashboard-practice-note">A difficulty breakdown will appear when practice data is available.</p>}</>}
                </div>
              </section>

              <section aria-labelledby="notifications-title" id="notifications" className="dashboard-notifications">
                <div className="dashboard-section-heading"><span className="dashboard-eyebrow">05 / STAY IN THE LOOP</span><h2 id="notifications-title">Recent notifications</h2></div>
                {data.notifications.length ? <div className="dashboard-notification-list">{data.notifications.map(notification => <div className="dashboard-notification-row" key={notification.id}><span className="dashboard-notification-indicator" data-unread={!notification.read} aria-label={notification.read ? 'Read' : 'Unread'} /><div><strong>{notification.title}</strong><p>{notification.detail}</p><time dateTime={notification.createdAt}>{dateLabel(notification.createdAt)}</time></div>{notification.href && <a href={notification.href} aria-label={`Go to ${notification.title}`}><ArrowRight size={15} /></a>}</div>)}</div> : <div className="dashboard-empty dashboard-empty-notifications"><Bell size={18} strokeWidth={1.5} aria-hidden="true" /><strong>All caught up.</strong><p>New updates will appear here.</p></div>}
              </section>
            </aside>
          </div>
        </main>
        <footer className="dashboard-footer"><span>© {new Date().getFullYear()} AlgoLearn</span><span>Think. Code. Repeat.</span></footer>
      </div>
    </div>
  );
};
