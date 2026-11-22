import React, { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Plus, FileText, Copy, Trash2, Edit2, Users } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import {
  useAssignmentList,
  useCreateAssignment,
  useDeleteAssignment,
  useCloneAssignment,
} from '../hooks/useAssignments';
import type { AssignmentStatus, CreateAssignmentRequest } from '../types';
import { AssignmentStatusBadge } from '../components/shared/AssignmentStatusBadge';
import { AssignmentTypeBadge } from '../components/shared/AssignmentTypeBadge';
import { DeadlineBadge } from '../components/shared/DeadlineBadge';
import { AssignmentFormModal } from '../components/teacher/AssignmentFormModal';
import { CloneAssignmentModal } from '../components/teacher/CloneAssignmentModal';
import { assignmentError } from '../api/assignmentApi';

export const TeacherAssignmentsPage: React.FC = () => {
  const { id: classId = '' } = useParams<{ id: string }>();
  const { user } = useAuth();

  const { data: assignments = [], isLoading } = useAssignmentList(classId, 'TEACHER');

  const createMutation = useCreateAssignment(classId);
  const deleteMutation = useDeleteAssignment(classId);
  const cloneMutation = useCloneAssignment(classId);

  const [filter, setFilter] = useState<'ALL' | AssignmentStatus>('ALL');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [cloneTargetAssignmentId, setCloneTargetAssignmentId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const filteredAssignments = assignments.filter((a) => {
    if (filter === 'ALL') return true;
    return a.status === filter;
  });

  const handleCreateAssignment = async (data: CreateAssignmentRequest) => {
    setActionError(null);
    try {
      await createMutation.mutateAsync(data);
    } catch (err) {
      setActionError(assignmentError(err));
      throw err;
    }
  };

  const handleDelete = async (assignmentId: string) => {
    if (window.confirm('Delete this assignment? This cannot be undone if there are no student attempts.')) {
      setActionError(null);
      try {
        await deleteMutation.mutateAsync(assignmentId);
      } catch (err) {
        setActionError(assignmentError(err));
      }
    }
  };

  const handleClone = async (targetClassroomId: string) => {
    if (!cloneTargetAssignmentId) return;
    setActionError(null);
    try {
      await cloneMutation.mutateAsync({
        assignmentId: cloneTargetAssignmentId,
        targetClassroomId,
      });
      alert('Assignment cloned successfully as draft.');
    } catch (err) {
      setActionError(assignmentError(err));
      throw err;
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
    <ClassroomLayout role="TEACHER" persona={layoutPersona} breadcrumbChild="Assignments">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Top Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
          <div>
            <h1 className="text-xl font-bold text-slate-900 tracking-tight">Classroom Assignments</h1>
            <p className="text-xs text-slate-500 mt-1">
              Create and manage problem sets, quizzes, code assessments, and student submissions.
            </p>
          </div>

          <Button
            size="sm"
            onClick={() => setIsCreateModalOpen(true)}
            className="font-bold gap-1.5 bg-slate-900 hover:bg-slate-800 text-white"
          >
            <Plus size={16} /> Create Assignment
          </Button>
        </div>

        {actionError && (
          <div className="p-3 bg-rose-50 border border-rose-200 text-rose-800 text-xs rounded-xl">
            {actionError}
          </div>
        )}

        {/* Filter Bar */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1">
          {(['ALL', 'PUBLISHED', 'SCHEDULED', 'DRAFT', 'CLOSED'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => setFilter(tab)}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-colors ${
                filter === tab
                  ? 'bg-slate-900 text-white shadow-xs'
                  : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
            >
              {tab === 'ALL' ? 'All Assignments' : tab.charAt(0) + tab.slice(1).toLowerCase()}
            </button>
          ))}
        </div>

        {/* Assignments List */}
        {isLoading ? (
          <div className="p-12 text-center text-xs text-slate-500">Loading assignments...</div>
        ) : filteredAssignments.length === 0 ? (
          <div className="p-12 rounded-3xl border border-dashed border-slate-300 bg-white text-center space-y-3">
            <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center mx-auto text-slate-400">
              <FileText size={24} />
            </div>
            <div>
              <h3 className="text-sm font-bold text-slate-900">No assignments found</h3>
              <p className="text-xs text-slate-500 mt-1">
                Get started by creating your first classroom assignment.
              </p>
            </div>
            <Button size="sm" onClick={() => setIsCreateModalOpen(true)} className="gap-1.5 mt-2">
              <Plus size={14} /> Create Assignment
            </Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {filteredAssignments.map((assignment) => (
              <div
                key={assignment.id}
                className="p-5 rounded-2xl border border-slate-200 bg-white hover:border-slate-300 transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-xs"
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

                  <Link
                    to={`/teacher/classrooms/${encodeURIComponent(classId)}/assignments/${encodeURIComponent(assignment.id)}`}
                    className="text-base font-bold text-slate-900 hover:text-blue-600 hover:underline block truncate"
                  >
                    {assignment.title}
                  </Link>

                  <div className="flex items-center gap-4 text-xs text-slate-500">
                    <span>
                      {assignment.durationMinutes ? `${assignment.durationMinutes} mins` : 'Untimed'}
                    </span>
                    <span>•</span>
                    <span>
                      {assignment.maxAttempts === 0
                        ? 'Unlimited attempts'
                        : `${assignment.maxAttempts} max attempts`}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <Link
                    to={`/teacher/classrooms/${encodeURIComponent(classId)}/assignments/${encodeURIComponent(assignment.id)}/submissions`}
                  >
                    <Button variant="outline" size="sm" className="gap-1 text-xs h-8">
                      <Users size={13} /> Submissions
                    </Button>
                  </Link>

                  <Link
                    to={`/teacher/classrooms/${encodeURIComponent(classId)}/assignments/${encodeURIComponent(assignment.id)}`}
                  >
                    <Button variant="outline" size="sm" className="gap-1 text-xs h-8">
                      <Edit2 size={13} /> Edit
                    </Button>
                  </Link>

                  <button
                    type="button"
                    onClick={() => setCloneTargetAssignmentId(assignment.id)}
                    className="p-2 text-slate-400 hover:text-slate-700 rounded-lg hover:bg-slate-100"
                    title="Clone to another classroom"
                    aria-label="Clone assignment"
                  >
                    <Copy size={15} />
                  </button>

                  <button
                    type="button"
                    onClick={() => handleDelete(assignment.id)}
                    className="p-2 text-slate-400 hover:text-rose-600 rounded-lg hover:bg-rose-50"
                    title="Delete assignment"
                    aria-label="Delete assignment"
                  >
                    <Trash2 size={15} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Creation Modal */}
        <AssignmentFormModal
          isOpen={isCreateModalOpen}
          onClose={() => setIsCreateModalOpen(false)}
          onSubmit={handleCreateAssignment}
          isLoading={createMutation.isPending}
        />

        {/* Clone Modal */}
        <CloneAssignmentModal
          isOpen={Boolean(cloneTargetAssignmentId)}
          onClose={() => setCloneTargetAssignmentId(null)}
          onClone={handleClone}
          isLoading={cloneMutation.isPending}
        />
      </div>
    </ClassroomLayout>
  );
};
