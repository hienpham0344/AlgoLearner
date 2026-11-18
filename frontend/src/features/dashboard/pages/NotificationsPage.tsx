import React from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, ArrowRight, Bell } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { dashboardApi, dashboardKeys } from '../api/dashboardApi';
import { mapStudentDashboard } from '../mapStudentDashboard';
import { dashboardDemoData } from '../dashboardDemoData';
import '../styles/dashboard.css';

const IS_DEMO = ['auth-demo', 'lessons-demo'].includes(import.meta.env.MODE);
const DISPLAY_TIME_ZONE = 'Asia/Bangkok';

const shortDateFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  month: 'short',
  day: 'numeric',
});

const formatTimestamp = (iso: string): string => {
  const createdMs = new Date(iso).getTime();
  const diffHours = Math.round((Date.now() - createdMs) / (60 * 60 * 1000));
  if (diffHours >= 0 && diffHours < 1) return 'Just now';
  if (diffHours >= 1 && diffHours < 24) return `${diffHours}h ago`;
  return shortDateFormatter.format(new Date(iso));
};

export const NotificationsPage: React.FC = () => {
  const { user } = useAuth();

  const studentQuery = useQuery({
    queryKey: dashboardKeys.student(),
    queryFn: ({ signal }) => dashboardApi.student(signal),
    enabled: user?.role === 'STUDENT' && !IS_DEMO,
    retry: false,
  });

  const teacherQuery = useQuery({
    queryKey: dashboardKeys.teacher(),
    queryFn: ({ signal }) => dashboardApi.teacher(signal),
    enabled: user?.role === 'TEACHER',
    retry: false,
  });

  if (!user) return <Navigate to="/login" replace />;
  if (user.role === 'ADMIN') return <Navigate to="/dashboard" replace />;

  const studentNotifications =
    user.role === 'STUDENT'
      ? IS_DEMO
        ? dashboardDemoData.notifications
        : studentQuery.data
          ? mapStudentDashboard(studentQuery.data).notifications
          : []
      : [];

  const teacherNotifications =
    user.role === 'TEACHER' && teacherQuery.data
      ? teacherQuery.data.recentNotifications.items.map(n => ({
          id: n.notificationId,
          title: n.title,
          content: '',
          course: '',
          createdAt: n.createdAt,
          read: n.readAt !== null,
          href: '/teacher/classrooms',
        }))
      : [];

  const items = user.role === 'STUDENT' ? studentNotifications : teacherNotifications;

  return (
    <main className="min-h-screen bg-[#f6f6f4] text-[#222427] px-6 py-8">
      <div className="mx-auto max-w-3xl space-y-6">
        <header className="flex items-center justify-between border-b border-[#e7e7e5] pb-4">
          <Link
            to="/dashboard"
            className="inline-flex items-center gap-2 text-xs font-semibold text-slate-700 hover:text-slate-900"
          >
            <ArrowLeft size={15} aria-hidden="true" />
            <span>Back to workspace</span>
          </Link>
          <span className="font-mono text-[11px] uppercase tracking-widest text-slate-500">
            ALGOLEARN / NOTIFICATIONS
          </span>
        </header>

        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <span className="dashboard-eyebrow">STAY IN THE LOOP</span>
            <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-900">Notifications.</h1>
            <p className="mt-1 text-sm text-slate-600">
              Updates across your enrolled classrooms, deadlines, and submissions.
            </p>
          </div>
          <Link
            to={`/${user.role.toLowerCase()}/classrooms`}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2 text-xs font-semibold text-slate-800 hover:bg-slate-50 transition-colors"
          >
            <Bell size={15} aria-hidden="true" />
            <span>Open classrooms</span>
            <ArrowRight size={14} aria-hidden="true" />
          </Link>
        </div>

        <section className="dashboard-notifications" aria-label="All notifications">
          {items.length ? (
            <div className="dashboard-notification-list" role="list">
              {items.map(item => (
                <Link
                  className="dashboard-notification-row"
                  key={item.id}
                  to={item.href && item.href.startsWith('/') ? item.href : `/${user.role.toLowerCase()}/classrooms`}
                  role="listitem"
                  data-unread={!item.read}
                >
                  <span
                    className="dashboard-notification-indicator"
                    data-unread={!item.read}
                    aria-label={item.read ? 'Read' : 'Unread'}
                  />
                  <div className="dashboard-notification-body">
                    <div className="dashboard-notification-head">
                      <strong>{item.title}</strong>
                      <time dateTime={item.createdAt}>{formatTimestamp(item.createdAt)}</time>
                    </div>
                    {(item.content || item.course) && (
                      <p className="dashboard-notification-sub">
                        {item.content && <span className="dashboard-notification-topic">{item.content}</span>}
                        {item.course && <span className="dashboard-notification-course">{item.course}</span>}
                      </p>
                    )}
                  </div>
                </Link>
              ))}
            </div>
          ) : (
            <div className="dashboard-empty dashboard-empty-notifications">
              <strong>All caught up.</strong>
              <p>New lessons and classroom updates will appear here.</p>
            </div>
          )}
        </section>
      </div>
    </main>
  );
};
