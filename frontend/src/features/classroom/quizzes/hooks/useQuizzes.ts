import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { quizApi } from '../api/quizApi';
import { quizKeys } from '../api/quizKeys';
import type {
  AdjustAttemptsRequest,
  GradeOverrideRequest,
  QuizConfigureRequest,
} from '../types';

export function useQuizList(classId: string, enabled: boolean = true) {
  return useQuery({
    queryKey: quizKeys.list(classId),
    queryFn: ({ signal }) => quizApi.list(classId, signal),
    enabled: Boolean(classId) && enabled,
    staleTime: 30_000,
  });
}

export function useQuizDetail(classId: string, quizId: string, enabled: boolean = true) {
  return useQuery({
    queryKey: quizKeys.detail(classId, quizId),
    queryFn: ({ signal }) => quizApi.get(classId, quizId, signal),
    enabled: Boolean(classId && quizId) && enabled,
    staleTime: 30_000,
  });
}

export function useQuizAuthoring(classId: string, quizId: string, enabled: boolean = true) {
  return useQuery({
    queryKey: quizKeys.authoring(classId, quizId),
    queryFn: ({ signal }) => quizApi.authoring(classId, quizId, signal),
    enabled: Boolean(classId && quizId) && enabled,
    staleTime: 10_000,
  });
}

export function useCreateQuiz(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: QuizConfigureRequest) => quizApi.create(classId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: quizKeys.list(classId) });
    },
  });
}

export function useUpdateQuiz(classId: string, quizId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: QuizConfigureRequest) => quizApi.update(classId, quizId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: quizKeys.list(classId) });
      queryClient.invalidateQueries({ queryKey: quizKeys.detail(classId, quizId) });
      queryClient.invalidateQueries({ queryKey: quizKeys.authoring(classId, quizId) });
    },
  });
}

export function usePublishQuiz(classId: string, quizId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => quizApi.publish(classId, quizId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: quizKeys.list(classId) });
      queryClient.invalidateQueries({ queryKey: quizKeys.detail(classId, quizId) });
      queryClient.invalidateQueries({ queryKey: quizKeys.authoring(classId, quizId) });
    },
  });
}

export function useCloseQuiz(classId: string, quizId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => quizApi.close(classId, quizId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: quizKeys.list(classId) });
      queryClient.invalidateQueries({ queryKey: quizKeys.detail(classId, quizId) });
      queryClient.invalidateQueries({ queryKey: quizKeys.authoring(classId, quizId) });
    },
  });
}

export function useDuplicateQuiz(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (quizId: string) => quizApi.duplicate(classId, quizId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: quizKeys.list(classId) });
    },
  });
}

export function usePublishGrades(classId: string, quizId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => quizApi.publishGrades(classId, quizId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: quizKeys.detail(classId, quizId) });
      queryClient.invalidateQueries({ queryKey: quizKeys.attemptHistory(classId, quizId) });
    },
  });
}

export function useAdjustAttempts(classId: string, quizId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: AdjustAttemptsRequest) => quizApi.adjustAttempts(classId, quizId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: quizKeys.attemptHistory(classId, quizId) });
    },
  });
}

export function useGradeOverride(classId: string, quizId: string, studentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: GradeOverrideRequest) =>
      quizApi.overrideGrade(classId, quizId, studentId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: quizKeys.gradeHistory(classId, quizId, studentId) });
      queryClient.invalidateQueries({ queryKey: quizKeys.studentResult(classId, quizId, studentId) });
      queryClient.invalidateQueries({ queryKey: quizKeys.attemptHistory(classId, quizId) });
    },
  });
}

export function useGradeHistory(classId: string, quizId: string, studentId: string, enabled: boolean = true) {
  return useQuery({
    queryKey: quizKeys.gradeHistory(classId, quizId, studentId),
    queryFn: ({ signal }) => quizApi.gradeHistory(classId, quizId, studentId, signal),
    enabled: Boolean(classId && quizId && studentId) && enabled,
  });
}
