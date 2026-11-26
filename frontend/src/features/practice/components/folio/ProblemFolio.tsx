import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { PracticeMarkdown } from '../shared/PracticeMarkdown';
import type { ClassPracticeDetail, PublicProblemDetail, PublicProblemSummary } from '../../types';
import { formatLanguageLabel } from '../../utils/practiceFormatters';
import { preloadPracticeWorkspace } from '../../utils/practicePrefetch';

interface OutlineEntry { id: string; label: string; level: number }

interface ProblemFolioProps {
  selected: PublicProblemSummary;
  detail?: PublicProblemDetail;
  classroomDetail?: ClassPracticeDetail;
  contextLabel?: string;
  loading: boolean;
  error: string | null;
  status: string;
  position: number;
  total: number;
  solveHref: string;
  solveLabel?: string;
  onSolve?: () => void;
  onClose: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onRetry: () => void;
}

export function ProblemFolio({ selected, detail, classroomDetail, contextLabel = 'Public catalog', loading, error, status, position, total, solveHref, solveLabel = 'Start solving', onSolve, onClose, onPrevious, onNext, onRetry }: ProblemFolioProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const documentRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [outline, setOutline] = useState<OutlineEntry[]>([]);
  const [activeId, setActiveId] = useState('');
  const currentDetail = useMemo(() => detail?.id === selected.id ? detail : classroomDetail?.id === selected.id ? {
    id: classroomDetail.id,
    title: classroomDetail.title,
    statement: classroomDetail.description,
    languages: classroomDetail.allowedLanguages,
    samples: classroomDetail.sampleTestcases.map(sample => ({ ...sample, sample: true })),
    limits: { runtimeMillis: classroomDetail.timeLimitMs },
    memoryLimitMb: classroomDetail.memoryLimitMb,
    format: classroomDetail.problemType === 'FUNCTION_METHOD' ? 'Function / Method' : 'Standard I/O',
  } : undefined, [detail, classroomDetail, selected.id]);
  const actionsRef = useRef({ onClose, onPrevious, onNext, position, total });
  actionsRef.current = { onClose, onPrevious, onNext, position, total };

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: 0 });
    setOutline([]);
    setActiveId('');
  }, [selected.id]);

  useEffect(() => {
    if (!currentDetail) return;
    const headings = Array.from(documentRef.current?.querySelectorAll<HTMLElement>('.problem-folio-prose h2,.problem-folio-prose h3') ?? []);
    const entries = headings.map((heading, index) => {
      const id = `folio-section-${index}`;
      heading.id = id;
      return { id, label: heading.textContent?.trim() ?? '', level: Number(heading.tagName[1]) };
    }).filter(entry => entry.label);
    setOutline(entries.length >= 2 ? entries : []);
    setActiveId(entries[0]?.id ?? '');
  }, [currentDetail]);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    document.body.style.overflow = 'hidden';
    dialogRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      const actions = actionsRef.current;
      if (event.key === 'Escape') { event.preventDefault(); actions.onClose(); return; }
      if (event.key === 'ArrowLeft' && !(event.target instanceof HTMLInputElement) && actions.position > 1) { event.preventDefault(); actions.onPrevious(); }
      if (event.key === 'ArrowRight' && !(event.target instanceof HTMLInputElement) && actions.position < actions.total) { event.preventDefault(); actions.onNext(); }
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const controls = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not(:disabled),a[href]'));
      if (!controls.length) return;
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialogRef.current)) { event.preventDefault(); first.focus(); }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => { document.body.style.overflow = previousOverflow; window.removeEventListener('keydown', onKeyDown); previousFocus?.focus(); };
  }, []);

  const onScroll = () => {
    if (!outline.length || !scrollRef.current) return;
    const top = scrollRef.current.getBoundingClientRect().top + 70;
    const current = [...outline].reverse().find(entry => (document.getElementById(entry.id)?.getBoundingClientRect().top ?? Infinity) <= top);
    setActiveId(current?.id ?? outline[0].id);
  };

  return <div className="problem-folio-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <div ref={dialogRef} className="problem-folio" role="dialog" aria-modal="true" aria-label={`Full statement: ${selected.title}`} tabIndex={-1}>
      <aside className="problem-folio-spine">
        <button type="button" className="problem-folio-return" onClick={onClose}><ArrowLeft size={15} aria-hidden="true" /> {contextLabel}</button>
        <div className="problem-folio-spine-scroll">
          <div className="problem-folio-mark"><span>PROBLEM FOLIO</span><div className="problem-folio-geometry" aria-hidden="true"><i /><i /><i /><i /></div></div>
          {outline.length > 0 && <nav className="problem-folio-outline" aria-label="On this page"><span>ON THIS PAGE</span>{outline.map(entry => <button key={entry.id} type="button" className={activeId === entry.id ? 'is-active' : ''} style={{ paddingLeft: entry.level === 3 ? 20 : 10 }} onClick={() => { document.getElementById(entry.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' }); setActiveId(entry.id); }}>{entry.label}</button>)}</nav>}
          {currentDetail && <dl className="problem-folio-meta">{'format' in currentDetail && <div><dt>FORMAT</dt><dd>{currentDetail.format}</dd></div>}<div><dt>TIME LIMIT</dt><dd>{currentDetail.limits.runtimeMillis} ms</dd></div>{'memoryLimitMb' in currentDetail && <div><dt>MEMORY</dt><dd>{currentDetail.memoryLimitMb} MB</dd></div>}<div><dt>LANGUAGES</dt><dd>{currentDetail.languages.map(formatLanguageLabel).join(' · ')}</dd></div></dl>}
        </div>
        <div className="problem-folio-navigation"><span>{position} of {total} problems</span><div><button type="button" disabled={position <= 1} onClick={onPrevious}><ArrowLeft size={15} aria-hidden="true" /> Previous</button><button type="button" disabled={position >= total} onClick={onNext}>Next <ArrowRight size={15} aria-hidden="true" /></button></div></div>
      </aside>
      <section className="problem-folio-canvas">
        <header className="problem-folio-toolbar"><span>{contextLabel.toUpperCase()} <span aria-hidden="true">/</span> PROBLEM STATEMENT</span><button type="button" aria-label="Close full statement" onClick={onClose}><X size={18} /></button></header>
        <div ref={scrollRef} className="problem-folio-document-scroll" onScroll={onScroll}>
          {loading && !currentDetail ? <div className="problem-folio-loading" role="status">Loading {selected.title}…</div>
            : error ? <div className="problem-folio-error" role="alert"><h2>Unable to load this problem.</h2><p>{error}</p><button type="button" onClick={onRetry}>Retry</button></div>
              : currentDetail && <article className="problem-folio-document" key={selected.id} ref={documentRef}>
                <span className="problem-folio-status">{status}</span>
                <h2 className="problem-folio-title">{currentDetail.title}</h2>
                <div className="problem-folio-rule" />
                <div className="problem-folio-section-label">PROBLEM STATEMENT</div>
                <div className="practice-prose problem-folio-prose"><PracticeMarkdown content={currentDetail.statement} /></div>
                {currentDetail.samples.filter(sample => sample.sample).map((sample, index) => <section className="problem-folio-example" key={index}><div className="problem-folio-example-heading"><span>EXAMPLE {String(index + 1).padStart(2, '0')}</span></div><div className="problem-folio-io"><div><span>INPUT</span><pre>{sample.input || '(empty)'}</pre></div><div><span>EXPECTED OUTPUT</span><pre>{sample.expectedOutput || '(empty)'}</pre></div></div></section>)}
              </article>}
        </div>
        <footer className="problem-folio-actions"><button type="button" onClick={onClose}>Back to {contextLabel.toLowerCase()}</button>{onSolve ? <button type="button" className="problem-folio-solve" onClick={onSolve}>{solveLabel} <ArrowRight size={16} aria-hidden="true" /></button> : <Link to={solveHref} className="problem-folio-solve" onMouseEnter={preloadPracticeWorkspace} onFocus={preloadPracticeWorkspace}>{solveLabel} <ArrowRight size={16} aria-hidden="true" /></Link>}</footer>
      </section>
    </div>
  </div>;
}
