/**
 * React Query Hook for Single Quiz Detail
 */

import { useQuery } from '@tanstack/react-query';
import { quizApi } from '../api/quizApi';
import { quizKeys } from '../api/quizKeys';

export function useQuizDetail(classroomId: string, quizId: string) {
  return useQuery({
    queryKey: quizKeys.detail(classroomId, quizId),
    queryFn: ({ signal }) => quizApi.get(classroomId, quizId, signal),
    enabled: Boolean(classroomId && quizId),
  });
}
