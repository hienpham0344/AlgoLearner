import React from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Clock, AlertTriangle, Play, RotateCcw, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { quizApi, quizError } from '../api/quizApi';
import { quizKeys } from '../api/quizKeys';
import { QuizStatusBadge } from '../components/shared/QuizStatusBadge';
import { QuizKindBadge } from '../components/shared/QuizKindBadge';
import { AttemptHistoryCard } from '../components/student/AttemptHistoryCard';
import { formatVietnamDateTime, formatDurationMinutes, isPastDeadline, isBeforeStartTime } from '../utils/quizTimeUtils';
import type { PaginatedData } from '@/types/api';
import type { QuizView, AttemptView } from '../types';

export const StudentQuizDetailPage: React.FC = () => {
  const { id: classId, quizId } = useParams<{ id: string; quizId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // Load quiz overview
  const {
    data: quiz,
    isLoading: isLoadingQuiz,
    isError: isQuizError,
    error: quizLoadError,
  } = useQuery<QuizView>({
    queryKey: quizKeys.detail(classId || '', quizId || ''),
    queryFn: ({ signal }) => quizApi.get(classId || '', quizId || '', signal),
    enabled: Boolean(classId && quizId),
  });

  // Load attempt history for current student
  const {
    data: historyData,
    isLoading: isLoadingHistory,
  } = useQuery<PaginatedData<AttemptView>>({
    queryKey: quizKeys.attemptHistory(classId || '', quizId || ''),
    queryFn: ({ signal }) => quizApi.history(classId || '', quizId || '', undefined, 0, 50, signal),
    enabled: Boolean(classId && quizId),
  });

  const attempts = historyData?.items || [];
  const inProgressAttempt = attempts.find((a) => a.status === 'IN_PROGRESS');
  const submittedAttempts = attempts.filter((a) => a.status === 'SUBMITTED');

  // Mutation to start or resume attempt
  const startMutation = useMutation({
    mutationFn: () => quizApi.startAttempt(classId || '', quizId || ''),
    onSuccess: (attempt) => {
      queryClient.setQueryData(quizKeys.attemptDetail(classId || '', quizId || '', attempt.id), attempt);
      navigate(`/student/classrooms/${encodeURIComponent(classId || '')}/quizzes/${encodeURIComponent(quizId || '')}/attempt`);
    },
  });

  if (!classId || !quizId) return null;

  if (isLoadingQuiz || isLoadingHistory) {
    return (
      <div className="min-h-screen bg-slate-50/60 p-6 flex items-center justify-center">
        <div className="w-8 h-8 rounded-full border-2 border-indigo-600 border-t-transparent animate-spin" />
      </div>
    );
  }

  if (isQuizError || !quiz) {
    return (
      <div className="min-h-screen bg-slate-50/60 p-6">
        <div className="max-w-2xl mx-auto bg-white rounded-2xl border border-rose-200 p-8 text-center shadow-xs">
          <AlertCircle size={36} className="mx-auto text-rose-500 mb-3" />
          <h2 className="text-base font-bold text-slate-900">Không thể tải thông tin bài kiểm tra</h2>
          <p className="text-xs text-slate-500 mt-1">{quizError(quizLoadError)}</p>
          <Button
            size="sm"
            onClick={() => navigate(`/student/classrooms/${encodeURIComponent(classId)}/quizzes`)}
            className="mt-4 bg-slate-900 text-white text-xs rounded-xl"
          >
            ← Quay lại danh sách bài kiểm tra
          </Button>
        </div>
      </div>
    );
  }

  const isClosed = quiz.status === 'CLOSED' || (isPastDeadline(quiz.closesAt) && !quiz.allowLate);
  const isUpcoming = isBeforeStartTime(quiz.opensAt);
  const attemptsUsed = submittedAttempts.length;
  const hasRemainingAttempts = attemptsUsed < quiz.maxAttempts;

  const canStart = !isClosed && !isUpcoming && (hasRemainingAttempts || Boolean(inProgressAttempt));

  const handleStartOrResume = () => {
    startMutation.mutate();
  };

  return (
    <div className="min-h-screen bg-slate-50/60 pb-16">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 pt-6 sm:pt-8 flex flex-col gap-6">
        {/* Breadcrumb */}
        <div className="flex items-center gap-2 text-xs font-semibold text-slate-500">
          <Link
            to={`/student/classrooms/${encodeURIComponent(classId)}/quizzes`}
            className="inline-flex items-center gap-1.5 hover:text-slate-900 transition-colors"
          >
            <ArrowLeft size={14} className="shrink-0" />
            <span>Classroom Quizzes</span>
          </Link>
          <span>/</span>
          <span className="text-slate-900 truncate">{quiz.title}</span>
        </div>

        {/* Hero Card */}
        <div className="bg-white rounded-2xl border border-slate-200/80 p-6 sm:p-8 shadow-xs flex flex-col gap-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <QuizStatusBadge status={quiz.status} />
              <QuizKindBadge graded={quiz.graded} />
              {quiz.allowLate && (
                <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-purple-50 text-purple-700 border border-purple-200">
                  Late submission allowed
                </span>
              )}
            </div>

            {inProgressAttempt && (
              <span className="inline-flex items-center gap-1.5 text-xs font-bold text-amber-800 bg-amber-50 border border-amber-300 px-3 py-1 rounded-full animate-pulse">
                <Clock size={13} />
                Active attempt in progress
              </span>
            )}
          </div>

          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-slate-900">
              {quiz.title}
            </h1>
            <p className="text-xs sm:text-sm text-slate-500 mt-1">
              Please review the assessment instructions and time limits before starting.
            </p>
          </div>

          {/* Quick Info Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-4 rounded-xl bg-slate-50 border border-slate-100">
            <div>
              <span className="text-[11px] font-medium text-slate-400 block">Time Limit</span>
              <span className="text-sm font-bold text-slate-800 flex items-center gap-1 mt-0.5">
                <Clock size={14} className="text-slate-500" />
                {formatDurationMinutes(quiz.durationSeconds)}
              </span>
            </div>

            <div>
              <span className="text-[11px] font-medium text-slate-400 block">Attempts</span>
              <span className="text-sm font-bold text-slate-800 mt-0.5 block">
                {attemptsUsed}/{quiz.maxAttempts} used
              </span>
            </div>

            <div>
              <span className="text-[11px] font-medium text-slate-400 block">Opens At</span>
              <span className="text-xs font-semibold text-slate-800 mt-0.5 block">
                {formatVietnamDateTime(quiz.opensAt)}
              </span>
            </div>

            <div>
              <span className="text-[11px] font-medium text-slate-400 block">Deadline</span>
              <span className="text-xs font-semibold text-slate-800 mt-0.5 block">
                {formatVietnamDateTime(quiz.closesAt)}
              </span>
            </div>
          </div>

          {/* Important Rules / Instructions */}
          <div className="p-4 rounded-xl bg-blue-50/70 border border-blue-200 text-xs text-blue-900 space-y-2">
            <h3 className="font-bold text-blue-950 flex items-center gap-1.5">
              <AlertTriangle size={14} className="text-blue-600" />
              Important assessment guidelines:
            </h3>
            <ul className="list-disc list-inside space-y-1 text-blue-800 pl-1 leading-relaxed">
              <li>Answers are <strong>atomically autosaved</strong> whenever you select an option.</li>
              <li>Timer synchronizes with the server. When time expires, your work is automatically submitted.</li>
              <li>If you lose internet connection or reload, you can resume your active attempt without losing answers.</li>
              <li>For graded quizzes, official scores and feedback are released following teacher publication.</li>
            </ul>
          </div>

          {/* Action Trigger */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-t border-slate-100 pt-5">
            <div className="text-xs text-slate-500">
              {inProgressAttempt ? (
                <span className="text-amber-800 font-semibold">
                  You have an active attempt in progress. Click below to resume your session.
                </span>
              ) : hasRemainingAttempts ? (
                <span>
                  You have <strong>{quiz.maxAttempts - attemptsUsed}</strong> attempt(s) remaining.
                </span>
              ) : (
                <span className="text-rose-600 font-medium">
                  You have reached the maximum allowed attempts.
                </span>
              )}
            </div>

            <Button
              type="button"
              size="lg"
              onClick={handleStartOrResume}
              disabled={!canStart || startMutation.isPending}
              className={`rounded-xl text-sm font-bold px-6 h-11 shadow-sm flex items-center justify-center gap-2 ${
                inProgressAttempt
                  ? 'bg-amber-600 hover:bg-amber-700 text-white'
                  : 'bg-indigo-600 hover:bg-indigo-700 text-white'
              }`}
            >
              {startMutation.isPending ? (
                <span>Loading workspace…</span>
              ) : inProgressAttempt ? (
                <>
                  <RotateCcw size={16} />
                  <span>Resume Quiz Attempt →</span>
                </>
              ) : canStart ? (
                <>
                  <Play size={16} className="fill-white" />
                  <span>Start Quiz Attempt →</span>
                </>
              ) : isUpcoming ? (
                <span>Not Yet Open</span>
              ) : isClosed ? (
                <span>Quiz Closed</span>
              ) : (
                <span>Attempts Exhausted</span>
              )}
            </Button>
          </div>

          {startMutation.isError && (
            <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-700">
              {quizError(startMutation.error)}
            </div>
          )}
        </div>

        {/* History Component */}
        <AttemptHistoryCard
          attempts={attempts}
          maxAttempts={quiz.maxAttempts}
          onContinueAttempt={() => handleStartOrResume()}
          onViewResult={(attemptId) => {
            navigate(
              `/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}/result?attemptId=${encodeURIComponent(attemptId)}`
            );
          }}
        />
      </div>
    </div>
  );
};
