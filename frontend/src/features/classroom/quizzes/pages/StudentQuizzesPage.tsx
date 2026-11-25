import React, { useState, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Clock, Calendar, Search, AlertCircle, BookOpen } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { quizApi, quizError } from '../api/quizApi';
import { quizKeys } from '../api/quizKeys';
import { QuizStatusBadge } from '../components/shared/QuizStatusBadge';
import { QuizKindBadge } from '../components/shared/QuizKindBadge';
import { formatVietnamDateTime, formatDurationMinutes, isPastDeadline, isBeforeStartTime } from '../utils/quizTimeUtils';
import type { QuizView } from '../types';

export const StudentQuizzesPage: React.FC = () => {
  const { id: classId } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'OPEN' | 'CLOSED'>('ALL');

  const {
    data: quizzes = [],
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery<QuizView[]>({
    queryKey: quizKeys.list(classId || ''),
    queryFn: ({ signal }) => quizApi.list(classId || '', signal),
    enabled: Boolean(classId),
  });

  const filteredQuizzes = useMemo(() => {
    return quizzes.filter((q) => {
      const matchesSearch = q.title.toLowerCase().includes(searchQuery.toLowerCase().trim());
      if (!matchesSearch) return false;

      const past = isPastDeadline(q.closesAt);
      const before = isBeforeStartTime(q.opensAt);

      if (statusFilter === 'OPEN') {
        return q.status === 'PUBLISHED' && !before && (!past || q.allowLate);
      }
      if (statusFilter === 'CLOSED') {
        return q.status === 'CLOSED' || (past && !q.allowLate);
      }
      return true;
    });
  }, [quizzes, searchQuery, statusFilter]);

  if (!classId) {
    return null;
  }

  return (
    <div className="min-h-screen bg-slate-50/60 pb-16">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 pt-6 sm:pt-8 flex flex-col gap-6">
        {/* Breadcrumb */}
        <div className="flex items-center gap-2 text-xs font-semibold text-slate-500">
          <Link
            to={`/student/classrooms/${encodeURIComponent(classId)}`}
            className="inline-flex items-center gap-1.5 hover:text-slate-900 transition-colors"
          >
            <ArrowLeft size={14} className="shrink-0" />
            <span>Back to Classroom</span>
          </Link>
          <span>/</span>
          <span className="text-slate-900">Classroom Quizzes</span>
        </div>

        {/* Page Title & Stats */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-200/80 pb-5">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-slate-900">
              Classroom Quizzes
            </h1>
            <p className="text-xs sm:text-sm text-slate-500 mt-1">
              Review and take objective quizzes and practice assessments
            </p>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <span className="text-xs font-semibold px-3 py-1 bg-white border border-slate-200 rounded-xl text-slate-700 shadow-2xs">
              Total: {quizzes.length} quizzes
            </span>
          </div>
        </div>

        {/* Filters & Search Toolbar */}
        <div className="bg-white rounded-2xl border border-slate-200/80 p-3 sm:p-4 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="relative flex-1 max-w-md">
            <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search quizzes by title…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 text-xs rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-600 transition-all"
            />
          </div>

          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
            <button
              type="button"
              onClick={() => setStatusFilter('ALL')}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                statusFilter === 'ALL'
                  ? 'bg-slate-900 text-white shadow-2xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200/80'
              }`}
            >
              All
            </button>
            <button
              type="button"
              onClick={() => setStatusFilter('OPEN')}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                statusFilter === 'OPEN'
                  ? 'bg-emerald-600 text-white shadow-2xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200/80'
              }`}
            >
              Open
            </button>
            <button
              type="button"
              onClick={() => setStatusFilter('CLOSED')}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                statusFilter === 'CLOSED'
                  ? 'bg-rose-600 text-white shadow-2xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200/80'
              }`}
            >
              Closed
            </button>
          </div>
        </div>

        {/* Content Area */}
        {isLoading ? (
          <div className="space-y-3">
            {[1, 2, 3].map((n) => (
              <div key={n} className="h-28 bg-white rounded-2xl border border-slate-200/70 p-5 animate-pulse" />
            ))}
          </div>
        ) : isError ? (
          <div className="p-8 text-center bg-white rounded-2xl border border-rose-200 shadow-xs">
            <AlertCircle size={32} className="mx-auto text-rose-500 mb-2" />
            <h3 className="text-sm font-bold text-slate-900">Unable to load classroom quizzes</h3>
            <p className="text-xs text-slate-500 mt-1">{quizError(error)}</p>
            <Button
              size="sm"
              onClick={() => refetch()}
              className="mt-4 bg-slate-900 text-white text-xs rounded-xl"
            >
              Retry
            </Button>
          </div>
        ) : filteredQuizzes.length === 0 ? (
          <div className="p-12 text-center bg-white rounded-2xl border border-dashed border-slate-300">
            <BookOpen size={36} className="mx-auto text-slate-400 mb-3" />
            <h3 className="text-sm font-bold text-slate-800">No matching quizzes found</h3>
            <p className="text-xs text-slate-500 mt-1">
              {searchQuery ? 'Try adjusting your search criteria' : 'No quizzes have been published in this classroom yet.'}
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3.5">
            {filteredQuizzes.map((quiz) => {
              const isClosed = quiz.status === 'CLOSED' || (isPastDeadline(quiz.closesAt) && !quiz.allowLate);
              const isUpcoming = isBeforeStartTime(quiz.opensAt);

              return (
                <article
                  key={quiz.id}
                  onClick={() => navigate(`/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quiz.id)}`)}
                  className="bg-white rounded-2xl border border-slate-200/80 hover:border-indigo-300 hover:shadow-md p-5 sm:p-6 transition-all cursor-pointer flex flex-col md:flex-row md:items-center justify-between gap-4"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex flex-wrap items-center gap-2 mb-2">
                      <QuizStatusBadge status={quiz.status} />
                      <QuizKindBadge graded={quiz.graded} />
                      {quiz.allowLate && (
                        <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-purple-50 text-purple-700 border border-purple-200">
                          Late submissions allowed
                        </span>
                      )}
                    </div>

                    <h2 className="text-base font-bold text-slate-900 hover:text-indigo-600 transition-colors truncate">
                      {quiz.title}
                    </h2>

                    <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-slate-500 mt-2.5">
                      <span className="inline-flex items-center gap-1.5">
                        <Clock size={13} className="text-slate-400" />
                        Duration: <strong className="text-slate-700 font-semibold">{formatDurationMinutes(quiz.durationSeconds)}</strong>
                      </span>
                      <span className="inline-flex items-center gap-1.5">
                        <Calendar size={13} className="text-slate-400" />
                        Deadline: <strong className="text-slate-700 font-semibold">{formatVietnamDateTime(quiz.closesAt)}</strong>
                      </span>
                      <span className="inline-flex items-center gap-1.5">
                        Limit: <strong className="text-slate-700 font-semibold">{quiz.maxAttempts} attempt(s)</strong>
                      </span>
                    </div>
                  </div>

                  <div className="shrink-0 flex items-center justify-end">
                    <Button
                      size="sm"
                      className="bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold px-4 h-9 shadow-xs"
                    >
                      {isClosed ? 'View Quiz →' : isUpcoming ? 'View Details →' : 'Take Quiz →'}
                    </Button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
