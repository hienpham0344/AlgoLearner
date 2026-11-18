export type LessonRole = 'TEACHER' | 'STUDENT';
export type LessonStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type CompletionMode = 'MANUAL' | 'CONDITION_BASED';
export interface ExternalLink { label: string; url: string }
export interface CodeExample { language: 'java' | 'python' | 'text'; code: string }
export interface LessonActivity {
  id: string;
  type: 'ASSIGNMENT' | 'QUIZ' | 'PRACTICE';
  title: string;
  linkedLessonId: string | null;
  available: boolean;
  // Only a same-class route supplied by the corresponding module can be opened.
  path: string | null;
  completed: boolean;
}
export interface LessonAttachment { id: string; name: string; size: number; contentType: string }
export interface LessonProgress { completed: boolean; completedAt: string | null }
export interface StudentLessonProgress extends LessonProgress { studentId: string; displayName: string }
export interface LessonInput {
  title: string;
  content: string;
  codeExample: CodeExample | null;
  externalLinks: ExternalLink[];
  completionMode: CompletionMode;
  activityIds: string[];
  requiredActivityIds: string[];
}
export interface Lesson extends Omit<LessonInput, 'activityIds'> {
  id: string;
  classroomId: string;
  status: LessonStatus;
  previousStatus: Exclude<LessonStatus, 'ARCHIVED'> | null;
  activities: LessonActivity[];
  attachments: LessonAttachment[];
  progress: LessonProgress | null;
  canDelete: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface LessonCapabilities {
  uploadsAvailable: boolean;
  activityLinkingAvailable: boolean;
  maxFileSizeBytes: number;
  allowedExtensions: string[];
}
