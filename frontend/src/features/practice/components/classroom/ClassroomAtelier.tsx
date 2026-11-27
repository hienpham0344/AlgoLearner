import { ArrowLeft, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { classifyPracticeError } from '../../api/practiceApi';
import { PatternRenderer } from '../generative-patterns/PatternRenderer';
import { resolvePattern } from '../generative-patterns/patternCore';

interface ClassroomEntry { id: string; name: string; description?: string | null }
interface Props {
  classrooms: ClassroomEntry[];
  total: number;
  page: number;
  totalPages: number;
  pending: boolean;
  error: unknown;
  onRetry: () => void;
  onPageChange: (page: number) => void;
}

function AtelierCover({ classroom }: { classroom: ClassroomEntry }) {
  const pattern = resolvePattern(classroom);
  return <article className="atelier-cover" data-tone={pattern.surface} data-family={pattern.family}>
    <div className="atelier-illustration"><PatternRenderer config={pattern} /></div>
    <div className="atelier-content"><h2>{classroom.name}</h2><p title={classroom.description || undefined}>{classroom.description || 'Classroom coding practice'}</p></div>
    <div className="atelier-action"><Link to={`/student/classrooms/${encodeURIComponent(classroom.id)}/practice`}>Open library <ArrowRight size={16} aria-hidden="true" /></Link></div>
  </article>;
}

export function ClassroomAtelier({ classrooms, total, page, totalPages, pending, error, onRetry, onPageChange }: Props) {
  return <div className="atelier-page">
    <nav className="problem-gallery-context" aria-label="Practice source"><span aria-current="page">Classroom practice</span><Link to="/practice?scope=public">Public catalog</Link></nav>
    <header className="atelier-page-head"><div><h1>Your classrooms.</h1><p>Choose a classroom to start practicing.</p></div>{!pending && !error && <span className="atelier-enrolled">{total} enrolled</span>}</header>
    {pending ? <div role="status" className="atelier-grid"><div className="atelier-skeleton" /><div className="atelier-skeleton" /><span className="sr-only">Loading enrolled classrooms…</span></div>
      : error ? <div role="alert" className="atelier-notice"><h2>{classifyPracticeError(error).heading}</h2><p>{classifyPracticeError(error).message}</p><button type="button" onClick={onRetry}>Retry</button></div>
        : classrooms.length === 0 ? <div className="atelier-notice"><h2>No enrolled classrooms yet.</h2><p>Join a classroom to access its coding problem library.</p><Link to="/student/classrooms">Open My classes</Link></div>
          : <><div className="atelier-grid">{classrooms.map(classroom => <AtelierCover key={classroom.id} classroom={classroom} />)}</div>{totalPages > 1 && <nav className="atelier-pagination" aria-label="Classroom pages"><span>Page {page + 1} of {totalPages}</span><div><button type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}><ArrowLeft size={14} /> Previous</button><button type="button" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>Next <ArrowRight size={14} /></button></div></nav>}</>}
  </div>;
}
