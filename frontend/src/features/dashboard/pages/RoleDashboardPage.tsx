import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowUpRight, Bell, BookOpen, ClipboardCheck, Clock3, LogOut, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import logoMark from '@/assets/logo-mark.svg';
import { dashboardApi, dashboardKeys, type AdminDashboardResponse, type Preview, type TeacherDashboardResponse } from '../api/dashboardApi';
import '../styles/role-dashboard.css';

const dateTime = (value: string) => new Intl.DateTimeFormat('en', { timeZone: 'Asia/Bangkok', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
const ItemList = <T,>({ title, eyebrow, preview, render }: { title: string; eyebrow: string; preview: Preview<T>; render: (item: T) => React.ReactNode }) => <section className="role-section"><span className="role-eyebrow">{eyebrow}</span><h2>{title}</h2>{preview.items.length ? <div className="role-list">{preview.items.map((item, index) => <div className="role-list-row" key={index}>{render(item)}</div>)}</div> : <p className="role-empty">Nothing to show right now.</p>}{preview.hasMore && <p className="role-preview-note">Showing {preview.items.length} of {preview.totalElements}. More items will be available in their module.</p>}</section>;

function TeacherView({ data }: { data: TeacherDashboardResponse }) {
  return <>
    {import.meta.env.MODE === 'auth-demo' && <p className="role-demo-note">Demo workspace · sample teaching data</p>}
    <div className="role-intro"><span className="role-eyebrow">YOUR TEACHING SPACE</span><h1>Keep the class moving.</h1><p>Classes, student work and updates in one focused view.</p></div>
    <div className="role-feature"><div><span className="role-eyebrow">YOUR CLASSROOMS</span><strong>{data.classrooms.totalElements}</strong><p>Classes you currently manage</p></div><BookOpen size={45} strokeWidth={1.2} aria-hidden="true" /></div>
    <div className="role-grid"><div>
      <ItemList eyebrow="01 / CLASSROOMS" title="My classes" preview={data.classrooms} render={item => <><BookOpen size={17} aria-hidden="true" /><strong>{item.name}</strong></>} />
      <ItemList eyebrow="02 / PLANNING" title="Open & upcoming assignments" preview={data.openOrUpcomingAssignments} render={item => <><div><strong>{item.title}</strong><span>{item.classroomName}</span></div><time dateTime={item.effectiveDueAt}>{dateTime(item.effectiveDueAt)}</time></>} />
      <ItemList eyebrow="03 / REVIEW" title="Ungraded work" preview={data.ungradedSubmissions} render={item => <><ClipboardCheck size={17} aria-hidden="true" /><div><strong>{item.studentUsername}</strong><span>{item.kind} · {dateTime(item.submittedAt)}</span></div></>} />
      <ItemList eyebrow="04 / ACTIVITY" title="Recent submissions" preview={data.recentSubmissions} render={item => <><Clock3 size={17} aria-hidden="true" /><div><strong>{item.studentUsername}</strong><span>{item.kind} · {dateTime(item.submittedAt)}</span></div></>} />
    </div><aside>
      <section className="role-section role-mini"><span className="role-eyebrow">CLASS HEALTH</span><h2>Class overview</h2>{data.classroomStatistics.items.length ? data.classroomStatistics.items.map(item => <div className="role-stat-row" key={item.classroomId}><strong>{item.name}</strong><span>{item.activeStudentCount} students · {item.ungradedWorkCount} to review</span></div>) : <p className="role-empty">No class statistics yet.</p>}</section>
      <section className="role-section role-mini"><span className="role-eyebrow">CONTRIBUTIONS</span><h2>Awaiting review</h2><div className="role-pair"><span>Courses</span><strong>{data.pendingContributions.courseCount}</strong></div><div className="role-pair"><span>Problems</span><strong>{data.pendingContributions.problemCount}</strong></div></section>
      <ItemList eyebrow="NOTIFICATIONS" title="Recent updates" preview={data.recentNotifications} render={item => <><Bell size={16} aria-hidden="true" /><div><strong>{item.title}</strong><span>{dateTime(item.createdAt)}{item.readAt === null ? ' · Unread' : ''}</span></div></>} />
    </aside></div>
  </>;
}

function AdminView({ data }: { data: AdminDashboardResponse }) {
  const totals = [
    ['Students', data.totals.studentCount], ['Teachers', data.totals.teacherCount], ['Classes', data.totals.classroomCount],
    ['Public courses', data.totals.publicCourseCount], ['Practice problems', data.totals.publicProblemCount], ['Submissions', data.totals.officialSubmissionCount],
  ] as const;
  const max = Math.max(1, ...data.charts.items.map(item => item.officialSubmissionCount));
  return <>
    {import.meta.env.MODE === 'auth-demo' && <p className="role-demo-note">Demo workspace · sample system data</p>}
    <div className="role-intro"><span className="role-eyebrow">SYSTEM OVERVIEW</span><h1>A clearer view of AlgoLearn.</h1><p>Activity and moderation, scoped to your admin account.</p></div>
    <div className="role-totals">{totals.map(([label, value]) => <div key={label}><span>{label}</span><strong>{value.toLocaleString()}</strong></div>)}</div>
    <div className="role-grid"><div>
      <section className="role-section"><span className="role-eyebrow">01 / ACTIVITY OVER TIME</span><h2>Official submissions</h2><p className="role-preview-note">{dateTime(data.charts.from)} – {dateTime(data.charts.to)} · {data.charts.interval} · UTC+7</p>{data.charts.items.length ? <div className="role-chart" role="img" aria-label="Official submissions by period">{data.charts.items.map(item => <div key={item.startsAt} title={`${dateTime(item.startsAt)}: ${item.officialSubmissionCount} submissions`}><span style={{ height: `${Math.max(3, item.officialSubmissionCount / max * 100)}%` }} /></div>)}</div> : <p className="role-empty">No chart data for this period.</p>}</section>
      <ItemList eyebrow="02 / SYSTEM" title="Recent activity" preview={data.recentActivities} render={item => <><div><strong>{item.summary}</strong><span>{item.sourceModule} · {item.action}</span></div><time dateTime={item.occurredAt}>{dateTime(item.occurredAt)}</time></>} />
      <ItemList eyebrow="03 / AUDIT" title="Important audit events" preview={data.importantAuditEvents} render={item => <><ShieldCheck size={17} aria-hidden="true" /><div><strong>{item.action}</strong><span>{item.outcome} · {dateTime(item.occurredAt)}</span></div></>} />
    </div><aside>
      <section className="role-section role-mini"><span className="role-eyebrow">MODERATION</span><h2>Accounts to watch</h2><div className="role-pair"><span>Locked</span><strong>{data.accountModeration.lockedAccountCount}</strong></div><div className="role-pair"><span>Suspended</span><strong>{data.accountModeration.suspendedAccountCount}</strong></div><div className="role-pair"><span>Confirmed violations</span><strong>{data.accountModeration.confirmedViolationAccountCount}</strong></div></section>
      <section className="role-section role-mini"><span className="role-eyebrow">PENDING CONTRIBUTIONS</span><h2>Teacher content</h2><div className="role-pair"><span>Courses</span><strong>{data.pendingTeacherContributions.courseCount}</strong></div><div className="role-pair"><span>Problems</span><strong>{data.pendingTeacherContributions.problemCount}</strong></div></section>
      <section className="role-section role-mini"><span className="role-eyebrow">CONFIGURATION</span><h2>System settings</h2>{data.configurationShortcuts.length ? data.configurationShortcuts.map(item => <div className="role-stat-row" key={item.code}><strong>{item.label}</strong><span>Available when the {item.code} module is connected</span></div>) : <p className="role-empty">No configuration shortcuts available.</p>}</section>
    </aside></div>
  </>;
}

export function RoleDashboardPage({ role }: { role: 'TEACHER' | 'ADMIN' }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const teacher = useQuery({ queryKey: dashboardKeys.teacher(), queryFn: ({ signal }) => dashboardApi.teacher(signal), enabled: role === 'TEACHER', retry: false });
  const admin = useQuery({ queryKey: dashboardKeys.admin(), queryFn: ({ signal }) => dashboardApi.admin(signal), enabled: role === 'ADMIN', retry: false });
  const query = role === 'TEACHER' ? teacher : admin;
  useEffect(() => { if (isAxiosError(query.error) && query.error.response?.status === 401) { logout(); navigate('/login', { replace: true }); } }, [query.error, logout, navigate]);
  const signOut = () => { logout(); navigate('/login', { replace: true }); };
  return <div className="role-dashboard"><aside className="role-sidebar"><Link to="/dashboard" className="role-brand"><img src={logoMark} width="30" height="30" alt="" /> AlgoLearn</Link><span className="role-eyebrow">{role} WORKSPACE</span><nav aria-label="Workspace"><Link to="/dashboard" aria-current="page">Dashboard</Link>{role === 'TEACHER' && <Link to="/profile">Profile settings</Link>}</nav><div className="role-sidebar-foot">THINK. CODE. REPEAT.</div></aside><div className="role-page"><header className="role-topbar"><span>Workspace / Dashboard</span><div>{role === 'TEACHER' && <Link to="/profile">Profile <ArrowUpRight size={14} aria-hidden="true" /></Link>}<span>{user?.fullName || user?.username}</span><button type="button" onClick={signOut} aria-label="Sign out"><LogOut size={17} aria-hidden="true" /></button></div></header><main className="role-content">{query.isPending ? <div className="role-fetch-state" role="status">Loading {role.toLowerCase()} dashboard…</div> : query.isError ? <div className="role-fetch-state" role="alert"><h1>{isAxiosError(query.error) && query.error.response?.status === 403 ? 'Access restricted.' : 'Dashboard unavailable.'}</h1><p>{isAxiosError(query.error) && query.error.response?.status === 403 ? 'This account cannot access this dashboard.' : 'We could not load the current data.'}</p><button type="button" onClick={() => query.refetch()}>Try again</button></div> : role === 'TEACHER' ? <TeacherView data={teacher.data!} /> : <AdminView data={admin.data!} />}</main><footer>© {new Date().getFullYear()} AlgoLearn</footer></div></div>;
}
