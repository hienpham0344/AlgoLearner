import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { assignmentApi } from '../api/assignmentApi';
import { assignmentKeys } from '../api/assignmentKeys';
import type {
  AttemptGrantRequest,
  AttemptView,
  EvaluationHistoryItem,
  GradeEssayRequest,
  OverrideGradeRequest,
  RejudgeRequest,
} from '../types';

export function useTeacherSubmissions(classId: string, assignmentId: string) {
  return useQuery<AttemptView[]>({
    queryKey: assignmentKeys.submissions(classId, assignmentId),
    queryFn: ({ signal }) => assignmentApi.getSubmissions(classId, assignmentId, signal),
    enabled: Boolean(classId && assignmentId),
    staleTime: 15_000,
  });
}

export function useEvaluationHistory(classId: string, assignmentId: string, attemptId: string) {
  return useQuery<EvaluationHistoryItem[]>({
    queryKey: assignmentKeys.evaluations(classId, assignmentId, attemptId),
    queryFn: ({ signal }) => assignmentApi.getEvaluationHistory(classId, assignmentId, attemptId, signal),
    enabled: Boolean(classId && assignmentId && attemptId),
    staleTime: 30_000,
  });
}

export function useGradeEssay(classId: string, assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ attemptId, request }: { attemptId: string; request: GradeEssayRequest }) =>
      assignmentApi.gradeEssay(classId, assignmentId, attemptId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: assignmentKeys.submissions(classId, assignmentId) });
      queryClient.invalidateQueries({ queryKey: assignmentKeys.evaluations(classId, assignmentId, '') });
    },
  });
}

export function useOverrideGrade(classId: string, assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ attemptId, request }: { attemptId: string; request: OverrideGradeRequest }) =>
      assignmentApi.overrideGrade(classId, assignmentId, attemptId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: assignmentKeys.submissions(classId, assignmentId) });
      queryClient.invalidateQueries({ queryKey: assignmentKeys.evaluations(classId, assignmentId, '') });
    },
  });
}

export function useRejudgeAssignment(classId: string, assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request?: RejudgeRequest) => assignmentApi.rejudge(classId, assignmentId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: assignmentKeys.submissions(classId, assignmentId) });
      queryClient.invalidateQueries({ queryKey: assignmentKeys.evaluations(classId, assignmentId, '') });
      queryClient.invalidateQueries({ queryKey: assignmentKeys.detail(classId, assignmentId) });
    },
  });
}

export function useGrantAttempts(classId: string, assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ studentId, request }: { studentId: string; request: AttemptGrantRequest }) =>
      assignmentApi.grantAttempts(classId, assignmentId, studentId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: assignmentKeys.submissions(classId, assignmentId) });
    },
  });
}
