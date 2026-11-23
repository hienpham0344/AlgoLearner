import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ArrowUpRight, BookOpenText } from 'lucide-react';
import { useIsPrototypeDemo } from '@/components/BackendStatusBanner';
import { useAuth } from '@/hooks/useAuth';
import { classroomApi, classroomError, classroomKeys } from '../../api/classroomApi';
import { readStoredClassrooms, readStoredPersona } from '../../mock/useClassroomStore';
import type { LessonRole } from '../types';

export function DashboardLessonLinks({ role }: { role: LessonRole }) {
  const { user } = useAuth();
  const isPrototypeDemo = useIsPrototypeDemo();
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: [...classroomKeys.list(page), isPrototypeDemo],
    queryFn: () => classroomApi.list(page),
    enabled: !isPrototypeDemo && user?.role === role,
    retry: false,
    staleTime: 15_000,
  });
  const base = `/${role.toLowerCase()}/classrooms`;
  const demoClassrooms = isPrototypeDemo && user?.role === role
    ? (() => {
        const persona = readStoredPersona(role);
        return readStoredClassrooms().filter(classroom =>
          role === 'TEACHER'
            ? classroom.ownerId === persona.id
            : classroom.members.some(member => member.studentId === persona.id && member.status === 'ACTIVE'),
        );
      })()
    : [];
  const classrooms = isPrototypeDemo
    ? demoClassrooms
    : (query.data?.items || []).filter(classroom => role !== 'TEACHER' || classroom.ownerId === user?.id);
  const isPending = !isPrototypeDemo && query.isPending;
  const isError = !isPrototypeDemo && query.isError;
  return <section id="lessons" className="dashboard-lessons" aria-labelledby="dashboard-lessons-title" tabIndex={-1}>
    <div className="dashboard-lessons-heading"><div><span className="dashboard-eyebrow">CLASSROOM LEARNING</span><h2 id="dashboard-lessons-title">Class lessons</h2><p>{role === 'TEACHER' ? 'Choose a class to create, publish and manage its lessons.' : 'Choose a class to read its lessons and track your completion.'}</p></div><Link to={base}>All classrooms <ArrowUpRight size={15} aria-hidden="true" /></Link></div>
    {isPending ? <p role="status" className="dashboard-lessons-state">Loading your classrooms…</p>
      : isError ? <div role="alert" className="dashboard-lessons-state"><p>{classroomError(query.error)}</p><button type="button" onClick={() => void query.refetch()}>Try again</button></div>
      : classrooms.length === 0 ? <p className="dashboard-lessons-state">{role === 'TEACHER' ? 'Your classrooms will appear here when you create one.' : 'Join a classroom to see its lessons here.'}</p>
      : <ul className="dashboard-lessons-classes">{classrooms.map(classroom => <li key={classroom.id}><Link to={`${base}/${encodeURIComponent(classroom.id)}/lessons`}>
        <BookOpenText size={22} strokeWidth={1.5} aria-hidden="true" /><div><strong>{classroom.name}</strong><span>{classroom.status === 'ARCHIVED' ? 'Archived · Read only' : 'Open lessons'}</span></div><ArrowUpRight size={18} aria-hidden="true" />
      </Link></li>)}</ul>}
    {!isPrototypeDemo && query.data && (page > 0 || query.data.pagination.totalPages > 1) && <nav className="dashboard-lessons-pages" aria-label="Classroom lesson pages">
      <button type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button><span>Page {page + 1}</span><button type="button" disabled={query.data.pagination.isLast} onClick={() => setPage(value => value + 1)}>Next</button>
    </nav>}
  </section>;
}
