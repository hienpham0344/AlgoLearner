import React from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, BookOpen, ClipboardCheck, FileCode2 } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { useIsDemoMode } from '@/components/BackendStatusBanner';
import { WorkspaceLayout } from '@/components/layout/WorkspaceLayout';
import { LoadingScreen } from '@/components/ui/LoadingScreen';
import { classroomApi, classroomError, classroomKeys } from '../../api/classroomApi';
import { readStoredClassrooms } from '../../mock/useClassroomStore';

export const TeacherAssignmentsHubPage: React.FC = () => {
  const { user } = useAuth();
  const isDemo = useIsDemoMode();

  const classroomsQuery = useQuery({
    queryKey: classroomKeys.list(0),
    queryFn: () => classroomApi.list(0),
    enabled: Boolean(user && user.role === 'TEACHER') && !isDemo,
    retry: false,
  });

  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'TEACHER') return <Navigate to="/dashboard" replace />;

  const demoClassrooms = isDemo ? readStoredClassrooms() : [];
  const classrooms = isDemo
    ? demoClassrooms.map((c) => ({
        id: c.id,
        name: c.name,
        description: c.description || '',
        status: c.status,
      }))
    : (classroomsQuery.data?.items || []);

  return (
    <WorkspaceLayout role="TEACHER">
      <div className="mx-auto max-w-6xl space-y-6 p-4 sm:p-8">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">Classroom Assignments & Quizzes</h1>
            <p className="mt-1 text-sm text-slate-500">
              Select an active classroom to author assignments, grade submissions, or manage timed quizzes.
            </p>
          </div>
          <Link
            to="/teacher/classrooms"
            className="inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-xs font-semibold text-slate-800 hover:bg-slate-50 transition"
          >
            <BookOpen size={15} />
            <span>Manage Classrooms</span>
          </Link>
        </div>

        {!isDemo && classroomsQuery.isPending && (
          <LoadingScreen variant="inline" message="Loading your classrooms…" />
        )}

        {!isDemo && classroomsQuery.isError && (
          <div
            role="alert"
            className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800"
          >
            {classroomError(classroomsQuery.error)}
          </div>
        )}

        {(!classroomsQuery.isPending || isDemo) && classrooms.length === 0 && (
          <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center shadow-xs space-y-3">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-600">
              <FileCode2 size={22} />
            </div>
            <h2 className="text-base font-bold text-slate-900">No classrooms yet</h2>
            <p className="mx-auto max-w-md text-xs text-slate-500">
              Assignments and quizzes are organized inside classrooms. Create a classroom first to begin authoring assessments.
            </p>
            <div className="pt-2">
              <Link
                to="/teacher/classrooms"
                className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-5 py-2.5 text-xs font-semibold text-white hover:bg-slate-800 transition"
              >
                <span>Open My Classes</span>
                <ArrowRight size={14} />
              </Link>
            </div>
          </div>
        )}

        {classrooms.length > 0 && (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {classrooms.map((cls) => (
              <section
                key={cls.id}
                className="flex flex-col justify-between rounded-2xl border border-slate-200 bg-white p-6 shadow-xs space-y-4"
              >
                <div>
                  <div className="flex items-center justify-between gap-2">
                    <h2 className="text-base font-bold text-slate-900 truncate">{cls.name}</h2>
                    <span
                      className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${
                        cls.status === 'ACTIVE'
                          ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                          : 'bg-slate-100 text-slate-600 border border-slate-200'
                      }`}
                    >
                      {cls.status}
                    </span>
                  </div>
                  <p className="mt-1.5 line-clamp-2 text-xs text-slate-500">
                    {cls.description || 'Manage coursework, coding tasks, and timed assessments for this class.'}
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-2.5 pt-3 border-t border-slate-100">
                  <Link
                    to={`/teacher/classrooms/${encodeURIComponent(cls.id)}/assignments`}
                    className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-4 py-2 text-xs font-semibold text-white hover:bg-slate-800 transition"
                  >
                    <FileCode2 size={14} />
                    <span>Assignments</span>
                    <ArrowRight size={13} />
                  </Link>
                  <Link
                    to={`/teacher/classrooms/${encodeURIComponent(cls.id)}/quizzes`}
                    className="inline-flex items-center gap-1.5 rounded-xl border border-slate-300 bg-white px-4 py-2 text-xs font-semibold text-slate-800 hover:bg-slate-50 transition"
                  >
                    <ClipboardCheck size={14} />
                    <span>Quizzes</span>
                    <ArrowRight size={13} />
                  </Link>
                </div>
              </section>
            ))}
          </div>
        )}
      </div>
    </WorkspaceLayout>
  );
};
