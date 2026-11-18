export type WorkspaceRole = 'STUDENT' | 'TEACHER' | 'ADMIN';

export type WorkspaceNavIconKey =
  | 'dashboard'
  | 'classes'
  | 'lessons'
  | 'courses'
  | 'practice'
  | 'assignments'
  | 'review'
  | 'notifications'
  | 'profile'
  | 'submissions'
  | 'logs'
  | 'audit'
  | 'moderation'
  | 'security';

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
  classroomId?: string,
): WorkspaceNavItemConfig[] {
  const rolePrefix = role.toLowerCase();
  const isOnDashboard = pathname === '/dashboard';
  const isLessonsRoute =
    pathname.startsWith(`/${rolePrefix}/lessons`)
    || (pathname.startsWith(`/${rolePrefix}/classrooms`) && pathname.includes('/lessons'));
  const isClassroomsRoute =
    pathname.startsWith(`/${rolePrefix}/classrooms`) && !pathname.includes('/lessons');

  const lessonsHref = isOnDashboard
    ? '#lessons'
    : classroomId && role !== 'ADMIN'
      ? `/${rolePrefix}/classrooms/${encodeURIComponent(classroomId)}/lessons`
      : `/${rolePrefix}/lessons`;

  if (role === 'TEACHER') {
    return [
      { label: 'Dashboard', href: '/dashboard', iconKey: 'dashboard', isCurrent: isOnDashboard },
      { label: 'My classes', href: '/teacher/classrooms', iconKey: 'classes', isCurrent: isClassroomsRoute },
      { label: 'Class lessons', href: lessonsHref, iconKey: 'lessons', isCurrent: isLessonsRoute },
      { label: 'Assignments', href: '/teacher/assignments', iconKey: 'assignments', isCurrent: pathname === '/teacher/assignments' },
      { label: 'Review queue', href: '/teacher/review-queue', iconKey: 'review', isCurrent: pathname === '/teacher/review-queue' },
      { label: 'Notifications', href: '/notifications', iconKey: 'notifications', isCurrent: pathname === '/notifications' },
      { label: 'My profile', href: '/profile', iconKey: 'profile', isCurrent: pathname === '/profile' || pathname === '/account/security' },
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
      { label: 'Account security', href: '/account/security', iconKey: 'security', isCurrent: pathname === '/account/security' },
    ];
  }

  return [
    { label: 'Dashboard', href: '/dashboard', iconKey: 'dashboard', isCurrent: isOnDashboard },
    { label: 'My classes', href: '/student/classrooms', iconKey: 'classes', isCurrent: isClassroomsRoute },
    { label: 'Class lessons', href: lessonsHref, iconKey: 'lessons', isCurrent: isLessonsRoute },
    { label: 'Public courses', href: '/student/courses', iconKey: 'courses', isCurrent: pathname === '/student/courses' },
    { label: 'Coding practice', href: '/practice', iconKey: 'practice', isCurrent: pathname === '/practice' },
    { label: 'Notifications', href: '/notifications', iconKey: 'notifications', isCurrent: pathname === '/notifications' },
    { label: 'My profile', href: '/profile', iconKey: 'profile', isCurrent: pathname === '/profile' || pathname === '/account/security' },
  ];
}

