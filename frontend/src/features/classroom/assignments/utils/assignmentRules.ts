import type {
  AddQuestionRequest,
  AssignmentDetailView,
  AssignmentStatus,
  AssignmentSummaryView,
  CreateAssignmentRequest,
  QuestionType,
} from '../types';
import { isBeforeStartTime, isPastDeadline } from './timeUtils';

export interface AssignmentFormErrors {
  title?: string;
  startTime?: string;
  deadline?: string;
  durationMinutes?: string;
  maxAttempts?: string;
  latePenaltyPercent?: string;
  general?: string;
}

export interface QuestionFormErrors {
  title?: string;
  prompt?: string;
  points?: string;
  options?: string;
  codeLanguage?: string;
  general?: string;
}

/**
 * Validates assignment creation / update settings
 */
export function validateAssignmentForm(input: Partial<CreateAssignmentRequest>): AssignmentFormErrors {
  const errors: AssignmentFormErrors = {};

  if (!input.title || input.title.trim().length === 0) {
    errors.title = 'Title is required.';
  } else if (input.title.length > 255) {
    errors.title = 'Title cannot exceed 255 characters.';
  }

  if (input.startTime && input.deadline) {
    const start = new Date(input.startTime).getTime();
    const end = new Date(input.deadline).getTime();
    if (!isNaN(start) && !isNaN(end) && end <= start) {
      errors.deadline = 'Deadline must be after start time.';
    }
  }

  if (input.durationMinutes !== undefined && input.durationMinutes !== null && input.durationMinutes <= 0) {
    errors.durationMinutes = 'Duration must be greater than 0 minutes.';
  }

  if (input.maxAttempts !== undefined && input.maxAttempts !== null && input.maxAttempts < 0) {
    errors.maxAttempts = 'Max attempts must be 0 (unlimited) or a positive number.';
  }

  if (
    input.latePenaltyPercent !== undefined &&
    input.latePenaltyPercent !== null &&
    (input.latePenaltyPercent < 0 || input.latePenaltyPercent > 100)
  ) {
    errors.latePenaltyPercent = 'Late penalty must be between 0% and 100%.';
  }

  return errors;
}

/**
 * Validates question authoring form
 */
export function validateQuestionForm(input: Partial<AddQuestionRequest>): QuestionFormErrors {
  const errors: QuestionFormErrors = {};

  if (!input.title || input.title.trim().length === 0) {
    errors.title = 'Question title is required.';
  }

  if (!input.prompt || input.prompt.trim().length === 0) {
    errors.prompt = 'Question prompt/content is required.';
  }

  if (input.points === undefined || input.points === null || input.points < 0) {
    errors.points = 'Points must be non-negative.';
  }

  const qType: QuestionType | undefined = input.questionType;

  if (qType === 'SINGLE_CHOICE' || qType === 'MULTIPLE_CHOICE' || qType === 'TRUE_FALSE') {
    const opts = input.options || [];
    if (opts.length < 2) {
      errors.options = 'At least 2 options are required.';
    } else {
      const hasEmpty = opts.some(o => !o.content || o.content.trim().length === 0);
      if (hasEmpty) {
        errors.options = 'All option choices must have text content.';
      } else {
        const correctCount = opts.filter(o => o.isCorrect).length;
        if (correctCount === 0) {
          errors.options = 'At least one option must be marked as correct.';
        } else if (qType === 'SINGLE_CHOICE' && correctCount > 1) {
          errors.options = 'Single choice questions must have exactly one correct option.';
        }
      }
    }
  }

  if (qType === 'CODE') {
    if (!input.codeLanguage || input.codeLanguage.trim().length === 0) {
      errors.codeLanguage = 'Programming language is required.';
    }
  }

  return errors;
}

/**
 * Checks if a student is permitted to start or resume an attempt
 */
export function canStudentStartAttempt(assignment: AssignmentSummaryView | AssignmentDetailView): {
  allowed: boolean;
  reason?: string;
} {
  if (assignment.status === 'DRAFT') {
    return { allowed: false, reason: 'This assignment is currently in draft mode.' };
  }

  if (assignment.status === 'CLOSED') {
    return { allowed: false, reason: 'This assignment is closed.' };
  }

  if (isBeforeStartTime(assignment.startTime)) {
    return { allowed: false, reason: 'This assignment has not started yet.' };
  }

  // Check deadline
  const pastDeadline = isPastDeadline(assignment.deadline);
  const allowLate = 'allowLateSubmission' in assignment ? assignment.allowLateSubmission : true;

  if (pastDeadline && !allowLate) {
    return { allowed: false, reason: 'The deadline for this assignment has passed.' };
  }

  // Check remaining attempts (0 means unlimited)
  if (assignment.maxAttempts > 0 && assignment.myRemainingAttempts !== null && assignment.myRemainingAttempts !== undefined) {
    if (assignment.myRemainingAttempts <= 0) {
      return { allowed: false, reason: 'You have exhausted your allowed attempts for this assignment.' };
    }
  }

  return { allowed: true };
}

/**
 * Returns human-readable status metadata
 */
export function getStatusBadgeConfig(
  status: AssignmentStatus,
  startTime?: string | null,
  deadline?: string | null
): { label: string; variant: 'draft' | 'scheduled' | 'published' | 'closed' | 'late' } {
  if (status === 'DRAFT') {
    return { label: 'Draft', variant: 'draft' };
  }

  if (status === 'SCHEDULED' || isBeforeStartTime(startTime)) {
    return { label: 'Scheduled', variant: 'scheduled' };
  }

  if (status === 'CLOSED') {
    return { label: 'Closed', variant: 'closed' };
  }

  if (isPastDeadline(deadline)) {
    return { label: 'Deadline Passed', variant: 'late' };
  }

  return { label: 'Published', variant: 'published' };
}
