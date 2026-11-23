/**
 * Query Key Factory for Module 4.6 (Classroom Assignments)
 * Adheres strictly to project coding standards for hierarchical cache management and scoping.
 */

export const assignmentKeys = {
  all: ['assignments'] as const,
  lists: () => [...assignmentKeys.all, 'list'] as const,
  list: (classId: string, role?: string) => [...assignmentKeys.lists(), classId, role ?? 'default'] as const,
  details: () => [...assignmentKeys.all, 'detail'] as const,
  detail: (classId: string, assignmentId: string) => [...assignmentKeys.details(), classId, assignmentId] as const,
  attempts: () => [...assignmentKeys.all, 'attempt'] as const,
  activeAttempt: (classId: string, assignmentId: string) => [...assignmentKeys.attempts(), 'active', classId, assignmentId] as const,
  ownAttempts: (classId: string, assignmentId: string) => [...assignmentKeys.attempts(), 'own-history', classId, assignmentId] as const,
  ownAttemptDetail: (classId: string, assignmentId: string, attemptId: string) =>
    [...assignmentKeys.attempts(), 'own-detail', classId, assignmentId, attemptId] as const,
  submissions: (classId: string, assignmentId: string) => [...assignmentKeys.all, 'submissions', classId, assignmentId] as const,
  evaluations: (classId: string, assignmentId: string, attemptId: string) =>
    [...assignmentKeys.all, 'evaluations', classId, assignmentId, attemptId] as const,
  jobResult: (classId: string, assignmentId: string, attemptId: string, jobId: string) =>
    [...assignmentKeys.all, 'job-result', classId, assignmentId, attemptId, jobId] as const,
};
