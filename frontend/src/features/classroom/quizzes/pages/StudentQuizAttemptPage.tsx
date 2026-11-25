import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AlertCircle, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { quizApi, quizError } from '../api/quizApi';
import { quizKeys } from '../api/quizKeys';
import { FocusModeHeader } from '../components/student/FocusModeHeader';
import { QuestionNavPalette } from '../components/student/QuestionNavPalette';
import { QuestionRenderer } from '../components/student/QuestionRenderer';
import { SubmitConfirmationDialog } from '../components/student/SubmitConfirmationDialog';
import { useQuizTimer } from '../hooks/useQuizTimer';
import { useQuizAutosave } from '../hooks/useQuizAutosave';
import type { AttemptView, QuizView } from '../types';

export const StudentQuizAttemptPage: React.FC = () => {
  const { id: classId, quizId } = useParams<{ id: string; quizId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [currentIndex, setCurrentIndex] = useState(0);
  const [flaggedQuestionIds, setFlaggedQuestionIds] = useState<Set<string>>(new Set());
  const [isSubmitDialogOpen, setIsSubmitDialogOpen] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Load quiz info
  const { data: quiz } = useQuery<QuizView>({
    queryKey: quizKeys.detail(classId || '', quizId || ''),
    queryFn: ({ signal }) => quizApi.get(classId || '', quizId || '', signal),
    enabled: Boolean(classId && quizId),
  });

  // Start or resume active attempt
  const {
    data: attempt,
    isLoading: isLoadingAttempt,
    isError: isAttemptError,
    error: attemptLoadError,
  } = useQuery<AttemptView>({
    queryKey: ['active-attempt', classId, quizId],
    queryFn: () => quizApi.startAttempt(classId || '', quizId || ''),
    enabled: Boolean(classId && quizId),
    staleTime: Infinity, // Keep frozen snapshot stable
  });

  // Redirect if attempt is already completed
  useEffect(() => {
    if (attempt && attempt.status !== 'IN_PROGRESS') {
      navigate(
        `/student/classrooms/${encodeURIComponent(classId || '')}/quizzes/${encodeURIComponent(quizId || '')}/result?attemptId=${encodeURIComponent(attempt.id)}`,
        { replace: true }
      );
    }
  }, [attempt, classId, quizId, navigate]);

  // Submit mutation
  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!classId || !quizId || !attempt) throw new Error('Attempt not loaded');
      // Ensure all debounced answers are pushed to backend before submitting
      await autosave.flush();
      return quizApi.submitAttempt(classId, quizId, attempt.id);
    },
    onSuccess: (submitted) => {
      queryClient.setQueryData(quizKeys.attemptDetail(classId || '', quizId || '', submitted.id), submitted);
      queryClient.invalidateQueries({ queryKey: quizKeys.attempts() });
      queryClient.invalidateQueries({ queryKey: quizKeys.results() });
      navigate(
        `/student/classrooms/${encodeURIComponent(classId || '')}/quizzes/${encodeURIComponent(quizId || '')}/result?attemptId=${encodeURIComponent(submitted.id)}`,
        { replace: true }
      );
    },
    onError: (err) => {
      setSubmitError(quizError(err));
    },
  });

  // Handle auto-submit on timeout
  const handleTimeout = useCallback(async () => {
    if (submitMutation.isPending) return;
    try {
      await submitMutation.mutateAsync();
    } catch {
      // Handled in mutation onError
    }
  }, [submitMutation]);

  // Server-synced timer hook
  const timer = useQuizTimer({
    cutoffAt: attempt?.cutoffAt,
    serverTime: attempt?.serverTime,
    onTimeout: handleTimeout,
    enabled: Boolean(attempt && attempt.status === 'IN_PROGRESS'),
  });

  // Autosave queue hook
  const autosave = useQuizAutosave({
    classId: classId || '',
    quizId: quizId || '',
    attemptId: attempt?.id || '',
    initialAnswers: attempt?.answers || [],
  });

  const questions = attempt?.questions || [];
  const currentQuestion = questions[currentIndex];

  // Calculate answered count
  const answeredCount = useMemo(() => {
    return questions.filter((q) => {
      const selected = autosave.answers[q.id];
      return selected && selected.length > 0;
    }).length;
  }, [questions, autosave.answers]);

  const unansweredCount = questions.length - answeredCount;
  const flaggedCount = flaggedQuestionIds.size;

  const toggleFlag = useCallback((questionId: string) => {
    setFlaggedQuestionIds((prev) => {
      const next = new Set(prev);
      if (next.has(questionId)) {
        next.delete(questionId);
      } else {
        next.add(questionId);
      }
      return next;
    });
  }, []);

  const handleSelectOption = useCallback(
    (optionId: string, isMultiple: boolean) => {
      if (!currentQuestion) return;
      autosave.selectOption(currentQuestion.id, optionId, isMultiple);
    },
    [currentQuestion, autosave]
  );

  if (!classId || !quizId) return null;

  if (isLoadingAttempt) {
    return (
      <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-6 text-white text-center">
        <Loader2 size={36} className="animate-spin text-indigo-400 mb-3" />
        <h2 className="text-base font-bold">Entering Quiz Workspace…</h2>
        <p className="text-xs text-slate-400 mt-1">Synchronizing questions and resuming your saved answers</p>
      </div>
    );
  }

  if (isAttemptError || !attempt) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
        <div className="bg-white rounded-2xl border border-rose-200 p-8 max-w-md w-full text-center shadow-sm">
          <AlertCircle size={36} className="mx-auto text-rose-500 mb-3" />
          <h2 className="text-base font-bold text-slate-900">Unable to start quiz attempt</h2>
          <p className="text-xs text-slate-500 mt-1">{quizError(attemptLoadError)}</p>
          <Button
            size="sm"
            onClick={() => navigate(`/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}`)}
            className="mt-4 bg-slate-900 text-white text-xs rounded-xl"
          >
            ← Back to Quiz Details
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50/70 flex flex-col">
      {/* Focus Mode Sticky Top Header */}
      <FocusModeHeader
        quizTitle={quiz?.title || 'Classroom Quiz'}
        totalQuestions={questions.length}
        answeredCount={answeredCount}
        formattedTime={timer.formattedTime}
        timerUrgency={timer.urgency}
        autosaveStatus={autosave.status}
        autosaveError={autosave.lastError}
        onRetryAutosave={autosave.retry}
        onSubmitClick={() => setIsSubmitDialogOpen(true)}
        isSubmitting={submitMutation.isPending}
      />

      {/* Main Workspace */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8">
        {submitError && (
          <div className="mb-4 p-4 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-800 flex items-center justify-between gap-3">
            <span>{submitError}</span>
            <button
              type="button"
              onClick={() => setSubmitError(null)}
              className="text-xs font-bold text-rose-700 underline"
            >
              Dismiss
            </button>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* Left Column: Active Question Workspace */}
          <div className="lg:col-span-8">
            {currentQuestion ? (
              <QuestionRenderer
                question={currentQuestion}
                questionIndex={currentIndex}
                totalQuestions={questions.length}
                selectedOptionIds={autosave.answers[currentQuestion.id] || []}
                onSelectOption={handleSelectOption}
                onPrevious={() => setCurrentIndex((idx) => Math.max(0, idx - 1))}
                onNext={() => setCurrentIndex((idx) => Math.min(questions.length - 1, idx + 1))}
                hasPrevious={currentIndex > 0}
                hasNext={currentIndex < questions.length - 1}
              />
            ) : (
              <div className="bg-white rounded-2xl border p-8 text-center text-slate-500">
                No questions found in this assessment.
              </div>
            )}
          </div>

          {/* Right Column: Question Navigator Palette */}
          <div className="lg:col-span-4 sticky top-24">
            <QuestionNavPalette
              questions={questions}
              currentIndex={currentIndex}
              answers={autosave.answers}
              flaggedQuestionIds={flaggedQuestionIds}
              onSelectQuestion={(idx) => setCurrentIndex(idx)}
              onToggleFlag={toggleFlag}
            />
          </div>
        </div>
      </main>

      {/* Submit Confirmation Dialog */}
      <SubmitConfirmationDialog
        isOpen={isSubmitDialogOpen}
        totalQuestions={questions.length}
        answeredCount={answeredCount}
        unansweredCount={unansweredCount}
        flaggedCount={flaggedCount}
        isSubmitting={submitMutation.isPending}
        onConfirm={() => {
          submitMutation.mutate();
        }}
        onCancel={() => setIsSubmitDialogOpen(false)}
      />
    </div>
  );
};
