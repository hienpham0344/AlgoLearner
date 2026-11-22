import React, { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, History } from 'lucide-react';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import { useAssignmentDetail } from '../hooks/useAssignments';
import { useOwnAttempts } from '../hooks/useStudentAttempt';
import { formatVietnamDateTime } from '../utils/timeUtils';
import { StudentAttemptDetail } from '../components/student/StudentAttemptDetail';

export const StudentAttemptsHistoryPage: React.FC = () => {
  const { id: classId = '', assignmentId = '' } = useParams<{ id: string; assignmentId: string }>();
  const { user } = useAuth();

  const { data: assignment } = useAssignmentDetail(classId, assignmentId);
  const { data: attempts = [], isLoading } = useOwnAttempts(classId, assignmentId);

  const [selectedAttemptId, setSelectedAttemptId] = useState<string | null>(null);

  const activeSelectedAttempt = attempts.find((a) => a.id === selectedAttemptId) || attempts[0];

  const layoutPersona = {
    id: user?.id || '',
    username: user?.username || '',
    fullName: user?.fullName || user?.username || 'Student',
    email: user?.email || '',
    role: 'STUDENT' as const,
  };

  return (
    <ClassroomLayout
      role="STUDENT"
      persona={layoutPersona}
      breadcrumbChild={assignment ? `${assignment.title} — Attempts` : 'Attempts'}
    >
      <div className="max-w-5xl mx-auto space-y-6">
        <Link
          to={`/student/classrooms/${encodeURIComponent(classId)}/assignments/${encodeURIComponent(assignmentId)}`}
          className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-500 hover:text-slate-800"
        >
          <ArrowLeft size={14} /> Back to Overview
        </Link>

        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight">Submission History</h1>
          <p className="text-xs text-slate-500 mt-0.5">
            View all official attempts and review evaluation feedback for {assignment?.title}.
          </p>
        </div>

        {isLoading ? (
          <div className="p-12 text-center text-xs text-slate-500">Loading attempts...</div>
        ) : attempts.length === 0 ? (
          <div className="p-12 rounded-3xl border border-dashed border-slate-300 bg-white text-center space-y-2">
            <History size={24} className="text-slate-400 mx-auto" />
            <h3 className="text-sm font-bold text-slate-900">No official submissions yet</h3>
            <p className="text-xs text-slate-500">You have not submitted any official attempts for this assignment.</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Attempts Selector Pills */}
            <div className="flex items-center gap-2 overflow-x-auto pb-1">
              {attempts.map((att) => {
                const isSelected = activeSelectedAttempt?.id === att.id;

                return (
                  <button
                    key={att.id}
                    type="button"
                    onClick={() => setSelectedAttemptId(att.id)}
                    className={`px-4 py-2 rounded-xl text-xs font-bold border transition-all flex items-center gap-2 shrink-0 ${
                      isSelected
                        ? 'border-slate-900 bg-slate-900 text-white shadow-xs'
                        : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                    }`}
                  >
                    <span>Attempt #{att.attemptNumber}</span>
                    <span className="opacity-70 text-[10px]">
                      {formatVietnamDateTime(att.submittedAt)}
                    </span>
                    {att.finalScore !== null && att.finalScore !== undefined && (
                      <span className="px-1.5 py-0.2 rounded bg-white/20 text-white text-[10px]">
                        {att.finalScore} pts
                      </span>
                    )}
                  </button>
                );
              })}
            </div>

            {/* Selected Attempt Details */}
            {activeSelectedAttempt && (
              <StudentAttemptDetail
                attempt={activeSelectedAttempt}
                questions={assignment?.questions}
              />
            )}
          </div>
        )}
      </div>
    </ClassroomLayout>
  );
};
