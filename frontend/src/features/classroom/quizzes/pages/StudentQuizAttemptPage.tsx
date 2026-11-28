import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Navigate } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Flag, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/hooks/useAuth';
import { useQuizDetail } from '../hooks/useQuizDetail';
import { useStartOrResumeAttempt, useSubmitAttempt } from '../hooks/useQuizAttempt';
import { useQuizTimer } from '../hooks/useQuizTimer';
import { useQuizAutosave } from '../hooks/useQuizAutosave';
import type { AttemptView, QuestionView } from '../types';
import { FocusModeHeader } from '../components/student/FocusModeHeader';
import { QuestionNavPalette } from '../components/student/QuestionNavPalette';
import { QuestionRenderer } from '../components/student/QuestionRenderer';
import { SubmitConfirmationDialog } from '../components/student/SubmitConfirmationDialog';
import { quizError } from '../api/quizApi';

export const StudentQuizAttemptPage: React.FC = () => {
  const { id: classId = '', quizId = '' } = useParams<{ id: string; quizId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [attempt, setAttempt] = useState<AttemptView | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [flaggedIndices, setFlaggedIndices] = useState<Set<number>>(new Set());
  const [isSubmitModalOpen, setIsSubmitModalOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const { data: quiz, isLoading: isQuizLoading } = useQuizDetail(classId, quizId);
  const startAttemptMutation = useStartOrResumeAttempt(classId, quizId);
  const submitAttemptMutation = useSubmitAttempt(classId, quizId, attempt?.id || '');

  // Initialize or resume attempt on mount
  useEffect(() => {
    if (user && user.role !== 'STUDENT') return;
    let isMounted = true;
    startAttemptMutation
      .mutateAsync()
      .then((att) => {
        if (isMounted) {
          setAttempt(att);
          // If attempt already finished, redirect to result
          if (att.status === 'SUBMITTED' || att.status === 'EXPIRED') {
            navigate(
              `/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}/result`,
              { replace: true }
            );
          }
        }
      })
      .catch((err) => {
        if (isMounted) {
          setErrorMessage(quizError(err));
        }
      });

    return () => {
      isMounted = false;
    };
  }, [classId, quizId, user]);

  const questions: QuestionView[] = attempt?.questions || [];
  const currentQuestion = questions[currentIndex] || null;

  // Autosave hook
  const {
    answers,
    globalStatus,
    selectAnswer,
    flush,
    retryAll,
  } = useQuizAutosave({
    classroomId: classId,
    quizId,
    attemptId: attempt?.id || '',
    initialAnswers: attempt?.answers || {},
    isLocked: attempt?.status !== 'ACTIVE',
  });

  // Calculate answered questions
  const answeredIndices = new Set<number>();
  questions.forEach((q, idx) => {
    const selected = answers[q.id];
    if (selected && selected.length > 0) {
      answeredIndices.add(idx);
    }
  });

  // Timeout callback
  const handleExpire = useCallback(async () => {
    if (!attempt || attempt.status !== 'ACTIVE') return;

    try {
      await flush();
      // Backend automatically grades and marks EXPIRED/SUBMITTED
      navigate(
        `/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}/result`,
        { replace: true }
      );
    } catch {
      // Navigate to result anyway
      navigate(
        `/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}/result`,
        { replace: true }
      );
    }
  }, [attempt, classId, quizId, flush, navigate]);

  // Server-Synchronized Timer
  const {
    remainingSeconds,
    isUntimed,
    isLowTime,
    isUrgent,
    isExpired,
  } = useQuizTimer({
    serverDeadline: attempt?.deadline,
    serverTime: attempt?.serverTime,
    isSubmitted: attempt?.status === 'SUBMITTED',
    onExpire: handleExpire,
  });

  // Warn before unloading tab while quiz attempt is active or autosave is syncing (F-03)
  useEffect(() => {
    const isAttemptActive = attempt?.status === 'ACTIVE' && !isExpired;
    const hasPendingAutosave = globalStatus === 'saving' || globalStatus === 'error';
    if (!isAttemptActive && !hasPendingAutosave) return;

    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = 'Your quiz attempt is in progress. Are you sure you want to leave?';
      return e.returnValue;
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [attempt?.status, isExpired, globalStatus]);

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

  // Answer selection handler
  const handleSelectOption = (optionId: string) => {
    if (!currentQuestion || isExpired || attempt?.status !== 'ACTIVE') return;

    const currentSelected = answers[currentQuestion.id] || [];
    let updatedSelected: string[];

    if (currentQuestion.type === 'MULTIPLE_CHOICE') {
      if (currentSelected.includes(optionId)) {
        updatedSelected = currentSelected.filter((id) => id !== optionId);
      } else {
        updatedSelected = [...currentSelected, optionId];
      }
    } else {
      // SINGLE_CHOICE or TRUE_FALSE
      updatedSelected = [optionId];
    }

    selectAnswer(currentQuestion.id, updatedSelected);
  };

  // Flag toggle
  const handleToggleFlag = (index: number) => {
    setFlaggedIndices((prev) => {
      const next = new Set(prev);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  };

  // Submit confirmation handler
  const handleConfirmSubmit = async () => {
    if (!attempt) return;
    setErrorMessage(null);

    try {
      await flush(); // Ensure all debounced autosaves have completed
      await submitAttemptMutation.mutateAsync();
      navigate(
        `/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}/result`,
        { replace: true }
      );
    } catch (err) {
      setErrorMessage(quizError(err));
      setIsSubmitModalOpen(false);
    }
  };

  if (startAttemptMutation.isPending || isQuizLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-50 text-xs font-semibold text-slate-500">
        Preparing quiz workspace…
      </div>
    );
  }

  if (errorMessage && !attempt) {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-50 p-4">
        <div className="max-w-md rounded-xl border border-rose-200 bg-white p-6 text-center shadow-xs">
          <AlertCircle size={32} className="mx-auto text-rose-600 mb-2" />
          <h2 className="text-base font-bold text-slate-900">Unable to Start Quiz</h2>
          <p className="mt-1 text-xs text-rose-800">{errorMessage}</p>
          <Button
            type="button"
            onClick={() =>
              navigate(`/student/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}`)
            }
            className="mt-4"
            size="sm"
          >
            Back to Quiz Briefing
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col">
      {/* Top Bar Focus Mode Header */}
      <FocusModeHeader
        quizTitle={quiz?.title || 'Quiz'}
        attemptNumber={attempt?.number || 1}
        currentQuestionIndex={currentIndex}
        totalQuestions={questions.length}
        autosaveStatus={globalStatus}
        onRetryAutosave={retryAll}
        remainingSeconds={remainingSeconds}
        isUntimed={isUntimed}
        isLowTime={isLowTime}
        isUrgent={isUrgent}
        isExpired={isExpired}
        onSubmitClick={() => setIsSubmitModalOpen(true)}
        isSubmitting={submitAttemptMutation.isPending}
      />

      {/* Main Workspace Body */}
      <main className="flex-1 mx-auto max-w-7xl w-full p-4 sm:p-6 lg:p-8">
        {errorMessage && (
          <div
            role="alert"
            className="mb-4 rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs font-semibold text-rose-800"
          >
            {errorMessage}
          </div>
        )}

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-4 items-start">
          {/* Main Question Area (3 cols) */}
          <div className="lg:col-span-3 space-y-4">
            {currentQuestion ? (
              <QuestionRenderer
                question={currentQuestion}
                questionNumber={currentIndex + 1}
                totalQuestions={questions.length}
                selectedOptionIds={answers[currentQuestion.id] || []}
                disabled={isExpired || attempt?.status !== 'ACTIVE'}
                onSelectOption={handleSelectOption}
              />
            ) : (
              <div className="p-12 text-center text-xs text-slate-500">
                No questions available in this attempt.
              </div>
            )}

            {/* Bottom Question Controls */}
            <div className="flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={currentIndex === 0}
                onClick={() => setCurrentIndex((prev) => Math.max(0, prev - 1))}
                className="flex items-center gap-1 text-xs font-semibold"
              >
                <ChevronLeft size={16} /> Previous
              </Button>

              <button
                type="button"
                onClick={() => handleToggleFlag(currentIndex)}
                className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-semibold transition-colors ${
                  flaggedIndices.has(currentIndex)
                    ? 'border-amber-300 bg-amber-50 text-amber-800 font-bold'
                    : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
                }`}
              >
                <Flag
                  size={14}
                  className={flaggedIndices.has(currentIndex) ? 'fill-amber-600 text-amber-600' : ''}
                />
                <span>
                  {flaggedIndices.has(currentIndex) ? 'Flagged for Review' : 'Flag Question'}
                </span>
              </button>

              {currentIndex < questions.length - 1 ? (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentIndex((prev) => Math.min(questions.length - 1, prev + 1))}
                  className="flex items-center gap-1 text-xs font-semibold"
                >
                  Next <ChevronRight size={16} />
                </Button>
              ) : (
                <Button
                  type="button"
                  size="sm"
                  onClick={() => setIsSubmitModalOpen(true)}
                  disabled={submitAttemptMutation.isPending || isExpired}
                  className="text-xs font-bold"
                >
                  Finish & Submit
                </Button>
              )}
            </div>
          </div>

          {/* Right Question Navigator Palette (1 col) */}
          <div className="lg:col-span-1">
            <QuestionNavPalette
              totalQuestions={questions.length}
              currentIndex={currentIndex}
              answeredIndices={answeredIndices}
              flaggedIndices={flaggedIndices}
              onSelectQuestion={(idx) => setCurrentIndex(idx)}
            />
          </div>
        </div>
      </main>

      {/* Confirmation Dialog */}
      <SubmitConfirmationDialog
        isOpen={isSubmitModalOpen}
        totalQuestions={questions.length}
        answeredCount={answeredIndices.size}
        unansweredCount={questions.length - answeredIndices.size}
        flaggedCount={flaggedIndices.size}
        isSubmitting={submitAttemptMutation.isPending}
        onConfirm={handleConfirmSubmit}
        onCancel={() => setIsSubmitModalOpen(false)}
      />
    </div>
  );
};
