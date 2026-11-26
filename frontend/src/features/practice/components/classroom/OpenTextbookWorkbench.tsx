import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRight, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { FilterSelect } from '@/components/ui/FilterSelect';
import { classifyPracticeError, practiceApi, practiceKeys } from '../../api/practiceApi';
import type { PracticeLibraryItem } from '../../types';
import { buildProblemWorkspacePath, formatLanguageLabel } from '../../utils/practiceFormatters';
import { prefetchProblemWorkspace } from '../../utils/practicePrefetch';
import { PracticeMarkdown } from '../shared/PracticeMarkdown';
import { ProblemFolio } from '../folio/ProblemFolio';

type StatusFilter = 'all' | 'unsolved' | 'in_progress' | 'completed';

interface Props {
  items: PracticeLibraryItem[];
  selected: PracticeLibraryItem;
  returnContext: string;
  statusFilter: StatusFilter;
  onStatusChange: (value: StatusFilter) => void;
  onSelect: (id: string) => void;
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}

export function OpenTextbookWorkbench({ items, selected, returnContext, statusFilter, onStatusChange, onSelect, page, totalPages, totalElements, onPageChange }: Props) {
  const queryClient = useQueryClient();
  const [closed, setClosed] = useState(() => typeof window !== 'undefined' && window.matchMedia('(max-width: 800px)').matches);
  const [folioOpen, setFolioOpen] = useState(false);
  const indexRef = useRef<HTMLDivElement>(null);
  const readingRef = useRef<HTMLDivElement>(null);
  const selectedRef = useRef<HTMLButtonElement>(null);
  const folioTriggerRef = useRef<HTMLButtonElement>(null);
  const detail = useQuery({
    queryKey: practiceKeys.classDetail(selected.classroomId ?? '', selected.id),
    queryFn: () => practiceApi.detail(selected.classroomId!, selected.id),
    enabled: Boolean(selected.classroomId),
    staleTime: 30_000,
    retry: false,
  });
  const currentDetail = detail.data?.id === selected.id ? detail.data : undefined;
  const solveHref = buildProblemWorkspacePath('classroom', selected.classroomId, selected.id, returnContext);
  const statusLabel = selected.completionStatus === 'COMPLETED' ? '✓ Completed' : selected.completionStatus === 'IN_PROGRESS' ? '◐ In progress' : '○ Unsolved';
  const selectedPosition = items.findIndex(item => item.id === selected.id);

  useEffect(() => {
    readingRef.current?.scrollTo({ top: 0 });
    const index = indexRef.current;
    const item = selectedRef.current;
    if (!index || !item) return;
    const indexBounds = index.getBoundingClientRect();
    const itemBounds = item.getBoundingClientRect();
    if (itemBounds.top < indexBounds.top) index.scrollTop -= indexBounds.top - itemBounds.top;
    else if (itemBounds.bottom > indexBounds.bottom) index.scrollTop += itemBounds.bottom - indexBounds.bottom;
  }, [selected.id]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && folioOpen) return;
      if (event.key === 'Escape' && !closed) setClosed(true);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [closed, folioOpen]);

  const changeSelection = (id: string) => { onSelect(id); setClosed(false); };
  const move = (delta: number) => { const next = items[selectedPosition + delta]; if (next) changeSelection(next.id); };
  const closeFolio = () => { setFolioOpen(false); requestAnimationFrame(() => folioTriggerRef.current?.focus()); };

  const warmWorkspace = () => prefetchProblemWorkspace(queryClient, 'classroom', selected.classroomId, selected.id);

  return <>
    <div className="open-textbook" data-closed={closed}>
      <section className="open-textbook-index" aria-label="Classroom problem index">
        <div className="open-textbook-index-head"><strong>Problems</strong><span>{items.length} on this page</span></div>
        <div className="open-textbook-status" role="group" aria-label="Status filter">{(['all', 'unsolved', 'in_progress', 'completed'] as const).map(value => <button key={value} type="button" aria-pressed={statusFilter === value} onClick={() => onStatusChange(value)}>{value === 'all' ? 'All' : value === 'in_progress' ? 'In progress' : value === 'unsolved' ? 'Unsolved' : 'Completed'}</button>)}</div>
        <div className="open-textbook-status-select"><FilterSelect label="Status filter" value={statusFilter} options={[{ value: 'all', label: 'All' }, { value: 'unsolved', label: 'Unsolved' }, { value: 'in_progress', label: 'In progress' }, { value: 'completed', label: 'Completed' }]} onValueChange={value => onStatusChange(value as StatusFilter)} /></div>
        <div className="open-textbook-index-list" ref={indexRef} role="listbox" aria-label="Problems" aria-activedescendant={`open-textbook-item-${selected.id}`}>{items.map((item, index) => {
          const isSelected = item.id === selected.id;
          const label = item.completionStatus === 'COMPLETED' ? '✓ Completed' : item.completionStatus === 'IN_PROGRESS' ? '◐ In progress' : '○ Unsolved';
          return <button id={`open-textbook-item-${item.id}`} ref={isSelected ? selectedRef : undefined} key={item.id} type="button" role="option" aria-selected={isSelected} className="open-textbook-index-item" onClick={() => changeSelection(item.id)} onKeyDown={event => { if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return; event.preventDefault(); const next = items[index + (event.key === 'ArrowDown' ? 1 : -1)]; if (next) { changeSelection(next.id); requestAnimationFrame(() => document.getElementById(`open-textbook-item-${next.id}`)?.focus()); } }}><span className="open-textbook-item-status">{label}</span><strong>{item.title}</strong><small>{item.problemType === 'FUNCTION_METHOD' ? 'Function / Method' : 'Standard I/O'}{!item.available ? ' · Read-only' : ''}</small></button>;
        })}</div>
        {totalPages > 1 && <div className="open-textbook-index-pages"><span>Page {page + 1} of {totalPages} · {totalElements} total</span><div><button type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}>Previous</button><button type="button" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>Next</button></div></div>}
      </section>
      {!closed && <section className="open-textbook-reading" aria-label={`Problem preview: ${selected.title}`}>
        <header className="open-textbook-reading-head"><span>PROBLEM PREVIEW</span><button type="button" onClick={() => setClosed(true)} aria-label="Back to problems" title="Close preview (Esc)"><span className="open-textbook-mobile-back">Back to problems</span><X size={17} aria-hidden="true" /></button></header>
        <div className="open-textbook-reading-scroll" ref={readingRef}>
          <article className="open-textbook-document" key={selected.id}>
            <span className="open-textbook-reading-status">{statusLabel}</span>
            <h2>{selected.title}</h2>
            <dl className="open-textbook-specs"><div><dt>FORMAT</dt><dd>{selected.problemType === 'FUNCTION_METHOD' ? 'Function / Method' : 'Standard I/O'}</dd></div>{selected.timeLimitMs !== null && <div><dt>TIME LIMIT</dt><dd>{selected.timeLimitMs} ms</dd></div>}{selected.memoryLimitMb !== null && <div><dt>MEMORY</dt><dd>{selected.memoryLimitMb} MB</dd></div>}</dl>
            {detail.isPending && <div role="status" className="open-textbook-loading"><div /><div /><div /><span className="sr-only">Loading problem preview…</span></div>}
            {detail.isError && <div role="alert" className="open-textbook-message"><h3>{classifyPracticeError(detail.error).heading}</h3><p>{classifyPracticeError(detail.error).message}</p><button type="button" onClick={() => void detail.refetch()}>Retry</button></div>}
            {currentDetail && <><div className="open-textbook-section-line"><span className="open-textbook-section-label">PROBLEM STATEMENT</span><button type="button" ref={folioTriggerRef} className="open-textbook-full" onClick={() => setFolioOpen(true)}>Read full statement <ArrowRight size={15} aria-hidden="true" /></button></div><div className="practice-prose open-textbook-prose"><PracticeMarkdown content={currentDetail.description} /></div>{currentDetail.sampleTestcases.length > 0 && <div className="open-textbook-sample"><h3>Example 1</h3><div className="open-textbook-sample-grid"><div><span>INPUT</span><pre>{currentDetail.sampleTestcases[0].input || '(empty)'}</pre></div><div><span>EXPECTED OUTPUT</span><pre>{currentDetail.sampleTestcases[0].expectedOutput || '(empty)'}</pre></div></div></div>}</>}
          </article>
        </div>
        <footer className="open-textbook-reading-foot"><div className="open-textbook-footer-meta"><div><span>LANGUAGES</span><strong>{selected.allowedLanguages.map(formatLanguageLabel).join(' · ')}</strong></div><div><span>PRACTICE</span><strong>{selected.available ? 'Unlimited attempts · Ungraded' : 'Read-only'}</strong></div></div><Link className="open-textbook-solve" to={solveHref} onMouseEnter={warmWorkspace} onFocus={warmWorkspace}>Start solving <ArrowRight size={16} aria-hidden="true" /></Link></footer>
      </section>}
      {closed && <div className="open-textbook-closed"><p>Choose a problem to read its preview.</p><button type="button" onClick={() => setClosed(false)}>Reopen {selected.title}</button></div>}
    </div>
    {folioOpen && <ProblemFolio selected={{ id: selected.id, title: selected.title, status: 'PUBLISHED', createdAt: selected.createdAt }} classroomDetail={currentDetail} contextLabel="Class problem library" loading={detail.isPending} error={detail.isError ? classifyPracticeError(detail.error).message : null} status={statusLabel} position={selectedPosition + 1} total={items.length} solveHref={solveHref} onClose={closeFolio} onPrevious={() => move(-1)} onNext={() => move(1)} onRetry={() => void detail.refetch()} />}
  </>;
}
