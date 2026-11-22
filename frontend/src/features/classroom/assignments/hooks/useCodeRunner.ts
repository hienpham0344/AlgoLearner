import { useCallback, useRef, useState } from 'react';
import { assignmentApi, assignmentError } from '../api/assignmentApi';
import type { EvaluationResult } from '../types';

export type CodeRunStatus = 'IDLE' | 'RUNNING' | 'POLLING' | 'SUCCESS' | 'ERROR';

interface UseCodeRunnerOptions {
  classId: string;
  assignmentId: string;
  attemptId: string;
}

const TERMINAL_VERDICTS = new Set([
  'ACCEPTED',
  'WRONG_ANSWER',
  'TIME_LIMIT_EXCEEDED',
  'MEMORY_LIMIT_EXCEEDED',
  'COMPILATION_ERROR',
  'RUNTIME_ERROR',
  'INTERNAL_ERROR',
]);

export function useCodeRunner({ classId, assignmentId, attemptId }: UseCodeRunnerOptions) {
  const [status, setStatus] = useState<CodeRunStatus>('IDLE');
  const [result, setResult] = useState<EvaluationResult | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const cleanup = () => {
    if (pollTimer.current) {
      clearTimeout(pollTimer.current);
      pollTimer.current = null;
    }
  };

  const runSample = useCallback(
    async (questionId: string, source: string) => {
      cleanup();
      setStatus('RUNNING');
      setResult(null);
      setErrorMessage(null);

      try {
        const jobId = await assignmentApi.runCode(classId, assignmentId, attemptId, {
          questionId,
          source,
        });

        setStatus('POLLING');

        // Poll for result up to 30 seconds (15 attempts with 2s interval)
        const maxAttempts = 15;
        let attempts = 0;

        const poll = async () => {
          attempts += 1;
          try {
            const evalResult = await assignmentApi.getJobResult(classId, assignmentId, attemptId, jobId);

            if (TERMINAL_VERDICTS.has(evalResult.verdict) || attempts >= maxAttempts) {
              setResult(evalResult);
              setStatus('SUCCESS');
            } else {
              pollTimer.current = setTimeout(poll, 2000);
            }
          } catch (pollErr) {
            if (attempts >= maxAttempts) {
              setStatus('ERROR');
              setErrorMessage('Code evaluation timed out. Please check again or re-run.');
            } else {
              pollTimer.current = setTimeout(poll, 2000);
            }
          }
        };

        pollTimer.current = setTimeout(poll, 1500);
      } catch (err) {
        setStatus('ERROR');
        setErrorMessage(assignmentError(err));
      }
    },
    [classId, assignmentId, attemptId]
  );

  return {
    status,
    result,
    errorMessage,
    runSample,
    reset: () => {
      cleanup();
      setStatus('IDLE');
      setResult(null);
      setErrorMessage(null);
    },
  };
}
