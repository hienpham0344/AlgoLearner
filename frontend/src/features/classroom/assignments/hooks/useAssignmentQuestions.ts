import { useMutation, useQueryClient } from '@tanstack/react-query';
import { assignmentApi } from '../api/assignmentApi';
import { assignmentKeys } from '../api/assignmentKeys';
import type { AddQuestionRequest, CodeCaseWeightsRequest } from '../types';

export function useAssignmentQuestions(classId: string, assignmentId: string) {
  const queryClient = useQueryClient();

  const invalidateAssignment = () => {
    queryClient.invalidateQueries({ queryKey: assignmentKeys.detail(classId, assignmentId) });
  };

  const addQuestion = useMutation({
    mutationFn: (request: AddQuestionRequest) =>
      assignmentApi.addQuestion(classId, assignmentId, request),
    onSuccess: invalidateAssignment,
  });

  const updateQuestion = useMutation({
    mutationFn: ({ questionId, request }: { questionId: string; request: AddQuestionRequest }) =>
      assignmentApi.updateQuestion(classId, assignmentId, questionId, request),
    onSuccess: invalidateAssignment,
  });

  const deleteQuestion = useMutation({
    mutationFn: (questionId: string) =>
      assignmentApi.deleteQuestion(classId, assignmentId, questionId),
    onSuccess: invalidateAssignment,
  });

  const reorderQuestions = useMutation({
    mutationFn: (questionIds: string[]) =>
      assignmentApi.reorderQuestions(classId, assignmentId, questionIds),
    onSuccess: invalidateAssignment,
  });

  const updateCodeCaseWeights = useMutation({
    mutationFn: ({ questionId, request }: { questionId: string; request: CodeCaseWeightsRequest }) =>
      assignmentApi.updateCodeCaseWeights(classId, assignmentId, questionId, request),
    onSuccess: invalidateAssignment,
  });

  return {
    addQuestion,
    updateQuestion,
    deleteQuestion,
    reorderQuestions,
    updateCodeCaseWeights,
  };
}
