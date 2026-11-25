import React from 'react';
import { useParams, useSearchParams, useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, AlertCircle, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/hooks/useAuth';
import { quizApi, quizError } from '../api/quizApi';
import { quizKeys } from '../api/quizKeys';
import { QuizResultSummary } from '../components/student/QuizResultSummary';
import { isPastDeadline } from '../utils/quizTimeUtils';
import type { AttemptView, QuizResult, QuizView, ReviewQuestion } from '../types';

export const StudentQuizResultPage: React.FC = () => {
  const { id: classId, quizId } = useParams<{ id: string; quizId: string }>();
  const [searchParams] = useSearchParams();
  const attemptId = searchParams.get('attemptId');
  const navigate = useNavigate();
  const { user } = useAuth();

  // Load quiz overview
  const { data: quiz, isLoading: isLoadingQuiz } = useQuery<QuizView>({
    queryKey: quizKeys.detail(classId || '', quizId || ''),
    queryFn: ({ signal }) => quizApi.get(classId || '', quizId || '', signal),
    enabled: Boolean(classId && quizId),
  });

  // Load attempt
  const {
    data: attempt,
    isLoading: isLoadingAttempt,
    isError: isAttemptError,
    error: attemptError,
  } = useQuery<AttemptView>({
    queryKey: quizKeys.attemptDetail(classId || '', quizId || '', attemptId || ''),
    queryFn: ({ signal }) => quizApi.resumeAttempt(classId || '', quizId || '', attemptId || '', signal),
    enabled: Boolean(classId && quizId && attemptId),
  });

  // Load official student result
  const { data: result } = useQuery<QuizResult>({
    queryKey: quizKeys.studentResult(classId || '', quizId || '', user?.id || ''),
    queryFn: ({ signal }) => quizApi.result(classId || '', quizId || '', user?.id || '', signal),
    enabled: Boolean(classId && quizId && user?.id),
  });

  // Load review questions & explanations
  const { data: reviews = [] } = useQuery<ReviewQuestion[]>({
    queryKey: quizKeys.review(classId || '', quizId || '', attemptId || ''),
    queryFn: ({ signal }) => quizApi.review(classId || '', quizId || '', attemptId || '', signal),
    enabled: Boolean(classId && quizId && attemptId && attempt?.status === 'SUBMITTED'),
  });

  if (!classId || !quizId) return null;

  if (isLoadingQuiz || isLoadingAttempt) {
    return (
      <div className="min-h-screen bg-slate-50/70 flex flex-col items-center justify-center p-6 text-center">
        <Loader2 size={36} className="animate-spin text-indigo-600 mb-3" />
        <h2 className="text-base font-bold text-slate-800">Loading quiz results…</h2>
      </div>
    );
  }

  if (isAttemptError || !attempt || !quiz) {
    return (
      <div className="min-h-screen bg-slate-50/70 p-6 flex items-center justify-center">
        <div className="bg-white rounded-2xl border border-rose-200 p-8 max-w-md w-full text-center shadow-xs">
          <AlertCircle size={36} className="mx-auto text-rose-500 mb-3" />
          <h2 className="text-base font-bold text-slate-900">Attempt record not found</h2>
          <p className="text-xs text-slate-500 mt-1">{quizError(attemptError)}</p>
          <Button
            size="sm"
            onClick={() => navigate(`/student/classrooms/${encodeURIComponent(classId)}/quizzes`)}
            className="mt-4 bg-slate-900 text-white text-xs rounded-xl"
          >
            ← Back to Quizzes
          </Button>
        </div>
      </div>
    );
  }

  const isClosed = quiz.status === 'CLOSED' || (isPastDeadline(quiz.closesAt) && !quiz.allowLate);

  return (
    <div className="min-h-screen bg-slate-50/70 pb-16">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 pt-6 sm:pt-8 flex flex-col gap-6">
        {/* Breadcrumb */}
        <div className="flex items-center gap-2 text-xs font-semibold text-slate-500">
          <Link
            to={`/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}`}
            className="inline-flex items-center gap-1.5 hover:text-slate-900 transition-colors"
          >
            <ArrowLeft size={14} className="shrink-0" />
            <span>Quiz Details</span>
          </Link>
          <span>/</span>
          <span className="text-slate-900">Quiz Result</span>
        </div>

        {/* Summary Component */}
        <QuizResultSummary
          quiz={quiz}
          attempt={attempt}
          result={result}
          reviews={reviews}
          onBackToClassroom={() => navigate(`/student/classrooms/${encodeURIComponent(classId)}`)}
          onRetakeQuiz={() => {
            navigate(`/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}/attempt`);
          }}
          canRetake={!isClosed}
        />
      </div>
    </div>
  );
};
