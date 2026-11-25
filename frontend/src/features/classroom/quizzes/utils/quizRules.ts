import type { Option, Question, QuestionType, QuizStatus, ScoringPolicy } from '../types';

/**
 * Validates the full Quiz Configuration form according to QuizPolicy and SRS 4.7
 */
export function validateQuizConfig(form: {
  title: string;
  graded: boolean;
  opensAt?: string | null;
  closesAt?: string | null;
  durationSeconds?: number | null;
  maxAttempts: number;
  passingScore?: number | null;
  questions: Question[];
}): Record<string, string> {
  const errors: Record<string, string> = {};

  const trimmedTitle = form.title.trim();
  if (!trimmedTitle) {
    errors.title = 'Quiz title is required.';
  } else if (trimmedTitle.length > 255) {
    errors.title = 'Quiz title cannot exceed 255 characters.';
  }

  if (!Number.isInteger(form.maxAttempts) || form.maxAttempts < 1) {
    errors.maxAttempts = 'Maximum attempts must be at least 1.';
  }

  if (form.durationSeconds !== undefined && form.durationSeconds !== null) {
    if (!Number.isInteger(form.durationSeconds) || form.durationSeconds < 1) {
      errors.durationSeconds = 'Duration must be at least 1 second (or leave empty for no limit).';
    }
  }

  if (form.opensAt && form.closesAt) {
    const openTime = new Date(form.opensAt).getTime();
    const closeTime = new Date(form.closesAt).getTime();
    if (!isNaN(openTime) && !isNaN(closeTime) && closeTime <= openTime) {
      errors.closesAt = 'Closing time (deadline) must be strictly after opening time.';
    }
  }

  if (form.passingScore !== undefined && form.passingScore !== null) {
    if (!form.graded) {
      errors.passingScore = 'Passing score is only applicable to graded quizzes.';
    } else if (isNaN(form.passingScore) || form.passingScore < 0 || form.passingScore > 100) {
      errors.passingScore = 'Passing score must be between 0 and 100.';
    }
  }

  if (!form.questions || form.questions.length === 0) {
    errors.questions = 'Quiz must contain at least one question before publishing or saving.';
  }

  return errors;
}

/**
 * Validates a single question and its options according to Backend QuizPolicy
 */
export function validateQuestion(q: {
  prompt: string;
  type: QuestionType;
  scoring: ScoringPolicy;
  points: number;
  options: Option[];
}): Record<string, string> {
  const errors: Record<string, string> = {};

  if (!q.prompt || !q.prompt.trim()) {
    errors.prompt = 'Question prompt cannot be empty.';
  }

  if (q.points === undefined || q.points === null || isNaN(q.points) || q.points <= 0) {
    errors.points = 'Points must be a positive number greater than 0.';
  }

  if (!q.options || q.options.length < 2) {
    errors.options = 'Question must have at least 2 options.';
  } else if (q.type === 'TRUE_FALSE' && q.options.length !== 2) {
    errors.options = 'True/False questions must have exactly 2 options.';
  }

  const optionTexts = (q.options || []).map((o) => (o.text || '').trim());
  if (optionTexts.some((text) => !text)) {
    errors.optionsContent = 'All options must have non-empty text.';
  }

  const correctCount = (q.options || []).filter((o) => o.correct).length;
  if (correctCount === 0) {
    errors.correct = 'At least one correct answer must be marked.';
  } else if (q.type === 'SINGLE_CHOICE' && correctCount !== 1) {
    errors.correct = 'Single-choice question must have exactly one correct answer.';
  } else if (q.type === 'TRUE_FALSE' && correctCount !== 1) {
    errors.correct = 'True/False question must have exactly one correct answer.';
  }

  // Weight validation if weights are specified
  const hasWeights = (q.options || []).some((o) => o.weight !== undefined && o.weight !== null);
  if (hasWeights) {
    if (q.scoring !== 'PARTIAL') {
      errors.weights = 'Weights can only be used with Partial scoring policy.';
    } else {
      const anyMissingWeight = (q.options || []).some(
        (o) => o.weight === undefined || o.weight === null || isNaN(o.weight) || o.weight < 0
      );
      if (anyMissingWeight) {
        errors.weights = 'All options must have a valid weight >= 0 when custom weights are used.';
      } else {
        const correctSum = (q.options || [])
          .filter((o) => o.correct)
          .reduce((sum, o) => sum + (o.weight ?? 0), 0);

        // Check if sum of correct weights equals total points (with small epsilon for float precision)
        if (Math.abs(correctSum - q.points) > 0.001) {
          errors.weights = `Sum of weights for correct options (${correctSum}) must equal total question points (${q.points}).`;
        }
      }
    }
  }

  return errors;
}

/**
 * Generates standard UUID v4 for new questions/options
 */
export function generateClientUuid(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Returns UI configuration for quiz statuses
 */
export function getQuizStatusBadgeConfig(status: QuizStatus): {
  label: string;
  badgeClass: string;
  description: string;
} {
  switch (status) {
    case 'DRAFT':
      return {
        label: 'Draft',
        badgeClass: 'bg-amber-100 text-amber-800 border-amber-300',
        description: 'Draft - Not accessible to students. Editable by teacher.',
      };
    case 'PUBLISHED':
      return {
        label: 'Published',
        badgeClass: 'bg-emerald-100 text-emerald-800 border-emerald-300',
        description: 'Published - Available to enrolled students.',
      };
    case 'CLOSED':
      return {
        label: 'Closed',
        badgeClass: 'bg-slate-100 text-slate-700 border-slate-300',
        description: 'Closed - No new attempts allowed. Records preserved.',
      };
    default:
      return {
        label: status,
        badgeClass: 'bg-slate-100 text-slate-700 border-slate-200',
        description: 'Unknown status',
      };
  }
}
