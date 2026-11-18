import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { lessonApi, lessonError, lessonKeys } from '../api/lessonApi';
import { lessonMockStore } from '../mock/lessonMockStore';
import { lessonDate } from '../lessonRules';
import { LessonPagination } from './LessonState';

const IS_PROTOTYPE_DEMO = import.meta.env.MODE === 'auth-demo';

export function LessonProgress({ userId, classId, lessonId }: { userId: string; classId: string; lessonId: string }) {
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: lessonKeys.progress(userId, classId, lessonId, page),
    queryFn: () => IS_PROTOTYPE_DEMO ? Promise.resolve(lessonMockStore.progress(classId, lessonId, page)) : lessonApi.progress(classId, lessonId, page),
    retry: false,
  });
  const hasCompleted = Boolean(query.data?.items.some(student => student.completed));

  useEffect(() => {
    if (hasCompleted) {
      void queryClient.invalidateQueries({ queryKey: lessonKeys.detail(userId, classId, lessonId) });
    }
  }, [hasCompleted, queryClient, userId, classId, lessonId]);

  async function refreshProgress() {
    await Promise.all([
      query.refetch(),
      queryClient.invalidateQueries({ queryKey: lessonKeys.detail(userId, classId, lessonId) }),
    ]);
  }

  return <section className="lesson-panel" aria-label="Student progress"><div className="lesson-section-head"><h2>Student progress</h2><button className="lesson-button text" type="button" disabled={query.isFetching} onClick={() => void refreshProgress()}>Refresh progress</button></div>
    {query.isPending && <p role="status">Loading student progress…</p>}
    {query.isError ? <p role="alert" className="lesson-alert">{lessonError(query.error)}</p> : query.data && <>
      {query.data.items.length === 0 ? <p className="lesson-muted">No student progress recorded yet.</p> : <div className="lesson-table-scroll"><table className="lesson-progress-table"><caption className="sr-only">Student lesson completion. Times are shown in UTC+7.</caption><thead><tr><th scope="col">Student</th><th scope="col">Completion</th><th scope="col">Completed at · UTC+7</th></tr></thead><tbody>
        {query.data.items.map(student => <tr key={student.studentId}><th scope="row">{student.displayName}</th><td>{student.completed ? 'Completed' : 'Not completed'}</td><td>{lessonDate(student.completedAt)}</td></tr>)}
      </tbody></table></div>}
      <LessonPagination page={page} totalPages={query.data.pagination.totalPages} onPage={setPage} />
    </>}
  </section>;
}
