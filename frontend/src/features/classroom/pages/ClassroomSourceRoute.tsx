import { useQuery } from '@tanstack/react-query';
import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import type { DemoRole } from '../types';
import { classroomApi, classroomApiUnavailable, classroomError } from '../api/classroomApi';
import { BackendClassroomPage } from './BackendClassroomPage';
import { TeacherClassroomListPage } from '../teacher/pages/TeacherClassroomListPage';
import { TeacherClassroomDetailPage } from '../teacher/pages/TeacherClassroomDetailPage';
import { StudentClassroomListPage } from '../student/pages/StudentClassroomListPage';
import { StudentClassroomDetailPage } from '../student/pages/StudentClassroomDetailPage';
import { AdminClassroomListPage } from '../admin/pages/AdminClassroomListPage';
import { AdminClassroomDetailPage } from '../admin/pages/AdminClassroomDetailPage';

const mockPages = {
  TEACHER: { list: TeacherClassroomListPage, detail: TeacherClassroomDetailPage },
  STUDENT: { list: StudentClassroomListPage, detail: StudentClassroomDetailPage },
  ADMIN: { list: AdminClassroomListPage, detail: AdminClassroomDetailPage },
};

/** Choose one data source for the whole classroom workspace. Never hide API authorization errors with demo data. */
export function ClassroomSourceRoute({ role, detail }: { role: DemoRole; detail: boolean }) {
  const { token, user, isLoading } = useAuth();
  const forceMock = import.meta.env.MODE === 'auth-demo';
  const availability = useQuery({
    queryKey: ['classrooms', 'source', token],
    queryFn: () => classroomApi.list(0),
    enabled: Boolean(token) && !forceMock,
    retry: false,
    staleTime: 30_000,
  });

  if (forceMock) {
    const Page = mockPages[role][detail ? 'detail' : 'list'];
    return <Page />;
  }
  if (isLoading) return <div role="status" className="p-8">Loading account…</div>;
  if (!token || !user) return <Navigate to="/login" replace />;
  if (availability.isPending) return <div role="status" className="p-8">Checking classroom API…</div>;
  if (availability.isError) {
    if (import.meta.env.DEV && classroomApiUnavailable(availability.error)) {
      const Page = mockPages[role][detail ? 'detail' : 'list'];
      return <Page />;
    }
    return <div role="alert" className="p-8">
      <p>Classroom API: {classroomError(availability.error)}</p>
      <button type="button" onClick={() => availability.refetch()}>Try again</button>
    </div>;
  }
  if (user.role !== role) return <div role="alert" className="p-8">
    This workspace belongs to {role.toLowerCase()} accounts. <Link to="/classrooms">Open my classrooms</Link>
  </div>;
  return <BackendClassroomPage role={role} />;
}
