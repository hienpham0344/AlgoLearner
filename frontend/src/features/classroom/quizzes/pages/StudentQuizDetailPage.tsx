import React, { useState } from 'react';
import { useParams, Link, useNavigate, Navigate } from 'react-router-dom';
import { Clock, AlertTriangle, Award, CheckCircle2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import { useQuizDetail } from '../hooks/useQuizDetail';
import { useAttemptList, useStartOrResumeAttempt } from '../hooks/useQuizAttempt';
import { useQuizResult } from '../hooks/useQuizResult';
import { QuizStatusBadge } from '../components/shared/QuizStatusBadge';
import { QuizKindBadge } from '../components/shared/QuizKindBadge';
import { AttemptHistoryCard } from '../components/student/AttemptHistoryCard';
import { formatVietnamDateTime, formatDurationHuman, isPastDeadline } from '../utils/timeUtils';
import { canStudentStartAttempt } from '../utils/quizValidation';
import { quizError } from '../api/quizApi';

export const StudentQuizDetailPage: React.FC = () => {
  const { id: classId = '', quizId = '' } = useParams<{ id: string; quizId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [startError, setStartError] = useState<string | null>(null);

  const { data: quiz, isLoading: isQuizLoading, isError, error } = useQuizDetail(classId, quizId);
  const { data: attemptsData } = useAttemptList(classId, quizId, 0, 50);
  const { data: result } = useQuizResult(classId, quizId, user?.id || '');

  const startMutation = useStartOrResumeAttempt(classId, quizId);

  if (user && user.role !== 'STUDENT') {
    return (
      <Navigate
        to={
          user.role === 'TEACHER' && classId
            ? `/teacher/classrooms/${encodeURIComponent(classId)}/quizzes`
            : '/dashboard'
        }
        replace
      />
    );
  }

  const attempts = attemptsData?.items || [];
  const activeAttempt = attempts.find((a) => a.status === 'ACTIVE');

  const handleStartOrResume = async () => {
    setStartError(null);
    try {
      await startMutation.mutateAsync();
      navigate(
        `/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}/attempt`
      );
    } catch (err) {
      setStartError(quizError(err));
    }
  };

  const persona = {
    id: user?.id || '',
    username: user?.username || '',
    email: user?.email || '',
    fullName: user?.fullName || user?.username || 'Student',
    role: 'STUDENT' as const,
  };

  if (isQuizLoading) {
    return (
      <div className="flex h-screen items-center justify-center text-xs text-slate-500">
        Loading quiz briefing…
      </div>
    );
  }

  if (isError || !quiz) {
    return (
      <div className="p-8 text-center text-sm text-rose-800">
        Error loading quiz: {quizError(error)}
      </div>
    );
  }

  const eligibility = canStudentStartAttempt(quiz, result, Boolean(activeAttempt));

  return (
    <ClassroomLayout role="STUDENT" persona={persona} breadcrumbChild={quiz.title}>
      <div className="mx-auto max-w-4xl space-y-6 p-4 sm:p-8">
        {/* Header */}
        <div>
          <Link
            to={`/student/classrooms/${encodeURIComponent(classId)}/quizzes`}
            className="text-xs font-semibold text-slate-500 hover:text-slate-800"
          >
            ← Back to Quizzes
          </Link>
          <div className="mt-2 flex items-center gap-2">
            <QuizStatusBadge status={quiz.status} />
            <QuizKindBadge kind={quiz.kind} />
          </div>
          <h1 className="mt-1 text-2xl font-bold text-slate-900">{quiz.title}</h1>
        </div>

        {startError && (
          <div
            role="alert"
            className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs font-semibold text-rose-800"
          >
            {startError}
          </div>
        )}

        {/* Rules & Overview Grid */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 rounded-xl border border-slate-200 bg-white p-6 shadow-xs text-xs">
          <div>
            <h2 className="font-bold uppercase tracking-wider text-slate-500 mb-2">Quiz Information</h2>
            <ul className="space-y-2 text-slate-700">
              <li className="flex items-center gap-2">
                <Clock size={14} className="text-slate-400" />
                <span>
                  <strong>Time Limit: </strong>
                  {formatDurationHuman(quiz.timeLimitSeconds)}
                </span>
              </li>
              <li>
                <strong>Attempts Allowed: </strong>
                {quiz.maxAttempts}
              </li>
              <li>
                <strong>Attempts Used: </strong>
                {attempts.filter((a) => a.status === 'SUBMITTED').length}
              </li>
            </ul>
          </div>

          <div>
            <h2 className="font-bold uppercase tracking-wider text-slate-500 mb-2">Timeline & Availability</h2>
            <ul className="space-y-2 text-slate-700">
              <li>
                <strong>Opens: </strong>
                {quiz.startTime ? formatVietnamDateTime(quiz.startTime) : 'Immediately available'}
              </li>
              <li>
                <strong>Deadline: </strong>
                {quiz.deadline ? formatVietnamDateTime(quiz.deadline) : 'No deadline'}
              </li>
              {quiz.deadline && (
                <li className={isPastDeadline(quiz.deadline) ? 'text-amber-700 font-semibold' : ''}>
                  <strong>Status: </strong>
                  {isPastDeadline(quiz.deadline) ? 'Deadline has passed' : 'Open'}
                </li>
              )}
            </ul>
          </div>
        </div>

        {/* Official Score Card if available */}
        {result?.finalScore != null && (
          <div className="rounded-xl border border-indigo-100 bg-indigo-50/50 p-5 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-indigo-600 text-white">
                <Award size={20} />
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wider text-indigo-700">
                  Your Official Score
                </p>
                <p className="text-xl font-extrabold text-slate-900">
                  {Number(result.finalScore).toFixed(2)}%
                </p>
              </div>
            </div>

            {result.completed != null && (
              <span
                className={`inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-bold ${
                  result.completed
                    ? 'border border-emerald-200 bg-emerald-50 text-emerald-800'
                    : 'border border-rose-200 bg-rose-50 text-rose-800'
                }`}
              >
                <CheckCircle2 size={13} />
                {result.completed ? 'Passed' : 'Not completed'}
              </span>
            )}
          </div>
        )}

        {/* Action Button Card */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs text-center space-y-3">
          {activeAttempt ? (
            <div>
              <p className="text-xs font-semibold text-indigo-600 mb-2">
                You have an active attempt (# {activeAttempt.number}) currently in progress.
              </p>
              <Button
                type="button"
                onClick={handleStartOrResume}
                disabled={startMutation.isPending}
                className="w-full sm:w-auto px-8 font-bold"
              >
                {startMutation.isPending ? 'Resuming…' : 'Resume Attempt →'}
              </Button>
            </div>
          ) : eligibility.canStart ? (
            <div>
              <p className="text-xs text-slate-500 mb-3">
                When you start, the server timer will begin counting down. Ensure you have a stable connection.
              </p>
              <Button
                type="button"
                onClick={handleStartOrResume}
                disabled={startMutation.isPending}
                className="w-full sm:w-auto px-8 font-bold"
              >
                {startMutation.isPending ? 'Starting…' : 'Start Quiz Attempt →'}
              </Button>
            </div>
          ) : (
            <div className="flex items-center justify-center gap-2 text-xs font-semibold text-rose-700 bg-rose-50 p-3 rounded-lg border border-rose-200">
              <AlertTriangle size={15} />
              <span>{eligibility.reason || 'You cannot start this quiz at this time.'}</span>
            </div>
          )}
        </div>

        {/* Previous Attempts History */}
        <AttemptHistoryCard
          attempts={attempts}
          classroomId={classId}
          quizId={quizId}
          quizKind={quiz.kind}
        />
      </div>
    </ClassroomLayout>
  );
};
