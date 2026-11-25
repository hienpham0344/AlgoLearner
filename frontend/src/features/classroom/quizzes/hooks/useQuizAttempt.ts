/**
 * React Query Hooks for Quiz Attempts (Start, Resume, List, Submit)
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { quizApi } from '../api/quizApi';
import { quizKeys } from '../api/quizKeys';
import type { AttemptView } from '../types';

export function useStartOrResumeAttempt(classroomId: string, quizId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => quizApi.startAttempt(classroomId, quizId),
    onSuccess: (attempt: AttemptView) => {
      queryClient.setQueryData(quizKeys.attempt(classroomId, quizId, attempt.id), attempt);
      void queryClient.invalidateQueries({ queryKey: quizKeys.attemptList(classroomId, quizId) });
    },
  });
}

export function useAttemptDetail(classroomId: string, quizId: string, attemptId: string) {
  return useQuery({
    queryKey: quizKeys.attempt(classroomId, quizId, attemptId),
    queryFn: ({ signal }) => quizApi.getAttempt(classroomId, quizId, attemptId, signal),
    enabled: Boolean(classroomId && quizId && attemptId),
    staleTime: 10_000,
  });
}

export function useAttemptList(
  classroomId: string,
  quizId: string,
  page: number = 0,
  size: number = 20
) {
  return useQuery({
    queryKey: quizKeys.attemptList(classroomId, quizId, page, size),
    queryFn: ({ signal }) => quizApi.listAttempts(classroomId, quizId, page, size, signal),
    enabled: Boolean(classroomId && quizId),
  });
}

export function useSubmitAttempt(classroomId: string, quizId: string, attemptId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => quizApi.submitAttempt(classroomId, quizId, attemptId),
    onSuccess: (attempt: AttemptView) => {
      queryClient.setQueryData(quizKeys.attempt(classroomId, quizId, attemptId), attempt);
      void queryClient.invalidateQueries({ queryKey: quizKeys.attemptList(classroomId, quizId) });
      void queryClient.invalidateQueries({ queryKey: quizKeys.results() });
    },
  });
}
