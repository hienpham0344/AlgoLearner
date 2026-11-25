import { useCallback, useEffect, useRef, useState } from 'react';
import { assignmentApi } from '../api/assignmentApi';
import type { AnswerView, SaveDraftAnswerRequest } from '../types';
import { AutosaveStatus, generateClientMutationId } from '../utils/autosaveManager';

interface UseAutosaveDraftOptions {
  classId: string;
  assignmentId: string;
  attemptId: string;
  debounceMs?: number;
  initialAnswers?: AnswerView[];
  onSaved?: (answer: AnswerView) => void;
  onError?: (error: unknown) => void;
}

export function useAutosaveDraft({
  classId,
  assignmentId,
  attemptId,
  debounceMs = 1000,
  initialAnswers = [],
  onSaved,
  onError,
}: UseAutosaveDraftOptions) {
  const [status, setStatus] = useState<AutosaveStatus>('IDLE');
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Maintain versions per questionId
  const versionMap = useRef<Record<string, number>>({});
  // Store pending timers per questionId
  const timerMap = useRef<Record<string, ReturnType<typeof setTimeout>>>({});
  // Store in-flight requests count
  const inFlightCount = useRef<number>(0);

  // Initialize versionMap with initialAnswers
  useEffect(() => {
    initialAnswers.forEach((ans) => {
      if (ans.questionId) {
        versionMap.current[ans.questionId] = Math.max(versionMap.current[ans.questionId] ?? 0, ans.version ?? 0);
      }
    });
  }, [initialAnswers]);

  // Warn on page unload if autosave is currently saving
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (inFlightCount.current > 0 || Object.keys(timerMap.current).length > 0) {
        e.preventDefault();
        e.returnValue = 'You have unsaved changes. Are you sure you want to leave?';
        return e.returnValue;
      }
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, []);

  const triggerSaveNow = useCallback(
    async (payload: Omit<SaveDraftAnswerRequest, 'clientMutationId' | 'expectedVersion'>) => {
      const qId = payload.questionId;

      // Clear existing debounce timer for this question if any
      if (timerMap.current[qId]) {
        clearTimeout(timerMap.current[qId]);
        delete timerMap.current[qId];
      }

      setStatus('SAVING');
      setErrorMessage(null);
      inFlightCount.current += 1;

      const currentVer = versionMap.current[qId] ?? 0;
      const clientMutationId = generateClientMutationId();

      const fullRequest: SaveDraftAnswerRequest = {
        ...payload,
        clientMutationId,
        expectedVersion: currentVer,
      };

      try {
        const savedAnswer = await assignmentApi.saveDraftAnswer(classId, assignmentId, attemptId, fullRequest);
        versionMap.current[qId] = savedAnswer.version;
        setLastSavedAt(new Date());
        setStatus('SAVED');
        if (onSaved) {
          onSaved(savedAnswer);
        }
        return savedAnswer;
      } catch (err) {
        setStatus('ERROR');
        setErrorMessage(err instanceof Error ? err.message : 'Failed to save draft answer.');
        if (onError) {
          onError(err);
        }
        throw err;
      } finally {
        inFlightCount.current = Math.max(0, inFlightCount.current - 1);
      }
    },
    [classId, assignmentId, attemptId, onSaved, onError]
  );

  const scheduleAutosave = useCallback(
    (payload: Omit<SaveDraftAnswerRequest, 'clientMutationId' | 'expectedVersion'>) => {
      const qId = payload.questionId;

      // Clear existing timer
      if (timerMap.current[qId]) {
        clearTimeout(timerMap.current[qId]);
      }

      setStatus('SAVING');

      timerMap.current[qId] = setTimeout(() => {
        delete timerMap.current[qId];
        triggerSaveNow(payload).catch(() => {
          // Errors already captured in triggerSaveNow
        });
      }, debounceMs);
    },
    [debounceMs, triggerSaveNow]
  );

  const flushAllPending = useCallback(async () => {
    // Flush any pending timers immediately
    const pendingQuestionIds = Object.keys(timerMap.current);
    if (pendingQuestionIds.length === 0) return;

    for (const qId of pendingQuestionIds) {
      if (timerMap.current[qId]) {
        clearTimeout(timerMap.current[qId]);
        delete timerMap.current[qId];
      }
    }
  }, []);

  return {
    status,
    lastSavedAt,
    errorMessage,
    scheduleAutosave,
    triggerSaveNow,
    flushAllPending,
    isSaving: status === 'SAVING' || inFlightCount.current > 0,
  };
}
