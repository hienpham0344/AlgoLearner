import { useEffect, useRef } from 'react';
import { AlertCircle } from 'lucide-react';
import { lessonError } from '../api/lessonApi';

export function LessonErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  return <section className="lesson-panel lesson-empty" role="alert"><AlertCircle size={30} aria-hidden="true" /><h2>Lesson unavailable</h2><p>{lessonError(error)}</p>{retry && <button className="lesson-button secondary" type="button" onClick={retry}>Try again</button>}</section>;
}
export function LessonPagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  if (page === 0 && totalPages <= 1) return null;
  return <nav aria-label="Lesson pagination" className="lesson-pagination"><button className="lesson-button secondary" type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button><span>Page {page + 1} of {Math.max(page + 1, totalPages)}</span><button className="lesson-button secondary" type="button" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</button></nav>;
}
export function LessonConfirm({ title, message, pending, onConfirm, onCancel }: { title: string; message: string; pending: boolean; onConfirm: () => void; onCancel: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { const element = dialog.current; element?.showModal(); return () => element?.close(); }, []);
  return <dialog ref={dialog} className="lesson-dialog" aria-labelledby="lesson-confirm-title" aria-describedby="lesson-confirm-message" onCancel={event => { event.preventDefault(); if (!pending) onCancel(); }}>
    <h2 id="lesson-confirm-title">{title}</h2><p id="lesson-confirm-message">{message}</p><div className="lesson-actions"><button autoFocus className="lesson-button secondary" type="button" disabled={pending} onClick={onCancel}>Cancel</button><button className="lesson-button" type="button" disabled={pending} onClick={onConfirm}>{pending ? 'Working…' : 'Confirm'}</button></div>
  </dialog>;
}
