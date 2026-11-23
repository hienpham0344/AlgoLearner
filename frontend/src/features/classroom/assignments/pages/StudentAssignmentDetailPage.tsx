import React, { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, AlertCircle } from 'lucide-react';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import { useAssignmentDetail } from '../hooks/useAssignments';
import { useActiveAttempt, useStartAttempt } from '../hooks/useStudentAttempt';
import { StudentAssignmentBriefing } from '../components/student/StudentAssignmentBriefing';
import { assignmentError } from '../api/assignmentApi';

export const StudentAssignmentDetailPage: React.FC = () => {
  const { id: classId = '', assignmentId = '' } = useParams<{ id: string; assignmentId: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();

  const { data: assignment, isLoading, error } = useAssignmentDetail(classId, assignmentId);
  const { data: activeAttempt } = useActiveAttempt(classId, assignmentId);

  const startMutation = useStartAttempt(classId, assignmentId);
  const [startError, setStartError] = useState<string | null>(null);

  const handleStartAttempt = async () => {
    setStartError(null);
    try {
      await startMutation.mutateAsync();
      navigate(`/student/classrooms/${encodeURIComponent(classId)}/assignments/${encodeURIComponent(assignmentId)}/workspace`);
    } catch (err) {
      setStartError(assignmentError(err));
    }
  };

  const layoutPersona = {
    id: user?.id || '',
    username: user?.username || '',
    fullName: user?.fullName || user?.username || 'Student',
    email: user?.email || '',
    role: 'STUDENT' as const,
  };

  if (isLoading) {
    return (
      <div className="p-12 text-center text-xs text-slate-500">
        Loading assignment details...
      </div>
    );
  }

  if (error || !assignment) {
    return (
      <div className="p-12 text-center text-rose-600 text-xs">
        Failed to load assignment. {error ? assignmentError(error) : ''}
      </div>
    );
  }

  return (
    <ClassroomLayout role="STUDENT" persona={layoutPersona} breadcrumbChild={assignment.title}>
      <div className="space-y-4">
        <Link
          to={`/student/classrooms/${encodeURIComponent(classId)}/assignments`}
          className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-500 hover:text-slate-800"
        >
          <ArrowLeft size={14} /> Back to Assignments
        </Link>

        {startError && (
          <div className="p-3.5 bg-rose-50 border border-rose-200 text-rose-800 text-xs rounded-xl flex items-center gap-2 max-w-4xl mx-auto">
            <AlertCircle size={16} className="shrink-0 text-rose-600" />
            <span>{startError}</span>
          </div>
        )}

        <StudentAssignmentBriefing
          assignment={assignment}
          activeAttempt={activeAttempt}
          onStart={handleStartAttempt}
          isStarting={startMutation.isPending}
        />
      </div>
    </ClassroomLayout>
  );
};
