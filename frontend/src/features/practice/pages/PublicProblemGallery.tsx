import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRight, Search } from 'lucide-react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { classifyPracticeError, practiceKeys, publicPracticeApi } from '../api/practiceApi';
import { PracticeMarkdown } from '../components/shared/PracticeMarkdown';
import { ProblemFolio } from '../components/folio/ProblemFolio';
import type { PublicProblemSummary } from '../types';
import { buildProblemWorkspacePath, formatLanguageLabel } from '../utils/practiceFormatters';
import { prefetchProblemWorkspace } from '../utils/practicePrefetch';
import { hasAnySavedDraft } from '../utils/practiceStorage';

type GalleryView = 'gallery' | 'index';
type Status = 'all' | 'unsolved' | 'in_progress';
type Sort = 'newest' | 'title_asc';
const PAGE_SIZE = 20;

export function PublicProblemGallery({ userId }: { userId: string }) {
  const queryClient = useQueryClient();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const view: GalleryView = params.get('view') === 'index' ? 'index' : 'gallery';
  const query = params.get('q') ?? '';
  const status: Status = params.get('status') === 'in_progress' ? 'in_progress' : params.get('status') === 'unsolved' ? 'unsolved' : 'all';
  const sort: Sort = params.get('sort') === 'title_asc' ? 'title_asc' : 'newest';
  const page = Math.max(0, Number(params.get('page') || 0) || 0);
  const selectedId = params.get('selected');
  const [fullOpen, setFullOpen] = useState(false);
  const [sampleIndex, setSampleIndex] = useState(0);
  const searchRef = useRef<HTMLInputElement>(null);
  const previewTriggerRef = useRef<HTMLButtonElement>(null);

  const statsQuery = useQuery({
    queryKey: practiceKeys.publicStats(),
    queryFn: () => publicPracticeApi.statistics(),
    staleTime: 30_000,
    retry: false,
  });

  const update = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
    next.set('scope', 'public');
    setParams(next, { replace: true });
  };

  // The list API has page parameters but no search contract. Fetch summaries across
  // its pages; fetch full statements only for the selected problem.
  const catalog = useQuery({
    queryKey: [...practiceKeys.all, 'public-gallery-summaries'],
    queryFn: async () => {
      const first = await publicPracticeApi.list(0);
      const pages = [];
      for (let start = 1; start < first.pagination.totalPages; start += 4) {
        const batch = await Promise.all(Array.from({ length: Math.min(4, first.pagination.totalPages - start) }, (_, index) => publicPracticeApi.list(start + index)));
        pages.push(...batch);
      }
      return [first, ...pages].flatMap(result => result.items);
    },
    staleTime: 30_000,
    retry: false,
  });

  const matching = useMemo(() => {
    const search = query.trim().toLocaleLowerCase();
    const rows = (catalog.data ?? []).filter(item => {
      const draft = hasAnySavedDraft(userId, 'public', null, item.id);
      if (search && !item.title.toLocaleLowerCase().includes(search)) return false;
      if (status === 'in_progress' && !draft) return false;
      if (status === 'unsolved' && draft) return false;
      return true;
    });
    return sort === 'title_asc' ? [...rows].sort((a, b) => a.title.localeCompare(b.title)) : rows;
  }, [catalog.data, query, status, sort, userId]);

  const visible = matching.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  useEffect(() => {
    if (!selectedId) return;
    const index = matching.findIndex(item => item.id === selectedId);
    if (index >= 0 && Math.floor(index / PAGE_SIZE) !== page) update({ page: String(Math.floor(index / PAGE_SIZE)) });
  }, [matching, selectedId, page]);
  const selected = matching.find(item => item.id === selectedId) ?? visible[0] ?? matching[0] ?? null;
  const detail = useQuery({
    queryKey: practiceKeys.publicDetail(selected?.id ?? ''),
    queryFn: () => publicPracticeApi.detail(selected!.id),
    enabled: Boolean(selected),
    staleTime: 30_000,
    retry: false,
  });
  const currentDetail = detail.data?.id === selected?.id ? detail.data : undefined;
  const samples = currentDetail?.samples.filter(sample => sample.sample) ?? [];
  const sample = samples[sampleIndex] ?? samples[0] ?? null;
  const origin = `${location.pathname}${location.search.includes('scope=public') ? location.search : '?scope=public'}`;
  const solveHref = selected ? buildProblemWorkspacePath('public', null, selected.id, origin) : '#';

  useEffect(() => { setSampleIndex(0); }, [selected?.id]);
  useEffect(() => {
    const keydown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); searchRef.current?.focus(); }
    };
    window.addEventListener('keydown', keydown);
    return () => window.removeEventListener('keydown', keydown);
  }, []);

  const choose = (item: PublicProblemSummary) => update({ selected: item.id });
  const move = (delta: number) => {
    if (!selected) return;
    const next = matching[matching.findIndex(item => item.id === selected.id) + delta];
    if (next) update({ selected: next.id, page: String(Math.floor(matching.indexOf(next) / PAGE_SIZE)) });
  };
  const closeFolio = () => { setFullOpen(false); requestAnimationFrame(() => previewTriggerRef.current?.focus()); };
  const selectedStatus = selected && hasAnySavedDraft(userId, 'public', null, selected.id) ? '◐ In progress' : '○ Unsolved';

  return <div className="problem-gallery">
    <nav className="problem-gallery-context" aria-label="Practice source">
      <Link to="/practice">Classroom practice</Link><span aria-current="page">Public catalog</span>
    </nav>
    <header className="problem-gallery-header">
      <div>
        <h1>Public catalog.</h1>
        {statsQuery.data && (
          <p className="practice-mono text-xs text-slate-600 mt-2 mb-0">
            {statsQuery.data.solved} solved · {statsQuery.data.currentStreak}d current streak · {statsQuery.data.highestStreak}d best
          </p>
        )}
      </div>
      <div className="problem-gallery-header-actions">
        <label className="problem-gallery-search"><Search size={16} aria-hidden="true" /><span className="sr-only">Search public problems</span><input ref={searchRef} type="search" placeholder="Search problems…" value={query} onChange={event => update({ q: event.target.value || null, page: null, selected: null })} /><kbd>Ctrl K</kbd></label>
        <div className="problem-gallery-view" role="group" aria-label="Catalog view"><button type="button" aria-pressed={view === 'gallery'} onClick={() => update({ view: null })}>Gallery</button><button type="button" aria-pressed={view === 'index'} onClick={() => update({ view: 'index' })}>Index</button></div>
      </div>
    </header>

    {catalog.isPending ? <div role="status" className="problem-gallery-loading"><div /><div /><div /><span className="sr-only">Loading public problems…</span></div>
      : catalog.isError ? <div role="alert" className="problem-gallery-notice"><h2>{classifyPracticeError(catalog.error).heading}</h2><p>{classifyPracticeError(catalog.error).message}</p><button type="button" onClick={() => void catalog.refetch()}>Retry</button></div>
      : !catalog.data?.length ? <div className="problem-gallery-notice"><h2>No public problems available.</h2><p>New problems will appear here when available.</p></div>
      : <>
        {view === 'gallery' && <section className="problem-gallery-preview" aria-label="Selected problem preview">
          {!selected ? <div className="problem-gallery-notice"><h2>No problems match your filters.</h2><button type="button" onClick={() => update({ q: null, status: null, page: null })}>Clear filters</button></div>
            : detail.isPending ? <div role="status" className="problem-gallery-preview-loading">Loading {selected.title}…</div>
              : detail.isError ? <div role="alert" className="problem-gallery-notice"><h2>Unable to load this problem.</h2><p>{classifyPracticeError(detail.error).message}</p><button type="button" onClick={() => void detail.refetch()}>Retry</button></div>
                : detail.data && <><div className="problem-gallery-reading" key={selected.id}>
                  <span className="problem-gallery-eyebrow">Problem preview</span><span className="problem-gallery-status">{selectedStatus}</span>
                  <h2>{detail.data.title}</h2>
                  <div className="problem-gallery-excerpt"><PracticeMarkdown content={detail.data.statement.split(/\n\s*\n/)[0] || detail.data.statement} /></div>
                  <div className="problem-gallery-meta"><div><span>Languages</span><strong>{detail.data.languages.map(formatLanguageLabel).join(' · ')}</strong></div><div><span>Format</span><strong>Standard I/O</strong></div></div>
                  <div className="problem-gallery-cta"><Link to={solveHref} className="practice-btn-primary" onMouseEnter={() => prefetchProblemWorkspace(queryClient, 'public', null, selected.id)} onFocus={() => prefetchProblemWorkspace(queryClient, 'public', null, selected.id)}>Start solving <ArrowRight size={15} aria-hidden="true" /></Link><button ref={previewTriggerRef} type="button" onClick={() => setFullOpen(true)}>Read full statement</button></div>
                </div><div className="problem-gallery-technical"><div className="problem-gallery-technical-head"><span>Sample case</span><span>{samples.length ? `${sampleIndex + 1} / ${samples.length}` : 'None'}</span></div>
                  {sample ? <><div className="problem-gallery-io"><div><span>Input</span><pre>{sample.input || '(empty)'}</pre></div><div><span>Expected output</span><pre>{sample.expectedOutput || '(empty)'}</pre></div></div><div className="problem-gallery-sample-nav">{samples.map((_, index) => <button key={index} type="button" aria-label={`Sample ${index + 1}`} aria-pressed={sampleIndex === index} onClick={() => setSampleIndex(index)}>{String(index + 1).padStart(2, '0')}</button>)}</div></> : <p>No public sample cases.</p>}
                  <div className="problem-gallery-limit"><span>Time limit</span><strong>{detail.data.limits.runtimeMillis} ms</strong></div>
                </div></>}
        </section>}
        <div className="problem-gallery-filters"><div role="group" aria-label="Problem status">{(['all', 'unsolved', 'in_progress'] as const).map(value => <button key={value} type="button" aria-pressed={status === value} onClick={() => update({ status: value === 'all' ? null : value, page: null, selected: null })}>{value === 'all' ? 'All' : value === 'unsolved' ? 'Unsolved' : 'In progress'}</button>)}</div><select aria-label="Sort problems" value={sort} onChange={event => update({ sort: event.target.value === 'newest' ? null : event.target.value })}><option value="newest">Newest</option><option value="title_asc">Title A–Z</option></select></div>
        {matching.length === 0 ? <div className="problem-gallery-notice"><h2>No problems match your filters.</h2><button type="button" onClick={() => update({ q: null, status: null, page: null })}>Clear filters</button></div> : <>
          {view === 'gallery' ? <div className="problem-gallery-strip" aria-label="Problems on this page">{visible.map((item, index) => <button key={item.id} type="button" className="problem-gallery-tile" aria-pressed={selected?.id === item.id} onClick={() => choose(item)} onKeyDown={event => { if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return; event.preventDefault(); const nextIndex = index + (event.key === 'ArrowRight' ? 1 : -1); const next = visible[nextIndex]; if (next) { choose(next); (event.currentTarget.parentElement?.children[nextIndex] as HTMLElement | undefined)?.focus(); } }}><span>{String(page * PAGE_SIZE + index + 1).padStart(2, '0')}</span><strong>{item.title}</strong><small>{hasAnySavedDraft(userId, 'public', null, item.id) ? '◐ In progress' : '○ Unsolved'}</small></button>)}</div>
            : <div className="problem-gallery-index">{visible.map((item, index) => <div key={item.id} className="problem-gallery-index-row"><span>{String(page * PAGE_SIZE + index + 1).padStart(2, '0')}</span><button type="button" onClick={() => { choose(item); update({ selected: item.id, view: null }); }}>{item.title}</button><small>{hasAnySavedDraft(userId, 'public', null, item.id) ? '◐ In progress' : '○ Unsolved'}</small><Link to={buildProblemWorkspacePath('public', null, item.id, origin)} onMouseEnter={() => prefetchProblemWorkspace(queryClient, 'public', null, item.id)} onFocus={() => prefetchProblemWorkspace(queryClient, 'public', null, item.id)}>Solve</Link></div>)}</div>}
          <footer className="problem-gallery-footer"><span>{matching.length} problems · Page {page + 1} of {Math.ceil(matching.length / PAGE_SIZE)}</span><div><button type="button" disabled={page === 0} onClick={() => update({ page: String(page - 1), selected: null })}>Previous</button><button type="button" disabled={(page + 1) * PAGE_SIZE >= matching.length} onClick={() => update({ page: String(page + 1), selected: null })}>Next</button></div></footer>
        </>}
      </>}
    {fullOpen && selected && <ProblemFolio selected={selected} detail={currentDetail} loading={detail.isPending} error={detail.isError ? classifyPracticeError(detail.error).message : null} status={selectedStatus ?? ''} position={matching.findIndex(item => item.id === selected.id) + 1} total={matching.length} solveHref={solveHref} onClose={closeFolio} onPrevious={() => move(-1)} onNext={() => move(1)} onRetry={() => void detail.refetch()} />}
  </div>;
}
