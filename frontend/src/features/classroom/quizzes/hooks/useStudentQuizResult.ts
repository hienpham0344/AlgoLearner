import { useQuery } from '@tanstack/react-query';
import { quizApi } from '../api/quizApi';
import { quizKeys } from '../api/quizKeys';
import type { QuizResult, ReviewQuestion } from '../types';

export function useStudentQuizResult(classId: string, quizId: string, studentId: string) {
  return useQuery<QuizResult>({
    queryKey: quizKeys.studentResult(classId, quizId, studentId),
    queryFn: ({ signal }) => quizApi.result(classId, quizId, studentId, signal),
    enabled: Boolean(classId && quizId && studentId),
  });
}

export function useStudentQuizReview(classId: string, quizId: string, attemptId: string) {
  return useQuery<ReviewQuestion[]>({
    queryKey: quizKeys.review(classId, quizId, attemptId),
    queryFn: ({ signal }) => quizApi.review(classId, quizId, attemptId, signal),
    enabled: Boolean(classId && quizId && attemptId),
  });
}
