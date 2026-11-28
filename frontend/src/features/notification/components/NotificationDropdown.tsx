import { useEffect, useId, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Bell, X } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { useIsDemoMode } from '@/components/BackendStatusBanner';
import { dashboardApi, dashboardKeys } from '@/features/dashboard/api/dashboardApi';
import { dashboardDemoData } from '@/features/dashboard/dashboardDemoData';

const dateFormatter = new Intl.DateTimeFormat('en', {
  timeZone: 'Asia/Ho_Chi_Minh', month: 'short', day: 'numeric',
});

export function NotificationDropdown({ unreadCount = 0 }: { unreadCount?: number }) {
  const { user } = useAuth();
  const isDemo = useIsDemoMode();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  const studentQuery = useQuery({
    queryKey: dashboardKeys.student(),
    queryFn: ({ signal }) => dashboardApi.student(signal),
    enabled: open && user?.role === 'STUDENT' && !isDemo,
    retry: false,
  });
  const teacherQuery = useQuery({
    queryKey: dashboardKeys.teacher(),
    queryFn: ({ signal }) => dashboardApi.teacher(signal),
    enabled: open && user?.role === 'TEACHER' && !isDemo,
    retry: false,
  });
  const query = user?.role === 'TEACHER' ? teacherQuery : studentQuery;
  const classroomHref = `/${user?.role === 'TEACHER' ? 'teacher' : 'student'}/classrooms`;
  const items = isDemo && user?.role === 'STUDENT'
    ? dashboardDemoData.notifications
    : (query.data?.recentNotifications.items ?? []).map(item => ({
        id: item.notificationId, title: item.title, createdAt: item.createdAt,
        read: item.readAt !== null, href: classroomHref,
      }));
  const loading = !isDemo && query.isLoading;
  const failed = !isDemo && query.isError;
  const count = Math.max(unreadCount, items.filter(item => !item.read).length);

  const closeAndFocus = () => {
    setOpen(false);
    triggerRef.current?.focus();
  };

  useEffect(() => { setOpen(false); }, [location.key]);

  useEffect(() => {
    if (!open) return;
    panelRef.current?.focus();
    const dismissOutside = (event: Event) => {
      if (event.target instanceof Node && !rootRef.current?.contains(event.target)) setOpen(false);
    };
    const dismissOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      setOpen(false);
      triggerRef.current?.focus();
    };
    document.addEventListener('pointerdown', dismissOutside);
    document.addEventListener('focusin', dismissOutside);
    document.addEventListener('keydown', dismissOnEscape);
    return () => {
      document.removeEventListener('pointerdown', dismissOutside);
      document.removeEventListener('focusin', dismissOutside);
      document.removeEventListener('keydown', dismissOnEscape);
    };
  }, [open]);

  return (
    <div className="notification-dropdown" ref={rootRef}>
      <button
        ref={triggerRef}
        type="button"
        className="dashboard-icon-link"
        aria-label={count ? `${count} unread notifications` : 'Notifications'}
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        aria-haspopup="dialog"
        onClick={() => setOpen(value => !value)}
      >
        <Bell size={18} strokeWidth={1.8} aria-hidden="true" />
        {count > 0 && <span className="dashboard-notification-dot" aria-hidden="true" />}
      </button>
      {open && (
        <div id={panelId} ref={panelRef} className="notification-dropdown-panel" role="dialog" aria-label="Notifications" tabIndex={-1}>
          <div className="notification-dropdown-heading">
            <h2>Notifications</h2>
            <button type="button" className="dashboard-icon-link" aria-label="Close notifications" onClick={closeAndFocus}>
              <X size={16} aria-hidden="true" />
            </button>
          </div>
          <div className="notification-dropdown-content" aria-busy={loading}>
            {loading ? <p className="notification-dropdown-message" role="status">Loading notifications…</p>
              : failed ? (
                <div className="notification-dropdown-message" role="alert">
                  <p>Could not load notifications.</p>
                  <button type="button" onClick={() => { panelRef.current?.focus(); void query.refetch(); }}>Try again</button>
                </div>
              ) : items.length ? (
                <ul className="notification-dropdown-list">
                  {items.slice(0, 5).map(item => (
                    <li key={item.id}>
                      <Link
                        className="dashboard-notification-row"
                        to={item.href?.startsWith('/') && !item.href.startsWith('//') ? item.href : classroomHref}
                        data-unread={!item.read}
                        onClick={() => setOpen(false)}
                      >
                        <span className="dashboard-notification-indicator" data-unread={!item.read} aria-hidden="true" />
                        <span className="sr-only">{item.read ? 'Read' : 'Unread'}</span>
                        <div className="dashboard-notification-body">
                          <strong>{item.title}</strong>
                          <time dateTime={item.createdAt}>{dateFormatter.format(new Date(item.createdAt))}</time>
                        </div>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="notification-dropdown-message">
                  <Bell size={22} strokeWidth={1.5} aria-hidden="true" />
                  <strong>All caught up.</strong>
                  <p>New classroom updates will appear here.</p>
                </div>
              )}
          </div>
          <Link className="notification-dropdown-footer" to="/notifications" onClick={() => setOpen(false)}>
            View all notifications <ArrowRight size={14} aria-hidden="true" />
          </Link>
        </div>
      )}
    </div>
  );
}
