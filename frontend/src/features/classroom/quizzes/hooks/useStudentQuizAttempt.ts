import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { quizApi } from '../api/quizApi';
import { quizKeys } from '../api/quizKeys';
import type { PaginatedData } from '@/types/api';
import type { AttemptView, QuizView } from '../types';

export function useQuizDetail(classId: string, quizId: string) {
  return useQuery<QuizView>({
    queryKey: quizKeys.detail(classId, quizId),
    queryFn: ({ signal }) => quizApi.get(classId, quizId, signal),
    enabled: Boolean(classId && quizId),
  });
}

export function useQuizHistory(classId: string, quizId: string, page = 0, size = 20) {
  return useQuery<PaginatedData<AttemptView>>({
    queryKey: [...quizKeys.attemptHistory(classId, quizId), page, size],
    queryFn: ({ signal }) => quizApi.history(classId, quizId, undefined, page, size, signal),
    enabled: Boolean(classId && quizId),
  });
}

export function useAttemptDetail(classId: string, quizId: string, attemptId: string) {
  return useQuery<AttemptView>({
    queryKey: quizKeys.attemptDetail(classId, quizId, attemptId),
    queryFn: ({ signal }) => quizApi.resumeAttempt(classId, quizId, attemptId, signal),
    enabled: Boolean(classId && quizId && attemptId),
  });
}

export function useStartAttempt(classId: string, quizId: string) {
  const queryClient = useQueryClient();

  return useMutation<AttemptView, Error, void>({
    mutationFn: () => quizApi.startAttempt(classId, quizId),
    onSuccess: (attempt) => {
      queryClient.setQueryData(quizKeys.attemptDetail(classId, quizId, attempt.id), attempt);
      queryClient.invalidateQueries({ queryKey: quizKeys.attempts() });
    },
  });
}

export function useSubmitAttempt(classId: string, quizId: string) {
  const queryClient = useQueryClient();

  return useMutation<AttemptView, Error, { attemptId: string }>({
    mutationFn: ({ attemptId }) => quizApi.submitAttempt(classId, quizId, attemptId),
    onSuccess: (attempt) => {
      queryClient.setQueryData(quizKeys.attemptDetail(classId, quizId, attempt.id), attempt);
      queryClient.invalidateQueries({ queryKey: quizKeys.attempts() });
      queryClient.invalidateQueries({ queryKey: quizKeys.results() });
    },
  });
}
