import React, { useEffect, useMemo, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, ArrowRight, RotateCcw, Search } from 'lucide-react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { useIsDemoMode } from '@/components/BackendStatusBanner';
import { WorkspaceLayout } from '@/components/layout/WorkspaceLayout';
import { FilterSelect } from '@/components/ui/FilterSelect';
import { classroomApi, classroomKeys } from '@/features/classroom/api/classroomApi';
import { readStoredClassrooms, readStoredPersona } from '@/features/classroom/mock/useClassroomStore';
import {
  classifyPracticeError,
  practiceApi,
  practiceKeys,
  publicPracticeApi,
} from '../api/practiceApi';
import { OpenTextbookWorkbench } from '../components/classroom/OpenTextbookWorkbench';
import { PatternRenderer } from '../components/generative-patterns/PatternRenderer';
import { resolvePattern } from '../components/generative-patterns/patternCore';
import { ClassroomAtelier } from '../components/classroom/ClassroomAtelier';
import { PublicProblemGallery } from './PublicProblemGallery';
import type {
  PracticeLanguage,
  PracticeLibraryItem,
  PracticeProblemType,
} from '../types';
import {
  buildProblemWorkspacePath,
  formatLanguageLabel,
  formatVietnamDateTime,
} from '../utils/practiceFormatters';
import { hasAnySavedDraft, readRecentDrafts } from '../utils/practiceStorage';
import '../styles/practice.css';

type StatusFilter = 'all' | 'unsolved' | 'in_progress' | 'completed';
type FormatFilter = 'all' | PracticeProblemType;
type LanguageFilter = 'all' | PracticeLanguage;
type SortOrder = 'newest' | 'title_asc';

export const PracticeStudioPage: React.FC = () => {
  const { id: routeClassroomId } = useParams<{ id: string }>();
  const { user } = useAuth();
  const isDemoMode = useIsDemoMode();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();

  const scopeParam = searchParams.get('scope') === 'public' ? 'public' : 'classroom';
  const activeScope = routeClassroomId ? 'classroom' : scopeParam;

  const page = Math.max(0, Number(searchParams.get('page') || '0') || 0);
  const searchQuery = searchParams.get('q') || '';
  const statusFilter = (searchParams.get('status') as StatusFilter) || 'all';
  const formatFilter = (searchParams.get('format') as FormatFilter) || 'all';
  const langFilter = (searchParams.get('lang') as LanguageFilter) || 'all';
  const sortOrder = (searchParams.get('sort') as SortOrder) || 'newest';

  const selectedItemId = searchParams.get('selected');

  const updateParams = (updates: Record<string, string | null>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(updates).forEach(([key, value]) => {
      if (!value || value === 'all' || (key === 'page' && value === '0') || (key === 'sort' && value === 'newest')) {
        next.delete(key);
      } else {
        next.set(key, value);
      }
    });
    setSearchParams(next, { replace: true });
  };

  const isStudent = user?.role === 'STUDENT';
  const canBrowsePublic = user?.role === 'STUDENT' || user?.role === 'TEACHER';
  const canAccessStudio = activeScope === 'public' ? canBrowsePublic : isStudent;
  const userId = user?.id || '';

  // Enrolled classrooms (real API when online, synchronized prototype classrooms in offline demo)
  const classesQuery = useQuery({
    queryKey: [...classroomKeys.list(page), isDemoMode],
    queryFn: async () => {
      if (isDemoMode) {
        const persona = readStoredPersona('STUDENT');
        const enrolled = readStoredClassrooms().filter(
          cls =>
            cls.status === 'ACTIVE' &&
            cls.members.some(m => m.studentId === persona.id && m.status === 'ACTIVE'),
        );
        return {
          items: enrolled.slice(page * 20, (page + 1) * 20).map(c => ({
            id: c.id,
            name: c.name,
            description: c.description,
            status: c.status,
            classCode: c.classCode,
            codeExpiresAt: c.codeExpiresAt,
            codeLocked: c.codeLocked,
            joiningOpen: c.joiningOpen,
            joinMode: c.joinMode,
            ownerId: c.ownerId,
            createdAt: c.createdAt,
            updatedAt: c.updatedAt,
          })),
          pagination: {
            page,
            size: 20,
            totalElements: enrolled.length,
            totalPages: Math.ceil(enrolled.length / 20),
            isFirst: page === 0,
            isLast: page >= Math.ceil(enrolled.length / 20) - 1,
          },
        };
      }
      return classroomApi.list(page);
    },
    enabled: isStudent,
    retry: false,
    staleTime: 30_000,
  });

  const enrolledClassrooms = classesQuery.data?.items ?? [];
  const selectedClassroomId = routeClassroomId || searchParams.get('classId') || '';

  const classroomDetailQuery = useQuery({
    queryKey: [...classroomKeys.detail(selectedClassroomId), isDemoMode],
    queryFn: async () => {
      if (isDemoMode) {
        const found = readStoredClassrooms().find(c => c.id === selectedClassroomId);
        if (found) {
          return {
            id: found.id,
            name: found.name,
            description: found.description,
            status: found.status,
            classCode: found.classCode,
            codeExpiresAt: found.codeExpiresAt,
            codeLocked: found.codeLocked,
            joiningOpen: found.joiningOpen,
            joinMode: found.joinMode,
            ownerId: found.ownerId,
            createdAt: found.createdAt,
            updatedAt: found.updatedAt,
          };
        }
      }
      return classroomApi.get(selectedClassroomId);
    },
    enabled: isStudent && Boolean(selectedClassroomId),
    retry: false,
  });

  const classPracticesQuery = useQuery({
    queryKey: practiceKeys.classList(selectedClassroomId, page),
    queryFn: () => practiceApi.list(selectedClassroomId, page),
    enabled: isStudent && activeScope === 'classroom' && Boolean(selectedClassroomId),
    retry: false,
  });

  const publicProblemsQuery = useQuery({
    queryKey: practiceKeys.publicList(page),
    queryFn: () => publicPracticeApi.list(page),
    enabled: canBrowsePublic && activeScope === 'public',
    retry: false,
  });

  const activeClassroomName =
    classroomDetailQuery.data?.name ||
    enrolledClassrooms.find(c => c.id === selectedClassroomId)?.name ||
    null;
  const classroomPattern = selectedClassroomId && activeClassroomName ? resolvePattern({
    id: selectedClassroomId,
    name: activeClassroomName,
    description: classroomDetailQuery.data?.description,
  }) : null;

  const normalizedItems: PracticeLibraryItem[] = useMemo(() => {
    if (activeScope === 'public') {
      return (publicProblemsQuery.data?.items ?? []).map(item => {
        const inProgress = hasAnySavedDraft(userId, 'public', null, item.id);
        return {
          id: item.id,
          scope: 'public',
          classroomId: null,
          classroomName: null,
          lessonId: null,
          title: item.title,
          problemType: 'STANDARD_IO',
          allowedLanguages: ['JAVA', 'PYTHON'],
          timeLimitMs: null,
          memoryLimitMb: null,
          available: item.status === 'PUBLISHED',
          completionStatus: inProgress ? 'IN_PROGRESS' : 'UNSOLVED',
          createdAt: item.createdAt,
          updatedAt: item.createdAt,
        };
      });
    }

    return (classPracticesQuery.data?.items ?? []).map(item => {
      const completed = item.completionStatus === 'COMPLETED';
      const inProgress =
        !completed && hasAnySavedDraft(userId, 'classroom', item.classroomId, item.id);
      return {
        id: item.id,
        scope: 'classroom',
        classroomId: item.classroomId,
        classroomName: activeClassroomName,
        lessonId: item.lessonId,
        title: item.title,
        problemType: item.problemType,
        allowedLanguages: item.allowedLanguages,
        timeLimitMs: item.timeLimitMs,
        memoryLimitMb: item.memoryLimitMb,
        available: item.available,
        completionStatus: completed ? 'COMPLETED' : inProgress ? 'IN_PROGRESS' : 'UNSOLVED',
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
      };
    });
  }, [
    activeScope,
    publicProblemsQuery.data,
    classPracticesQuery.data,
    userId,
    activeClassroomName,
  ]);

  const filteredItems = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const list = normalizedItems.filter(item => {
      if (q && !item.title.toLowerCase().includes(q)) return false;
      if (statusFilter === 'completed' && item.completionStatus !== 'COMPLETED') return false;
      if (statusFilter === 'in_progress' && item.completionStatus !== 'IN_PROGRESS') return false;
      if (statusFilter === 'unsolved' && item.completionStatus !== 'UNSOLVED') return false;
      if (activeScope === 'classroom' && formatFilter !== 'all' && item.problemType !== formatFilter) return false;
      if (langFilter !== 'all' && !item.allowedLanguages.includes(langFilter)) return false;
      return true;
    });

    if (sortOrder === 'title_asc') {
      return [...list].sort((a, b) => a.title.localeCompare(b.title));
    }
    return list;
  }, [normalizedItems, searchQuery, statusFilter, formatFilter, langFilter, sortOrder, activeScope]);
  const availableFormats = Array.from(new Set([
    ...normalizedItems.map(item => item.problemType).filter((value): value is PracticeProblemType => value !== null),
    ...(formatFilter !== 'all' ? [formatFilter] : []),
  ]));
  const availableLanguages = Array.from(new Set([
    ...normalizedItems.flatMap(item => item.allowedLanguages),
    ...(langFilter !== 'all' ? [langFilter] : []),
  ]));

  const selectedInspectorItem = filteredItems.find(item => item.id === selectedItemId) ?? filteredItems[0] ?? null;

  // Ctrl/Cmd + K focuses search input when outside an input
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        searchInputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  // Keep draft resume available without placing a large hero above the directory.
  const recentDraft = useMemo(() => readRecentDrafts(userId)[0] ?? null, [userId]);

  const returnParams = new URLSearchParams(searchParams);
  if (selectedInspectorItem) returnParams.set('selected', selectedInspectorItem.id);
  const returnContext = `${location.pathname}?${returnParams.toString()}`;
  const hasActiveFilters =
    Boolean(searchQuery.trim()) ||
    statusFilter !== 'all' ||
    (activeScope === 'classroom' && formatFilter !== 'all') ||
    langFilter !== 'all' ||
    sortOrder !== 'newest';

  const activeQuery =
    activeScope === 'public'
      ? publicProblemsQuery
      : selectedClassroomId
        ? classPracticesQuery
        : classesQuery;

  const pagination =
    activeScope === 'public'
      ? publicProblemsQuery.data?.pagination
      : selectedClassroomId
        ? classPracticesQuery.data?.pagination
        : classesQuery.data?.pagination;

  if (activeScope === 'public' && canBrowsePublic) {
    return <WorkspaceLayout role={user?.role === 'TEACHER' ? 'TEACHER' : 'STUDENT'} mainClassName="practice-page"><PublicProblemGallery userId={userId} /></WorkspaceLayout>;
  }

  if (activeScope === 'classroom' && !selectedClassroomId && isStudent) {
    return <WorkspaceLayout role="STUDENT" mainClassName="practice-page"><ClassroomAtelier
      classrooms={enrolledClassrooms}
      total={classesQuery.data?.pagination.totalElements ?? 0}
      page={page}
      totalPages={classesQuery.data?.pagination.totalPages ?? 0}
      pending={classesQuery.isPending}
      error={classesQuery.error}
      onRetry={() => void classesQuery.refetch()}
      onPageChange={next => updateParams({ page: String(next) })}
    /></WorkspaceLayout>;
  }


  return (
    <WorkspaceLayout role="STUDENT" mainClassName="practice-page practice-class-library-page">
      <div className="practice-studio">
        <header className="practice-header open-textbook-class-header">
          <div>
            {routeClassroomId && <Link to="/practice" className="open-textbook-parent"><ArrowLeft size={15} aria-hidden="true" /> All classrooms</Link>}
            <h1>{routeClassroomId ? activeClassroomName || 'Class practice' : activeScope === 'public' ? 'Public catalog' : 'Classroom practice'}</h1>
            <p>{routeClassroomId
              ? 'Practice at your own pace. No deadline, no grade, unlimited attempts.'
              : activeScope === 'public'
                ? 'Explore published coding problems available to every learner.'
                : 'Choose an enrolled classroom to open its coding problem library.'}</p>
          </div>
          {classroomPattern && <div className="open-textbook-header-pattern" data-tone={classroomPattern.surface}><PatternRenderer config={classroomPattern} /></div>}
        </header>

        {!canAccessStudio ? (
          <div role="alert" className="py-12 border-b border-slate-300 text-sm text-slate-700">
            Student Practice is restricted to active student accounts.
          </div>
        ) : (
          <>
            {!routeClassroomId && recentDraft?.scope === activeScope && (
              <div className="practice-resume-line">
                <span>RECENT DRAFT</span>
                <Link to={buildProblemWorkspacePath(recentDraft.scope, recentDraft.classroomId, recentDraft.problemId, returnContext)}>
                  Resume {recentDraft.title} <ArrowRight size={15} aria-hidden="true" />
                </Link>
                <time>{formatVietnamDateTime(recentDraft.updatedAt)}</time>
              </div>
            )}

            {/* D & E. Problem Library / Class Directory */}
            <section className="practice-library-section" aria-label={routeClassroomId ? 'Class problem library' : undefined} aria-labelledby={routeClassroomId ? undefined : 'practice-library-title'}>
              {!routeClassroomId && <div className="practice-library-header">
                <h2 id="practice-library-title">
                  {activeScope === 'public' ? 'Public problems' : selectedClassroomId ? 'Class problem library' : 'Your classrooms'}
                </h2>
              </div>}

              {!routeClassroomId && (
                <div className="practice-scope-tabs" role="tablist" aria-label="Practice source">
                  <button
                    type="button"
                    role="tab"
                    className="practice-scope-tab"
                    aria-selected={activeScope === 'classroom'}
                    onClick={() => {
                      updateParams({ scope: null, page: '0' });
                    }}
                  >
                    Classroom practice
                  </button>
                  <button
                    type="button"
                    role="tab"
                    className="practice-scope-tab"
                    aria-selected={activeScope === 'public'}
                    onClick={() => {
                      updateParams({ scope: 'public', classId: null, format: null, page: '0' });
                    }}
                  >
                    Public catalog
                  </button>
                </div>
              )}

              {/* Class Chooser when on /practice (classroom tab) with no classroom selected */}
              {activeScope === 'classroom' && !selectedClassroomId ? (
                classesQuery.isPending ? (
                  <div role="status" className="py-10 text-sm text-slate-600">
                    Loading enrolled classes…
                  </div>
                ) : classesQuery.isError ? (
                  <div role="alert" className="py-10 text-sm text-rose-800 space-y-2">
                    <p>
                      <strong>{classifyPracticeError(classesQuery.error).heading}:</strong>{' '}
                      {classifyPracticeError(classesQuery.error).message}
                    </p>
                    <button
                      type="button"
                      className="practice-btn-secondary"
                      onClick={() => void classesQuery.refetch()}
                    >
                      Try again
                    </button>
                  </div>
                ) : enrolledClassrooms.length === 0 ? (
                  <div className="py-12 border-t border-slate-300 space-y-3">
                    <p className="text-sm font-semibold text-slate-900">
                      You have not joined an active classroom yet.
                    </p>
                    <p className="text-xs text-slate-600 max-w-md">
                      Join a classroom with a code from your instructor to access class problem sets, or switch to the Public catalog tab above.
                    </p>
                    <div className="flex gap-2 pt-1">
                      <Link to="/student/classrooms" className="practice-btn-primary">
                        Join a classroom
                      </Link>
                      <button
                        type="button"
                        className="practice-btn-secondary"
                        onClick={() => updateParams({ scope: 'public' })}
                      >
                        Open Public catalog
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                    <div className="practice-class-directory">
                      {enrolledClassrooms.map((cls, index) => (
                        <Link key={cls.id} className="practice-class-entry" to={`/student/classrooms/${encodeURIComponent(cls.id)}/practice`}>
                          <span className="practice-class-number">{String(page * 20 + index + 1).padStart(2, '0')}</span>
                          <span className="practice-class-copy"><strong>{cls.name}</strong><small>{cls.description || 'Classroom coding practice'}</small></span>
                          <span className="practice-class-action">Open library <ArrowRight size={16} aria-hidden="true" /></span>
                        </Link>
                      ))}
                    </div>
                    {classesQuery.data && classesQuery.data.pagination.totalPages > 1 && (
                      <nav className="practice-directory-pages" aria-label="Classroom pages">
                        <span>Page {page + 1} of {classesQuery.data.pagination.totalPages}</span>
                        <div>
                          <button type="button" className="practice-btn-secondary" disabled={classesQuery.data.pagination.isFirst} onClick={() => updateParams({ page: String(page - 1) })}>Previous</button>
                          <button type="button" className="practice-btn-secondary" disabled={classesQuery.data.pagination.isLast} onClick={() => updateParams({ page: String(page + 1) })}>Next</button>
                        </div>
                      </nav>
                    )}
                  </>
                )
              ) : (
                /* Problem Library Table + Contextual Problem Inspector */
                <>
                  <div className="practice-toolbar">
                    <div className="practice-toolbar-left">
                      <label className="practice-search-box">
                        <Search size={15} className="text-slate-500 shrink-0" aria-hidden="true" />
                        <span className="sr-only">Search problems by title</span>
                        <input
                          ref={searchInputRef}
                          type="search"
                          value={searchQuery}
                          onChange={event => updateParams({ q: event.target.value, page: '0' })}
                          placeholder="Search problems…"
                        />
                        <kbd>Ctrl K</kbd>
                      </label>

                    </div>

                    <div className="practice-toolbar-right">
                      {activeScope === 'classroom' && <FilterSelect label="Problem format" value={formatFilter} options={[{ value: 'all', label: 'All formats' }, ...availableFormats.map(value => ({ value, label: value === 'FUNCTION_METHOD' ? 'Function / Method' : 'Standard I/O' }))]} onValueChange={value => updateParams({ format: value, page: '0' })} />}

                      <FilterSelect label="Language filter" value={langFilter} options={[{ value: 'all', label: 'All languages' }, ...availableLanguages.map(value => ({ value, label: formatLanguageLabel(value) }))]} onValueChange={value => updateParams({ lang: value, page: '0' })} />

                      <FilterSelect label="Sort problems" value={sortOrder} options={[{ value: 'newest', label: 'Newest first' }, { value: 'title_asc', label: 'Title A–Z' }]} onValueChange={value => updateParams({ sort: value })} />

                      {hasActiveFilters && (
                        <button
                          type="button"
                          className="practice-clear-btn inline-flex items-center gap-1"
                          onClick={() =>
                            updateParams({
                              q: null,
                              status: null,
                              format: null,
                              lang: null,
                              sort: null,
                            })
                          }
                        >
                          <RotateCcw size={12} aria-hidden="true" />
                          <span>Reset filters</span>
                        </button>
                      )}
                    </div>
                  </div>

                  {activeQuery.isPending ? (
                    <div role="status" className="py-10 space-y-3 border-t border-slate-300">
                      {[0, 1, 2, 3].map(idx => (
                        <div key={idx} className="h-12 w-full rounded bg-slate-200/70 animate-pulse" />
                      ))}
                      <span className="sr-only">Loading problems…</span>
                    </div>
                  ) : activeQuery.isError ? (
                    <div role="alert" className="py-10 border-t border-slate-300 space-y-3">
                      <p className="text-sm font-semibold text-rose-900">
                        {classifyPracticeError(activeQuery.error).heading}
                      </p>
                      <p className="text-xs text-slate-600">
                        {classifyPracticeError(activeQuery.error).message}
                      </p>
                      <button
                        type="button"
                        className="practice-btn-secondary"
                        onClick={() => void activeQuery.refetch()}
                      >
                        Try again
                      </button>
                    </div>
                  ) : normalizedItems.length === 0 ? (
                    <div className="py-12 border-t border-slate-300 space-y-2">
                      <p className="text-sm font-semibold text-slate-900">
                        No published practice problems yet.
                      </p>
                      <p className="text-xs text-slate-600">
                        {activeScope === 'classroom'
                          ? 'Published coding problems for this classroom will appear here automatically.'
                          : 'Published problems in the public catalog will appear here.'}
                      </p>
                    </div>
                  ) : filteredItems.length === 0 ? (
                    <div className="py-12 border-t border-slate-300 space-y-3">
                      <p className="text-sm font-semibold text-slate-900">
                        No problems match your current search or filters.
                      </p>
                      <button
                        type="button"
                        className="practice-btn-secondary"
                        onClick={() =>
                          updateParams({
                            q: null,
                            status: null,
                            format: null,
                            lang: null,
                            sort: null,
                          })
                        }
                      >
                        Clear filters
                      </button>
                    </div>
                  ) : (
                    <OpenTextbookWorkbench
                      items={filteredItems}
                      selected={selectedInspectorItem!}
                      returnContext={returnContext}
                      statusFilter={statusFilter}
                      onStatusChange={value => updateParams({ status: value, page: '0' })}
                      onSelect={id => updateParams({ selected: id })}
                      page={page}
                      totalPages={pagination?.totalPages ?? 1}
                      totalElements={pagination?.totalElements ?? filteredItems.length}
                      onPageChange={next => updateParams({ page: String(next), selected: null })}
                    />
                  )}
                </>
              )}
            </section>
          </>
        )}
      </div>
    </WorkspaceLayout>
  );
};
