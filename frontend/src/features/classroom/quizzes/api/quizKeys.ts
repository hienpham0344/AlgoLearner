/**
 * TanStack Query Key Factory for Classroom Quizzes (Module 4.7)
 * Ensures strict cache segregation by classroom, quiz, attempt, and user contexts.
 */

export const quizKeys = {
  all: ['quizzes'] as const,

  // Lists by classroom and role
  lists: () => [...quizKeys.all, 'list'] as const,
  list: (classroomId: string, page: number = 0, size: number = 20) =>
    [...quizKeys.lists(), classroomId, { page, size }] as const,

  // Single quiz detail
  details: () => [...quizKeys.all, 'detail'] as const,
  detail: (classroomId: string, quizId: string) =>
    [...quizKeys.details(), classroomId, quizId] as const,

  // Student & teacher attempt lists
  attempts: () => [...quizKeys.all, 'attempts'] as const,
  attemptList: (classroomId: string, quizId: string, page: number = 0, size: number = 20) =>
    [...quizKeys.attempts(), classroomId, quizId, { page, size }] as const,

  // Single attempt detail
  attempt: (classroomId: string, quizId: string, attemptId: string) =>
    [...quizKeys.attempts(), classroomId, quizId, attemptId] as const,

  // Student official result
  results: () => [...quizKeys.all, 'results'] as const,
  result: (classroomId: string, quizId: string, studentId: string) =>
    [...quizKeys.results(), classroomId, quizId, studentId] as const,

  // Grade overrides audit history
  gradeOverrides: (classroomId: string, quizId: string, studentId: string, page: number = 0, size: number = 20) =>
    [...quizKeys.all, 'grade-overrides', classroomId, quizId, studentId, { page, size }] as const,
};
