/**
 * Query Key Factory for Module 4.7 (Classroom Quiz)
 * Hierarchical key scoping for TanStack Query v5.
 */

export const quizKeys = {
  all: ['quizzes'] as const,
  lists: () => [...quizKeys.all, 'list'] as const,
  list: (classId: string) => [...quizKeys.lists(), classId] as const,
  details: () => [...quizKeys.all, 'detail'] as const,
  detail: (classId: string, quizId: string) => [...quizKeys.details(), classId, quizId] as const,
  authorings: () => [...quizKeys.all, 'authoring'] as const,
  authoring: (classId: string, quizId: string) => [...quizKeys.authorings(), classId, quizId] as const,
  attempts: () => [...quizKeys.all, 'attempts'] as const,
  attemptHistory: (classId: string, quizId: string, studentId?: string) =>
    [...quizKeys.attempts(), 'history', classId, quizId, studentId ?? 'self'] as const,
  attemptDetail: (classId: string, quizId: string, attemptId: string) =>
    [...quizKeys.attempts(), 'detail', classId, quizId, attemptId] as const,
  review: (classId: string, quizId: string, attemptId: string) =>
    [...quizKeys.attempts(), 'review', classId, quizId, attemptId] as const,
  results: () => [...quizKeys.all, 'results'] as const,
  studentResult: (classId: string, quizId: string, studentId: string) =>
    [...quizKeys.results(), classId, quizId, studentId] as const,
  gradeOverrides: () => [...quizKeys.all, 'grade-overrides'] as const,
  gradeHistory: (classId: string, quizId: string, studentId: string) =>
    [...quizKeys.gradeOverrides(), classId, quizId, studentId] as const,
};
