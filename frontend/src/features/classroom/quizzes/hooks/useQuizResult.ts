/**
 * React Query Hooks for Quiz Results, Access Overrides, and Grade Overrides
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { quizApi } from '../api/quizApi';
import { quizKeys } from '../api/quizKeys';
import type { AccessOverrideRequest, GradeOverrideRequest } from '../types';

export function useQuizResult(classroomId: string, quizId: string, studentId: string) {
  return useQuery({
    queryKey: quizKeys.result(classroomId, quizId, studentId),
    queryFn: ({ signal }) => quizApi.getResult(classroomId, quizId, studentId, signal),
    enabled: Boolean(classroomId && quizId && studentId),
  });
}

export function useAccessOverride(classroomId: string, quizId: string, studentId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: AccessOverrideRequest) =>
      quizApi.overrideAccess(classroomId, quizId, studentId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: quizKeys.result(classroomId, quizId, studentId),
      });
      void queryClient.invalidateQueries({
        queryKey: quizKeys.attemptList(classroomId, quizId),
      });
    },
  });
}

export function useGradeOverride(classroomId: string, quizId: string, studentId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: GradeOverrideRequest) =>
      quizApi.overrideGrade(classroomId, quizId, studentId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: quizKeys.result(classroomId, quizId, studentId),
      });
      void queryClient.invalidateQueries({
        queryKey: quizKeys.gradeOverrides(classroomId, quizId, studentId),
      });
    },
  });
}

export function useGradeOverridesHistory(
  classroomId: string,
  quizId: string,
  studentId: string,
  page: number = 0,
  size: number = 20
) {
  return useQuery({
    queryKey: quizKeys.gradeOverrides(classroomId, quizId, studentId, page, size),
    queryFn: ({ signal }) =>
      quizApi.listGradeOverrides(classroomId, quizId, studentId, page, size, signal),
    enabled: Boolean(classroomId && quizId && studentId),
  });
}
