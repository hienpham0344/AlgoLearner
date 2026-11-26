import type { QueryClient } from '@tanstack/react-query';
import { practiceApi, practiceKeys, publicPracticeApi } from '../api/practiceApi';
import type { PracticeScope } from '../types';

let workspaceChunkPromise: Promise<unknown> | null = null;
let editorChunkPromise: Promise<unknown> | null = null;

/** Preload the PracticeWorkspacePage route chunk and lazy Monaco editor chunk once. */
export function preloadPracticeWorkspace(): void {
  if (!workspaceChunkPromise) {
    workspaceChunkPromise = import('../pages/PracticeWorkspacePage').catch(() => {
      workspaceChunkPromise = null;
    });
  }
  if (!editorChunkPromise) {
    editorChunkPromise = import('../components/editor/CodeEditorSurface').catch(() => {
      editorChunkPromise = null;
    });
  }
}

/** Prefetch problem detail and page 0 submission history alongside route & Monaco chunks. */
export function prefetchProblemWorkspace(
  queryClient: QueryClient,
  scope: PracticeScope,
  classroomId: string | null,
  problemId: string,
): void {
  if (!problemId) return;
  preloadPracticeWorkspace();

  if (scope === 'classroom' && classroomId) {
    void queryClient.prefetchQuery({
      queryKey: practiceKeys.classDetail(classroomId, problemId),
      queryFn: () => practiceApi.detail(classroomId, problemId),
      staleTime: 60_000,
    });
    void queryClient.prefetchQuery({
      queryKey: practiceKeys.classHistory(classroomId, problemId, 0),
      queryFn: () => practiceApi.history(classroomId, problemId, 0),
      staleTime: 30_000,
    });
  } else if (scope === 'public') {
    void queryClient.prefetchQuery({
      queryKey: practiceKeys.publicDetail(problemId),
      queryFn: () => publicPracticeApi.detail(problemId),
      staleTime: 60_000,
    });
    void queryClient.prefetchQuery({
      queryKey: practiceKeys.publicHistory(problemId, 0),
      queryFn: () => publicPracticeApi.history(problemId, 0),
      staleTime: 30_000,
    });
  }
}

