import React, { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { FileText, ArrowRight } from 'lucide-react';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import { useAssignmentList } from '../hooks/useAssignments';
import { AssignmentStatusBadge } from '../components/shared/AssignmentStatusBadge';
import { AssignmentTypeBadge } from '../components/shared/AssignmentTypeBadge';
import { DeadlineBadge } from '../components/shared/DeadlineBadge';
import { ScoreDisplay } from '../components/shared/ScoreDisplay';

export const StudentAssignmentsPage: React.FC = () => {
  const { id: classId = '' } = useParams<{ id: string }>();
  const { user } = useAuth();

  const { data: assignments = [], isLoading } = useAssignmentList(classId, 'STUDENT');

  const [filter, setFilter] = useState<'ALL' | 'OPEN' | 'UPCOMING' | 'COMPLETED'>('ALL');

  const filteredAssignments = assignments.filter((a) => {
    if (filter === 'ALL') return true;
    if (filter === 'OPEN') return a.status === 'PUBLISHED';
    if (filter === 'UPCOMING') return a.status === 'SCHEDULED';
    if (filter === 'COMPLETED') return a.myBestScore !== null && a.myBestScore !== undefined;
    return true;
  });

  const layoutPersona = {
    id: user?.id || '',
    username: user?.username || '',
    fullName: user?.fullName || user?.username || 'Student',
    email: user?.email || '',
    role: 'STUDENT' as const,
  };

  return (
    <ClassroomLayout role="STUDENT" persona={layoutPersona} breadcrumbChild="Class Assignments">
      <div className="max-w-6xl mx-auto space-y-6">
        {/* Header */}
        <div className="pb-4 border-b border-slate-200">
          <h1 className="text-xl font-bold text-slate-900 tracking-tight">Classroom Assignments</h1>
          <p className="text-xs text-slate-500 mt-1">
            Browse and complete problem sets, quizzes, and programming evaluations assigned to your class.
          </p>
        </div>

        {/* Filter Tabs */}
        <div className="flex items-center gap-2">
          {(['ALL', 'OPEN', 'UPCOMING', 'COMPLETED'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => setFilter(tab)}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition-colors ${
                filter === tab
                  ? 'bg-slate-900 text-white shadow-xs'
                  : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
            >
              {tab === 'ALL'
                ? 'All Assignments'
                : tab === 'OPEN'
                ? 'Open Now'
                : tab === 'UPCOMING'
                ? 'Upcoming'
                : 'Completed'}
            </button>
          ))}
        </div>

        {/* Assignments Cards */}
        {isLoading ? (
          <div className="p-12 text-center text-xs text-slate-500">Loading assignments...</div>
        ) : filteredAssignments.length === 0 ? (
          <div className="p-12 rounded-3xl border border-dashed border-slate-300 bg-white text-center space-y-2">
            <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center mx-auto text-slate-400">
              <FileText size={24} />
            </div>
            <h3 className="text-sm font-bold text-slate-900">No assignments found</h3>
            <p className="text-xs text-slate-500">There are no assignments matching your current filter.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {filteredAssignments.map((assignment) => (
              <Link
                key={assignment.id}
                to={`/student/classrooms/${encodeURIComponent(classId)}/assignments/${encodeURIComponent(assignment.id)}`}
                className="p-5 rounded-2xl border border-slate-200 bg-white hover:border-slate-300 hover:shadow-xs transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-4 block group"
              >
                <div className="space-y-1.5 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <AssignmentTypeBadge type={assignment.type} />
                    <AssignmentStatusBadge
                      status={assignment.status}
                      startTime={assignment.startTime}
                      deadline={assignment.deadline}
                    />
                    <DeadlineBadge deadline={assignment.deadline} />
                  </div>

                  <h3 className="text-base font-bold text-slate-900 group-hover:text-blue-600 group-hover:underline truncate">
                    {assignment.title}
                  </h3>

                  <div className="flex items-center gap-4 text-xs text-slate-500">
                    <span>
                      {assignment.durationMinutes ? `${assignment.durationMinutes} mins` : 'Untimed'}
                    </span>
                    <span>•</span>
                    <span>
                      {assignment.maxAttempts === 0
                        ? 'Unlimited attempts'
                        : `${assignment.myRemainingAttempts ?? assignment.maxAttempts} attempts left`}
                    </span>
                  </div>
                </div>

                <div className="flex items-center justify-between sm:justify-end gap-6 shrink-0 pt-2 sm:pt-0 border-t sm:border-t-0 border-slate-100">
                  <div className="text-right">
                    <span className="text-[10px] uppercase font-bold text-slate-400 block">Best Score</span>
                    <ScoreDisplay score={assignment.myBestScore} />
                  </div>

                  <span className="p-2 rounded-xl bg-slate-50 text-slate-400 group-hover:bg-slate-900 group-hover:text-white transition-colors">
                    <ArrowRight size={16} />
                  </span>
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </ClassroomLayout>
  );
};
