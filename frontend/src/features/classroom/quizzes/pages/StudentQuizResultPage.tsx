import React from 'react';
import { useParams, Link, Navigate } from 'react-router-dom';
import { ArrowLeft, RotateCcw } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import { useQuizDetail } from '../hooks/useQuizDetail';
import { useQuizResult } from '../hooks/useQuizResult';
import { useAttemptList, useAttemptDetail } from '../hooks/useQuizAttempt';
import { QuizResultSummary } from '../components/student/QuizResultSummary';
import { quizError } from '../api/quizApi';

export const StudentQuizResultPage: React.FC = () => {
  const { id: classId = '', quizId = '' } = useParams<{ id: string; quizId: string }>();
  const { user } = useAuth();

  const { data: quiz, isLoading: isQuizLoading, isError, error } = useQuizDetail(classId, quizId);
  const { data: result, isLoading: isResultLoading } = useQuizResult(
    classId,
    quizId,
    user?.id || ''
  );
  const { data: attemptsData, isLoading: isAttemptsLoading } = useAttemptList(classId, quizId, 0, 50);

  const attempts = attemptsData?.items || [];
  const latestAttemptSummary = attempts[0] || null;
  const { data: enrichedAttempt, isLoading: isReviewLoading } = useAttemptDetail(
    classId,
    quizId,
    latestAttemptSummary?.id || ''
  );
  const latestAttempt = enrichedAttempt || latestAttemptSummary;

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

  const persona = {
    id: user?.id || '',
    username: user?.username || '',
    email: user?.email || '',
    fullName: user?.fullName || user?.username || 'Student',
    role: 'STUDENT' as const,
  };

  if (isQuizLoading || isResultLoading || isAttemptsLoading || (latestAttemptSummary && isReviewLoading)) {
    return (
      <div className="flex h-screen items-center justify-center text-xs text-slate-500">
        Loading quiz results…
      </div>
    );
  }

  if (isError || !quiz) {
    return (
      <div className="p-8 text-center text-sm text-rose-800">
        Error loading results: {quizError(error)}
      </div>
    );
  }

  return (
    <ClassroomLayout role="STUDENT" persona={persona} breadcrumbChild="Quiz Result">
      <div className="mx-auto max-w-3xl space-y-6 p-4 sm:p-8">
        {/* Navigation link */}
        <div className="flex items-center justify-between">
          <Link
            to={`/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}`}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-800"
          >
            <ArrowLeft size={14} /> Back to Quiz Briefing
          </Link>

          <Link
            to={`/student/classrooms/${encodeURIComponent(classId)}/quizzes`}
            className="text-xs font-semibold text-indigo-600 hover:underline"
          >
            All Class Quizzes
          </Link>
        </div>

        {/* Result summary card & review */}
        <QuizResultSummary
          quizTitle={quiz.title}
          quizKind={quiz.kind}
          attempt={latestAttempt}
          result={result}
        />

        {/* Footer actions */}
        <div className="flex items-center justify-center gap-3 pt-4">
          <Link
            to={`/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}`}
          >
            <Button variant="outline" className="text-xs font-semibold">
              <RotateCcw size={14} className="mr-1.5" /> View Attempt History
            </Button>
          </Link>
        </div>
      </div>
    </ClassroomLayout>
  );
};
