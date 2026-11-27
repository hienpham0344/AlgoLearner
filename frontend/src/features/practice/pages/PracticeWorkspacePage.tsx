import React, { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileCode2, Play, RotateCcw, Send } from 'lucide-react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { useIsDemoMode } from '@/components/BackendStatusBanner';
import { FilterSelect } from '@/components/ui/FilterSelect';
import { classroomApi, classroomKeys } from '@/features/classroom/api/classroomApi';
import { readStoredClassrooms } from '@/features/classroom/mock/useClassroomStore';
import type { PaginatedData } from '@/types/api';
import {
  classifyPracticeError,
  practiceApi,
  practiceKeys,
  publicPracticeApi,
  type ClassifiedPracticeError,
} from '../api/practiceApi';
import { CodeEditorFallback } from '../components/editor/CodeEditorFallback';
import type { EditorialCodeEditor } from '../components/editor/CodeEditorSurface';
import {
  ExecutionConsole,
  type ConsoleSampleCase,
  type ConsoleSubmissionOutcome,
  type ConsoleTab,
} from '../components/execution/ExecutionConsole';
import { ProblemFolio } from '../components/folio/ProblemFolio';
import { FloatingProblemReference } from '../components/workspace/FloatingProblemReference';
import { ProblemReadingPane, type ReadingTab } from '../components/workspace/ProblemReadingPane';
import { WorkspaceTopbar } from '../components/workspace/WorkspaceTopbar';
import type {
  ClassPracticeSummary,
  PracticeLanguage,
  PracticeProblemType,
  PracticeScope,
  PublicEvaluationResult,
  PublicProblemSummary,
  RunResult,
} from '../types';
import {
  formatLanguageLabel,
  getSourceFilename,
  validateCustomInputBytes,
} from '../utils/practiceFormatters';
import { preloadPracticeWorkspace } from '../utils/practicePrefetch';
import {
  clearPracticeDraft,
  DEFAULT_STARTER_CODE,
  readPracticeDraft,
  readWorkspaceLayout,
  writePracticeDraft,
  writeWorkspaceLayout,
  type ConsolePanelState,
  type WorkspaceMode,
  type WorkspaceSplitLayout,
} from '../utils/practiceStorage';
import '../styles/practice.css';

const LazyCodeEditorSurface = lazy(() => import('../components/editor/CodeEditorSurface'));

type MobileTab = 'description' | 'code' | 'tests' | 'history';

interface NormalizedWorkspaceProblem {
  id: string;
  scope: PracticeScope;
  classroomId: string | null;
  title: string;
  statement: string;
  problemType: PracticeProblemType;
  available: boolean;
  allowedLanguages: PracticeLanguage[];
  starterCode: Record<PracticeLanguage, string>;
  timeLimitMs: number;
  memoryLimitMb: number | null;
  testcaseVersion: number | null;
  completionStatus: 'COMPLETED' | 'NOT_COMPLETED';
  samples: ConsoleSampleCase[];
}

class PublicEvaluationNoticeError extends Error {
  readonly classified: ClassifiedPracticeError;

  constructor(classified: ClassifiedPracticeError) {
    super(classified.message);
    this.classified = classified;
  }
}

async function pollPublicEvaluation(
  problemId: string,
  jobId: string,
  isCancelled: () => boolean,
): Promise<PublicEvaluationResult> {
  for (let attempt = 0; attempt < 63; attempt++) {
    const result = await publicPracticeApi.jobResult(problemId, jobId);
    if (isCancelled()) return result;
    if (result.verdict === 'INFRASTRUCTURE_ERROR') {
      throw new PublicEvaluationNoticeError({
        kind: 'infrastructure',
        heading: 'Code Judge temporarily unavailable',
        message:
          'The isolated execution worker could not complete this request. Your code was not marked incorrect and no failed verdict was recorded.',
      });
    }
    if (result.verdict !== 'QUEUED' && result.verdict !== 'RUNNING') {
      return result;
    }
    const delayMs = attempt < 30 ? 400 : 1000;
    await new Promise(resolve => window.setTimeout(resolve, delayMs));
  }
  const latest = await publicPracticeApi.jobResult(problemId, jobId);
  if (latest.verdict === 'QUEUED' || latest.verdict === 'RUNNING') {
    throw new PublicEvaluationNoticeError({
      kind: 'infrastructure',
      heading: 'Evaluation still in progress',
      message:
        'The Code Judge is still processing this request. Check Submission history shortly for the final verdict.',
    });
  }
  if (latest.verdict === 'INFRASTRUCTURE_ERROR') {
    throw new PublicEvaluationNoticeError({
      kind: 'infrastructure',
      heading: 'Code Judge temporarily unavailable',
      message:
        'The isolated execution worker could not complete this request. Your code was not marked incorrect and no failed verdict was recorded.',
    });
  }
  return latest;
}

export const PracticeWorkspacePage: React.FC = () => {
  const { id: routeClassroomId = '', practiceId = '' } = useParams<{
    id?: string;
    practiceId: string;
  }>();
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const { user } = useAuth();
  const isDemoMode = useIsDemoMode();
  const queryClient = useQueryClient();

  const scope: PracticeScope = routeClassroomId ? 'classroom' : 'public';
  const userId = user?.id || '';
  const hasWorkspaceAccess =
    scope === 'public'
      ? user?.role === 'STUDENT' || user?.role === 'TEACHER'
      : user?.role === 'STUDENT';

  const rawFrom = searchParams.get('from');
  const fallbackBackHref = scope === 'classroom'
    ? `/student/classrooms/${encodeURIComponent(routeClassroomId)}/practice`
    : '/practice?scope=public';
  const safeBackHref = rawFrom && (
    scope === 'classroom'
      ? rawFrom.split('?')[0] === fallbackBackHref
      : rawFrom.split('?')[0] === '/practice' && new URLSearchParams(rawFrom.split('?')[1] || '').get('scope') === 'public'
  ) ? rawFrom : fallbackBackHref;
  const backLabel = scope === 'classroom' ? 'Class problem library' : 'Public catalog';

  const [language, setLanguage] = useState<PracticeLanguage>('JAVA');
  const [code, setCode] = useState('');
  const [customInput, setCustomInput] = useState('');
  const [splitLayout, setSplitLayout] = useState<WorkspaceSplitLayout>(() => readWorkspaceLayout());
  const [leftTab, setLeftTab] = useState<ReadingTab>('description');
  const [consoleTab, setConsoleTab] = useState<ConsoleTab>('testcases');
  const [mobileTab, setMobileTab] = useState<MobileTab>('code');
  const [selectedSubmissionId, setSelectedSubmissionId] = useState<string | null>(null);
  const [confirmingReset, setConfirmingReset] = useState(false);
  const [quickLookOpen, setQuickLookOpen] = useState(false);
  const [folioOpen, setFolioOpen] = useState(false);
  const [isCompactViewport, setIsCompactViewport] = useState<boolean>(() =>
    typeof window !== 'undefined' ? window.matchMedia('(max-width: 959px)').matches : false,
  );

  const [runResult, setRunResult] = useState<RunResult | null>(null);
  const [submissionOutcome, setSubmissionOutcome] = useState<ConsoleSubmissionOutcome | null>(null);
  const [executionError, setExecutionError] = useState<ClassifiedPracticeError | null>(null);
  const [lastExecutedSnapshot, setLastExecutedSnapshot] = useState<string | null>(null);
  const [lastRunInput, setLastRunInput] = useState<string | null>(null);
  const [lastAction, setLastAction] = useState<'run' | 'submit' | null>(null);
  const [cursorPos, setCursorPos] = useState<{ line: number; column: number }>({
    line: 1,
    column: 1,
  });

  const mainSplitRef = useRef<HTMLDivElement | null>(null);
  const rightSplitRef = useRef<HTMLDivElement | null>(null);
  const editorInstanceRef = useRef<EditorialCodeEditor | null>(null);
  const folioTriggerRef = useRef<HTMLButtonElement | null>(null);
  const quickLookTriggerRef = useRef<HTMLButtonElement | null>(null);
  const requestVersionRef = useRef(0);
  const inFlightRef = useRef(false);
  const loadedDraftIdentityRef = useRef('');

  useEffect(() => {
    preloadPracticeWorkspace();
  }, []);

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 959px)');
    const sync = () => setIsCompactViewport(mq.matches);
    sync();
    mq.addEventListener('change', sync);
    return () => mq.removeEventListener('change', sync);
  }, []);

  const updateLayout = useCallback(
    (updater: (prev: WorkspaceSplitLayout) => WorkspaceSplitLayout) => {
      setSplitLayout(prev => {
        const next = updater(prev);
        writeWorkspaceLayout(next);
        return next;
      });
    },
    [],
  );

  const effectiveLayoutMode: WorkspaceMode = isCompactViewport ? 'split' : splitLayout.mode;
  const effectiveConsoleState: ConsolePanelState = isCompactViewport
    ? 'expanded'
    : effectiveLayoutMode === 'focus'
      ? 'collapsed'
      : splitLayout.consoleState;

  const handleLayoutModeChange = useCallback(
    (nextMode: WorkspaceMode) => {
      if (nextMode !== 'focus') {
        setQuickLookOpen(false);
      }
      updateLayout(prev => ({
        ...prev,
        mode: nextMode,
        consoleState: nextMode === 'console' ? 'expanded' : prev.consoleState,
      }));
    },
    [updateLayout],
  );

  const handleConsoleStateChange = useCallback(
    (nextConsoleState: ConsolePanelState) => {
      updateLayout(prev => ({
        ...prev,
        consoleState: nextConsoleState,
      }));
    },
    [updateLayout],
  );

  const classroomDetailQuery = useQuery({
    queryKey: [...classroomKeys.detail(routeClassroomId), isDemoMode],
    queryFn: async () => {
      if (isDemoMode) {
        const stored = readStoredClassrooms().find(c => c.id === routeClassroomId);
        if (stored) {
          return {
            id: stored.id,
            name: stored.name,
            description: stored.description,
            status: stored.status,
            classCode: stored.classCode,
            codeExpiresAt: stored.codeExpiresAt,
            codeLocked: stored.codeLocked,
            joiningOpen: stored.joiningOpen,
            joinMode: stored.joinMode,
            ownerId: stored.ownerId,
            createdAt: stored.createdAt,
            updatedAt: stored.updatedAt,
          };
        }
      }
      return classroomApi.get(routeClassroomId);
    },
    enabled: hasWorkspaceAccess && scope === 'classroom' && Boolean(routeClassroomId),
    staleTime: 60_000,
    retry: false,
  });

  const classPracticeQuery = useQuery({
    queryKey: practiceKeys.classDetail(routeClassroomId, practiceId),
    queryFn: () => practiceApi.detail(routeClassroomId, practiceId),
    enabled: hasWorkspaceAccess && scope === 'classroom' && Boolean(routeClassroomId && practiceId),
    staleTime: 60_000,
    retry: false,
  });

  const publicProblemQuery = useQuery({
    queryKey: practiceKeys.publicDetail(practiceId),
    queryFn: () => publicPracticeApi.detail(practiceId),
    enabled: hasWorkspaceAccess && scope === 'public' && Boolean(practiceId),
    staleTime: 60_000,
    retry: false,
  });

  // Read cached summary title so first frame shows exact problem title before detail roundtrip completes
  const cachedSummaryTitle = useMemo(() => {
    if (scope === 'classroom') {
      const lists = queryClient.getQueriesData<PaginatedData<ClassPracticeSummary>>({
        queryKey: [...practiceKeys.all, 'class-list', routeClassroomId],
      });
      for (const [, data] of lists) {
        const hit = data?.items?.find(item => item.id === practiceId);
        if (hit) return hit.title;
      }
      return null;
    }
    const pubLists = queryClient.getQueriesData<PaginatedData<PublicProblemSummary>>({
      queryKey: [...practiceKeys.all, 'public-list'],
    });
    for (const [, data] of pubLists) {
      const hit = data?.items?.find(item => item.id === practiceId);
      if (hit) return hit.title;
    }
    return null;
  }, [queryClient, scope, routeClassroomId, practiceId]);

  const problem: NormalizedWorkspaceProblem | null = useMemo(() => {
    if (scope === 'classroom') {
      const data = classPracticeQuery.data;
      if (!data) return null;
      return {
        id: data.id,
        scope: 'classroom',
        classroomId: data.classroomId,
        title: data.title,
        statement: data.description,
        problemType: data.problemType,
        available: data.available,
        allowedLanguages: data.allowedLanguages.length ? data.allowedLanguages : ['JAVA', 'PYTHON'],
        starterCode: {
          JAVA: data.starterCode?.JAVA ?? DEFAULT_STARTER_CODE.JAVA,
          PYTHON: data.starterCode?.PYTHON ?? DEFAULT_STARTER_CODE.PYTHON,
        },
        timeLimitMs: data.timeLimitMs,
        memoryLimitMb: data.memoryLimitMb,
        testcaseVersion: data.testcaseVersion,
        completionStatus: data.completionStatus === 'COMPLETED' ? 'COMPLETED' : 'NOT_COMPLETED',
        samples: data.sampleTestcases.map(tc => ({
          id: tc.id,
          input: tc.input,
          expectedOutput: tc.expectedOutput,
        })),
      };
    }

    const pub = publicProblemQuery.data;
    if (!pub) return null;
    return {
      id: pub.id,
      scope: 'public',
      classroomId: null,
      title: pub.title,
      statement: pub.statement,
      problemType: 'STANDARD_IO',
      available: pub.status === 'PUBLISHED',
      allowedLanguages: pub.languages.length ? pub.languages : ['JAVA', 'PYTHON'],
      starterCode: DEFAULT_STARTER_CODE,
      timeLimitMs: pub.limits.runtimeMillis,
      memoryLimitMb: null,
      testcaseVersion: null,
      completionStatus: 'NOT_COMPLETED',
      samples: pub.samples
        .filter(s => s.sample)
        .map((s, idx) => ({
          id: `pub-sample-${idx}`,
          input: s.input,
          expectedOutput: s.expectedOutput,
        })),
    };
  }, [scope, classPracticeQuery.data, publicProblemQuery.data]);

  // Ensure selected language is supported by the problem
  useEffect(() => {
    if (!problem) return;
    if (!problem.allowedLanguages.includes(language)) {
      setLanguage(problem.allowedLanguages[0] ?? 'JAVA');
    }
  }, [problem, language]);

  const draftIdentity = `${userId}:${scope}:${routeClassroomId || 'public'}:${practiceId}:${language}`;

  // Hydrate per-language saved draft or starter template
  useEffect(() => {
    if (!problem || !problem.allowedLanguages.includes(language)) return;
    if (loadedDraftIdentityRef.current === draftIdentity) return;

    loadedDraftIdentityRef.current = draftIdentity;
    requestVersionRef.current += 1;

    const saved = readPracticeDraft(
      userId,
      scope,
      routeClassroomId || null,
      practiceId,
      language,
    );
    const starter = problem.starterCode[language] || '';
    setCode(saved !== null ? saved : starter);
    setConfirmingReset(false);
    setExecutionError(null);
  }, [problem, language, draftIdentity, userId, scope, routeClassroomId, practiceId]);

  const starterForActiveLang = problem?.starterCode[language] ?? '';
  const isDraftModified = Boolean(problem && code !== starterForActiveLang && code.trim().length > 0);

  // Persist local draft safely
  useEffect(() => {
    if (!problem || loadedDraftIdentityRef.current !== draftIdentity) return;
    const saveNow = () => {
      writePracticeDraft({
        userId,
        scope,
        classroomId: routeClassroomId || null,
        classroomName: classroomDetailQuery.data?.name ?? null,
        problemId: practiceId,
        title: problem.title,
        problemType: problem.problemType,
        language,
        code,
        starterCode: problem.starterCode[language],
      });
    };
    const timer = window.setTimeout(saveNow, 350);
    return () => {
      window.clearTimeout(timer);
      saveNow();
    };
  }, [
    code,
    problem,
    draftIdentity,
    userId,
    scope,
    routeClassroomId,
    classroomDetailQuery.data?.name,
    practiceId,
    language,
  ]);

  const runMutation = useMutation({
    mutationFn: async (input: {
      language: PracticeLanguage;
      sourceCode: string;
      customInput?: string;
      version: number;
    }): Promise<RunResult> => {
      if (scope === 'classroom') {
        return practiceApi.run(routeClassroomId, practiceId, {
          language: input.language,
          sourceCode: input.sourceCode,
          ...(input.customInput ? { customInput: input.customInput } : {}),
        });
      }
      const jobId = await publicPracticeApi.run(practiceId, {
        language: input.language,
        source: input.sourceCode,
        requestKey: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      });
      const evalRes = await pollPublicEvaluation(
        practiceId,
        jobId,
        () => input.version !== requestVersionRef.current,
      );
      return {
        verdict: evalRes.verdict,
        cases: evalRes.cases.map(c => ({
          index: c.index,
          input: c.input ?? '',
          expectedOutput: c.expectedOutput ?? '',
          verdict: c.verdict,
          stdout: c.actualOutput ?? '',
          stderr: '',
          runtimeMs: null,
        })),
      };
    },
  });

  const submitMutation = useMutation({
    mutationFn: async (input: {
      language: PracticeLanguage;
      sourceCode: string;
      version: number;
    }): Promise<ConsoleSubmissionOutcome> => {
      if (scope === 'classroom') {
        const sub = await practiceApi.submit(routeClassroomId, practiceId, {
          language: input.language,
          sourceCode: input.sourceCode,
        });
        return {
          submissionId: sub.id,
          verdict: sub.verdict,
          language: sub.language,
          testsPassed: sub.testsPassed,
          totalTests: sub.totalTests,
          runtimeMs: sub.runtimeMs,
          memoryKb: sub.memoryKb,
          testcaseVersion: sub.testcaseVersion,
          submittedAt: sub.submittedAt,
        };
      }
      const jobId = await publicPracticeApi.submit(practiceId, {
        language: input.language,
        source: input.sourceCode,
        requestKey: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      });
      const evalRes = await pollPublicEvaluation(
        practiceId,
        jobId,
        () => input.version !== requestVersionRef.current,
      );
      return {
        submissionId: null,
        verdict: evalRes.verdict,
        language: input.language,
        testsPassed: evalRes.passed,
        totalTests: evalRes.total,
        runtimeMs: null,
        memoryKb: null,
        testcaseVersion: evalRes.generation,
        submittedAt: new Date().toISOString(),
      };
    },
  });

  const isBusy = runMutation.isPending || submitMutation.isPending;
  const canExecute = Boolean(
    problem?.available &&
      problem.allowedLanguages.includes(language) &&
      code.trim().length > 0 &&
      !isBusy,
  );
  const canRun = canExecute && Boolean(
    problem && (problem.samples.length > 0 || (scope === 'classroom' && customInput.length > 0)),
  );

  const execute = useCallback(
    async (action: 'run' | 'submit') => {
      if (!canExecute || (action === 'run' && !canRun) || !problem || inFlightRef.current) return;

      if (action === 'run' && scope === 'classroom' && customInput) {
        const byteErr = validateCustomInputBytes(customInput);
        if (byteErr) {
          setConsoleTab('result');
          setMobileTab('tests');
          setQuickLookOpen(false);
          updateLayout(prev => ({
            ...prev,
            mode: prev.mode === 'focus' ? 'split' : prev.mode,
            consoleState: 'expanded',
          }));
          setExecutionError({
            kind: 'validation',
            heading: 'Custom input exceeds limit',
            message: byteErr,
          });
          return;
        }
      }

      const version = ++requestVersionRef.current;
      const snapshot = code;
      const inputSnapshot = customInput;
      inFlightRef.current = true;
      setLastAction(action);
      setExecutionError(null);
      setConsoleTab('result');
      setMobileTab('tests');
      setQuickLookOpen(false);
      updateLayout(prev => ({
        ...prev,
        mode: prev.mode === 'focus' ? 'split' : prev.mode,
        consoleState: 'expanded',
      }));

      if (action === 'run') {
        setSubmissionOutcome(null);
      } else {
        setRunResult(null);
      }

      try {
        if (action === 'run') {
          const res = await runMutation.mutateAsync({
            language,
            sourceCode: snapshot,
            ...(customInput ? { customInput } : {}),
            version,
          });
          if (version === requestVersionRef.current) {
            setRunResult(res);
            setLastExecutedSnapshot(snapshot);
            setLastRunInput(inputSnapshot);
          }
        } else {
          const outcome = await submitMutation.mutateAsync({
            language,
            sourceCode: snapshot,
            version,
          });
          if (version === requestVersionRef.current) {
            setSubmissionOutcome(outcome);
            setLastExecutedSnapshot(snapshot);
          }
          if (scope === 'classroom') {
            void queryClient.invalidateQueries({
              queryKey: practiceKeys.classHistory(routeClassroomId, practiceId, 0),
            });
            void queryClient.invalidateQueries({
              queryKey: practiceKeys.classDetail(routeClassroomId, practiceId),
            });
          } else {
            void queryClient.invalidateQueries({
              queryKey: practiceKeys.publicHistory(practiceId, 0),
            });
          }
          void queryClient.invalidateQueries({ queryKey: practiceKeys.all });
        }
      } catch (err) {
        if (scope === 'public') {
          void queryClient.invalidateQueries({
            queryKey: practiceKeys.publicHistory(practiceId, 0),
          });
        }
        if (version === requestVersionRef.current) {
          setExecutionError(
            err instanceof PublicEvaluationNoticeError
              ? err.classified
              : classifyPracticeError(err),
          );
        }
      } finally {
        inFlightRef.current = false;
      }
    },
    [
      canExecute,
      canRun,
      problem,
      customInput,
      code,
      runMutation,
      language,
      submitMutation,
      scope,
      queryClient,
      routeClassroomId,
      practiceId,
      updateLayout,
    ],
  );

  const executeRef = useRef(execute);
  executeRef.current = execute;

  // Global Ctrl/Cmd + Enter shortcut for Run Code
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
        event.preventDefault();
        void executeRef.current('run');
      }
    };
    window.addEventListener('keydown', onKeyDown, { capture: true });
    return () => window.removeEventListener('keydown', onKeyDown, { capture: true });
  }, []);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => editorInstanceRef.current?.layout());
    return () => window.cancelAnimationFrame(frame);
  }, [effectiveLayoutMode, effectiveConsoleState, mobileTab, splitLayout]);

  const focusEditorSurface = useCallback(() => {
    setMobileTab('code');
    window.requestAnimationFrame(() => {
      editorInstanceRef.current?.focus();
    });
  }, []);

  const handleJumpToLine = useCallback((line: number) => {
    setMobileTab('code');
    window.requestAnimationFrame(() => {
      const editor = editorInstanceRef.current;
      if (!editor) return;
      editor.revealLineInCenter(line);
      editor.setPosition({ lineNumber: line, column: 1 });
      editor.focus();
    });
  }, []);

  // Horizontal drag resize (Left problem panel vs Right editor/console)
  const startHorizontalResize = (event: React.PointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    const container = mainSplitRef.current;
    if (!container) return;
    const rect = container.getBoundingClientRect();

    const onMove = (moveEvent: PointerEvent) => {
      const pct = ((moveEvent.clientX - rect.left) / rect.width) * 100;
      const clamped = Math.min(58, Math.max(26, pct));
      updateLayout(prev => ({ ...prev, leftWidthPercent: clamped }));
    };

    const onUp = () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  };

  // Vertical drag resize (Editor height vs Console height)
  const startVerticalResize = (event: React.PointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    const container = rightSplitRef.current;
    if (!container) return;
    const rect = container.getBoundingClientRect();

    const onMove = (moveEvent: PointerEvent) => {
      const pct = ((moveEvent.clientY - rect.top) / rect.height) * 100;
      const clamped = Math.min(76, Math.max(30, pct));
      updateLayout(prev => ({
        ...prev,
        mode: prev.mode === 'console' ? 'split' : prev.mode,
        consoleState: 'expanded',
        editorHeightPercent: clamped,
      }));
    };

    const onUp = () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  };

  const handleResetConfirm = () => {
    if (!problem) return;
    const starter = problem.starterCode[language] || '';
    clearPracticeDraft(userId, scope, routeClassroomId || null, practiceId, language);
    setCode(starter);
    setConfirmingReset(false);
  };

  const handleRestoreSubmissionCode = (restoredLang: PracticeLanguage, restoredCode: string) => {
    loadedDraftIdentityRef.current = `${userId}:${scope}:${routeClassroomId || 'public'}:${practiceId}:${restoredLang}`;
    setLanguage(restoredLang);
    setCode(restoredCode);
    focusEditorSurface();
  };

  const handleUseCustomInput = useCallback(
    (input: string) => {
      setCustomInput(input);
      setConsoleTab('testcases');
      setMobileTab('tests');
      updateLayout(prev => ({
        ...prev,
        mode: prev.mode === 'focus' ? 'split' : prev.mode,
        consoleState: 'expanded',
      }));
    },
    [updateLayout],
  );

  const detailPending =
    scope === 'classroom' ? classPracticeQuery.isPending : publicProblemQuery.isPending;
  const detailError =
    scope === 'classroom' ? classPracticeQuery.error : publicProblemQuery.error;

  const isCompleted =
    problem?.completionStatus === 'COMPLETED' || submissionOutcome?.verdict === 'ACCEPTED';
  const isResultStale =
    lastExecutedSnapshot !== null &&
    (code !== lastExecutedSnapshot || (lastAction === 'run' && lastRunInput !== customInput));

  const effectiveEditorHeight =
    effectiveLayoutMode === 'console' ? 42 : splitLayout.editorHeightPercent;

  const contextLabel =
    scope === 'classroom' ? classroomDetailQuery.data?.name || 'Class practice' : 'Public practice';
  const displayTitle = problem?.title ?? cachedSummaryTitle ?? 'Coding workspace';

  const folioSelection: PublicProblemSummary | null = problem
    ? {
        id: problem.id,
        title: problem.title,
        status: problem.available ? 'PUBLISHED' : 'ARCHIVED',
        createdAt: '',
      }
    : null;

  if (!hasWorkspaceAccess) {
    return (
      <div className="practice-page min-h-screen flex items-center justify-center p-6">
        <div role="alert" className="max-w-md space-y-3">
          <span className="practice-kicker">ACCESS RESTRICTED</span>
          <h1 className="text-xl font-bold text-slate-900 m-0">
            Student Practice Workspace
          </h1>
          <p className="text-sm text-slate-600">
            This coding workspace is reserved for student accounts.
          </p>
          <Link to="/dashboard" className="practice-btn-primary">
            Return to dashboard
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="practice-focus-shell">
      <WorkspaceTopbar
        backHref={safeBackHref}
        backLabel={backLabel}
        contextLabel={contextLabel}
        problemTitle={displayTitle}
        layoutMode={effectiveLayoutMode}
        onLayoutModeChange={handleLayoutModeChange}
        isCompleted={isCompleted}
        isDraftModified={isDraftModified}
        hasProblem={Boolean(problem)}
        canRun={canRun}
        canSubmit={canExecute}
        isRunning={runMutation.isPending}
        isSubmitting={submitMutation.isPending}
        onRun={() => void execute('run')}
        onSubmit={() => void execute('submit')}
      />

      {detailError && !detailPending ? (
        <div className="flex-1 p-8 flex items-start justify-center">
          <div role="alert" className="practice-alert-box max-w-lg w-full space-y-3">
            <span className="practice-kicker" style={{ marginBottom: 0 }}>
              WORKSPACE ERROR
            </span>
            <h1 className="text-base font-bold text-slate-900 m-0">
              {classifyPracticeError(detailError).heading}
            </h1>
            <p className="text-xs text-slate-600 m-0">
              {classifyPracticeError(detailError).message}
            </p>
            <div className="flex items-center gap-2 pt-1">
              <button
                type="button"
                className="practice-btn-primary"
                onClick={() =>
                  void (scope === 'classroom'
                    ? classPracticeQuery.refetch()
                    : publicProblemQuery.refetch())
                }
              >
                Try again
              </button>
              <Link to={safeBackHref} className="practice-btn-secondary">
                Back to problems
              </Link>
            </div>
          </div>
        </div>
      ) : (
        <>
          {problem && !problem.available && (
            <div
              role="status"
              className="px-4 py-2 bg-amber-100 border-b border-amber-300 text-amber-950 text-xs font-medium"
            >
              This practice problem is currently closed for execution. You can still read the statement and review past submissions.
            </div>
          )}

          {/* Mobile Single-Column Task Switcher (<960px) */}
          <nav className="practice-mobile-bar" role="tablist" aria-label="Workspace sections">
            {(
              [
                ['description', 'Description'],
                ['code', 'Code'],
                ['tests', 'Tests'],
                ['history', 'History'],
              ] as const
            ).map(([tabKey, label]) => (
              <button
                key={tabKey}
                type="button"
                role="tab"
                aria-selected={mobileTab === tabKey}
                onClick={() => {
                  setMobileTab(tabKey);
                  if (tabKey === 'description') setLeftTab('description');
                  if (tabKey === 'history') setLeftTab('history');
                }}
              >
                {label}
              </button>
            ))}
          </nav>

          {/* Main Editorial IDE Split Studio */}
          <div
            className="practice-ws-main"
            ref={mainSplitRef}
            data-mode={effectiveLayoutMode}
          >
            {/* Left Reading Pane (keeps scroll/tab state alive via smooth collapse in Focus mode) */}
            <section
              className={`practice-ws-left ${
                mobileTab !== 'description' && mobileTab !== 'history'
                  ? 'practice-mobile-hidden'
                  : ''
              }`}
              style={{ width: `${splitLayout.leftWidthPercent}%` }}
              aria-label="Problem and submissions"
              aria-hidden={effectiveLayoutMode === 'focus'}
            >
              {problem ? (
                <ProblemReadingPane
                  scope={scope}
                  classroomId={routeClassroomId}
                  problemId={practiceId}
                  title={problem.title}
                  titleHeadingLevel={effectiveLayoutMode === 'focus' ? 2 : 1}
                  problemType={problem.problemType}
                  timeLimitMs={problem.timeLimitMs}
                  memoryLimitMb={problem.memoryLimitMb}
                  allowedLanguages={problem.allowedLanguages}
                  statement={problem.statement}
                  samples={problem.samples}
                  isCompleted={isCompleted}
                  isDraftModified={isDraftModified}
                  activeTab={leftTab}
                  onTabChange={setLeftTab}
                  selectedSubmissionId={selectedSubmissionId}
                  onSelectSubmission={setSelectedSubmissionId}
                  onRestoreCode={handleRestoreSubmissionCode}
                  onUseCustomInput={handleUseCustomInput}
                  onOpenFolio={() => setFolioOpen(true)}
                  folioTriggerRef={folioTriggerRef}
                />
              ) : (
                <div role="status" className="practice-statement-scroll light-scroll space-y-4">
                  <div className="practice-editorial-header">
                    <span className="practice-editorial-status" data-state="unsolved">
                      Loading workspace…
                    </span>
                    <h1 className="practice-problem-title">{displayTitle}</h1>
                  </div>
                  <div className="h-4 w-full bg-slate-200 animate-pulse rounded" />
                  <div className="h-4 w-5/6 bg-slate-200 animate-pulse rounded" />
                  <div className="h-24 w-full bg-slate-200/80 animate-pulse rounded" />
                </div>
              )}
            </section>

            <div
              role="separator"
              aria-orientation="vertical"
              aria-label="Resize problem and code panels"
              aria-valuemin={26}
              aria-valuemax={58}
              aria-valuenow={Math.round(splitLayout.leftWidthPercent)}
              aria-hidden={effectiveLayoutMode === 'focus'}
              tabIndex={effectiveLayoutMode === 'focus' ? -1 : 0}
              className="practice-splitter-h"
              onPointerDown={startHorizontalResize}
              onKeyDown={event => {
                if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
                  event.preventDefault();
                  const delta = event.key === 'ArrowLeft' ? -2 : 2;
                  updateLayout(prev => ({
                    ...prev,
                    leftWidthPercent: Math.min(58, Math.max(26, prev.leftWidthPercent + delta)),
                  }));
                }
              }}
            />

            {/* Right Column: Persistent Charcoal Code Editor + Docked Light Execution Console */}
            <div
              ref={rightSplitRef}
              className={`practice-ws-right ${
                mobileTab !== 'code' && mobileTab !== 'tests' ? 'practice-mobile-hidden' : ''
              }`}
            >
              <section
                className={`practice-editor-region ${
                  mobileTab !== 'code' ? 'practice-mobile-hidden' : ''
                }`}
                style={
                  !isCompactViewport &&
                  effectiveLayoutMode !== 'focus' &&
                  effectiveConsoleState === 'expanded'
                    ? { height: `${effectiveEditorHeight}%`, flex: 'none' }
                    : undefined
                }
                aria-label="Code editor"
              >
                <div className="practice-editor-toolbar">
                  <div className="practice-editor-file">
                    <FileCode2 size={14} aria-hidden="true" />
                    <span>{getSourceFilename(language)}</span>
                    <span className="practice-editor-draft-state">
                      {isDraftModified ? 'Modified draft' : 'Starter template'}
                    </span>
                  </div>

                  <div className="practice-editor-controls">
                    <FilterSelect
                      tone="dark"
                      label="Programming language"
                      value={language}
                      options={(problem?.allowedLanguages ?? ['JAVA', 'PYTHON']).map(lang => ({
                        value: lang,
                        label: formatLanguageLabel(lang),
                      }))}
                      onValueChange={val => setLanguage(val as PracticeLanguage)}
                    />

                    {confirmingReset ? (
                      <div className="practice-inline-confirm" role="group" aria-label="Confirm reset">
                        <span>Reset to starter?</span>
                        <button
                          type="button"
                          className="practice-inline-confirm-yes"
                          onClick={handleResetConfirm}
                        >
                          Confirm
                        </button>
                        <button
                          type="button"
                          className="practice-inline-confirm-no"
                          onClick={() => setConfirmingReset(false)}
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        disabled={!problem?.available || !isDraftModified}
                        onClick={() => setConfirmingReset(true)}
                        className="practice-editor-reset"
                      >
                        <RotateCcw size={12} aria-hidden="true" />
                        <span>Reset</span>
                      </button>
                    )}
                  </div>
                </div>

                <div className="practice-editor-stage">
                  <div className="practice-editor-canvas">
                    <Suspense
                      fallback={
                        <CodeEditorFallback
                          value={problem ? code : DEFAULT_STARTER_CODE[language]}
                        />
                      }
                    >
                      <LazyCodeEditorSurface
                        language={language}
                        value={problem ? code : DEFAULT_STARTER_CODE[language]}
                        readOnly={!problem?.available}
                        onChange={setCode}
                        onCursorChange={setCursorPos}
                        onRunShortcut={() => void executeRef.current('run')}
                        onEditorReady={editor => {
                          editorInstanceRef.current = editor;
                        }}
                      />
                    </Suspense>
                  </div>

                  {problem && effectiveLayoutMode === 'focus' && !quickLookOpen && (
                    <button
                      ref={quickLookTriggerRef}
                      type="button"
                      className="practice-quicklook-trigger"
                      onClick={() => setQuickLookOpen(true)}
                    >
                      Open problem reference
                    </button>
                  )}

                  {problem && (
                    <FloatingProblemReference
                      open={effectiveLayoutMode === 'focus' && quickLookOpen}
                      title={problem.title}
                      problemType={problem.problemType}
                      timeLimitMs={problem.timeLimitMs}
                      memoryLimitMb={problem.memoryLimitMb}
                      statement={problem.statement}
                      samples={problem.samples}
                      onClose={() => {
                        setQuickLookOpen(false);
                        window.requestAnimationFrame(() => quickLookTriggerRef.current?.focus());
                      }}
                      onDockSplit={() => handleLayoutModeChange('split')}
                      onOpenFolio={() => setFolioOpen(true)}
                    />
                  )}
                </div>
              </section>

              {effectiveLayoutMode !== 'focus' && effectiveConsoleState === 'expanded' && (
                <div
                  role="separator"
                  aria-orientation="horizontal"
                  aria-label="Resize code editor and execution console"
                  aria-valuemin={30}
                  aria-valuemax={76}
                  aria-valuenow={Math.round(effectiveEditorHeight)}
                  tabIndex={0}
                  className="practice-splitter-v"
                  onPointerDown={startVerticalResize}
                  onKeyDown={event => {
                    if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
                      event.preventDefault();
                      const delta = event.key === 'ArrowUp' ? -3 : 3;
                      updateLayout(prev => ({
                        ...prev,
                        mode: prev.mode === 'console' ? 'split' : prev.mode,
                        consoleState: 'expanded',
                        editorHeightPercent: Math.min(76, Math.max(30, prev.editorHeightPercent + delta)),
                      }));
                    }
                  }}
                />
              )}

              {/* Bottom: Adaptive Docked Execution Console */}
              <div
                className={`practice-console-slot ${
                  mobileTab !== 'tests' ? 'practice-mobile-hidden' : ''
                }`}
              >
                <ExecutionConsole
                  activeTab={consoleTab}
                  onTabChange={setConsoleTab}
                  consoleState={effectiveConsoleState}
                  onConsoleStateChange={handleConsoleStateChange}
                  language={language}
                  onJumpToLine={handleJumpToLine}
                  samples={problem?.samples ?? []}
                  supportsCustomInput={scope === 'classroom'}
                  customInput={scope === 'classroom' ? customInput : ''}
                  onCustomInputChange={setCustomInput}
                  isRunning={runMutation.isPending}
                  isSubmitting={submitMutation.isPending}
                  runResult={runResult}
                  submissionOutcome={submissionOutcome}
                  executionError={executionError}
                  isResultStale={isResultStale}
                  canExecute={canRun}
                  backHref={safeBackHref}
                  onRun={() => void execute('run')}
                  onRetryLastAction={lastAction ? () => void execute(lastAction) : null}
                  onViewSubmission={subId => {
                    if (effectiveLayoutMode === 'focus') handleLayoutModeChange('split');
                    setQuickLookOpen(false);
                    setSelectedSubmissionId(subId);
                    setLeftTab('history');
                    setMobileTab('history');
                  }}
                  onFocusEditor={focusEditorSurface}
                />
              </div>
            </div>
          </div>

          {/* Sticky Mobile Action Bar (<960px) */}
          <div className="practice-mobile-actions">
            <button
              type="button"
              className="practice-ws-run-btn justify-center"
              disabled={!canRun}
              onClick={() => void execute('run')}
            >
              <Play size={13} aria-hidden="true" />
              <span>{runMutation.isPending ? 'Running…' : 'Run code'}</span>
            </button>
            <button
              type="button"
              className="practice-ws-submit-btn justify-center"
              disabled={!canExecute}
              onClick={() => void execute('submit')}
            >
              <Send size={13} aria-hidden="true" />
              <span>{submitMutation.isPending ? 'Submitting…' : 'Submit solution'}</span>
            </button>
          </div>

          {/* Compact Editorial Status Footer */}
          <footer className="practice-ws-statusbar" data-tone={effectiveLayoutMode === 'focus' ? 'dark' : 'light'}>
            <div className="flex items-center gap-3 truncate">
              <span>{scope === 'classroom' ? 'Class practice' : 'Public practice'}</span>
              <span className="hidden sm:inline">{formatLanguageLabel(language)}</span>
            </div>
            <div className="flex items-center gap-3 shrink-0">
              <span className="hidden sm:inline">Spaces: 4</span>
              <span>
                Ln {cursorPos.line}, Col {cursorPos.column}
              </span>
            </div>
          </footer>
        </>
      )}

      {folioOpen && folioSelection && (
        <ProblemFolio
          selected={folioSelection}
          detail={scope === 'public' ? publicProblemQuery.data : undefined}
          classroomDetail={scope === 'classroom' ? classPracticeQuery.data : undefined}
          contextLabel={contextLabel}
          loading={detailPending}
          error={detailError ? classifyPracticeError(detailError).message : null}
          status={isCompleted ? 'Completed' : isDraftModified ? 'In progress' : 'Practice'}
          position={1}
          total={1}
          solveHref={`${location.pathname}${location.search}`}
          solveLabel="Return to editor"
          onSolve={() => {
            setFolioOpen(false);
            focusEditorSurface();
          }}
          onClose={() => {
            setFolioOpen(false);
            window.requestAnimationFrame(() => folioTriggerRef.current?.focus());
          }}
          onPrevious={() => {}}
          onNext={() => {}}
          onRetry={() =>
            void (scope === 'classroom'
              ? classPracticeQuery.refetch()
              : publicProblemQuery.refetch())
          }
        />
      )}
    </div>
  );
};
