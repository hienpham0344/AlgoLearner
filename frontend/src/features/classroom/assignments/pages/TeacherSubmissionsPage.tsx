import React, { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft,
  RefreshCw,
  AlertTriangle,
  Search,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import { useAssignmentDetail } from '../hooks/useAssignments';
import {
  useTeacherSubmissions,
  useGradeEssay,
  useOverrideGrade,
  useRejudgeAssignment,
  useGrantAttempts,
} from '../hooks/useTeacherSubmissions';
import type {
  AttemptGrantRequest,
  GradeEssayRequest,
  OverrideGradeRequest,
  RejudgeRequest,
} from '../types';
import { formatVietnamDateTime } from '../utils/timeUtils';
import { ScoreDisplay } from '../components/shared/ScoreDisplay';
import { GradeEssayModal } from '../components/teacher/GradeEssayModal';
import { GradeOverrideModal } from '../components/teacher/GradeOverrideModal';
import { GrantAttemptsModal } from '../components/teacher/GrantAttemptsModal';
import { RejudgeModal } from '../components/teacher/RejudgeModal';
import { assignmentError } from '../api/assignmentApi';

export const TeacherSubmissionsPage: React.FC = () => {
  const { id: classId = '', assignmentId = '' } = useParams<{ id: string; assignmentId: string }>();
  const { user } = useAuth();

  const { data: assignment } = useAssignmentDetail(classId, assignmentId);
  const { data: submissions = [], isLoading } = useTeacherSubmissions(classId, assignmentId);

  const gradeEssayMutation = useGradeEssay(classId, assignmentId);
  const overrideGradeMutation = useOverrideGrade(classId, assignmentId);
  const rejudgeMutation = useRejudgeAssignment(classId, assignmentId);
  const grantAttemptsMutation = useGrantAttempts(classId, assignmentId);

  const [searchQuery, setSearchQuery] = useState('');
  const [isRejudgeModalOpen, setIsRejudgeModalOpen] = useState(false);

  // Modal active targets
  const [selectedEssayTarget, setSelectedEssayTarget] = useState<{
    attemptId: string;
    questionId: string;
    questionTitle: string;
    maxPoints: number;
    currentScore?: number | null;
    currentFeedback?: string | null;
  } | null>(null);

  const [selectedOverrideTarget, setSelectedOverrideTarget] = useState<{
    attemptId: string;
    studentName: string;
    currentScore?: number | null;
  } | null>(null);

  const [selectedGrantTarget, setSelectedGrantTarget] = useState<{
    studentId: string;
    studentName: string;
  } | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);

  const filteredSubmissions = submissions.filter((sub) => {
    if (!searchQuery.trim()) return true;
    const name = sub.studentName || 'Student';
    return name.toLowerCase().includes(searchQuery.toLowerCase());
  });

  const handleGradeEssay = async (req: GradeEssayRequest) => {
    if (!selectedEssayTarget) return;
    setActionError(null);
    try {
      await gradeEssayMutation.mutateAsync({
        attemptId: selectedEssayTarget.attemptId,
        request: req,
      });
      alert('Essay graded successfully.');
    } catch (err) {
      setActionError(assignmentError(err));
      throw err;
    }
  };

  const handleOverrideScore = async (req: OverrideGradeRequest) => {
    if (!selectedOverrideTarget) return;
    setActionError(null);
    try {
      await overrideGradeMutation.mutateAsync({
        attemptId: selectedOverrideTarget.attemptId,
        request: req,
      });
      alert('Final score overridden successfully.');
    } catch (err) {
      setActionError(assignmentError(err));
      throw err;
    }
  };

  const handleGrantAttempts = async (req: AttemptGrantRequest) => {
    if (!selectedGrantTarget) return;
    setActionError(null);
    try {
      await grantAttemptsMutation.mutateAsync({
        studentId: selectedGrantTarget.studentId,
        request: req,
      });
      alert('Attempt allowance updated.');
    } catch (err) {
      setActionError(assignmentError(err));
      throw err;
    }
  };

  const handleRejudge = async (req: RejudgeRequest) => {
    setActionError(null);
    try {
      await rejudgeMutation.mutateAsync(req);
      alert('Rejudge job started in background.');
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
    <ClassroomLayout
      role="TEACHER"
      persona={layoutPersona}
      breadcrumbChild={assignment ? `${assignment.title} — Submissions` : 'Submissions'}
    >
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Navigation & Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <Link
              to={`/teacher/classrooms/${encodeURIComponent(classId)}/assignments/${encodeURIComponent(assignmentId)}`}
              className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-500 hover:text-slate-800 mb-2"
            >
              <ArrowLeft size={14} /> Back to Assignment
            </Link>
            <h1 className="text-xl font-bold text-slate-900 tracking-tight">
              Submissions & Gradebook
            </h1>
            <p className="text-xs text-slate-500 mt-0.5">
              Review student attempts, grade essay questions, override scores, and trigger rejudge.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsRejudgeModalOpen(true)}
              className="gap-1.5 h-8 text-xs font-bold"
            >
              <RefreshCw size={14} /> Rejudge Code Submissions
            </Button>
          </div>
        </div>

        {actionError && (
          <div className="p-3.5 bg-rose-50 border border-rose-200 text-rose-800 text-xs rounded-xl flex items-center gap-2">
            <AlertTriangle size={16} className="shrink-0 text-rose-600" />
            <span>{actionError}</span>
          </div>
        )}

        {/* Search Bar */}
        <div className="flex items-center justify-between gap-4">
          <div className="relative max-w-sm w-full">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search by student name..."
              className="w-full pl-9 pr-4 py-2 rounded-xl border border-slate-300 text-xs focus:outline-hidden focus:ring-2 focus:ring-slate-900 bg-white"
            />
          </div>

          <span className="text-xs font-bold text-slate-500">
            Total submissions: {submissions.length}
          </span>
        </div>

        {/* Submissions Table */}
        <div className="rounded-2xl border border-slate-200 bg-white overflow-hidden shadow-xs">
          {isLoading ? (
            <div className="p-12 text-center text-xs text-slate-500">Loading student submissions...</div>
          ) : filteredSubmissions.length === 0 ? (
            <div className="p-12 text-center text-xs text-slate-500">
              No submissions found for this assignment.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="bg-slate-50/80 border-b border-slate-200 text-slate-600 font-bold uppercase tracking-wider text-[10px]">
                    <th className="py-3.5 px-4">Student</th>
                    <th className="py-3.5 px-4">Attempt</th>
                    <th className="py-3.5 px-4">Submitted At</th>
                    <th className="py-3.5 px-4">Status</th>
                    <th className="py-3.5 px-4">Auto Score</th>
                    <th className="py-3.5 px-4">Essay Score</th>
                    <th className="py-3.5 px-4">Final Score</th>
                    <th className="py-3.5 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredSubmissions.map((sub) => {
                    const studentDisplay = sub.studentName || 'Student';

                    return (
                      <tr key={sub.id} className="hover:bg-slate-50/50 transition-colors">
                        <td className="py-3.5 px-4 font-bold text-slate-900">{studentDisplay}</td>
                        <td className="py-3.5 px-4 text-slate-600">#{sub.attemptNumber}</td>
                        <td className="py-3.5 px-4 text-slate-600">
                          {formatVietnamDateTime(sub.submittedAt)}
                        </td>
                        <td className="py-3.5 px-4">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <span className="px-2 py-0.5 rounded font-semibold text-[10px] bg-emerald-50 text-emerald-800 border border-emerald-200">
                              {sub.status}
                            </span>
                            {sub.isLate && (
                              <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-50 text-amber-800 border border-amber-200">
                                Late
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="py-3.5 px-4">
                          <ScoreDisplay score={sub.autoScore} />
                        </td>
                        <td className="py-3.5 px-4">
                          <ScoreDisplay score={sub.manualScore} />
                        </td>
                        <td className="py-3.5 px-4 font-bold">
                          <ScoreDisplay
                            score={sub.finalScore}
                            isOverridden={sub.overriddenScore !== null && sub.overriddenScore !== undefined}
                          />
                        </td>
                        <td className="py-3.5 px-4 text-right">
                          <div className="flex items-center justify-end gap-1.5">
                            {/* Check if attempt has essay questions */}
                            {sub.answers?.some((a) => a.textAnswer || a.fileId) && (
                              <button
                                type="button"
                                onClick={() => {
                                  const essayAns = sub.answers.find((a) => a.textAnswer || a.fileId);
                                  if (essayAns) {
                                    setSelectedEssayTarget({
                                      attemptId: sub.id,
                                      questionId: essayAns.questionId,
                                      questionTitle: 'Essay Question',
                                      maxPoints: 20,
                                      currentScore: essayAns.score,
                                      currentFeedback: essayAns.feedback,
                                    });
                                  }
                                }}
                                className="px-2.5 py-1 rounded-lg border border-slate-200 text-slate-700 hover:bg-slate-100 font-semibold"
                                title="Grade Essay"
                              >
                                Grade Essay
                              </button>
                            )}

                            <button
                              type="button"
                              onClick={() =>
                                setSelectedOverrideTarget({
                                  attemptId: sub.id,
                                  studentName: studentDisplay,
                                  currentScore: sub.finalScore,
                                })
                              }
                              className="px-2.5 py-1 rounded-lg border border-slate-200 text-slate-700 hover:bg-slate-100 font-semibold"
                              title="Override Score"
                            >
                              Override
                            </button>

                            <button
                              type="button"
                              onClick={() =>
                                setSelectedGrantTarget({
                                  studentId: sub.studentId,
                                  studentName: studentDisplay,
                                })
                              }
                              className="px-2.5 py-1 rounded-lg border border-slate-200 text-slate-700 hover:bg-slate-100 font-semibold"
                              title="Grant Attempts"
                            >
                              Grant +1
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Grade Essay Modal */}
        {selectedEssayTarget && (
          <GradeEssayModal
            isOpen={Boolean(selectedEssayTarget)}
            onClose={() => setSelectedEssayTarget(null)}
            onGrade={handleGradeEssay}
            questionTitle={selectedEssayTarget.questionTitle}
            questionId={selectedEssayTarget.questionId}
            maxPoints={selectedEssayTarget.maxPoints}
            currentScore={selectedEssayTarget.currentScore}
            currentFeedback={selectedEssayTarget.currentFeedback}
            isLoading={gradeEssayMutation.isPending}
          />
        )}

        {/* Grade Override Modal */}
        {selectedOverrideTarget && (
          <GradeOverrideModal
            isOpen={Boolean(selectedOverrideTarget)}
            onClose={() => setSelectedOverrideTarget(null)}
            onOverride={handleOverrideScore}
            studentName={selectedOverrideTarget.studentName}
            currentScore={selectedOverrideTarget.currentScore}
            isLoading={overrideGradeMutation.isPending}
          />
        )}

        {/* Grant Attempts Modal */}
        {selectedGrantTarget && (
          <GrantAttemptsModal
            isOpen={Boolean(selectedGrantTarget)}
            onClose={() => setSelectedGrantTarget(null)}
            onGrant={handleGrantAttempts}
            studentName={selectedGrantTarget.studentName}
            isLoading={grantAttemptsMutation.isPending}
          />
        )}

        {/* Rejudge Modal */}
        <RejudgeModal
          isOpen={isRejudgeModalOpen}
          onClose={() => setIsRejudgeModalOpen(false)}
          onRejudge={handleRejudge}
          assignmentTitle={assignment?.title || 'Assignment'}
          isLoading={rejudgeMutation.isPending}
        />
      </div>
    </ClassroomLayout>
  );
};
