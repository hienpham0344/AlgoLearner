import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  Activity,
  Bell,
  BookOpen,
  ChevronRight,
  ClipboardCheck,
  Code2,
  FileCode2,
  Flame,
  LayoutDashboard,
  LogOut,
  Menu,
  ShieldAlert,
  UserRound,
  X,
} from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import logoMark from '@/assets/logo-mark.svg';
import { getWorkspaceNavItems, type WorkspaceNavIconKey, type WorkspaceRole } from './workspaceNav';
import '@/features/dashboard/styles/dashboard.css';

export type { WorkspaceRole };

const NAV_ICONS: Record<WorkspaceNavIconKey, React.ElementType> = {
  dashboard: LayoutDashboard,
  classes: BookOpen,
  courses: Code2,
  practice: Flame,
  assignments: FileCode2,
  review: ClipboardCheck,
  submissions: Activity,
  logs: FileCode2,
  audit: ShieldAlert,
  moderation: UserRound,
};

function getDefaultBreadcrumbLabel(role: WorkspaceRole, pathname: string): string {
  if (pathname === '/dashboard') {
    return role === 'TEACHER' ? 'Teacher Dashboard' : role === 'ADMIN' ? 'Admin Dashboard' : 'Dashboard';
  }
  if (pathname.includes('/lessons')) return 'Class lessons';
  if (pathname.includes('/classrooms')) return 'Classrooms';
  if (pathname === '/student/courses') return 'Public courses';
  if (pathname === '/practice') return 'Coding practice';
  if (pathname === '/teacher/assignments') return 'Assignments';
  if (pathname === '/teacher/review-queue') return 'Review queue';
  if (pathname === '/admin/submissions') return 'Submissions';
  if (pathname === '/admin/system-logs') return 'System log';
  if (pathname === '/admin/security-audit') return 'Security audit';
  if (pathname === '/admin/accounts') return 'Moderation';
  if (pathname === '/notifications') return 'Notifications';
  if (pathname === '/profile') return 'My profile';
  if (pathname === '/account/security') return 'Account security';
  return 'Workspace';
}

export interface WorkspaceLayoutProps {
  children: React.ReactNode;
  role?: WorkspaceRole;
  breadcrumb?: React.ReactNode;
  topbarActions?: React.ReactNode;
  unreadCount?: number;
  displayNameOverride?: string;
  sidebarFooterText?: string;
  mainClassName?: string;
}

export const WorkspaceLayout: React.FC<WorkspaceLayoutProps> = ({
  children,
  role: propRole,
  breadcrumb,
  topbarActions,
  unreadCount = 0,
  displayNameOverride,
  sidebarFooterText = 'Small steps. Real progress.',
  mainClassName = 'dashboard-content',
}) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [navOpen, setNavOpen] = useState(false);
  const [signOutError, setSignOutError] = useState<string | null>(null);

  const activeRole: WorkspaceRole = propRole || (user?.role as WorkspaceRole) || 'STUDENT';
  const navItems = getWorkspaceNavItems(activeRole, location.pathname);

  const rawDisplayName = displayNameOverride || user?.fullName || user?.username || activeRole;
  const displayName = rawDisplayName.replace(/^Demo\s+/i, '') || activeRole;
  const firstName = displayName.split(' ')[0] || 'Learner';

  const signOut = async () => {
    setSignOutError(null);
    try {
      await logout();
      navigate('/login', { replace: true });
    } catch {
      setSignOutError('Could not sign out. Check your connection and try again.');
    }
  };

  const navLabel =
    activeRole === 'TEACHER'
      ? 'TEACHER WORKSPACE'
      : activeRole === 'ADMIN'
        ? 'ADMIN WORKSPACE'
        : 'WORKSPACE';

  const breadcrumbLabel = getDefaultBreadcrumbLabel(activeRole, location.pathname);

  return (
    <div className="student-dashboard">
      <aside className="dashboard-sidebar" data-open={navOpen}>
        <div className="dashboard-sidebar-top">
          <Link
            className="dashboard-brand"
            to="/dashboard"
            onClick={() => setNavOpen(false)}
            aria-label="AlgoLearn"
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

        <nav className="dashboard-nav" aria-label={`${activeRole.toLowerCase()} workspace`}>
          <span className="dashboard-nav-label">{navLabel}</span>
          {navItems.map(({ label, href, iconKey, isCurrent }) => {
            const Icon = NAV_ICONS[iconKey];
            return href.startsWith('#') ? (
              <a
                key={label}
                href={href}
                className="dashboard-nav-link"
                onClick={() => setNavOpen(false)}
                title={label}
              >
                <Icon size={18} strokeWidth={1.8} aria-hidden="true" />
                <span>{label}</span>
              </a>
            ) : (
              <Link
                key={label}
                to={href}
                className="dashboard-nav-link"
                aria-current={isCurrent ? 'page' : undefined}
                onClick={() => setNavOpen(false)}
                title={label}
              >
                <Icon size={18} strokeWidth={1.8} aria-hidden="true" />
                <span>{label}</span>
                {isCurrent && <span className="dashboard-nav-current" aria-hidden="true" />}
              </Link>
            );
          })}
        </nav>

        <div className="dashboard-sidebar-foot">
          <span>THINK. CODE. REPEAT.</span>
          <p>{sidebarFooterText}</p>
        </div>
      </aside>

      <div className="dashboard-page">
        <header className="dashboard-topbar">
          <div className="dashboard-topbar-path">
            {breadcrumb ?? (
              <>
                <span>Workspace</span>
                <ChevronRight size={13} aria-hidden="true" />
                <span>{breadcrumbLabel}</span>
              </>
            )}
          </div>

          <div className="dashboard-topbar-actions">
            {topbarActions ?? (
              <>
                {activeRole !== 'ADMIN' && (
                  <>
                    <Link
                      className="dashboard-icon-link"
                      to="/notifications"
                      aria-label={unreadCount ? `${unreadCount} unread notifications` : 'Notifications'}
                    >
                      <Bell size={18} strokeWidth={1.8} aria-hidden="true" />
                      {unreadCount > 0 && <span className="dashboard-notification-dot" />}
                    </Link>

                    <Link className="dashboard-topbar-profile-link" to="/profile">
                      <UserRound size={16} aria-hidden="true" />
                      <span>My profile</span>
                    </Link>
                  </>
                )}

                <details className="dashboard-profile">
                  <summary aria-label="Account menu">
                    <span className="dashboard-avatar">{firstName.slice(0, 1).toUpperCase()}</span>
                    <span className="dashboard-profile-name">{displayName}</span>
                  </summary>
                  <div className="dashboard-profile-menu">
                    <strong>{displayName}</strong>
                    <span>{user?.email}</span>
                    {activeRole !== 'ADMIN' && (
                      <Link to="/profile" className="dashboard-profile-setting">
                        Profile settings
                      </Link>
                    )}
                    <Link to="/account/security" className="dashboard-profile-setting">
                      Account security
                    </Link>
                    <button type="button" onClick={signOut}>
                      <LogOut size={15} aria-hidden="true" /> Sign out
                    </button>
                  </div>
                </details>
              </>
            )}
          </div>
        </header>

        <main className={mainClassName}>
          {signOutError && <p role="alert">{signOutError}</p>}
          {children}
        </main>

        <footer className="dashboard-footer">
          <span>© {new Date().getFullYear()} AlgoLearn</span>
          <span>Think. Code. Repeat.</span>
        </footer>
      </div>
    </div>
  );
};
