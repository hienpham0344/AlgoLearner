import React, { useState } from 'react';
import { useParams, Link, useNavigate, Navigate } from 'react-router-dom';
import { Plus, Search, FileQuestion, Copy, Trash2, Edit2, BarChart2, Eye } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import {
  useQuizList,
  useDeleteQuiz,
  useDuplicateQuiz,
} from '../hooks/useQuizzes';
import type { QuizStatus } from '../types';
import { QuizStatusBadge } from '../components/shared/QuizStatusBadge';
import { QuizKindBadge } from '../components/shared/QuizKindBadge';
import { formatVietnamDateTime, formatDurationHuman } from '../utils/timeUtils';
import { quizError } from '../api/quizApi';

export const TeacherQuizzesPage: React.FC = () => {
  const { id: classId = '' } = useParams<{ id: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<'ALL' | QuizStatus>('ALL');
  const [search, setSearch] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  const { data: pageData, isLoading, isError, error } = useQuizList(classId, page, 20);

  const deleteMutation = useDeleteQuiz(classId);
  const duplicateMutation = useDuplicateQuiz(classId);

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

  const quizzes = pageData?.items || [];

  const filteredQuizzes = quizzes.filter((q) => {
    if (filter !== 'ALL' && q.status !== filter) return false;
    if (search.trim() && !q.title.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  const handleDelete = async (quizId: string) => {
    if (window.confirm('Delete this quiz? This can only be done if no attempts exist.')) {
      setActionError(null);
      try {
        await deleteMutation.mutateAsync(quizId);
      } catch (err) {
        setActionError(quizError(err));
      }
    }
  };

  const handleDuplicate = async (quizId: string) => {
    setActionError(null);
    try {
      const duplicated = await duplicateMutation.mutateAsync(quizId);
      navigate(`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(duplicated.id)}/edit`);
    } catch (err) {
      setActionError(quizError(err));
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
    <ClassroomLayout role="TEACHER" persona={persona} breadcrumbChild="Quizzes">
      <div className="mx-auto max-w-6xl space-y-6 p-4 sm:p-8">
        {/* Top Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <Link
              to={`/teacher/classrooms/${encodeURIComponent(classId)}`}
              className="text-xs font-semibold text-slate-500 hover:text-slate-800"
            >
              ← Back to Classroom
            </Link>
            <h1 className="mt-1 text-2xl font-bold text-slate-900">Class Quizzes</h1>
            <p className="mt-0.5 text-xs text-slate-500">
              Create, configure, and monitor objective quizzes and assessments.
            </p>
          </div>

          <Button
            type="button"
            onClick={() =>
              navigate(`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/new`)
            }
            className="flex items-center gap-1.5 font-bold"
          >
            <Plus size={16} />
            Create Quiz
          </Button>
        </div>

        {actionError && (
          <div
            role="alert"
            className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs font-semibold text-rose-800"
          >
            {actionError}
          </div>
        )}

        {/* Filters & Search */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-3 shadow-xs">
          {/* Status Tabs */}
          <div className="flex items-center gap-1 overflow-x-auto">
            {(['ALL', 'DRAFT', 'PUBLISHED', 'CLOSED'] as const).map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setFilter(s)}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
                  filter === s
                    ? 'bg-slate-900 text-white'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                }`}
              >
                {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
              </button>
            ))}
          </div>

          {/* Search Box */}
          <div className="relative w-full sm:w-64">
            <Search
              size={14}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
            />
            <input
              type="text"
              placeholder="Search quiz title..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full rounded-lg border border-slate-200 pl-8 pr-3 py-1.5 text-xs text-slate-900 focus:border-slate-800 focus:outline-hidden"
            />
          </div>
        </div>

        {/* Content Area */}
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

        {!isLoading && !isError && filteredQuizzes.length === 0 && (
          <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50/50 p-12 text-center">
            <FileQuestion size={40} className="mx-auto text-slate-400 mb-3" />
            <h3 className="text-sm font-bold text-slate-900">No quizzes found</h3>
            <p className="mt-1 text-xs text-slate-500 max-w-sm mx-auto">
              {search || filter !== 'ALL'
                ? 'No quizzes match your filter criteria.'
                : 'Get started by creating your first quiz for this classroom.'}
            </p>
            {(!search && filter === 'ALL') && (
              <Button
                type="button"
                onClick={() =>
                  navigate(`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/new`)
                }
                className="mt-4 font-bold"
                size="sm"
              >
                Create Quiz
              </Button>
            )}
          </div>
        )}

        {/* Quizzes List */}
        {!isLoading && !isError && filteredQuizzes.length > 0 && (
          <div className="grid gap-3">
            {filteredQuizzes.map((quiz) => (
              <div
                key={quiz.id}
                className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-5 shadow-xs hover:border-slate-300 transition-colors"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 mb-1.5">
                    <QuizStatusBadge status={quiz.status} />
                    <QuizKindBadge kind={quiz.kind} />
                    {quiz.timeLimitSeconds && (
                      <span className="text-xs text-slate-500">
                        ⏱ {formatDurationHuman(quiz.timeLimitSeconds)}
                      </span>
                    )}
                  </div>

                  <Link
                    to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quiz.id)}`}
                    className="block text-base font-bold text-slate-900 hover:underline truncate"
                  >
                    {quiz.title}
                  </Link>

                  <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
                    <span>Max attempts: {quiz.maxAttempts}</span>
                    {quiz.deadline && (
                      <span>Deadline: {formatVietnamDateTime(quiz.deadline)}</span>
                    )}
                    {quiz.questions && (
                      <span>Questions: {quiz.questions.length}</span>
                    )}
                  </div>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-2 shrink-0">
                  <Link
                    to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quiz.id)}/results`}
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    <BarChart2 size={14} />
                    Results
                  </Link>

                  <Link
                    to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quiz.id)}`}
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    <Eye size={14} />
                    View
                  </Link>

                  <Link
                    to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quiz.id)}/edit`}
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    <Edit2 size={14} />
                    Edit
                  </Link>

                  <button
                    type="button"
                    onClick={() => handleDuplicate(quiz.id)}
                    title="Duplicate quiz"
                    className="rounded-lg border border-slate-200 bg-white p-1.5 text-slate-600 hover:bg-slate-50"
                  >
                    <Copy size={14} />
                  </button>

                  {quiz.status === 'DRAFT' && (
                    <button
                      type="button"
                      onClick={() => handleDelete(quiz.id)}
                      title="Delete draft quiz"
                      className="rounded-lg border border-slate-200 bg-white p-1.5 text-slate-600 hover:bg-rose-50 hover:text-rose-600"
                    >
                      <Trash2 size={14} />
                    </button>
                  )}
                </div>
              </div>
            ))}
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
