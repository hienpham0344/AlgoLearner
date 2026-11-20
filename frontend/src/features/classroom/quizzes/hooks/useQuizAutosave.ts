/**
 * React Hook for Quiz Answer Autosaving
 * Integrates QuizAutosaveManager with component state.
 */

import { useState, useEffect, useRef, useCallback } from 'react';
import { QuizAutosaveManager } from '../utils/autosaveQueue';
import { quizApi } from '../api/quizApi';
import type { AutosaveStatus, AnswerState } from '../types';

interface UseQuizAutosaveOptions {
  classroomId: string;
  quizId: string;
  attemptId: string;
  initialAnswers?: Record<string, string[]>;
  isLocked?: boolean;
}

export function useQuizAutosave({
  classroomId,
  quizId,
  attemptId,
  initialAnswers = {},
  isLocked = false,
}: UseQuizAutosaveOptions) {
  const [answers, setAnswers] = useState<Record<string, string[]>>(initialAnswers);
  const [answerStates, setAnswerStates] = useState<Record<string, AnswerState>>({});
  const [globalStatus, setGlobalStatus] = useState<AutosaveStatus>('idle');

  // Synchronize initial answers when attempt loads/resumes
  useEffect(() => {
    if (initialAnswers && Object.keys(initialAnswers).length > 0) {
      setAnswers((prev) => ({ ...initialAnswers, ...prev }));
    }
  }, [initialAnswers]);

  // Create stable save function that calls backend API
  const saveFn = useCallback(
    async (questionId: string, optionIds: string[]) => {
      await quizApi.autosaveAnswer(classroomId, quizId, attemptId, questionId, optionIds);
    },
    [classroomId, quizId, attemptId]
  );

  const managerRef = useRef<QuizAutosaveManager | null>(null);

  useEffect(() => {
    const manager = new QuizAutosaveManager(saveFn, 400);
    managerRef.current = manager;

    const unsubscribe = manager.subscribe((questionId, entry, newGlobalStatus) => {
      setAnswerStates((prev) => ({
        ...prev,
        [questionId]: {
          selectedOptionIds: entry.optionIds,
          status: entry.status,
          lastSavedAt: entry.lastSavedAt,
          errorMessage: entry.errorMessage,
        },
      }));
      setGlobalStatus(newGlobalStatus);
    });

    return () => {
      unsubscribe();
      manager.destroy();
    };
  }, [saveFn]);

  const selectAnswer = useCallback(
    (questionId: string, optionIds: string[]) => {
      if (isLocked) return;

      // Update local state immediately for instant feedback
      setAnswers((prev) => ({
        ...prev,
        [questionId]: optionIds,
      }));

      // Enqueue to autosave manager
      managerRef.current?.enqueue(questionId, optionIds);
    },
    [isLocked]
  );

  const flush = useCallback(async () => {
    if (managerRef.current) {
      await managerRef.current.flush();
    }
  }, []);

  const retry = useCallback((questionId: string) => {
    managerRef.current?.retry(questionId);
  }, []);

  const retryAll = useCallback(() => {
    managerRef.current?.retryAll();
  }, []);

  return {
    answers,
    answerStates,
    globalStatus,
    selectAnswer,
    flush,
    retry,
    retryAll,
  };
}
