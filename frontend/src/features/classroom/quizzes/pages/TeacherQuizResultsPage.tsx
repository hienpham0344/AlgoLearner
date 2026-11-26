import React, { useState } from 'react';
import { useParams, Link, Navigate } from 'react-router-dom';
import { User, ShieldAlert, History, Edit3, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import { useQuizDetail } from '../hooks/useQuizDetail';
import { useAttemptList } from '../hooks/useQuizAttempt';
import { useAccessOverride, useGradeOverride } from '../hooks/useQuizResult';
import type { AttemptView, AccessOverrideRequest, GradeOverrideRequest } from '../types';
import { AccessOverrideModal } from '../components/teacher/AccessOverrideModal';
import { GradeOverrideModal } from '../components/teacher/GradeOverrideModal';
import { GradeHistoryModal } from '../components/teacher/GradeHistoryModal';
import { formatVietnamDateTime } from '../utils/timeUtils';
import { quizError } from '../api/quizApi';

export const TeacherQuizResultsPage: React.FC = () => {
  const { id: classId = '', quizId = '' } = useParams<{ id: string; quizId: string }>();
  const { user } = useAuth();

  const [page, setPage] = useState(0);
  const [activeModal, setActiveModal] = useState<{
    type: 'ACCESS_OVERRIDE' | 'GRADE_OVERRIDE' | 'GRADE_HISTORY';
    studentId: string;
    studentName?: string;
    currentScore?: number | null;
  } | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);

  const { data: quiz, isLoading: isQuizLoading } = useQuizDetail(classId, quizId);
  const {
    data: attemptsPage,
    isLoading: isAttemptsLoading,
    isError,
    error,
    refetch,
  } = useAttemptList(classId, quizId, page, 50);

  const accessOverrideMutation = useAccessOverride(classId, quizId, activeModal?.studentId || '');
  const gradeOverrideMutation = useGradeOverride(classId, quizId, activeModal?.studentId || '');

  if (user && user.role !== 'TEACHER') {
    return (
      <Navigate
        to={
          user.role === 'STUDENT' && classId
            ? `/student/classrooms/${encodeURIComponent(classId)}/quizzes`
            : '/dashboard'
        }
        replace
      />
    );
  }

  const attempts = attemptsPage?.items || [];

  const handleAccessOverrideSave = async (req: AccessOverrideRequest) => {
    setActionError(null);
    try {
      await accessOverrideMutation.mutateAsync(req);
      void refetch();
    } catch (err) {
      setActionError(quizError(err));
      throw err;
    }
  };

  const handleGradeOverrideSave = async (req: GradeOverrideRequest) => {
    setActionError(null);
    try {
      await gradeOverrideMutation.mutateAsync(req);
      void refetch();
    } catch (err) {
      setActionError(quizError(err));
      throw err;
    }
  };

  const persona = {
    id: user?.id || '',
    username: user?.username || '',
    email: user?.email || '',
    fullName: user?.fullName || user?.username || 'Teacher',
    role: 'TEACHER' as const,
  };

  return (
    <ClassroomLayout role="TEACHER" persona={persona} breadcrumbChild="Quiz Results">
      <div className="mx-auto max-w-6xl space-y-6 p-4 sm:p-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <Link
              to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}`}
              className="inline-flex items-center gap-1 text-xs font-semibold text-slate-500 hover:text-slate-800"
            >
              <ArrowLeft size={13} /> Back to Quiz
            </Link>
            <h1 className="mt-1 text-2xl font-bold text-slate-900">
              Student Attempts & Results
            </h1>
            {quiz && (
              <p className="mt-0.5 text-xs text-slate-500">
                {quiz.title} · Kind: {quiz.kind} · Max attempts: {quiz.maxAttempts}
              </p>
            )}
          </div>
        </div>

        {actionError && (
          <div
            role="alert"
            className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs font-semibold text-rose-800"
          >
            {actionError}
          </div>
        )}

        {/* Attempts Table Card */}
        <div className="rounded-xl border border-slate-200 bg-white shadow-xs overflow-hidden">
          <div className="border-b border-slate-200 px-6 py-4 flex items-center justify-between">
            <h2 className="text-sm font-bold uppercase tracking-wider text-slate-700">
              Submitted Attempts ({attemptsPage?.pagination.totalElements ?? 0})
            </h2>
            <Button variant="outline" size="sm" onClick={() => void refetch()} className="text-xs">
              Refresh
            </Button>
          </div>

          {(isQuizLoading || isAttemptsLoading) && (
            <div className="p-12 text-center text-xs text-slate-500">
              Loading student attempts…
            </div>
          )}

          {isError && (
            <div role="alert" className="p-6 text-center text-xs text-rose-800">
              Failed to load attempts: {quizError(error)}
            </div>
          )}

          {!isAttemptsLoading && !isError && attempts.length === 0 && (
            <div className="p-12 text-center text-xs text-slate-500">
              No students have taken this quiz yet.
            </div>
          )}

          {!isAttemptsLoading && !isError && attempts.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-slate-700">
                <thead className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  <tr>
                    <th scope="col" className="px-6 py-3">Student</th>
                    <th scope="col" className="px-6 py-3">Attempt</th>
                    <th scope="col" className="px-6 py-3">Status</th>
                    <th scope="col" className="px-6 py-3">Started / Submitted</th>
                    <th scope="col" className="px-6 py-3">Score</th>
                    <th scope="col" className="px-6 py-3">Completed</th>
                    <th scope="col" className="px-6 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {attempts.map((att: AttemptView) => (
                    <tr key={att.id} className="hover:bg-slate-50/50">
                      <td className="px-6 py-3 font-medium text-slate-900">
                        <div className="flex items-center gap-1.5">
                          <User size={13} className="text-slate-400" />
                          <span className="font-mono">{att.studentId.slice(0, 8)}…</span>
                        </div>
                      </td>

                      <td className="px-6 py-3 font-semibold">
                        #{att.number}
                      </td>

                      <td className="px-6 py-3">
                        <div className="flex items-center gap-1.5">
                          <span
                            className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-bold ${
                              att.status === 'SUBMITTED'
                                ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                : att.status === 'ACTIVE'
                                  ? 'bg-indigo-50 text-indigo-700 border border-indigo-200'
                                  : 'bg-rose-50 text-rose-700 border border-rose-200'
                            }`}
                          >
                            {att.status}
                          </span>
                          {att.late && (
                            <span className="rounded-full bg-amber-50 px-1.5 py-0.5 text-[10px] font-bold text-amber-700 border border-amber-200">
                              Late
                            </span>
                          )}
                        </div>
                      </td>

                      <td className="px-6 py-3 text-slate-500">
                        <div>Started: {formatVietnamDateTime(att.startedAt)}</div>
                        {att.submittedAt && (
                          <div>Submitted: {formatVietnamDateTime(att.submittedAt)}</div>
                        )}
                      </td>

                      <td className="px-6 py-3 font-bold text-slate-900">
                        {att.score != null ? `${Number(att.score).toFixed(2)}%` : '—'}
                      </td>

                      <td className="px-6 py-3">
                        {att.completed != null ? (
                          <span
                            className={`font-semibold ${
                              att.completed ? 'text-emerald-700' : 'text-rose-700'
                            }`}
                          >
                            {att.completed ? 'Yes' : 'No'}
                          </span>
                        ) : (
                          '—'
                        )}
                      </td>

                      <td className="px-6 py-3 text-right">
                        <div className="inline-flex items-center gap-1.5">
                          <button
                            type="button"
                            onClick={() =>
                              setActiveModal({
                                type: 'ACCESS_OVERRIDE',
                                studentId: att.studentId,
                                studentName: `Student ${att.studentId.slice(0, 8)}`,
                              })
                            }
                            title="Reset attempts or grant extra allowance"
                            className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-semibold text-slate-700 hover:bg-slate-50"
                          >
                            <ShieldAlert size={12} />
                            Access
                          </button>

                          <button
                            type="button"
                            onClick={() =>
                              setActiveModal({
                                type: 'GRADE_OVERRIDE',
                                studentId: att.studentId,
                                studentName: `Student ${att.studentId.slice(0, 8)}`,
                                currentScore: att.score,
                              })
                            }
                            title="Override student final score"
                            className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-semibold text-slate-700 hover:bg-slate-50"
                          >
                            <Edit3 size={12} />
                            Grade
                          </button>

                          <button
                            type="button"
                            onClick={() =>
                              setActiveModal({
                                type: 'GRADE_HISTORY',
                                studentId: att.studentId,
                                studentName: `Student ${att.studentId.slice(0, 8)}`,
                              })
                            }
                            title="View grade audit history"
                            className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-semibold text-slate-700 hover:bg-slate-50"
                          >
                            <History size={12} />
                            Logs
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination Controls */}
          {attemptsPage && attemptsPage.pagination.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-slate-200 px-6 py-3 text-xs text-slate-600">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="rounded-lg border border-slate-300 px-3 py-1.5 disabled:opacity-40"
              >
                Previous
              </button>
              <span>
                Page {page + 1} of {attemptsPage.pagination.totalPages}
              </span>
              <button
                type="button"
                disabled={page + 1 >= attemptsPage.pagination.totalPages}
                onClick={() => setPage((p) => p + 1)}
                className="rounded-lg border border-slate-300 px-3 py-1.5 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          )}
        </div>

        {/* Modals */}
        {activeModal?.type === 'ACCESS_OVERRIDE' && (
          <AccessOverrideModal
            isOpen
            studentId={activeModal.studentId}
            studentName={activeModal.studentName}
            onSave={handleAccessOverrideSave}
            onClose={() => setActiveModal(null)}
          />
        )}

        {activeModal?.type === 'GRADE_OVERRIDE' && (
          <GradeOverrideModal
            isOpen
            studentId={activeModal.studentId}
            studentName={activeModal.studentName}
            currentScore={activeModal.currentScore}
            onSave={handleGradeOverrideSave}
            onClose={() => setActiveModal(null)}
          />
        )}

        {activeModal?.type === 'GRADE_HISTORY' && (
          <GradeHistoryModal
            isOpen
            classroomId={classId}
            quizId={quizId}
            studentId={activeModal.studentId}
            studentName={activeModal.studentName}
            onClose={() => setActiveModal(null)}
          />
        )}
      </div>
    </ClassroomLayout>
  );
};
