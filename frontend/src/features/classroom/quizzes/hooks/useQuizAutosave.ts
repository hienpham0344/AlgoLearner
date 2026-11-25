import { useState, useEffect, useRef, useCallback } from 'react';
import { AutosaveQueue, type AutosaveStatus } from '../utils/autosaveQueue';
import type { AnswerView, SaveReceipt } from '../types';

export interface UseQuizAutosaveOptions {
  classId: string;
  quizId: string;
  attemptId: string;
  initialAnswers?: AnswerView[];
  debounceMs?: number;
}

export interface UseQuizAutosaveResult {
  answers: Record<string, string[]>;
  status: AutosaveStatus;
  lastError: string | null;
  selectOption: (questionId: string, optionId: string, isMultiple: boolean) => void;
  setAnswer: (questionId: string, selectedOptionIds: string[]) => void;
  flush: () => Promise<void>;
  retry: () => void;
}

export function useQuizAutosave({
  classId,
  quizId,
  attemptId,
  initialAnswers = [],
  debounceMs = 350,
}: UseQuizAutosaveOptions): UseQuizAutosaveResult {
  // Local state for immediate UI feedback
  const [answers, setAnswers] = useState<Record<string, string[]>>(() => {
    const map: Record<string, string[]> = {};
    initialAnswers.forEach((ans) => {
      map[ans.questionId] = ans.selectedOptionIds;
    });
    return map;
  });

  const [status, setStatus] = useState<AutosaveStatus>('idle');
  const [lastError, setLastError] = useState<string | null>(null);

  const queueRef = useRef<AutosaveQueue | null>(null);
  const answersRef = useRef(answers);
  answersRef.current = answers;

  // Initialize queue
  useEffect(() => {
    const queue = new AutosaveQueue({
      classId,
      quizId,
      attemptId,
      debounceMs,
      onStatusChange: (s, err) => {
        setStatus(s);
        setLastError(err ?? null);
      },
      onAnswerSaved: (_receipt: SaveReceipt) => {
        // Version is updated in queue
      },
    });

    queue.initializeVersions(
      initialAnswers.map((a) => ({ questionId: a.questionId, version: a.version }))
    );

    queueRef.current = queue;

    return () => {
      queue.destroy();
      queueRef.current = null;
    };
  }, [classId, quizId, attemptId, debounceMs]);

  // Handle option selection
  const selectOption = useCallback(
    (questionId: string, optionId: string, isMultiple: boolean) => {
      setAnswers((prev) => {
        const currentSelected = prev[questionId] || [];
        let nextSelected: string[];

        if (isMultiple) {
          if (currentSelected.includes(optionId)) {
            nextSelected = currentSelected.filter((id) => id !== optionId);
          } else {
            nextSelected = [...currentSelected, optionId];
          }
        } else {
          nextSelected = [optionId];
        }

        queueRef.current?.enqueue(questionId, nextSelected);

        return {
          ...prev,
          [questionId]: nextSelected,
        };
      });
    },
    []
  );

  const setAnswer = useCallback((questionId: string, selectedOptionIds: string[]) => {
    setAnswers((prev) => ({
      ...prev,
      [questionId]: selectedOptionIds,
    }));
    queueRef.current?.enqueue(questionId, selectedOptionIds);
  }, []);

  const flush = useCallback(async () => {
    if (queueRef.current) {
      await queueRef.current.flush();
    }
  }, []);

  const retry = useCallback(() => {
    if (!queueRef.current) return;
    // Re-enqueue all answers that may have failed
    Object.entries(answersRef.current).forEach(([qId, selected]) => {
      queueRef.current?.enqueue(qId, selected);
    });
  }, []);

  return {
    answers,
    status,
    lastError,
    selectOption,
    setAnswer,
    flush,
    retry,
  };
}
