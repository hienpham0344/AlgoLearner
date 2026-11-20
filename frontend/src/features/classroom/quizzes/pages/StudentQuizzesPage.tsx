import React, { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { FileQuestion, ArrowRight, Clock } from 'lucide-react';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import { useQuizList } from '../hooks/useQuizzes';
import { QuizStatusBadge } from '../components/shared/QuizStatusBadge';
import { QuizKindBadge } from '../components/shared/QuizKindBadge';
import { formatVietnamDateTime, formatDurationHuman, isPastDeadline } from '../utils/timeUtils';
import { quizError } from '../api/quizApi';

export const StudentQuizzesPage: React.FC = () => {
  const { id: classId = '' } = useParams<{ id: string }>();
  const { user } = useAuth();
  const [page, setPage] = useState(0);

  const { data: pageData, isLoading, isError, error } = useQuizList(classId, page, 20);

  const quizzes = pageData?.items || [];

  const persona = {
    id: user?.id || '',
    username: user?.username || '',
    email: user?.email || '',
    fullName: user?.fullName || user?.username || 'Student',
    role: 'STUDENT' as const,
  };

  return (
    <ClassroomLayout role="STUDENT" persona={persona} breadcrumbChild="Quizzes">
      <div className="mx-auto max-w-5xl space-y-6 p-4 sm:p-8">
        {/* Header */}
        <div>
          <Link
            to={`/student/classrooms/${encodeURIComponent(classId)}`}
            className="text-xs font-semibold text-slate-500 hover:text-slate-800"
          >
            ← Back to Classroom
          </Link>
          <h1 className="mt-1 text-2xl font-bold text-slate-900">Class Quizzes</h1>
          <p className="mt-0.5 text-xs text-slate-500">
            View available tests, practice assessments, and your submission results.
          </p>
        </div>

        {/* Content */}
        {isLoading && (
          <div className="rounded-xl border border-slate-200 bg-white p-12 text-center text-xs text-slate-500">
            Loading quizzes…
          </div>
        )}

        {isError && (
          <div
            role="alert"
            className="rounded-xl border border-rose-200 bg-rose-50 p-6 text-center text-xs text-rose-800"
          >
            Failed to load quizzes: {quizError(error)}
          </div>
        )}

        {!isLoading && !isError && quizzes.length === 0 && (
          <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50/50 p-12 text-center">
            <FileQuestion size={40} className="mx-auto text-slate-400 mb-3" />
            <h3 className="text-sm font-bold text-slate-900">No quizzes available</h3>
            <p className="mt-1 text-xs text-slate-500 max-w-sm mx-auto">
              There are currently no quizzes published for this classroom. Check back later!
            </p>
          </div>
        )}

        {/* Quizzes List */}
        {!isLoading && !isError && quizzes.length > 0 && (
          <div className="grid gap-3.5">
            {quizzes.map((quiz) => {
              const pastDeadline = isPastDeadline(quiz.deadline);

              return (
                <div
                  key={quiz.id}
                  className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-5 shadow-xs hover:border-slate-300 transition-colors"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1.5">
                      <QuizStatusBadge status={quiz.status} />
                      <QuizKindBadge kind={quiz.kind} />
                      {quiz.timeLimitSeconds && (
                        <span className="flex items-center gap-1 text-xs text-slate-500">
                          <Clock size={12} />
                          {formatDurationHuman(quiz.timeLimitSeconds)}
                        </span>
                      )}
                    </div>

                    <Link
                      to={`/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quiz.id)}`}
                      className="block text-base font-bold text-slate-900 hover:underline truncate"
                    >
                      {quiz.title}
                    </Link>

                    <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
                      <span>Max attempts: {quiz.maxAttempts}</span>
                      {quiz.startTime && (
                        <span>Opens: {formatVietnamDateTime(quiz.startTime)}</span>
                      )}
                      {quiz.deadline && (
                        <span className={pastDeadline ? 'text-amber-700 font-semibold' : ''}>
                          Due: {formatVietnamDateTime(quiz.deadline)}
                          {pastDeadline ? ' (Passed)' : ''}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="shrink-0">
                    <Link
                      to={`/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quiz.id)}`}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-slate-900 px-4 py-2 text-xs font-bold text-white hover:bg-slate-800 transition-colors"
                    >
                      <span>View Quiz</span>
                      <ArrowRight size={13} />
                    </Link>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Pagination */}
        {pageData && pageData.pagination.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-200 pt-4 text-xs font-medium text-slate-600">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-lg border border-slate-300 px-3 py-1.5 disabled:opacity-40"
            >
              Previous
            </button>
            <span>
              Page {page + 1} of {pageData.pagination.totalPages}
            </span>
            <button
              type="button"
              disabled={page + 1 >= pageData.pagination.totalPages}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-lg border border-slate-300 px-3 py-1.5 disabled:opacity-40"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </ClassroomLayout>
  );
};
