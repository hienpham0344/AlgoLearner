import { isAxiosError } from 'axios';
import { apiClient } from '@/services/apiClient';
import type { ApiResponse, ApiErrorResponse, PaginatedData } from '@/types/api';
import type {
  CodeExample,
  CompletionMode,
  ExternalLink,
  Lesson,
  LessonActivity,
  LessonAttachment,
  LessonCapabilities,
  LessonInput,
  LessonProgress,
  LessonStatus,
  StudentLessonProgress,
} from '../types';

type BackendCompletionMode = 'MANUAL' | 'AUTO_ITEMS' | 'NONE';
type BackendMaterialType = 'PDF' | 'DOCX' | 'PPTX' | 'ZIP' | 'IMAGE';

interface BackendMaterialView {
  id: string;
  fileName: string;
  fileUrl: string;
  fileType: BackendMaterialType;
  fileSize: number;
  createdAt?: string;
}

interface BackendLinkedItemView {
  id: string;
  itemType: LessonActivity['type'];
  itemId: string;
  displayOrder?: number;
  title?: string | null;
  available?: boolean | null;
}

interface BackendLessonRaw {
  id: string;
  classroomId: string;
  title: string;
  content?: string | null;
  codeExample?: CodeExample | string | null;
  externalLinks?: ExternalLink[] | string | null;
  status: LessonStatus;
  previousStatus?: Exclude<LessonStatus, 'ARCHIVED'> | null;
  completionMode: CompletionMode | BackendCompletionMode;
  displayOrder?: number;
  materials?: BackendMaterialView[];
  attachments?: LessonAttachment[];
  linkedItems?: BackendLinkedItemView[];
  activities?: LessonActivity[];
  requiredItemIds?: string[];
  requiredActivityIds?: string[];
  isCompleted?: boolean | null;
  completedAt?: string | null;
  progress?: LessonProgress | null;
  canDelete?: boolean | null;
  createdAt: string;
  updatedAt: string;
}

interface BackendActivityOptionView {
  id: string;
  type: LessonActivity['type'];
  title: string;
  linkedLessonId: string | null;
  available: boolean;
  path?: string | null;
  completed?: boolean;
}

interface BackendFileMetadata {
  id: string;
  name: string;
  mediaType: string;
  kind: string;
  size: number;
  purpose: string;
  state: string;
  sha256: string;
  createdAt: string;
}

interface BackendStudentProgressRow {
  studentId: string;
  username?: string;
  displayName?: string;
  completed: boolean;
  completedAt: string | null;
}

interface BackendLessonRequest {
  title: string;
  content: string;
  codeExample: string;
  externalLinks: string;
  completionMode: 'MANUAL' | 'AUTO_ITEMS' | 'NONE';
}

interface BackendCompletionRuleRequest {
  operator: 'ALL' | 'ANY';
  conditions: Array<{
    itemType: 'ASSIGNMENT' | 'QUIZ';
    itemId: string;
    requirement: 'SUBMITTED' | 'SCORE_AT_LEAST';
    scorePercent: number | null;
  }>;
}

const PAGE_SIZE = 20;
const materialDownloadPaths = new Map<string, string>();
const activityTypeCache = new Map<string, Map<string, LessonActivity['type']>>();

const base = (classId: string) => `/classrooms/${encodeURIComponent(classId)}/lessons`;
const resource = (classId: string, lessonId: string) => `${base(classId)}/${encodeURIComponent(lessonId)}`;
const unwrap = <T>({ data }: { data: ApiResponse<T> }): T => {
  if (!data.success) throw new Error(data.message || 'The lesson request failed.');
  return data.data;
};

function stripApiPrefix(url: string): string {
  return url.startsWith('/api/v1/') ? url.slice('/api/v1'.length) : url;
}

function mimeFromFileType(fileType: BackendMaterialType, fileName?: string): string {
  switch (fileType) {
    case 'PDF': return 'application/pdf';
    case 'DOCX': return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
    case 'PPTX': return 'application/vnd.openxmlformats-officedocument.presentationml.presentation';
    case 'ZIP': return 'application/zip';
    case 'IMAGE': {
      const ext = fileName?.split('.').pop()?.toLowerCase();
      if (ext === 'png') return 'image/png';
      if (ext === 'jpg' || ext === 'jpeg') return 'image/jpeg';
      if (ext === 'gif') return 'image/gif';
      if (ext === 'webp') return 'image/webp';
      return 'image/*';
    }
    default: return 'application/octet-stream';
  }
}

function parseCodeExample(raw: CodeExample | string | null | undefined): CodeExample | null {
  if (raw == null) return null;
  if (typeof raw === 'object') {
    if (!raw.code) return null;
    const language = raw.language === 'java' || raw.language === 'python' || raw.language === 'text' ? raw.language : 'text';
    return { language, code: raw.code };
  }
  if (typeof raw !== 'string' || raw.trim() === '') return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (parsed && typeof parsed === 'object' && 'code' in parsed && typeof parsed.code === 'string') {
      if (!parsed.code) return null;
      const lang = 'language' in parsed ? parsed.language : undefined;
      const language = lang === 'java' || lang === 'python' || lang === 'text' ? lang : 'text';
      return { language, code: parsed.code };
    }
  } catch {
    // Non-JSON legacy plain-text code example string.
  }
  return { language: 'text', code: raw };
}

function normalizeExternalLinksList(items: unknown[]): ExternalLink[] {
  const result: ExternalLink[] = [];
  for (const item of items) {
    if (typeof item === 'string') {
      result.push({ label: item, url: item });
    } else if (item && typeof item === 'object' && 'url' in item && typeof item.url === 'string') {
      const label = 'label' in item && typeof item.label === 'string' ? item.label : item.url;
      result.push({ label, url: item.url });
    }
  }
  return result;
}

function parseExternalLinks(raw: ExternalLink[] | string | null | undefined): ExternalLink[] {
  if (Array.isArray(raw)) return normalizeExternalLinksList(raw);
  if (typeof raw !== 'string' || raw.trim() === '') return [];
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? normalizeExternalLinksList(parsed) : [];
  } catch {
    return [];
  }
}

function toAttachment(material: BackendMaterialView): LessonAttachment {
  if (material.fileUrl) {
    materialDownloadPaths.set(material.id, stripApiPrefix(material.fileUrl));
  }
  return {
    id: material.id,
    name: material.fileName,
    size: material.fileSize,
    contentType: mimeFromFileType(material.fileType, material.fileName),
  };
}

function toActivity(item: BackendLinkedItemView, lessonId: string): LessonActivity {
  return {
    id: item.itemId,
    type: item.itemType,
    title: item.title ?? item.itemType,
    linkedLessonId: lessonId,
    available: item.available ?? true,
    path: null,
    completed: false,
  };
}

function toLesson(raw: BackendLessonRaw): Lesson {
  const attachments = raw.attachments ?? raw.materials?.map(toAttachment) ?? [];
  const activities = raw.activities ?? raw.linkedItems?.map(item => toActivity(item, raw.id)) ?? [];
  if (activities.length > 0) {
    const cached = activityTypeCache.get(raw.classroomId) ?? new Map<string, LessonActivity['type']>();
    for (const act of activities) cached.set(act.id, act.type);
    activityTypeCache.set(raw.classroomId, cached);
  }
  const completionMode: CompletionMode =
    raw.completionMode === 'AUTO_ITEMS' || raw.completionMode === 'CONDITION_BASED' ? 'CONDITION_BASED' : 'MANUAL';
  const progress: LessonProgress | null =
    raw.progress !== undefined
      ? raw.progress
      : raw.isCompleted !== null && raw.isCompleted !== undefined
        ? { completed: Boolean(raw.isCompleted), completedAt: raw.completedAt ?? null }
        : null;

  return {
    id: raw.id,
    classroomId: raw.classroomId,
    title: raw.title,
    content: raw.content ?? '',
    codeExample: parseCodeExample(raw.codeExample),
    externalLinks: parseExternalLinks(raw.externalLinks),
    completionMode,
    requiredActivityIds: raw.requiredActivityIds ?? raw.requiredItemIds ?? [],
    status: raw.status,
    previousStatus: raw.previousStatus ?? null,
    activities,
    attachments,
    progress,
    canDelete: raw.canDelete ?? false,
    createdAt: raw.createdAt,
    updatedAt: raw.updatedAt,
  };
}

function toBackendRequest(input: LessonInput, forceManualForRuleSetup: boolean): BackendLessonRequest {
  return {
    title: input.title,
    content: input.content,
    codeExample: input.codeExample ? JSON.stringify(input.codeExample) : '',
    externalLinks: JSON.stringify(input.externalLinks),
    completionMode: forceManualForRuleSetup
      ? 'MANUAL'
      : input.completionMode === 'CONDITION_BASED'
        ? 'AUTO_ITEMS'
        : 'MANUAL',
  };
}

function sameIdSet(a: string[], b: string[]): boolean {
  const setA = new Set(a);
  const setB = new Set(b);
  return setA.size === setB.size && [...setA].every(id => setB.has(id));
}

async function resolveActivityTypes(
  classId: string,
  neededIds: string[],
  knownActivities: LessonActivity[] = [],
): Promise<Map<string, LessonActivity['type']>> {
  const typeMap = new Map<string, LessonActivity['type']>(activityTypeCache.get(classId));
  for (const act of knownActivities) typeMap.set(act.id, act.type);
  if (neededIds.some(id => !typeMap.has(id))) {
    const fetched = await lessonApi.activities(classId);
    for (const act of fetched) typeMap.set(act.id, act.type);
  }
  return typeMap;
}

function buildCompletionRule(
  requiredIds: string[],
  typeMap: Map<string, LessonActivity['type']>,
): BackendCompletionRuleRequest {
  return {
    operator: 'ALL',
    conditions: requiredIds.map(itemId => {
      const itemType = typeMap.get(itemId);
      if (!itemType || itemType === 'PRACTICE') {
        throw new Error('Choose at least one linked assignment or quiz as a completion requirement.');
      }
      return {
        itemType,
        itemId,
        requirement: 'SUBMITTED' as const,
        scorePercent: null,
      };
    }),
  };
}

export const lessonKeys = {
  root: (userId: string, classId: string) => ['lessons', userId, classId] as const,
  list: (userId: string, classId: string, page: number) => [...lessonKeys.root(userId, classId), 'list', page] as const,
  detail: (userId: string, classId: string, lessonId: string) => [...lessonKeys.root(userId, classId), 'detail', lessonId] as const,
  capabilities: (userId: string, classId: string) => [...lessonKeys.root(userId, classId), 'capabilities'] as const,
  activities: (userId: string, classId: string) => [...lessonKeys.root(userId, classId), 'activities'] as const,
  progress: (userId: string, classId: string, lessonId: string, page: number) => [...lessonKeys.root(userId, classId), 'progress', lessonId, page] as const,
};

// Adapter aligned with backend LessonController & FileController contracts.
export const lessonApi = {
  list: async (classId: string, page = 0, signal?: AbortSignal): Promise<PaginatedData<Lesson>> => {
    const raw = unwrap(
      await apiClient.get<ApiResponse<BackendLessonRaw[] | PaginatedData<BackendLessonRaw>>>(base(classId), { signal }),
    );
    if (Array.isArray(raw)) {
      const totalElements = raw.length;
      return {
        items: raw.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE).map(toLesson),
        pagination: {
          page,
          size: PAGE_SIZE,
          totalElements,
          totalPages: Math.ceil(totalElements / PAGE_SIZE),
          isFirst: page === 0,
          isLast: (page + 1) * PAGE_SIZE >= totalElements,
        },
      };
    }
    return { items: raw.items.map(toLesson), pagination: raw.pagination };
  },
  get: async (classId: string, lessonId: string, signal?: AbortSignal): Promise<Lesson> =>
    toLesson(unwrap(await apiClient.get<ApiResponse<BackendLessonRaw>>(resource(classId, lessonId), { signal }))),
  create: async (classId: string, input: LessonInput): Promise<Lesson> => {
    const needsRuleSetup = input.completionMode === 'CONDITION_BASED' && input.requiredActivityIds.length > 0;
    const created = unwrap(
      await apiClient.post<ApiResponse<BackendLessonRaw>>(base(classId), toBackendRequest(input, needsRuleSetup)),
    );
    if (input.activityIds.length === 0 && !needsRuleSetup) {
      return toLesson(created);
    }
    const typeMap = await resolveActivityTypes(classId, [...input.activityIds, ...input.requiredActivityIds]);
    for (const itemId of input.activityIds) {
      const itemType = typeMap.get(itemId);
      if (!itemType) throw new Error(`Activity type is unavailable for item ${itemId}.`);
      await apiClient.post(`${resource(classId, created.id)}/items`, { itemType, itemId });
    }
    if (needsRuleSetup) {
      await apiClient.put<ApiResponse<null>>(
        `${resource(classId, created.id)}/completion-rule`,
        buildCompletionRule(input.requiredActivityIds, typeMap),
      );
    }
    return lessonApi.get(classId, created.id);
  },
  update: async (classId: string, lessonId: string, input: LessonInput): Promise<Lesson> => {
    const current = await lessonApi.get(classId, lessonId);
    const needsRuleSetup = input.completionMode === 'CONDITION_BASED' && input.requiredActivityIds.length > 0;
    const forceManualForRuleSetup =
      needsRuleSetup && current.status === 'PUBLISHED' && current.requiredActivityIds.length === 0;
    const updated = unwrap(
      await apiClient.put<ApiResponse<BackendLessonRaw>>(
        resource(classId, lessonId),
        toBackendRequest(input, forceManualForRuleSetup),
      ),
    );
    const currentIds = new Set(current.activities.map(activity => activity.id));
    const nextIds = new Set(input.activityIds);
    const addedIds = input.activityIds.filter(id => !currentIds.has(id));
    const removedIds = current.activities.map(activity => activity.id).filter(id => !nextIds.has(id));
    const ruleChanged =
      needsRuleSetup &&
      (current.completionMode !== 'CONDITION_BASED' || !sameIdSet(current.requiredActivityIds, input.requiredActivityIds));

    if (addedIds.length === 0 && removedIds.length === 0 && !ruleChanged) {
      return toLesson(updated);
    }
    const typeMap =
      addedIds.length > 0 || ruleChanged
        ? await resolveActivityTypes(
            classId,
            [...addedIds, ...(ruleChanged ? input.requiredActivityIds : [])],
            current.activities,
          )
        : new Map<string, LessonActivity['type']>();

    for (const itemId of addedIds) {
      const itemType = typeMap.get(itemId);
      if (!itemType) throw new Error(`Activity type is unavailable for item ${itemId}.`);
      await apiClient.post(`${resource(classId, lessonId)}/items`, { itemType, itemId });
    }
    if (ruleChanged) {
      await apiClient.put<ApiResponse<null>>(
        `${resource(classId, lessonId)}/completion-rule`,
        buildCompletionRule(input.requiredActivityIds, typeMap),
      );
    }
    for (const itemId of removedIds) {
      await apiClient.delete(`${resource(classId, lessonId)}/items/${encodeURIComponent(itemId)}`);
    }
    return lessonApi.get(classId, lessonId);
  },
  transition: async (
    classId: string,
    lessonId: string,
    action: 'publish' | 'draft' | 'archive' | 'restore',
  ): Promise<Lesson> => {
    const mappedAction = action === 'draft' ? 'unpublish' : action;
    return toLesson(
      unwrap(await apiClient.patch<ApiResponse<BackendLessonRaw>>(`${resource(classId, lessonId)}/${mappedAction}`)),
    );
  },
  remove: async (classId: string, lessonId: string): Promise<void> => {
    await apiClient.delete<ApiResponse<null> | undefined>(resource(classId, lessonId));
  },
  complete: async (classId: string, lessonId: string): Promise<Lesson> => {
    const res = await apiClient.post<ApiResponse<BackendLessonRaw> | string | undefined>(
      `${resource(classId, lessonId)}/complete`,
    );
    if (res.data && typeof res.data === 'object' && 'success' in res.data) {
      if (!res.data.success) throw new Error(res.data.message || 'The lesson request failed.');
      if (res.data.data) return toLesson(res.data.data);
    }
    return lessonApi.get(classId, lessonId);
  },
  capabilities: async (classId: string): Promise<LessonCapabilities> =>
    unwrap(await apiClient.get<ApiResponse<LessonCapabilities>>(`${base(classId)}/capabilities`)),
  activities: async (classId: string): Promise<LessonActivity[]> => {
    const items = unwrap(await apiClient.get<ApiResponse<BackendActivityOptionView[]>>(`${base(classId)}/activities`));
    const cached = new Map<string, LessonActivity['type']>();
    const normalized = items.map(item => {
      cached.set(item.id, item.type);
      return {
        id: item.id,
        type: item.type,
        title: item.title,
        linkedLessonId: item.linkedLessonId ?? null,
        available: item.available,
        path: item.path ?? null,
        completed: item.completed ?? false,
      };
    });
    activityTypeCache.set(classId, cached);
    return normalized;
  },
  progress: async (classId: string, lessonId: string, page = 0): Promise<PaginatedData<StudentLessonProgress>> => {
    const data = unwrap(
      await apiClient.get<ApiResponse<PaginatedData<BackendStudentProgressRow>>>(
        `${resource(classId, lessonId)}/progress`,
        { params: { page, size: PAGE_SIZE } },
      ),
    );
    return {
      items: data.items.map(row => ({
        studentId: row.studentId,
        displayName: row.displayName ?? row.username ?? row.studentId,
        completed: row.completed,
        completedAt: row.completedAt,
      })),
      pagination: data.pagination,
    };
  },
  upload: async (classId: string, lessonId: string, file: File): Promise<LessonAttachment> => {
    const form = new FormData();
    form.append('file', file);
    const uploaded = unwrap(
      await apiClient.post<ApiResponse<BackendFileMetadata>>(`/classrooms/${encodeURIComponent(classId)}/files`, form, {
        params: { purpose: 'LESSON_MATERIAL' },
        headers: { 'Content-Type': undefined },
      }),
    );
    const material = unwrap(
      await apiClient.post<ApiResponse<BackendMaterialView>>(`${resource(classId, lessonId)}/materials`, {
        fileId: uploaded.id,
      }),
    );
    return toAttachment(material);
  },
  removeAttachment: async (classId: string, lessonId: string, attachmentId: string): Promise<void> => {
    await apiClient.delete<ApiResponse<null> | undefined>(
      `${resource(classId, lessonId)}/materials/${encodeURIComponent(attachmentId)}`,
    );
    materialDownloadPaths.delete(attachmentId);
  },
  // Binary responses are intentionally outside the JSON envelope; errors retain ApiErrorResponse.
  download: async (classId: string, lessonId: string, attachmentId: string): Promise<Blob> => {
    let path = materialDownloadPaths.get(attachmentId);
    if (!path) {
      await lessonApi.get(classId, lessonId);
      path = materialDownloadPaths.get(attachmentId);
    }
    const target = path || `/files/${encodeURIComponent(attachmentId)}/content`;
    try {
      return (await apiClient.get<Blob>(target, { responseType: 'blob' })).data;
    } catch (error) {
      if (isAxiosError<ApiErrorResponse | Blob>(error) && error.response?.data instanceof Blob) {
        try { error.response.data = JSON.parse(await error.response.data.text()) as ApiErrorResponse; } catch { /* Non-JSON storage error: keep the HTTP status. */ }
      }
      throw error;
    }
  },
};

export function lessonError(error: unknown): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    const status = error.response?.status;
    if (status === 401) return 'Your session has expired. Please sign in again.';
    if (status === 403) return 'You do not have permission to access or change this lesson.';
    if (status === 404 || status === 503) return 'This lesson or service is currently unavailable.';
    if (status === 409) return error.response?.data?.message || 'The lesson changed or has related learning data. Refresh and try again; archive it to preserve history.';
    if (status === 400 || status === 422) return [error.response?.data?.message, ...(error.response?.data?.errors || []).map(field => `${field.field}: ${field.message}`)].filter(Boolean).join(' ') || 'Check the lesson fields and try again.';
    return error.response?.data?.message || (error.response ? `The request failed (HTTP ${status}). Please try again.` : 'Cannot connect. Check your connection and try again.');
  }
  return error instanceof Error ? error.message : 'The lesson request failed.';
}
