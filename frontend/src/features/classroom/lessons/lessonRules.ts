import type { Lesson, LessonActivity, LessonCapabilities, LessonInput, LessonRole } from './types';

export const SUPPORTED_EXTENSIONS = ['pdf', 'docx', 'pptx', 'zip', 'png', 'jpg', 'jpeg', 'gif', 'webp'];
export const EMPTY_LESSON: LessonInput = { title: '', content: '', codeExample: null,
  externalLinks: [], completionMode: 'MANUAL', activityIds: [], requiredActivityIds: [] };

export function safeExternalUrl(value: string): string | null {
  if (!/^https?:\/\//i.test(value)) return null;
  try {
    const url = new URL(value);
    return url.username || url.password ? null : url.href;
  } catch { return null; }
}

export function activitySelectable(activity: LessonActivity, lessonId?: string): boolean {
  return activity.type !== 'QUIZ' || !activity.linkedLessonId || activity.linkedLessonId === lessonId;
}

export function validateLesson(input: LessonInput, activities: LessonActivity[], lessonId?: string) {
  const errors: Record<string, string> = {};
  if (!input.title.trim() || input.title.trim().length > 200) errors.title = 'Enter a lesson title of 1–200 characters.';
  if (input.content.length > 100_000 || (input.codeExample?.code.length ?? 0) > 50_000) errors.content = 'The lesson content is too long.';
  if (input.externalLinks.some(link => !link.label.trim() || !safeExternalUrl(link.url.trim()))) errors.links = 'Each link needs a label and a valid http:// or https:// URL without credentials.';
  if (input.activityIds.some(id => !activities.some(activity => activity.id === id && activitySelectable(activity, lessonId)))) errors.activities = 'An activity is unavailable or its quiz already belongs to another lesson.';
  if (input.completionMode === 'CONDITION_BASED' && (!input.requiredActivityIds.length || input.requiredActivityIds.some(id =>
    !input.activityIds.includes(id) || !activities.some(activity => activity.id === id && activity.type !== 'PRACTICE')))) {
    errors.completion = 'Choose at least one linked assignment or quiz as a completion requirement.';
  }
  return errors;
}

export function validateAttachment(file: Pick<File, 'name' | 'size'>, policy: LessonCapabilities | null): string | null {
  if (!policy?.uploadsAvailable || policy.maxFileSizeBytes <= 0) return 'File uploads are currently unavailable. Your lesson content is preserved.';
  const extension = file.name.split('.').pop()?.toLowerCase() || '';
  if (!SUPPORTED_EXTENSIONS.includes(extension) || !policy.allowedExtensions.map(value => value.toLowerCase()).includes(extension)) return 'This file type is not allowed. Choose a supported document, source ZIP or image.';
  if (file.size <= 0 || file.size > policy.maxFileSizeBytes) return `Choose a non-empty file up to ${formatBytes(policy.maxFileSizeBytes)}.`;
  return null;
}

export const visibleLessons = (lessons: Lesson[], role: LessonRole) => lessons.filter(lesson => role === 'TEACHER' || lesson.status === 'PUBLISHED');
export const canCompleteLesson = (lesson: Lesson) => lesson.status === 'PUBLISHED' && lesson.completionMode === 'MANUAL' && !lesson.progress?.completed;
export const formatBytes = (bytes: number) => bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
export function lessonDate(value: string | null): string {
  if (!value || !Number.isFinite(Date.parse(value))) return '—';
  return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(value));
}

export function activityPath(path: string | null, classroomId: string, role: LessonRole): string | null {
  if (!path || !path.startsWith(`/${role.toLowerCase()}/classrooms/${encodeURIComponent(classroomId)}/`)) return null;
  try {
    const url = new URL(path, 'https://algolearner.invalid');
    if (url.origin !== 'https://algolearner.invalid' || !url.pathname.startsWith(`/${role.toLowerCase()}/classrooms/${encodeURIComponent(classroomId)}/`)) return null;
    return url.pathname + url.search;
  } catch { return null; }
}
