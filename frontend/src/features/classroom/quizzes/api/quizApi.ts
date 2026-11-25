/**
 * Quiz API Service
 * Maps frontend Quiz models strictly to Backend /api/v1/classrooms/{classId}/quizzes (QuizController & QuizDtos).
 */

import { isAxiosError } from 'axios';
import { apiClient } from '@/services/apiClient';
import type { ApiResponse, PageResponse, PaginatedData } from '@/types/api';
import type {
  QuizView,
  QuizConfig,
  QuizQuestion,
  QuestionView,
  AttemptView,
  AttemptStatus,
  QuizResult,
  GradeChange,
  CreateQuizRequest,
  UpdateQuizRequest,
  AccessOverrideRequest,
  GradeOverrideRequest,
  QuizStatus,
} from '../types';

interface BackendOption {
  id: string;
  text: string;
  correct: boolean;
  weight?: number | null;
}

interface BackendQuestion {
  id: string;
  prompt: string;
  type: QuizQuestion['type'];
  scoring: 'ALL_OR_NOTHING' | 'PARTIAL';
  points: number;
  options: BackendOption[];
  explanation?: string | null;
}

interface BackendConfigure {
  title: string;
  graded: boolean;
  opensAt: string | null;
  closesAt: string | null;
  durationSeconds: number | null;
  maxAttempts: number;
  allowLate: boolean;
  shuffleQuestions: boolean;
  shuffleAnswers: boolean;
  releasePolicy: 'MANUAL' | 'IMMEDIATE';
  timeoutMode: 'AUTO_SUBMIT' | 'LOCK';
  questions: BackendQuestion[];
  showCorrectAnswers: boolean;
  showExplanations: boolean;
  passingScore: number | null;
}

interface BackendQuizView {
  id: string;
  classroomId: string;
  title: string;
  status: QuizStatus;
  graded: boolean;
  opensAt: string | null;
  closesAt: string | null;
  durationSeconds: number | null;
  maxAttempts: number;
  allowLate: boolean;
  releasePolicy: 'MANUAL' | 'IMMEDIATE';
}

interface BackendStudentOption {
  id: string;
  text: string;
}

interface BackendStudentQuestion {
  id: string;
  prompt: string;
  type: QuizQuestion['type'];
  points: number;
  options: BackendStudentOption[];
}

interface BackendAnswerView {
  questionId: string;
  selectedOptionIds: string[];
  version: number;
}

interface BackendAttemptView {
  id: string;
  quizId: string;
  studentId?: string;
  status: 'IN_PROGRESS' | 'SUBMITTED' | 'LOCKED' | AttemptStatus;
  startedAt: string;
  cutoffAt: string | null;
  submittedAt: string | null;
  score: number | null;
  gradePublished: boolean;
  questions: BackendStudentQuestion[];
  answers: BackendAnswerView[];
  serverTime: string;
  late: boolean;
}

interface BackendSaveReceipt {
  accepted: boolean;
  attemptStatus: string;
  questionId: string;
  version: number;
}

interface BackendReviewQuestion {
  questionId: string;
  correctOptionIds: string[] | null;
  explanation: string | null;
}

interface BackendResult {
  submitted: boolean;
  completed: boolean;
  originalScore: number | null;
  finalScore: number | null;
  attemptsUsed?: number;
  attemptsAllowed?: number;
}

interface BackendGradeChange {
  id: number | string;
  actorId: string;
  originalScore: number;
  finalScore: number;
  reason: string;
  createdAt: string;
}

const base = (classroomId: string) =>
  `/classrooms/${encodeURIComponent(classroomId)}/quizzes`;

const quizResource = (classroomId: string, quizId: string) =>
  `${base(classroomId)}/${encodeURIComponent(quizId)}`;

/** Tracks per-question optimistic lock versions across start/resume/autosave calls. */
const answerVersions = new Map<string, number>();
const versionKey = (attemptId: string, questionId: string) => `${attemptId}:${questionId}`;

function generateUuid(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function currentUser(): { id?: string; role?: string } | null {
  try {
    const raw = globalThis.localStorage?.getItem('algolearner_user');
    return raw ? (JSON.parse(raw) as { id?: string; role?: string }) : null;
  } catch {
    return null;
  }
}

function unwrap<T>(data: ApiResponse<T>): T {
  if (!data.success) {
    throw new Error(data.message || 'Quiz operation failed.');
  }
  return data.data;
}

function paginateArray<T>(allItems: T[], page: number, size: number): PaginatedData<T> {
  const safeSize = Math.max(1, size);
  const start = page * safeSize;
  const items = allItems.slice(start, start + safeSize);
  const totalElements = allItems.length;
  const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / safeSize);
  return {
    items,
    pagination: {
      page,
      size: safeSize,
      totalElements,
      totalPages,
      isFirst: page <= 0,
      isLast: totalPages === 0 || page >= totalPages - 1,
    },
  };
}

function toBackendConfigure(title: string, config: QuizConfig): BackendConfigure {
  const scoring = config.scoring === 'PARTIAL_CREDIT' ? 'PARTIAL' : 'ALL_OR_NOTHING';
  return {
    title: title.trim(),
    graded: config.kind === 'GRADED',
    opensAt: config.startTime ?? null,
    closesAt: config.deadline ?? null,
    durationSeconds: config.timeLimitSeconds ?? null,
    maxAttempts: config.maxAttempts,
    allowLate: config.allowLateAttempts,
    shuffleQuestions: config.shuffleQuestions,
    shuffleAnswers: config.shuffleAnswers,
    releasePolicy: config.publication === 'AUTO' ? 'IMMEDIATE' : 'MANUAL',
    timeoutMode: config.timeout === 'LOCK_WITHOUT_SUBMIT' ? 'LOCK' : 'AUTO_SUBMIT',
    showCorrectAnswers: config.showCorrectAnswers,
    showExplanations: config.showExplanations,
    passingScore: config.completion === 'PASSING_SCORE' ? config.passingScore : null,
    questions: config.questions.map((q) => ({
      id: q.id,
      prompt: q.text,
      type: q.type,
      scoring,
      points: Number(q.points),
      explanation: q.explanation ?? null,
      options: q.options.map((o) => ({
        id: o.id,
        text: o.text,
        correct: Boolean(o.correct),
        weight: scoring === 'PARTIAL' ? (o.weight ?? null) : null,
      })),
    })),
  };
}

function fromBackendConfigure(raw: BackendConfigure): QuizConfig {
  const hasPartial = raw.questions.some((q) => q.scoring === 'PARTIAL');
  return {
    kind: raw.graded ? 'GRADED' : 'UNGRADED',
    maxAttempts: raw.maxAttempts,
    startTime: raw.opensAt ?? null,
    deadline: raw.closesAt ?? null,
    allowLateAttempts: Boolean(raw.allowLate),
    timeLimitSeconds: raw.durationSeconds ?? null,
    timeout: raw.timeoutMode === 'LOCK' ? 'LOCK_WITHOUT_SUBMIT' : 'AUTO_SUBMIT',
    shuffleQuestions: Boolean(raw.shuffleQuestions),
    shuffleAnswers: Boolean(raw.shuffleAnswers),
    scoring: hasPartial ? 'PARTIAL_CREDIT' : 'ALL_OR_NOTHING',
    completion: raw.passingScore != null ? 'PASSING_SCORE' : 'SUBMIT_IS_ENOUGH',
    passingScore: raw.passingScore != null ? Number(raw.passingScore) : null,
    publication: raw.releasePolicy === 'IMMEDIATE' ? 'AUTO' : 'MANUAL',
    showCorrectAnswers: Boolean(raw.showCorrectAnswers),
    showExplanations: Boolean(raw.showExplanations),
    questions: raw.questions.map((q) => ({
      id: q.id,
      text: q.prompt,
      type: q.type,
      points: Number(q.points),
      explanation: q.explanation ?? null,
      options: q.options.map((o) => ({
        id: o.id,
        text: o.text,
        correct: Boolean(o.correct),
        weight: o.weight != null ? Number(o.weight) : null,
      })),
    })),
  };
}

function toQuizView(raw: BackendQuizView, config: QuizConfig | null = null): QuizView {
  const questions: QuestionView[] = config
    ? config.questions.map((q) => ({
        id: q.id,
        text: q.text,
        type: q.type,
        points: q.points,
        explanation: q.explanation ?? null,
        options: q.options.map((o) => ({
          id: o.id,
          text: o.text,
          correct: o.correct,
        })),
      }))
    : [];

  return {
    id: raw.id,
    classroomId: raw.classroomId,
    title: raw.title,
    status: raw.status,
    teacherConfiguration: config,
    kind: raw.graded ? 'GRADED' : 'UNGRADED',
    maxAttempts: raw.maxAttempts,
    allowLateAttempts: Boolean(raw.allowLate),
    startTime: raw.opensAt ?? null,
    deadline: raw.closesAt ?? null,
    timeLimitSeconds: raw.durationSeconds ?? null,
    questions,
  };
}

function mapAttemptStatus(rawStatus: BackendAttemptView['status']): AttemptStatus {
  if (rawStatus === 'IN_PROGRESS' || rawStatus === 'ACTIVE') return 'ACTIVE';
  if (rawStatus === 'LOCKED' || rawStatus === 'EXPIRED') return 'EXPIRED';
  return 'SUBMITTED';
}

function toAttemptView(
  raw: BackendAttemptView,
  attemptNumber: number = 1,
  reviewItems?: BackendReviewQuestion[]
): AttemptView {
  const answersMap: Record<string, string[]> = {};
  for (const ans of raw.answers || []) {
    answersMap[ans.questionId] = ans.selectedOptionIds || [];
    answerVersions.set(versionKey(raw.id, ans.questionId), ans.version ?? 0);
  }
  for (const q of raw.questions || []) {
    const k = versionKey(raw.id, q.id);
    if (!answerVersions.has(k)) {
      answerVersions.set(k, 0);
    }
  }

  const reviewByQuestion = new Map<string, BackendReviewQuestion>();
  if (reviewItems) {
    for (const r of reviewItems) {
      reviewByQuestion.set(r.questionId, r);
    }
  }

  const questions: QuestionView[] = (raw.questions || []).map((q) => {
    const rev = reviewByQuestion.get(q.id);
    const correctSet = rev?.correctOptionIds ? new Set(rev.correctOptionIds) : null;
    return {
      id: q.id,
      text: q.prompt,
      type: q.type,
      points: Number(q.points),
      explanation: rev?.explanation ?? null,
      options: (q.options || []).map((o) => ({
        id: o.id,
        text: o.text,
        correct: correctSet ? correctSet.has(o.id) : null,
      })),
    };
  });

  const fallbackUserId = currentUser()?.id || '';
  return {
    id: raw.id,
    studentId: raw.studentId || fallbackUserId,
    number: attemptNumber,
    status: mapAttemptStatus(raw.status),
    serverTime: raw.serverTime,
    startedAt: raw.startedAt,
    deadline: raw.cutoffAt ?? null,
    submittedAt: raw.submittedAt ?? null,
    late: Boolean(raw.late),
    score: raw.score != null ? Number(raw.score) : null,
    completed: raw.status === 'SUBMITTED' ? true : null,
    questions,
    answers: answersMap,
  };
}

export const quizApi = {
  // Teacher: Create draft quiz
  create: async (classroomId: string, request: CreateQuizRequest): Promise<QuizView> => {
    const payload = toBackendConfigure(request.title, request.config);
    const res = await apiClient.post<ApiResponse<BackendQuizView>>(base(classroomId), payload);
    return toQuizView(unwrap(res.data), request.config);
  },

  // Teacher & Student: List visible quizzes
  list: async (
    classroomId: string,
    page: number = 0,
    size: number = 20,
    signal?: AbortSignal
  ): Promise<PaginatedData<QuizView>> => {
    const res = await apiClient.get<ApiResponse<BackendQuizView[]>>(base(classroomId), { signal });
    const rawList = unwrap(res.data) || [];
    const mapped = rawList.map((item) => toQuizView(item, null));
    return paginateArray(mapped, page, size);
  },

  // Teacher & Student: Read quiz details (+ authoring config for Teacher owner)
  get: async (classroomId: string, quizId: string, signal?: AbortSignal): Promise<QuizView> => {
    const res = await apiClient.get<ApiResponse<BackendQuizView>>(
      quizResource(classroomId, quizId),
      { signal }
    );
    const view = unwrap(res.data);

    if (currentUser()?.role === 'TEACHER') {
      try {
        const authRes = await apiClient.get<ApiResponse<BackendConfigure>>(
          `${quizResource(classroomId, quizId)}/authoring`,
          { signal }
        );
        const config = fromBackendConfigure(unwrap(authRes.data));
        return toQuizView(view, config);
      } catch (err) {
        if (!isAxiosError(err) || err.response?.status !== 403) {
          throw err;
        }
      }
    }

    return toQuizView(view, null);
  },

  // Teacher: Update draft quiz
  update: async (
    classroomId: string,
    quizId: string,
    request: UpdateQuizRequest
  ): Promise<QuizView> => {
    const payload = toBackendConfigure(request.title, request.config);
    const res = await apiClient.put<ApiResponse<BackendQuizView>>(
      quizResource(classroomId, quizId),
      payload
    );
    return toQuizView(unwrap(res.data), request.config);
  },

  // Teacher: Delete unused draft quiz
  delete: async (classroomId: string, quizId: string): Promise<void> => {
    await apiClient.delete(`${quizResource(classroomId, quizId)}`);
  },

  // Teacher: Publish or Close quiz
  changeStatus: async (
    classroomId: string,
    quizId: string,
    status: QuizStatus
  ): Promise<QuizView> => {
    const action = status === 'PUBLISHED' ? 'publish' : status === 'CLOSED' ? 'close' : null;
    if (!action) {
      return quizApi.get(classroomId, quizId);
    }
    const res = await apiClient.post<ApiResponse<BackendQuizView>>(
      `${quizResource(classroomId, quizId)}/${action}`
    );
    const updatedView = unwrap(res.data);
    if (currentUser()?.role === 'TEACHER') {
      try {
        const authRes = await apiClient.get<ApiResponse<BackendConfigure>>(
          `${quizResource(classroomId, quizId)}/authoring`
        );
        return toQuizView(updatedView, fromBackendConfigure(unwrap(authRes.data)));
      } catch {
        // Ignore authoring fetch fallback
      }
    }
    return toQuizView(updatedView, null);
  },

  // Teacher: Duplicate quiz into independent draft
  duplicate: async (classroomId: string, quizId: string): Promise<QuizView> => {
    const res = await apiClient.post<ApiResponse<BackendQuizView>>(
      `${quizResource(classroomId, quizId)}/duplicate`
    );
    return toQuizView(unwrap(res.data), null);
  },

  // Teacher: Manually publish official grades
  publishGrades: async (
    classroomId: string,
    quizId: string,
    _published: boolean = true
  ): Promise<void> => {
    await apiClient.post<ApiResponse<null>>(
      `${quizResource(classroomId, quizId)}/publish-grades`
    );
  },

  // Student: Start new attempt or resume active attempt
  startAttempt: async (classroomId: string, quizId: string): Promise<AttemptView> => {
    const res = await apiClient.post<ApiResponse<BackendAttemptView>>(
      `${quizResource(classroomId, quizId)}/attempts`
    );
    return toAttemptView(unwrap(res.data), 1);
  },

  // Teacher & Student: List attempts
  listAttempts: async (
    classroomId: string,
    quizId: string,
    page: number = 0,
    size: number = 20,
    signal?: AbortSignal
  ): Promise<PageResponse<AttemptView>['data']> => {
    const res = await apiClient.get<PageResponse<BackendAttemptView>>(
      `${quizResource(classroomId, quizId)}/attempts`,
      { params: { page, size }, signal }
    );
    const pageData = res.data.data;
    const total = pageData.pagination?.totalElements ?? pageData.items.length;
    return {
      items: pageData.items.map((raw, idx) =>
        toAttemptView(raw, Math.max(1, total - (page * size + idx)))
      ),
      pagination: pageData.pagination,
    };
  },

  // Teacher & Student: Get single attempt details (+ released review data when submitted)
  getAttempt: async (
    classroomId: string,
    quizId: string,
    attemptId: string,
    signal?: AbortSignal
  ): Promise<AttemptView> => {
    const res = await apiClient.get<ApiResponse<BackendAttemptView>>(
      `${quizResource(classroomId, quizId)}/attempts/${encodeURIComponent(attemptId)}`,
      { signal }
    );
    const raw = unwrap(res.data);

    let reviewItems: BackendReviewQuestion[] | undefined;
    if (raw.status === 'SUBMITTED') {
      try {
        const revRes = await apiClient.get<ApiResponse<BackendReviewQuestion[]>>(
          `${quizResource(classroomId, quizId)}/attempts/${encodeURIComponent(attemptId)}/review`,
          { signal }
        );
        reviewItems = unwrap(revRes.data);
      } catch {
        reviewItems = undefined;
      }
    }

    return toAttemptView(raw, 1, reviewItems);
  },

  // Student: Autosave atomic answer with optimistic version & idempotency key
  autosaveAnswer: async (
    classroomId: string,
    quizId: string,
    attemptId: string,
    questionId: string,
    optionIds: string[]
  ): Promise<BackendSaveReceipt> => {
    const k = versionKey(attemptId, questionId);
    const expectedVersion = answerVersions.get(k) ?? 0;

    const sendSave = async (version: number) => {
      const res = await apiClient.put<ApiResponse<BackendSaveReceipt>>(
        `${quizResource(classroomId, quizId)}/attempts/${encodeURIComponent(attemptId)}/answers`,
        {
          questionId,
          selectedOptionIds: optionIds,
          expectedVersion: version,
          idempotencyKey: generateUuid(),
        }
      );
      return unwrap(res.data);
    };

    try {
      const receipt = await sendSave(expectedVersion);
      answerVersions.set(k, receipt.version);
      return receipt;
    } catch (err) {
      if (
        isAxiosError(err) &&
        err.response?.status === 409 &&
        err.response?.data?.errorCode === 'ANSWER_VERSION_CONFLICT'
      ) {
        // Refresh version from server attempt state once and retry
        const refreshed = await quizApi.getAttempt(classroomId, quizId, attemptId);
        if (refreshed.status !== 'ACTIVE') {
          throw err;
        }
        const retryVersion = answerVersions.get(k) ?? 0;
        const receipt = await sendSave(retryVersion);
        answerVersions.set(k, receipt.version);
        return receipt;
      }
      throw err;
    }
  },

  // Student: Submit attempt
  submitAttempt: async (
    classroomId: string,
    quizId: string,
    attemptId: string
  ): Promise<AttemptView> => {
    const res = await apiClient.post<ApiResponse<BackendAttemptView>>(
      `${quizResource(classroomId, quizId)}/attempts/${encodeURIComponent(attemptId)}/submit`
    );
    return toAttemptView(unwrap(res.data), 1);
  },

  // Teacher & Student: Get student official result
  getResult: async (
    classroomId: string,
    quizId: string,
    studentId: string,
    signal?: AbortSignal
  ): Promise<QuizResult> => {
    const res = await apiClient.get<ApiResponse<BackendResult>>(
      `${quizResource(classroomId, quizId)}/students/${encodeURIComponent(studentId)}/result`,
      { signal }
    );
    const raw = unwrap(res.data);
    return {
      studentId,
      originalAutoScore: raw.originalScore != null ? Number(raw.originalScore) : null,
      finalScore: raw.finalScore != null ? Number(raw.finalScore) : null,
      completed: raw.completed ?? null,
      attemptsUsed: raw.attemptsUsed ?? (raw.submitted ? 1 : 0),
      attemptsAllowed: raw.attemptsAllowed ?? Number.MAX_SAFE_INTEGER,
    };
  },

  // Teacher: Grant or Reset student attempt allowance (audited)
  overrideAccess: async (
    classroomId: string,
    quizId: string,
    studentId: string,
    request: AccessOverrideRequest
  ): Promise<void> => {
    const action = request.resetAttempts ? 'RESET' : 'GRANT';
    const additionalAttempts = request.resetAttempts ? 0 : Math.max(1, request.extraAttempts || 1);
    await apiClient.post<ApiResponse<null>>(
      `${quizResource(classroomId, quizId)}/attempt-rights`,
      {
        studentId,
        action,
        additionalAttempts,
        reason: request.reason.trim(),
      }
    );
  },

  // Teacher: Grade override
  overrideGrade: async (
    classroomId: string,
    quizId: string,
    studentId: string,
    request: GradeOverrideRequest
  ): Promise<GradeChange> => {
    const res = await apiClient.post<ApiResponse<BackendGradeChange>>(
      `${quizResource(classroomId, quizId)}/students/${encodeURIComponent(studentId)}/grade-overrides`,
      request
    );
    const raw = unwrap(res.data);
    return {
      id: String(raw.id),
      actorId: raw.actorId,
      originalScore: Number(raw.originalScore),
      finalScore: Number(raw.finalScore),
      reason: raw.reason,
      createdAt: raw.createdAt,
    };
  },

  // Teacher: List grade overrides audit history
  listGradeOverrides: async (
    classroomId: string,
    quizId: string,
    studentId: string,
    page: number = 0,
    size: number = 20,
    signal?: AbortSignal
  ): Promise<PaginatedData<GradeChange>> => {
    const res = await apiClient.get<ApiResponse<BackendGradeChange[]>>(
      `${quizResource(classroomId, quizId)}/students/${encodeURIComponent(studentId)}/grade-overrides`,
      { signal }
    );
    const list = (unwrap(res.data) || []).map((raw) => ({
      id: String(raw.id),
      actorId: raw.actorId,
      originalScore: Number(raw.originalScore),
      finalScore: Number(raw.finalScore),
      reason: raw.reason,
      createdAt: raw.createdAt,
    }));
    return paginateArray(list, page, size);
  },
};

/**
 * Standard error extractor for Quiz API responses
 */
export function quizError(error: unknown): string {
  if (isAxiosError(error)) {
    const responseData = error.response?.data;
    if (responseData && typeof responseData === 'object') {
      const code = 'errorCode' in responseData ? String(responseData.errorCode) : '';
      if (code === 'QUIZ_ATTEMPT_LIMIT') {
        return 'You have reached the maximum number of allowed attempts for this quiz.';
      }
      if (code === 'QUIZ_TEACHER_PERMISSION_REQUIRED') {
        return 'Your previous attempt was locked on timeout. Please ask your teacher to grant attempt permission.';
      }
      if (code === 'QUIZ_UNAVAILABLE') {
        return 'This quiz is not currently open for attempts.';
      }
      if ('message' in responseData && responseData.message) {
        return code ? `${String(responseData.message)} (${code})` : String(responseData.message);
      }
    }
    if (error.response?.status === 401) return 'Session expired. Please log in again.';
    if (error.response?.status === 403) return 'You do not have permission to perform this action.';
    if (error.response?.status === 404) return 'The requested quiz resource was not found.';
    if (error.response?.status === 409) return 'Action conflict. The quiz or attempt state has changed.';
    return error.message || 'Network request failed.';
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'An unexpected error occurred.';
}
