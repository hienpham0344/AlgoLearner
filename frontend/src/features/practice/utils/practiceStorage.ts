import { UNAUTHORIZED_EVENT } from '@/services/apiClient';
import type {
  PracticeLanguage,
  PracticeProblemType,
  PracticeScope,
  SavedDraftMetadata,
} from '../types';

const DRAFT_PREFIX = 'algolearner:practice-draft:v2:';
const RECENT_INDEX_PREFIX = 'algolearner:practice-recent:v2:';
const LAYOUT_PREF_KEY = 'algolearner:practice-layout:v1';

export const DEFAULT_STARTER_CODE: Record<PracticeLanguage, string> = {
  JAVA: `import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        // Write your solution here
    }
}
`,
  PYTHON: `import sys


def solve() -> None:
    data = sys.stdin.read().split()
    # Write your solution here


if __name__ == "__main__":
    solve()
`,
};

function buildDraftKey(
  userId: string,
  scope: PracticeScope,
  classroomId: string | null,
  problemId: string,
  language: PracticeLanguage,
): string {
  return `${DRAFT_PREFIX}${userId}:${scope}:${classroomId || 'public'}:${problemId}:${language}`;
}

function buildRecentKey(userId: string): string {
  return `${RECENT_INDEX_PREFIX}${userId}`;
}

export function readPracticeDraft(
  userId: string,
  scope: PracticeScope,
  classroomId: string | null,
  problemId: string,
  language: PracticeLanguage,
): string | null {
  if (!userId) return null;
  try {
    return localStorage.getItem(buildDraftKey(userId, scope, classroomId, problemId, language));
  } catch {
    return null;
  }
}

export function hasAnySavedDraft(
  userId: string,
  scope: PracticeScope,
  classroomId: string | null,
  problemId: string,
): boolean {
  if (!userId) return false;
  try {
    const javaDraft = localStorage.getItem(
      buildDraftKey(userId, scope, classroomId, problemId, 'JAVA'),
    );
    const pyDraft = localStorage.getItem(
      buildDraftKey(userId, scope, classroomId, problemId, 'PYTHON'),
    );
    return Boolean((javaDraft && javaDraft.trim()) || (pyDraft && pyDraft.trim()));
  } catch {
    return false;
  }
}

export function writePracticeDraft(params: {
  userId: string;
  scope: PracticeScope;
  classroomId: string | null;
  classroomName?: string | null;
  problemId: string;
  title: string;
  problemType: PracticeProblemType | null;
  language: PracticeLanguage;
  code: string;
  starterCode?: string;
}): void {
  const { userId, scope, classroomId, classroomName = null, problemId, title, problemType, language, code, starterCode } = params;
  if (!userId || !problemId) return;

  try {
    const key = buildDraftKey(userId, scope, classroomId, problemId, language);
    // Only record as an active unfinished draft if non-empty and modified from starter template
    if (!code.trim() || (starterCode !== undefined && code === starterCode)) {
      localStorage.removeItem(key);
      return;
    }
    localStorage.setItem(key, code);

    const meta: SavedDraftMetadata = {
      userId,
      scope,
      classroomId,
      classroomName,
      problemId,
      title,
      problemType,
      language,
      updatedAt: new Date().toISOString(),
    };
    const existing = readRecentDrafts(userId).filter(
      item => !(item.scope === scope && item.classroomId === classroomId && item.problemId === problemId),
    );
    existing.unshift(meta);
    localStorage.setItem(buildRecentKey(userId), JSON.stringify(existing.slice(0, 12)));
  } catch {
    // Ignore storage quota errors safely
  }
}

export function clearPracticeDraft(
  userId: string,
  scope: PracticeScope,
  classroomId: string | null,
  problemId: string,
  language: PracticeLanguage,
): void {
  if (!userId) return;
  try {
    localStorage.removeItem(buildDraftKey(userId, scope, classroomId, problemId, language));
  } catch {
    // Ignore
  }
}

export function readRecentDrafts(userId: string): SavedDraftMetadata[] {
  if (!userId) return [];
  try {
    const raw = localStorage.getItem(buildRecentKey(userId));
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (item): item is SavedDraftMetadata =>
        typeof item === 'object' &&
        item !== null &&
        (item as SavedDraftMetadata).userId === userId &&
        typeof (item as SavedDraftMetadata).problemId === 'string',
    );
  } catch {
    return [];
  }
}

/** Purge drafts belonging to a user when signing out on a shared machine, or purge stale anonymous keys. */
export function purgeOtherUserDrafts(activeUserId: string | null): void {
  try {
    const keysToRemove: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (!key) continue;
      if (key.startsWith(DRAFT_PREFIX) || key.startsWith(RECENT_INDEX_PREFIX) || key.startsWith('algolearner:practice-draft:')) {
        if (!activeUserId || (!key.startsWith(`${DRAFT_PREFIX}${activeUserId}:`) && key !== `${RECENT_INDEX_PREFIX}${activeUserId}`)) {
          keysToRemove.push(key);
        }
      }
    }
    keysToRemove.forEach(k => localStorage.removeItem(k));
  } catch {
    // Ignore
  }
}

export type WorkspaceMode = 'split' | 'focus' | 'console';
export type ConsolePanelState = 'collapsed' | 'compact' | 'expanded';

export interface WorkspaceSplitLayout {
  mode: WorkspaceMode;
  leftWidthPercent: number;
  editorHeightPercent: number;
  consoleState: ConsolePanelState;
}

const DEFAULT_LAYOUT: WorkspaceSplitLayout = {
  mode: 'split',
  leftWidthPercent: 36,
  editorHeightPercent: 64,
  consoleState: 'compact',
};

export function readWorkspaceLayout(): WorkspaceSplitLayout {
  try {
    const raw = localStorage.getItem(LAYOUT_PREF_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<WorkspaceSplitLayout>;
      const mode: WorkspaceMode =
        parsed.mode === 'focus' || parsed.mode === 'console' || parsed.mode === 'split'
          ? parsed.mode
          : DEFAULT_LAYOUT.mode;
      const left =
        typeof parsed.leftWidthPercent === 'number'
          ? Math.min(58, Math.max(26, parsed.leftWidthPercent))
          : DEFAULT_LAYOUT.leftWidthPercent;
      const top =
        typeof parsed.editorHeightPercent === 'number'
          ? Math.min(76, Math.max(30, parsed.editorHeightPercent))
          : DEFAULT_LAYOUT.editorHeightPercent;
      const consoleState: ConsolePanelState =
        parsed.consoleState === 'collapsed' ||
        parsed.consoleState === 'compact' ||
        parsed.consoleState === 'expanded'
          ? parsed.consoleState
          : DEFAULT_LAYOUT.consoleState;
      return { mode, leftWidthPercent: left, editorHeightPercent: top, consoleState };
    }
  } catch {
    // Ignore
  }
  return { ...DEFAULT_LAYOUT };
}

export function writeWorkspaceLayout(layout: WorkspaceSplitLayout): void {
  try {
    localStorage.setItem(LAYOUT_PREF_KEY, JSON.stringify(layout));
  } catch {
    // Ignore
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener(UNAUTHORIZED_EVENT, () => {
    purgeOtherUserDrafts(null);
  });
}
