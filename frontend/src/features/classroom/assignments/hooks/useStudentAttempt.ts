import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { assignmentApi } from '../api/assignmentApi';
import { assignmentKeys } from '../api/assignmentKeys';
import type { AttemptView } from '../types';

export function useActiveAttempt(classId: string, assignmentId: string, enabled = true) {
  return useQuery<AttemptView>({
    queryKey: assignmentKeys.activeAttempt(classId, assignmentId),
    queryFn: ({ signal }) => assignmentApi.getActiveAttempt(classId, assignmentId, signal),
    enabled: Boolean(classId && assignmentId && enabled),
    retry: false, // If no active attempt exists (404), do not retry indefinitely
    staleTime: 5_000,
  });
}

export function useOwnAttempts(classId: string, assignmentId: string) {
  return useQuery<AttemptView[]>({
    queryKey: assignmentKeys.ownAttempts(classId, assignmentId),
    queryFn: ({ signal }) => assignmentApi.getOwnAttempts(classId, assignmentId, signal),
    enabled: Boolean(classId && assignmentId),
    staleTime: 15_000,
  });
}

export function useOwnAttemptDetail(classId: string, assignmentId: string, attemptId: string) {
  return useQuery<AttemptView>({
    queryKey: assignmentKeys.ownAttemptDetail(classId, assignmentId, attemptId),
    queryFn: ({ signal }) => assignmentApi.getOwnAttemptDetail(classId, assignmentId, attemptId, signal),
    enabled: Boolean(classId && assignmentId && attemptId),
    staleTime: 30_000,
  });
}

export function useStartAttempt(classId: string, assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => assignmentApi.startAttempt(classId, assignmentId),
    onSuccess: (newAttempt) => {
      queryClient.setQueryData(assignmentKeys.activeAttempt(classId, assignmentId), newAttempt);
      queryClient.invalidateQueries({ queryKey: assignmentKeys.detail(classId, assignmentId) });
      queryClient.invalidateQueries({ queryKey: assignmentKeys.lists() });
    },
  });
}

export function useSubmitAttempt(classId: string, assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (attemptId: string) => assignmentApi.submitAttempt(classId, assignmentId, attemptId),
    onSuccess: (submittedAttempt) => {
      queryClient.removeQueries({ queryKey: assignmentKeys.activeAttempt(classId, assignmentId) });
      queryClient.invalidateQueries({ queryKey: assignmentKeys.ownAttempts(classId, assignmentId) });
      queryClient.invalidateQueries({ queryKey: assignmentKeys.detail(classId, assignmentId) });
      queryClient.invalidateQueries({ queryKey: assignmentKeys.lists() });
      queryClient.setQueryData(
        assignmentKeys.ownAttemptDetail(classId, assignmentId, submittedAttempt.id),
        submittedAttempt
      );
    },
  });
}
