import React, { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft,
  Plus,
  Settings,
  Send,
  Lock,
  Award,
  Users,
  AlertCircle,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import {
  useAssignmentDetail,
  useUpdateAssignment,
  usePublishAssignment,
  useCloseAssignment,
  usePublishGrades,
} from '../hooks/useAssignments';
import { useAssignmentQuestions } from '../hooks/useAssignmentQuestions';
import type { AddQuestionRequest, QuestionView, UpdateAssignmentRequest } from '../types';
import { AssignmentStatusBadge } from '../components/shared/AssignmentStatusBadge';
import { AssignmentTypeBadge } from '../components/shared/AssignmentTypeBadge';
import { DeadlineBadge } from '../components/shared/DeadlineBadge';
import { MarkdownRenderer } from '../components/shared/MarkdownRenderer';
import { QuestionCard } from '../components/teacher/QuestionCard';
import { QuestionAuthoringModal } from '../components/teacher/QuestionAuthoringModal';
import { AssignmentFormModal } from '../components/teacher/AssignmentFormModal';
import { assignmentError } from '../api/assignmentApi';

export const TeacherAssignmentDetailPage: React.FC = () => {
  const { id: classId = '', assignmentId = '' } = useParams<{ id: string; assignmentId: string }>();
  const { user } = useAuth();

  const { data: assignment, isLoading, error } = useAssignmentDetail(classId, assignmentId);

  const updateMutation = useUpdateAssignment(classId, assignmentId);
  const publishMutation = usePublishAssignment(classId, assignmentId);
  const closeMutation = useCloseAssignment(classId, assignmentId);
  const publishGradesMutation = usePublishGrades(classId, assignmentId);

  const { addQuestion, updateQuestion, deleteQuestion, reorderQuestions } =
    useAssignmentQuestions(classId, assignmentId);

  const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false);
  const [isQuestionModalOpen, setIsQuestionModalOpen] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState<QuestionView | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

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
        Failed to load assignment details. {error ? assignmentError(error) : ''}
      </div>
    );
  }

  const questions = assignment.questions || [];
  const totalPoints = questions.reduce((sum, q) => sum + (q.points || 0), 0);

  const handleSaveSettings = async (data: UpdateAssignmentRequest) => {
    setActionError(null);
    try {
      await updateMutation.mutateAsync(data);
    } catch (err) {
      setActionError(assignmentError(err));
      throw err;
    }
  };

  const handleSaveQuestion = async (data: AddQuestionRequest) => {
    setActionError(null);
    try {
      if (editingQuestion) {
        await updateQuestion.mutateAsync({
          questionId: editingQuestion.id,
          request: data,
        });
      } else {
        await addQuestion.mutateAsync(data);
      }
    } catch (err) {
      setActionError(assignmentError(err));
      throw err;
    }
  };

  const handleDeleteQuestion = async (questionId: string) => {
    if (window.confirm('Delete this question? If this assignment has published revisions, the question will be retired for future attempts.')) {
      setActionError(null);
      try {
        await deleteQuestion.mutateAsync(questionId);
      } catch (err) {
        setActionError(assignmentError(err));
      }
    }
  };

  const handleMoveQuestion = async (index: number, direction: 'UP' | 'DOWN') => {
    const targetIndex = direction === 'UP' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= questions.length) return;

    const newOrder = [...questions];
    const [moved] = newOrder.splice(index, 1);
    newOrder.splice(targetIndex, 0, moved);

    setActionError(null);
    try {
      await reorderQuestions.mutateAsync(newOrder.map((q) => q.id));
    } catch (err) {
      setActionError(assignmentError(err));
    }
  };

  const handlePublish = async () => {
    setActionError(null);
    try {
      await publishMutation.mutateAsync();
    } catch (err) {
      setActionError(assignmentError(err));
    }
  };

  const handleClose = async () => {
    if (window.confirm('Close this assignment? Students will no longer be able to start new attempts or submit.')) {
      setActionError(null);
      try {
        await closeMutation.mutateAsync();
      } catch (err) {
        setActionError(assignmentError(err));
      }
    }
  };

  const handlePublishGrades = async () => {
    if (window.confirm('Publish grades to students? Students will immediately be able to view their final scores.')) {
      setActionError(null);
      try {
        await publishGradesMutation.mutateAsync();
        alert('Grades published successfully.');
      } catch (err) {
        setActionError(assignmentError(err));
      }
    }
  };

  const layoutPersona = {
    id: user?.id || '',
    username: user?.username || '',
    fullName: user?.fullName || user?.username || 'Teacher',
    email: user?.email || '',
    role: 'TEACHER' as const,
  };

  return (
    <ClassroomLayout role="TEACHER" persona={layoutPersona} breadcrumbChild={assignment.title}>
      <div className="max-w-6xl mx-auto space-y-6">
        {/* Navigation & Actions Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <Link
            to={`/teacher/classrooms/${encodeURIComponent(classId)}/assignments`}
            className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-500 hover:text-slate-800"
          >
            <ArrowLeft size={14} /> Back to Assignments
          </Link>

          <div className="flex items-center gap-2 flex-wrap">
            <Link
              to={`/teacher/classrooms/${encodeURIComponent(classId)}/assignments/${encodeURIComponent(assignmentId)}/submissions`}
            >
              <Button variant="outline" size="sm" className="gap-1.5 h-8 text-xs font-bold">
                <Users size={14} /> Gradebook & Submissions
              </Button>
            </Link>

            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsSettingsModalOpen(true)}
              className="gap-1.5 h-8 text-xs font-bold"
            >
              <Settings size={14} /> Edit Settings
            </Button>

            {assignment.status === 'DRAFT' && (
              <Button
                size="sm"
                onClick={handlePublish}
                disabled={publishMutation.isPending}
                className="gap-1.5 h-8 text-xs font-bold bg-emerald-600 hover:bg-emerald-700 text-white"
              >
                <Send size={14} /> {publishMutation.isPending ? 'Publishing...' : 'Publish Assignment'}
              </Button>
            )}

            {assignment.status === 'PUBLISHED' && (
              <>
                <Button
                  size="sm"
                  onClick={handleClose}
                  disabled={closeMutation.isPending}
                  className="gap-1.5 h-8 text-xs font-bold bg-rose-600 hover:bg-rose-700 text-white"
                >
                  <Lock size={14} /> Close Assignment
                </Button>

                {!assignment.isGradePublished && (
                  <Button
                    size="sm"
                    onClick={handlePublishGrades}
                    disabled={publishGradesMutation.isPending}
                    className="gap-1.5 h-8 text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white"
                  >
                    <Award size={14} /> Publish Grades
                  </Button>
                )}
              </>
            )}
          </div>
        </div>

        {actionError && (
          <div className="p-3.5 bg-rose-50 border border-rose-200 text-rose-800 text-xs rounded-xl flex items-center gap-2">
            <AlertCircle size={16} className="shrink-0 text-rose-600" />
            <span>{actionError}</span>
          </div>
        )}

        {/* Assignment Metadata Overview Card */}
        <div className="p-6 rounded-3xl border border-slate-200 bg-white shadow-xs space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <AssignmentTypeBadge type={assignment.type} />
            <AssignmentStatusBadge
              status={assignment.status}
              startTime={assignment.startTime}
              deadline={assignment.deadline}
            />
            <DeadlineBadge deadline={assignment.deadline} allowLate={assignment.allowLateSubmission} />
          </div>

          <div>
            <h1 className="text-2xl font-black text-slate-900 tracking-tight">{assignment.title}</h1>
            {assignment.description && (
              <div className="mt-2 text-xs text-slate-600 leading-relaxed">
                <MarkdownRenderer content={assignment.description} />
              </div>
            )}
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-3 border-t border-slate-100 text-xs text-slate-600">
            <div>
              <span className="text-slate-400 block text-[10px] uppercase font-bold">Total Points</span>
              <strong className="text-slate-900 text-sm">{totalPoints} pts</strong>
            </div>
            <div>
              <span className="text-slate-400 block text-[10px] uppercase font-bold">Duration</span>
              <strong className="text-slate-900 text-sm">
                {assignment.durationMinutes ? `${assignment.durationMinutes} mins` : 'Untimed'}
              </strong>
            </div>
            <div>
              <span className="text-slate-400 block text-[10px] uppercase font-bold">Max Attempts</span>
              <strong className="text-slate-900 text-sm">
                {assignment.maxAttempts === 0 ? 'Unlimited' : assignment.maxAttempts}
              </strong>
            </div>
            <div>
              <span className="text-slate-400 block text-[10px] uppercase font-bold">Grade Release</span>
              <strong className="text-slate-900 text-sm">{assignment.gradePublishMode}</strong>
            </div>
          </div>
        </div>

        {/* Questions Section */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-bold text-slate-900">
                Assignment Questions ({questions.length})
              </h2>
              <p className="text-xs text-slate-500">
                Author code problems, multiple choice, and essay questions for this assignment.
              </p>
            </div>

            <Button
              size="sm"
              onClick={() => {
                setEditingQuestion(null);
                setIsQuestionModalOpen(true);
              }}
              className="gap-1.5 h-8 text-xs font-bold bg-slate-900 text-white"
            >
              <Plus size={14} /> Add Question
            </Button>
          </div>

          {questions.length === 0 ? (
            <div className="p-10 rounded-2xl border border-dashed border-slate-300 bg-white text-center space-y-2">
              <p className="text-sm font-semibold text-slate-800">No questions added yet</p>
              <p className="text-xs text-slate-500">
                Add code, multiple-choice, or essay questions to build this assessment.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {questions.map((q, idx) => (
                <QuestionCard
                  key={q.id || idx}
                  question={q}
                  index={idx}
                  total={questions.length}
                  onEdit={(quest) => {
                    setEditingQuestion(quest);
                    setIsQuestionModalOpen(true);
                  }}
                  onDelete={handleDeleteQuestion}
                  onMoveUp={() => handleMoveQuestion(idx, 'UP')}
                  onMoveDown={() => handleMoveQuestion(idx, 'DOWN')}
                />
              ))}
            </div>
          )}
        </div>

        {/* Settings Modal */}
        <AssignmentFormModal
          isOpen={isSettingsModalOpen}
          onClose={() => setIsSettingsModalOpen(false)}
          onSubmit={handleSaveSettings}
          initialData={assignment}
          isLoading={updateMutation.isPending}
        />

        {/* Question Modal */}
        <QuestionAuthoringModal
          isOpen={isQuestionModalOpen}
          onClose={() => {
            setIsQuestionModalOpen(false);
            setEditingQuestion(null);
          }}
          onSubmit={handleSaveQuestion}
          initialData={editingQuestion}
          isLoading={addQuestion.isPending || updateQuestion.isPending}
        />
      </div>
    </ClassroomLayout>
  );
};
