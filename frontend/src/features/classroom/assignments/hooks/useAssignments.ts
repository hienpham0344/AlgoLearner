import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { assignmentApi } from '../api/assignmentApi';
import { assignmentKeys } from '../api/assignmentKeys';
import type {
  AssignmentDetailView,
  AssignmentSummaryView,
  CreateAssignmentRequest,
  UpdateAssignmentRequest,
} from '../types';

export function useAssignmentList(classId: string, role?: string) {
  return useQuery<AssignmentSummaryView[]>({
    queryKey: assignmentKeys.list(classId, role),
    queryFn: ({ signal }) => assignmentApi.list(classId, signal),
    enabled: Boolean(classId),
    staleTime: 30_000,
  });
}

export function useAssignmentDetail(classId: string, assignmentId: string) {
  return useQuery<AssignmentDetailView>({
    queryKey: assignmentKeys.detail(classId, assignmentId),
    queryFn: ({ signal }) => assignmentApi.get(classId, assignmentId, signal),
    enabled: Boolean(classId && assignmentId),
    staleTime: 30_000,
  });
}

export function useCreateAssignment(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateAssignmentRequest) => assignmentApi.create(classId, request),
    onSuccess: (newAssignment) => {
      queryClient.invalidateQueries({ queryKey: assignmentKeys.lists() });
      queryClient.setQueryData(assignmentKeys.detail(classId, newAssignment.id), newAssignment);
    },
  });
}

export function useUpdateAssignment(classId: string, assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UpdateAssignmentRequest) => assignmentApi.update(classId, assignmentId, request),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: assignmentKeys.lists() });
      queryClient.setQueryData(assignmentKeys.detail(classId, assignmentId), updated);
    },
  });
}

export function useDeleteAssignment(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (assignmentId: string) => assignmentApi.delete(classId, assignmentId),
    onSuccess: (_, assignmentId) => {
      queryClient.invalidateQueries({ queryKey: assignmentKeys.lists() });
      queryClient.removeQueries({ queryKey: assignmentKeys.detail(classId, assignmentId) });
    },
  });
}

export function usePublishAssignment(classId: string, assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => assignmentApi.publish(classId, assignmentId),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: assignmentKeys.lists() });
      queryClient.setQueryData(assignmentKeys.detail(classId, assignmentId), updated);
    },
  });
}

export function useCloseAssignment(classId: string, assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => assignmentApi.close(classId, assignmentId),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: assignmentKeys.lists() });
      queryClient.setQueryData(assignmentKeys.detail(classId, assignmentId), updated);
    },
  });
}

export function usePublishGrades(classId: string, assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => assignmentApi.publishGrades(classId, assignmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: assignmentKeys.detail(classId, assignmentId) });
      queryClient.invalidateQueries({ queryKey: assignmentKeys.submissions(classId, assignmentId) });
      queryClient.invalidateQueries({ queryKey: assignmentKeys.attempts() });
    },
  });
}

export function useCloneAssignment(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ assignmentId, targetClassroomId }: { assignmentId: string; targetClassroomId: string }) =>
      assignmentApi.clone(classId, assignmentId, targetClassroomId),
    onSuccess: (cloned) => {
      queryClient.invalidateQueries({ queryKey: assignmentKeys.list(cloned.classroomId) });
    },
  });
}
