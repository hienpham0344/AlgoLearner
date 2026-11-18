import { marked } from 'marked';
import { stripMathDelimiters } from './lessonMath';
import type { LessonInput } from './types';

export interface LessonOutlineItem {
  id: string;
  label: string;
  level: 1 | 2 | 3 | 4;
}

const STORAGE_KEY_READ_PROGRESS = 'algolearner_lesson_read_pct_v1';

function stripInlineMarkdown(text: string): string {
  return stripMathDelimiters(text)
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/__([^_]+)__/g, '$1')
    .replace(/_([^_]+)_/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .trim();
}

export function makeUniqueHeadingId(
  text: string,
  usedIds: Map<string, number>,
  fallbackIndex: number,
): string {
  const baseSlug = text
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-') || `section-${fallbackIndex}`;
  const count = usedIds.get(baseSlug) || 0;
  usedIds.set(baseSlug, count + 1);
  return count === 0 ? baseSlug : `${baseSlug}-${count + 1}`;
}

export function extractLessonOutline(
  lesson: Pick<LessonInput, 'content' | 'codeExample' | 'externalLinks'>,
  includeSupportSection = true,
): LessonOutlineItem[] {
  const items: LessonOutlineItem[] = [];
  const usedIds = new Map<string, number>();

  if (lesson.content?.trim()) {
    const tokens = marked.lexer(lesson.content);
    let idx = 0;
    for (const token of tokens) {
      if (token.type === 'heading' && token.depth >= 1 && token.depth <= 4) {
        idx += 1;
        const label = stripInlineMarkdown(token.text);
        if (!label) continue;
        const id = makeUniqueHeadingId(label, usedIds, idx);
        items.push({
          id,
          label,
          level: token.depth as 1 | 2 | 3 | 4,
        });
      }
    }
  }

  if (lesson.codeExample?.code?.trim()) {
    items.push({ id: 'lesson-code-example', label: 'Code example', level: 2 });
  }
  if (lesson.externalLinks.length > 0) {
    items.push({ id: 'lesson-further-reading', label: 'Further reading', level: 2 });
  }
  if (includeSupportSection) {
    items.push({ id: 'lesson-materials-completion', label: 'Materials & Completion', level: 2 });
  }

  return items;
}

export function estimateReadingMinutes(
  lesson: Pick<LessonInput, 'content' | 'codeExample'>,
): number {
  const words = lesson.content
    ? lesson.content.trim().split(/\s+/).filter(Boolean).length
    : 0;
  const codeLines = lesson.codeExample?.code
    ? lesson.codeExample.code.trim().split('\n').length
    : 0;
  const minutes = words / 180 + codeLines / 25;
  return Math.max(1, Math.ceil(minutes));
}

export function readSavedLessonProgress(userId: string, lessonId: string): number {
  try {
    if (typeof sessionStorage === 'undefined') return 0;
    const raw = sessionStorage.getItem(STORAGE_KEY_READ_PROGRESS);
    if (!raw) return 0;
    const map = JSON.parse(raw) as Record<string, Record<string, number>>;
    const value = map[userId]?.[lessonId];
    return typeof value === 'number' ? Math.max(0, Math.min(100, value)) : 0;
  } catch {
    return 0;
  }
}

export function saveLessonReadProgress(userId: string, lessonId: string, percent: number): number {
  const clamped = Math.max(0, Math.min(100, Math.round(percent)));
  try {
    if (typeof sessionStorage === 'undefined') return clamped;
    const raw = sessionStorage.getItem(STORAGE_KEY_READ_PROGRESS);
    const map = raw ? (JSON.parse(raw) as Record<string, Record<string, number>>) : {};
    const prev = map[userId]?.[lessonId] || 0;
    const next = Math.max(prev, clamped);
    map[userId] = { ...(map[userId] || {}), [lessonId]: next };
    sessionStorage.setItem(STORAGE_KEY_READ_PROGRESS, JSON.stringify(map));
    return next;
  } catch {
    return clamped;
  }
}

