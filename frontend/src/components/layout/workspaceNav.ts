export type WorkspaceRole = 'STUDENT' | 'TEACHER' | 'ADMIN';

export type WorkspaceNavIconKey =
  | 'dashboard'
  | 'classes'
  | 'courses'
  | 'practice'
  | 'assignments'
  | 'review'
  | 'submissions'
  | 'logs'
  | 'audit'
  | 'moderation';

export interface WorkspaceNavItemConfig {
  label: string;
  href: string;
  iconKey: WorkspaceNavIconKey;
  isCurrent: boolean;
}

/** Resolve unified left-taskbar items and active states for the current role and route. */
export function getWorkspaceNavItems(
  role: WorkspaceRole,
  pathname: string,
): WorkspaceNavItemConfig[] {
  const rolePrefix = role.toLowerCase();
  const isOnDashboard = pathname === '/dashboard';
  const isClassroomsRoute = pathname.startsWith(`/${rolePrefix}/classrooms`) && !pathname.includes('/practice');

  if (role === 'TEACHER') {
    return [
      { label: 'Dashboard', href: '/dashboard', iconKey: 'dashboard', isCurrent: isOnDashboard },
      { label: 'My classes', href: '/teacher/classrooms', iconKey: 'classes', isCurrent: isClassroomsRoute },
      { label: 'Assignments', href: '/teacher/assignments', iconKey: 'assignments', isCurrent: pathname === '/teacher/assignments' },
      { label: 'Review queue', href: '/teacher/review-queue', iconKey: 'review', isCurrent: pathname === '/teacher/review-queue' },
    ];
  }

  if (role === 'ADMIN') {
    return [
      { label: 'Dashboard', href: '/dashboard', iconKey: 'dashboard', isCurrent: isOnDashboard },
      { label: 'Classrooms', href: '/admin/classrooms', iconKey: 'classes', isCurrent: isClassroomsRoute },
      { label: 'Submissions', href: '/admin/submissions', iconKey: 'submissions', isCurrent: pathname === '/admin/submissions' },
      { label: 'System log', href: '/admin/system-logs', iconKey: 'logs', isCurrent: pathname === '/admin/system-logs' },
      { label: 'Security audit', href: '/admin/security-audit', iconKey: 'audit', isCurrent: pathname === '/admin/security-audit' },
      { label: 'Moderation', href: '/admin/accounts', iconKey: 'moderation', isCurrent: pathname === '/admin/accounts' },
    ];
  }

  return [
    { label: 'Dashboard', href: '/dashboard', iconKey: 'dashboard', isCurrent: isOnDashboard },
    { label: 'My classes', href: '/student/classrooms', iconKey: 'classes', isCurrent: isClassroomsRoute },
    { label: 'Public courses', href: '/student/courses', iconKey: 'courses', isCurrent: pathname === '/student/courses' },
    { label: 'Coding practice', href: '/practice', iconKey: 'practice', isCurrent: pathname === '/practice' || pathname.includes('/practice') },
  ];
}
