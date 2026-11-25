/**
 * Quiz Validation and Business Rules
 * Mirrors com.algolearner.platform.quiz.QuizGrading backend rules exactly
 */

import type {
  QuizConfig,
  QuizQuestion,
  QuestionValidationResult,
  QuizFormValidationResult,
  QuizView,
  QuizResult,
} from '../types';

/**
 * Validates a single question and its options
 */
export function validateQuestion(
  question: QuizQuestion,
  scoringMode: 'ALL_OR_NOTHING' | 'PARTIAL_CREDIT' = 'ALL_OR_NOTHING'
): QuestionValidationResult {
  const errors: Record<string, string> = {};

  if (!question.text || !question.text.trim()) {
    errors.text = 'Question text is required';
  } else if (question.text.length > 20000) {
    errors.text = 'Question text cannot exceed 20,000 characters';
  }

  if (question.points == null || isNaN(question.points) || question.points < 0.01 || question.points > 1000) {
    errors.points = 'Points must be between 0.01 and 1,000';
  }

  if (question.explanation && question.explanation.length > 20000) {
    errors.explanation = 'Explanation cannot exceed 20,000 characters';
  }

  if (!question.options || question.options.length < 2) {
    errors.options = 'Question must have at least 2 options';
  } else if (question.options.length > 20) {
    errors.options = 'Question cannot have more than 20 options';
  }

  if (question.type === 'TRUE_FALSE' && question.options?.length !== 2) {
    errors.options = 'True/False question must have exactly 2 options';
  }

  if (question.options && question.options.length >= 2) {
    const correctOptions = question.options.filter((o) => o.correct);
    const incorrectOptions = question.options.filter((o) => !o.correct);

    if (question.type === 'SINGLE_CHOICE' && correctOptions.length !== 1) {
      errors.correct = 'Single choice questions must have exactly one correct answer';
    } else if (question.type === 'TRUE_FALSE' && correctOptions.length !== 1) {
      errors.correct = 'True/False questions must have exactly one correct answer';
    } else if (correctOptions.length === 0) {
      errors.correct = 'At least one correct answer must be selected';
    } else if (incorrectOptions.length === 0) {
      errors.correct = 'At least one incorrect answer is required';
    }

    // Check individual option text
    const blankOptionIndex = question.options.findIndex((o) => !o.text || !o.text.trim());
    if (blankOptionIndex !== -1) {
      errors[`option_${blankOptionIndex}`] = `Option ${blankOptionIndex + 1} text cannot be blank`;
    }

    // Partial Credit validations
    if (scoringMode === 'PARTIAL_CREDIT') {
      let correctWeightsSum = 0;
      for (let i = 0; i < question.options.length; i++) {
        const opt = question.options[i];
        if (opt.weight == null || isNaN(opt.weight) || opt.weight < 0) {
          errors[`weight_${i}`] = `Option ${i + 1} weight must be >= 0`;
        } else if (opt.weight > (question.points || 0)) {
          errors[`weight_${i}`] = `Option ${i + 1} weight cannot exceed question points`;
        } else if (opt.correct) {
          correctWeightsSum += Number(opt.weight);
        }
      }

      // Check sum of correct weights equals question points (allowing small float diff)
      if (!errors.points && Math.abs(correctWeightsSum - (question.points || 0)) > 0.0001) {
        errors.weightsSum = `Sum of correct option weights (${correctWeightsSum.toFixed(2)}) must equal question points (${(question.points || 0).toFixed(2)})`;
      }
    }
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

/**
 * Validates the full Quiz configuration
 */
export function validateQuizConfig(
  title: string,
  config: QuizConfig
): QuizFormValidationResult {
  const errors: Record<string, string> = {};

  if (!title || !title.trim()) {
    errors.title = 'Quiz title is required';
  } else if (title.trim().length > 200) {
    errors.title = 'Quiz title cannot exceed 200 characters';
  }

  if (!config.kind) {
    errors.kind = 'Quiz kind is required (Graded or Ungraded)';
  }

  if (config.maxAttempts == null || config.maxAttempts < 1 || config.maxAttempts > 10000) {
    errors.maxAttempts = 'Maximum attempts must be between 1 and 10,000';
  }

  if (config.startTime && config.deadline) {
    const sTime = new Date(config.startTime).getTime();
    const dTime = new Date(config.deadline).getTime();
    if (dTime <= sTime) {
      errors.deadline = 'Deadline must be after start time';
    }
  }

  if (config.timeLimitSeconds != null && (config.timeLimitSeconds < 1 || config.timeLimitSeconds > 604800)) {
    errors.timeLimitSeconds = 'Time limit must be between 1 and 604,800 seconds (7 days)';
  }

  if (config.completion === 'PASSING_SCORE') {
    if (config.passingScore == null || isNaN(config.passingScore) || config.passingScore < 0 || config.passingScore > 100) {
      errors.passingScore = 'Passing score is required and must be between 0 and 100%';
    }
  }

  if (!config.questions || config.questions.length === 0) {
    errors.questions = 'Quiz must contain at least 1 question';
  } else if (config.questions.length > 200) {
    errors.questions = 'Quiz cannot exceed 200 questions';
  } else {
    // Check for duplicate question IDs
    const qIds = new Set<string>();
    for (const q of config.questions) {
      if (qIds.has(q.id)) {
        errors.questions = 'Question IDs must be unique';
        break;
      }
      qIds.add(q.id);
    }

    // Validate each question
    for (let i = 0; i < config.questions.length; i++) {
      const qRes = validateQuestion(config.questions[i], config.scoring);
      if (!qRes.isValid) {
        errors[`question_${i}`] = `Question ${i + 1} has validation errors: ${Object.values(qRes.errors)[0]}`;
      }
    }
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

/**
 * Checks whether a student is eligible to start or resume a quiz attempt
 */
export function canStudentStartAttempt(
  quiz: QuizView,
  result?: QuizResult | null,
  activeAttemptExists: boolean = false
): { canStart: boolean; reason?: string } {
  if (quiz.status === 'DRAFT') {
    return { canStart: false, reason: 'This quiz is still a draft and has not been published.' };
  }

  if (quiz.status === 'CLOSED') {
    return { canStart: false, reason: 'This quiz has been closed by the teacher.' };
  }

  if (quiz.startTime) {
    const sTime = new Date(quiz.startTime).getTime();
    if (Date.now() < sTime) {
      return { canStart: false, reason: 'This quiz is not available yet (start time has not arrived).' };
    }
  }

  // If student has an active attempt, they can always resume it!
  if (activeAttemptExists) {
    return { canStart: true };
  }

  // Check deadline
  if (quiz.deadline) {
    const dTime = new Date(quiz.deadline).getTime();
    const isPast = Date.now() > dTime;
    const allowLate = quiz.allowLateAttempts ?? quiz.teacherConfiguration?.allowLateAttempts ?? false;
    if (isPast && !allowLate) {
      return { canStart: false, reason: 'The deadline has passed and late attempts are not permitted.' };
    }
  }

  // Check attempts used
  if (result) {
    if (result.attemptsUsed >= result.attemptsAllowed) {
      return { canStart: false, reason: `You have used all ${result.attemptsAllowed} allowed attempt(s).` };
    }
  }

  return { canStart: true };
}

/**
 * Computes official score calculation for display
 */
export function calculateQuizScoreDisplay(
  score?: number | null,
  kind?: 'GRADED' | 'UNGRADED'
): string {
  if (kind === 'UNGRADED') return 'Ungraded';
  if (score == null) return '—';
  return `${Number(score).toFixed(2)}%`;
}
