import type { PracticeLanguage, PracticeScope, PracticeVerdict } from '../types';

const VIETNAM_TZ = 'Asia/Ho_Chi_Minh';

const dateTimeFormatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: VIETNAM_TZ,
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const timeOnlyFormatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: VIETNAM_TZ,
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

const dayKeyFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: VIETNAM_TZ,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

export function formatVietnamDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return dateTimeFormatter.format(date);
}

export function formatVietnamTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return timeOnlyFormatter.format(date);
}

export function getVietnamDayGroup(iso: string, now = new Date()): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return 'EARLIER';

  const targetKey = dayKeyFormatter.format(date);
  const todayKey = dayKeyFormatter.format(now);
  if (targetKey === todayKey) return 'TODAY';

  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  const yesterdayKey = dayKeyFormatter.format(yesterday);
  if (targetKey === yesterdayKey) return 'YESTERDAY';

  return new Intl.DateTimeFormat('en-GB', {
    timeZone: VIETNAM_TZ,
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  })
    .format(date)
    .toUpperCase();
}

export interface VerdictMeta {
  label: string;
  shortSymbol: string;
  tone: 'accepted' | 'failed' | 'pending' | 'system';
  summary: string;
}

export function getVerdictMeta(verdict: PracticeVerdict | string): VerdictMeta {
  switch (verdict) {
    case 'ACCEPTED':
      return {
        label: 'Accepted',
        shortSymbol: '✓',
        tone: 'accepted',
        summary: 'All evaluated test cases matched expected output within resource limits.',
      };
    case 'WRONG_ANSWER':
      return {
        label: 'Wrong Answer',
        shortSymbol: '×',
        tone: 'failed',
        summary: 'Program completed, but output differed from expected output on one or more cases.',
      };
    case 'COMPILATION_ERROR':
    case 'COMPILE_ERROR':
      return {
        label: 'Compilation Error',
        shortSymbol: '!',
        tone: 'failed',
        summary: 'Source code failed to compile in the isolated build step.',
      };
    case 'RUNTIME_ERROR':
      return {
        label: 'Runtime Error',
        shortSymbol: '!',
        tone: 'failed',
        summary: 'Execution terminated with a non-zero exit status or uncaught exception.',
      };
    case 'TIME_LIMIT_EXCEEDED':
      return {
        label: 'Time Limit Exceeded',
        shortSymbol: '⏱',
        tone: 'failed',
        summary: 'Execution exceeded the configured CPU/wall-clock time limit.',
      };
    case 'MEMORY_LIMIT_EXCEEDED':
      return {
        label: 'Memory Limit Exceeded',
        shortSymbol: '▣',
        tone: 'failed',
        summary: 'Process exceeded the configured sandbox memory ceiling.',
      };
    case 'OUTPUT_LIMIT_EXCEEDED':
      return {
        label: 'Output Limit Exceeded',
        shortSymbol: '!',
        tone: 'failed',
        summary: 'Standard output exceeded the maximum allowed byte stream size.',
      };
    case 'QUEUED':
    case 'RUNNING':
      return {
        label: verdict === 'QUEUED' ? 'Queued' : 'Running',
        shortSymbol: '⋯',
        tone: 'pending',
        summary: 'Code Judge is evaluating your solution in an isolated container.',
      };
    case 'INTERNAL_ERROR':
    case 'INFRASTRUCTURE_ERROR':
      return {
        label: 'System Error',
        shortSymbol: '⚠',
        tone: 'system',
        summary: 'Execution failed due to a sandbox infrastructure issue, not your solution.',
      };
    default:
      return {
        label: verdict.replace(/_/g, ' '),
        shortSymbol: '•',
        tone: 'failed',
        summary: 'Evaluation completed.',
      };
  }
}

export function formatLanguageLabel(language: PracticeLanguage): string {
  return language === 'JAVA' ? 'Java 21' : 'Python 3';
}

export function getSourceFilename(language: PracticeLanguage): string {
  return language === 'JAVA' ? 'Main.java' : 'solution.py';
}

export function formatMemoryUsage(memoryKb: number | null | undefined): string {
  if (memoryKb === null || memoryKb === undefined) return '—';
  if (memoryKb >= 1024) {
    return `${(memoryKb / 1024).toFixed(1)} MB`;
  }
  return `${memoryKb} KB`;
}

export function validateCustomInputBytes(input: string): string | null {
  const bytes = new TextEncoder().encode(input).length;
  if (bytes > 64 * 1024) {
    return `Custom input is ${(bytes / 1024).toFixed(1)} KB; maximum allowed is 64 KB.`;
  }
  return null;
}

export function buildProblemWorkspacePath(
  scope: PracticeScope,
  classroomId: string | null,
  problemId: string,
  returnSearch = '',
): string {
  const suffix = returnSearch ? `?from=${encodeURIComponent(returnSearch)}` : '';
  if (scope === 'public' || !classroomId) {
    return `/practice/problems/${encodeURIComponent(problemId)}${suffix}`;
  }
  return `/student/classrooms/${encodeURIComponent(classroomId)}/practice/${encodeURIComponent(problemId)}${suffix}`;
}

/** Extract a 1-based line number from deterministic Java/Python compiler or traceback diagnostics. */
export function parseDiagnosticLine(stderr: string, language: PracticeLanguage): number | null {
  if (!stderr) return null;
  if (language === 'JAVA') {
    const javaMatch = /(?:Main\.java|\.java):(\d+)\b/.exec(stderr);
    if (javaMatch) {
      const n = Number(javaMatch[1]);
      return Number.isInteger(n) && n > 0 ? n : null;
    }
  } else {
    const pyMatch = /File\s+"[^"]*",\s+line\s+(\d+)\b/.exec(stderr);
    if (pyMatch) {
      const n = Number(pyMatch[1]);
      return Number.isInteger(n) && n > 0 ? n : null;
    }
  }
  return null;
}

