/**
 * React Query Hooks for Quiz Listing and Teacher Management Mutations
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { quizApi } from '../api/quizApi';
import { quizKeys } from '../api/quizKeys';
import type {
  CreateQuizRequest,
  UpdateQuizRequest,
  QuizStatus,
} from '../types';

export function useQuizList(classroomId: string, page: number = 0, size: number = 20) {
  return useQuery({
    queryKey: quizKeys.list(classroomId, page, size),
    queryFn: ({ signal }) => quizApi.list(classroomId, page, size, signal),
    enabled: Boolean(classroomId),
  });
}

export function useCreateQuiz(classroomId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateQuizRequest) => quizApi.create(classroomId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: quizKeys.lists() });
    },
  });
}

export function useUpdateQuiz(classroomId: string, quizId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: UpdateQuizRequest) => quizApi.update(classroomId, quizId, request),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: quizKeys.detail(classroomId, quizId) });
      void queryClient.invalidateQueries({ queryKey: quizKeys.lists() });
      queryClient.setQueryData(quizKeys.detail(classroomId, quizId), updated);
    },
  });
}

export function useDeleteQuiz(classroomId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (quizId: string) => quizApi.delete(classroomId, quizId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: quizKeys.lists() });
    },
  });
}

export function useChangeQuizStatus(classroomId: string, quizId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (status: QuizStatus) => quizApi.changeStatus(classroomId, quizId, status),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: quizKeys.detail(classroomId, quizId) });
      void queryClient.invalidateQueries({ queryKey: quizKeys.lists() });
      queryClient.setQueryData(quizKeys.detail(classroomId, quizId), updated);
    },
  });
}

export function useDuplicateQuiz(classroomId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (quizId: string) => quizApi.duplicate(classroomId, quizId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: quizKeys.lists() });
    },
  });
}

export function usePublishGrades(classroomId: string, quizId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (published: boolean) => quizApi.publishGrades(classroomId, quizId, published),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: quizKeys.detail(classroomId, quizId) });
      void queryClient.invalidateQueries({ queryKey: quizKeys.results() });
      void queryClient.invalidateQueries({ queryKey: quizKeys.attempts() });
    },
  });
}
