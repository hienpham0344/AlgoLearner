import React from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { AlertCircle, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useAssignmentDetail } from '../hooks/useAssignments';
import { useActiveAttempt, useSubmitAttempt } from '../hooks/useStudentAttempt';
import { AssessmentWorkspace } from '../components/student/AssessmentWorkspace';
import { assignmentError } from '../api/assignmentApi';

export const StudentWorkspacePage: React.FC = () => {
  const { id: classId = '', assignmentId = '' } = useParams<{ id: string; assignmentId: string }>();
  const navigate = useNavigate();

  const { data: assignment, isLoading: isAssignmentLoading } = useAssignmentDetail(classId, assignmentId);
  const { data: activeAttempt, isLoading: isAttemptLoading, error: attemptError } = useActiveAttempt(classId, assignmentId);

  const submitMutation = useSubmitAttempt(classId, assignmentId);

  if (isAssignmentLoading || isAttemptLoading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-8">
        <div className="text-center space-y-2">
          <div className="w-8 h-8 rounded-full border-2 border-slate-900 border-t-transparent animate-spin mx-auto" />
          <p className="text-xs font-bold text-slate-700">Loading assessment workspace...</p>
        </div>
      </div>
    );
  }

  if (attemptError || !activeAttempt) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <div className="max-w-md w-full p-6 rounded-2xl bg-white border border-slate-200 shadow-sm text-center space-y-4">
          <AlertCircle size={32} className="text-rose-500 mx-auto" />
          <h2 className="text-base font-bold text-slate-900">No Active Attempt Found</h2>
          <p className="text-xs text-slate-500">
            You do not currently have an in-progress attempt for this assignment.
          </p>
          <Link to={`/student/classrooms/${encodeURIComponent(classId)}/assignments/${encodeURIComponent(assignmentId)}`}>
            <Button size="sm" className="gap-1.5 font-bold">
              <ArrowLeft size={14} /> Back to Overview
            </Button>
          </Link>
        </div>
      </div>
    );
  }

  const questions = activeAttempt.questions || assignment?.questions || [];

  const handleSubmitAttempt = async (attemptId: string) => {
    try {
      await submitMutation.mutateAsync(attemptId);
      navigate(
        `/student/classrooms/${encodeURIComponent(classId)}/assignments/${encodeURIComponent(assignmentId)}/attempts`
      );
    } catch (err) {
      alert(assignmentError(err));
      throw err;
    }
  };

  return (
    <AssessmentWorkspace
      classId={classId}
      assignmentId={assignmentId}
      assignmentTitle={assignment?.title || 'Assignment Assessment'}
      timeExpiredAction={assignment?.timeExpiredAction || 'AUTO_SUBMIT'}
      attempt={activeAttempt}
      questions={questions}
      onSubmitAttempt={handleSubmitAttempt}
      isSubmitting={submitMutation.isPending}
    />
  );
};
