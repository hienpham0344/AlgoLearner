import React, { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Plus,
  Settings,
  Send,
  Lock,
  Copy,
  BookOpen,
  Award,
  Clock,
  Calendar,
  Layers,
  AlertCircle,
  CheckCircle2,
  Users,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import {
  useQuizDetail,
  useQuizAuthoring,
  useUpdateQuiz,
  usePublishQuiz,
  useCloseQuiz,
  useDuplicateQuiz,
  usePublishGrades,
  useAdjustAttempts,
  useGradeOverride,
  useGradeHistory,
} from '../hooks/useQuizzes';
import type {
  AdjustAttemptsRequest,
  GradeOverrideRequest,
  Question,
  QuizConfigureRequest,
} from '../types';
import { QuizStatusBadge } from '../components/shared/QuizStatusBadge';
import { QuizQuestionCard } from '../components/teacher/QuizQuestionCard';
import { QuizQuestionModal } from '../components/teacher/QuizQuestionModal';
import { QuizFormModal } from '../components/teacher/QuizFormModal';
import { QuestionBankImportModal } from '../components/teacher/QuestionBankImportModal';
import { AdjustAttemptsModal } from '../components/teacher/AdjustAttemptsModal';
import { GradeOverrideModal } from '../components/teacher/GradeOverrideModal';
import { formatDurationMinutes, formatVietnamDateTime } from '../utils/quizTimeUtils';
import { quizApi, quizError } from '../api/quizApi';
import { useQuery } from '@tanstack/react-query';
import { quizKeys } from '../api/quizKeys';

export const TeacherQuizDetailPage: React.FC = () => {
  const { id: classId = '', quizId = '' } = useParams<{ id: string; quizId: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();

  const isTeacher = user?.role === 'TEACHER' || user?.role === 'ADMIN';

  const { data: quiz, isLoading: isQuizLoading, error: quizFetchError } = useQuizDetail(classId, quizId, isTeacher);
  const { data: authoring, isLoading: isAuthoringLoading, error: authoringError } = useQuizAuthoring(
    classId,
    quizId,
    isTeacher
  );

  const updateMutation = useUpdateQuiz(classId, quizId);
  const publishMutation = usePublishQuiz(classId, quizId);
  const closeMutation = useCloseQuiz(classId, quizId);
  const duplicateMutation = useDuplicateQuiz(classId);
  const publishGradesMutation = usePublishGrades(classId, quizId);
  const adjustAttemptsMutation = useAdjustAttempts(classId, quizId);

  // Classroom quizzes for question bank import source
  const { data: allQuizzes = [] } = useQuery({
    queryKey: quizKeys.list(classId),
    queryFn: () => quizApi.list(classId),
    enabled: Boolean(classId) && isTeacher,
  });

  // Modal states
  const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false);
  const [isQuestionModalOpen, setIsQuestionModalOpen] = useState(false);
  const [isBankModalOpen, setIsBankModalOpen] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState<Question | null>(null);

  // Student attempts & override states
  const [activeTab, setActiveTab] = useState<'QUESTIONS' | 'EVALUATIONS'>('QUESTIONS');
  const [selectedStudentForAdjust, setSelectedStudentForAdjust] = useState<{ id: string; name: string } | null>(null);
  const [selectedStudentForOverride, setSelectedStudentForOverride] = useState<{ id: string; name: string; score?: number | null } | null>(null);

  // Audit history for override modal
  const { data: studentGradeHistory = [], isLoading: isLoadingHistory } = useGradeHistory(
    classId,
    quizId,
    selectedStudentForOverride?.id || '',
    Boolean(selectedStudentForOverride?.id)
  );

  // Classroom attempts query
  const { data: attemptsPage, isLoading: isAttemptsLoading } = useQuery({
    queryKey: quizKeys.attemptHistory(classId, quizId),
    queryFn: () => quizApi.history(classId, quizId),
    enabled: Boolean(classId && quizId) && activeTab === 'EVALUATIONS',
  });

  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  if (!isTeacher) {
    return (
      <div className="p-12 text-center">
        <h2 className="text-base font-bold text-slate-900 mb-2">Access Denied</h2>
        <p className="text-xs text-slate-500 mb-4">
          Only classroom teachers and administrators can author or manage quizzes.
        </p>
        <Link to={`/student/classrooms/${encodeURIComponent(classId)}`}>
          <Button size="sm">Return to Classroom</Button>
        </Link>
      </div>
    );
  }

  if (isQuizLoading || isAuthoringLoading) {
    return (
      <div className="p-12 text-center text-xs text-slate-500">
        Loading quiz configuration and questions...
      </div>
    );
  }

  if (quizFetchError || authoringError || !quiz || !authoring) {
    return (
      <div className="max-w-4xl mx-auto p-8 text-center">
        <div className="p-4 rounded-2xl bg-rose-50 border border-rose-200 text-rose-700 text-xs mb-4">
          {quizError(quizFetchError || authoringError || new Error('Quiz not found'))}
        </div>
        <Link to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes`}>
          <Button variant="outline" size="sm">
            <ArrowLeft size={14} className="mr-1.5" />
            Back to Quizzes
          </Button>
        </Link>
      </div>
    );
  }

  const isDraft = quiz.status === 'DRAFT';
  const isPublished = quiz.status === 'PUBLISHED';
  const questions = authoring.questions || [];
  const totalPoints = questions.reduce((sum, q) => sum + (q.points || 0), 0);

  // Question handlers
  const handleSaveQuestion = async (savedQ: Question) => {
    if (!isDraft) return;
    setActionError(null);
    try {
      const existingIdx = questions.findIndex((q) => q.id === savedQ.id);
      let updatedQuestions: Question[];
      if (existingIdx >= 0) {
        updatedQuestions = [...questions];
        updatedQuestions[existingIdx] = savedQ;
      } else {
        updatedQuestions = [...questions, savedQ];
      }

      await updateMutation.mutateAsync({
        ...authoring,
        questions: updatedQuestions,
      });
      setActionSuccess('Question saved successfully.');
    } catch (err) {
      setActionError(quizError(err));
    }
  };

  const handleDeleteQuestion = async (questionId: string) => {
    if (!isDraft) return;
    if (questions.length <= 1) {
      setActionError('Quiz must contain at least one question.');
      return;
    }
    setActionError(null);
    try {
      const updatedQuestions = questions.filter((q) => q.id !== questionId);
      await updateMutation.mutateAsync({
        ...authoring,
        questions: updatedQuestions,
      });
      setActionSuccess('Question removed.');
    } catch (err) {
      setActionError(quizError(err));
    }
  };

  const handleMoveQuestion = async (fromIndex: number, direction: 'UP' | 'DOWN') => {
    if (!isDraft) return;
    const toIndex = direction === 'UP' ? fromIndex - 1 : fromIndex + 1;
    if (toIndex < 0 || toIndex >= questions.length) return;

    const reordered = [...questions];
    const [moved] = reordered.splice(fromIndex, 1);
    reordered.splice(toIndex, 0, moved);

    setActionError(null);
    try {
      await updateMutation.mutateAsync({
        ...authoring,
        questions: reordered,
      });
    } catch (err) {
      setActionError(quizError(err));
    }
  };

  const handleImportQuestions = async (imported: Question[]) => {
    if (!isDraft || imported.length === 0) return;
    setActionError(null);
    try {
      const updatedQuestions = [...questions, ...imported];
      await updateMutation.mutateAsync({
        ...authoring,
        questions: updatedQuestions,
      });
      setActionSuccess(`Imported ${imported.length} question(s) successfully.`);
    } catch (err) {
      setActionError(quizError(err));
    }
  };

  const handleSaveSettings = async (data: QuizConfigureRequest) => {
    setActionError(null);
    try {
      await updateMutation.mutateAsync(data);
      setIsSettingsModalOpen(false);
      setActionSuccess('Quiz settings updated successfully.');
    } catch (err) {
      setActionError(quizError(err));
      throw err;
    }
  };

  const handlePublish = async () => {
    if (questions.length === 0) {
      setActionError('Cannot publish quiz with zero questions.');
      return;
    }
    if (window.confirm('Publish this quiz? Once published, questions cannot be changed to ensure fairness and attempt integrity.')) {
      setActionError(null);
      try {
        await publishMutation.mutateAsync();
        setActionSuccess('Quiz has been published successfully.');
      } catch (err) {
        setActionError(quizError(err));
      }
    }
  };

  const handleClose = async () => {
    if (window.confirm('Close this quiz? No new attempts will be allowed, but all student attempts and results remain preserved.')) {
      setActionError(null);
      try {
        await closeMutation.mutateAsync();
        setActionSuccess('Quiz has been closed to new attempts.');
      } catch (err) {
        setActionError(quizError(err));
      }
    }
  };

  const handleDuplicate = async () => {
    setActionError(null);
    try {
      const copy = await duplicateMutation.mutateAsync(quizId);
      setActionSuccess('Quiz duplicated successfully as draft.');
      navigate(`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(copy.id)}`);
    } catch (err) {
      setActionError(quizError(err));
    }
  };

  const handlePublishGrades = async () => {
    setActionError(null);
    try {
      await publishGradesMutation.mutateAsync();
      setActionSuccess('Official grades have been released to students.');
    } catch (err) {
      setActionError(quizError(err));
    }
  };

  const handleAdjustAttemptsSubmit = async (data: AdjustAttemptsRequest) => {
    setActionError(null);
    try {
      await adjustAttemptsMutation.mutateAsync(data);
      setActionSuccess('Student attempt allowance updated.');
    } catch (err) {
      setActionError(quizError(err));
    }
  };

  const overrideMutation = useGradeOverride(
    classId,
    quizId,
    selectedStudentForOverride?.id || ''
  );

  const handleGradeOverrideSubmit = async (data: GradeOverrideRequest) => {
    setActionError(null);
    try {
      await overrideMutation.mutateAsync(data);
      setActionSuccess('Student grade overridden successfully with audit record.');
    } catch (err) {
      setActionError(quizError(err));
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
      breadcrumbChild={
        <div className="flex items-center gap-1.5 truncate">
          <Link to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes`} className="hover:underline text-slate-500">
            Quizzes
          </Link>
          <span className="text-slate-400">/</span>
          <span className="truncate">{quiz.title}</span>
        </div>
      }
    >
      <div className="max-w-6xl mx-auto space-y-6">
        {/* Navigation & Status Header */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-4 border-b border-slate-200">
          <div className="space-y-1.5">
            <Link
              to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes`}
              className="inline-flex items-center gap-1 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors"
            >
              <ArrowLeft size={13} />
              Back to Quiz List
            </Link>

            <div className="flex items-center gap-3 flex-wrap">
              <h1 className="text-xl font-black text-slate-900 tracking-tight">{quiz.title}</h1>
              <QuizStatusBadge status={quiz.status} />
              <span
                className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                  quiz.graded
                    ? 'bg-indigo-50 text-indigo-700 border border-indigo-200'
                    : 'bg-slate-100 text-slate-600'
                }`}
              >
                {quiz.graded ? 'Graded' : 'Ungraded'}
              </span>
            </div>

            <div className="flex items-center gap-4 text-xs text-slate-500 flex-wrap">
              <span className="flex items-center gap-1">
                <Clock size={13} className="text-slate-400" />
                Duration: {formatDurationMinutes(quiz.durationSeconds)}
              </span>
              <span className="flex items-center gap-1">
                <Layers size={13} className="text-slate-400" />
                Max: {quiz.maxAttempts} attempts
              </span>
              {quiz.opensAt && (
                <span className="flex items-center gap-1">
                  <Calendar size={13} className="text-slate-400" />
                  Opens: {formatVietnamDateTime(quiz.opensAt)}
                </span>
              )}
              {quiz.closesAt && (
                <span className="flex items-center gap-1">
                  <Calendar size={13} className="text-slate-400" />
                  Closes: {formatVietnamDateTime(quiz.closesAt)}
                </span>
              )}
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex items-center gap-2 flex-wrap">
            {isDraft && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setIsSettingsModalOpen(true)}
                  className="h-8 text-xs text-slate-700"
                >
                  <Settings size={13} className="mr-1" />
                  Configure
                </Button>
                <Button
                  size="sm"
                  onClick={handlePublish}
                  disabled={publishMutation.isPending || questions.length === 0}
                  className="h-8 text-xs bg-[#d64527] hover:bg-[#be3b20] text-white disabled:opacity-50"
                >
                  <Send size={13} className="mr-1" />
                  {publishMutation.isPending ? 'Publishing...' : 'Publish Quiz'}
                </Button>
              </>
            )}

            {isPublished && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleClose}
                  disabled={closeMutation.isPending}
                  className="h-8 text-xs text-rose-600 border-rose-200 hover:bg-rose-50"
                >
                  <Lock size={13} className="mr-1" />
                  {closeMutation.isPending ? 'Closing...' : 'Close Quiz'}
                </Button>
                {quiz.releasePolicy === 'MANUAL' && (
                  <Button
                    size="sm"
                    onClick={handlePublishGrades}
                    disabled={publishGradesMutation.isPending}
                    className="h-8 text-xs bg-indigo-600 hover:bg-indigo-700 text-white"
                  >
                    <Award size={13} className="mr-1" />
                    Publish Grades
                  </Button>
                )}
              </>
            )}

            <Button
              variant="outline"
              size="sm"
              onClick={handleDuplicate}
              className="h-8 text-xs text-slate-600"
              title="Duplicate into a new draft"
            >
              <Copy size={13} className="mr-1" />
              Duplicate
            </Button>
          </div>
        </div>

        {/* Action feedback banners */}
        {actionError && (
          <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <AlertCircle size={16} className="shrink-0" />
              <span>{actionError}</span>
            </div>
            <button
              type="button"
              onClick={() => setActionError(null)}
              className="text-rose-600 hover:text-rose-800 font-bold"
            >
              Dismiss
            </button>
          </div>
        )}

        {actionSuccess && (
          <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <CheckCircle2 size={16} className="shrink-0" />
              <span>{actionSuccess}</span>
            </div>
            <button
              type="button"
              onClick={() => setActionSuccess(null)}
              className="text-emerald-600 hover:text-emerald-800 font-bold"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* View Switcher: Questions vs Evaluations */}
        <div className="flex border-b border-slate-200">
          <button
            type="button"
            onClick={() => setActiveTab('QUESTIONS')}
            className={`py-3 px-5 text-xs font-bold border-b-2 transition-colors flex items-center gap-1.5 ${
              activeTab === 'QUESTIONS'
                ? 'border-[#d64527] text-[#d64527]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <BookOpen size={14} />
            Question Authoring ({questions.length} questions · {totalPoints} pts)
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('EVALUATIONS')}
            className={`py-3 px-5 text-xs font-bold border-b-2 transition-colors flex items-center gap-1.5 ${
              activeTab === 'EVALUATIONS'
                ? 'border-[#d64527] text-[#d64527]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Users size={14} />
            Student Attempts &amp; Grade Overrides
          </button>
        </div>

        {/* Questions Tab */}
        {activeTab === 'QUESTIONS' && (
          <div className="space-y-4">
            {!isDraft && (
              <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 text-slate-700 text-xs flex items-center gap-2">
                <Lock size={15} className="text-slate-400 shrink-0" />
                <span>
                  Questions are locked in read-only mode because this quiz is{' '}
                  <strong className="font-semibold">{quiz.status}</strong>. This ensures fairness and preserves past
                  student attempt snapshots.
                </span>
              </div>
            )}

            {/* Authoring Toolbar */}
            {isDraft && (
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white p-3 rounded-xl border border-slate-200">
                <div className="text-xs text-slate-600">
                  Total Questions: <strong className="font-bold text-slate-900">{questions.length}</strong> · Total Points:{' '}
                  <strong className="font-bold text-emerald-700">{totalPoints} pts</strong>
                </div>

                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setIsBankModalOpen(true)}
                    className="h-8 text-xs text-[#d64527] border-[#d64527]/30 hover:bg-[#d64527]/10"
                  >
                    <BookOpen size={13} className="mr-1.5" />
                    Question Bank / Import
                  </Button>
                  <Button
                    size="sm"
                    onClick={() => {
                      setEditingQuestion(null);
                      setIsQuestionModalOpen(true);
                    }}
                    className="h-8 text-xs bg-[#d64527] hover:bg-[#be3b20] text-white"
                  >
                    <Plus size={14} className="mr-1" />
                    Add Question
                  </Button>
                </div>
              </div>
            )}

            {/* Questions List */}
            {questions.length === 0 ? (
              <div className="p-12 text-center bg-white border border-slate-200 rounded-2xl">
                <p className="text-xs text-slate-500 mb-3">No questions added to this quiz yet.</p>
                {isDraft && (
                  <Button
                    size="sm"
                    onClick={() => {
                      setEditingQuestion(null);
                      setIsQuestionModalOpen(true);
                    }}
                    className="bg-[#d64527] hover:bg-[#be3b20] text-white"
                  >
                    <Plus size={14} className="mr-1" />
                    Add First Question
                  </Button>
                )}
              </div>
            ) : (
              <div className="space-y-3">
                {questions.map((q, idx) => (
                  <QuizQuestionCard
                    key={q.id}
                    question={q}
                    index={idx}
                    totalQuestions={questions.length}
                    readOnly={!isDraft}
                    onEdit={(target) => {
                      setEditingQuestion(target);
                      setIsQuestionModalOpen(true);
                    }}
                    onDelete={handleDeleteQuestion}
                    onMoveUp={(i) => handleMoveQuestion(i, 'UP')}
                    onMoveDown={(i) => handleMoveQuestion(i, 'DOWN')}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {/* Evaluations Tab */}
        {activeTab === 'EVALUATIONS' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-sm font-bold text-slate-900">Student Attempts &amp; Submissions</h2>
                <p className="text-xs text-slate-500">
                  Review student completion, grant extra attempts or override scores with full audit traceability.
                </p>
              </div>
            </div>

            {isAttemptsLoading ? (
              <div className="p-12 text-center text-xs text-slate-500">Loading attempt records...</div>
            ) : !attemptsPage || !attemptsPage.items || attemptsPage.items.length === 0 ? (
              <div className="p-12 text-center bg-white border border-slate-200 rounded-2xl text-xs text-slate-500">
                No student attempts recorded for this quiz yet.
              </div>
            ) : (
              <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-xs">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 border-b border-slate-200 text-slate-600 font-semibold">
                    <tr>
                      <th className="py-2.5 px-4">Attempt ID</th>
                      <th className="py-2.5 px-4">Status</th>
                      <th className="py-2.5 px-4">Score</th>
                      <th className="py-2.5 px-4">Started (UTC+7)</th>
                      <th className="py-2.5 px-4">Submitted (UTC+7)</th>
                      <th className="py-2.5 px-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {attemptsPage.items.map((attempt) => (
                      <tr key={attempt.id} className="hover:bg-slate-50/50">
                        <td className="py-2.5 px-4 font-mono text-[11px] text-slate-600 truncate max-w-[120px]">
                          {attempt.id.slice(0, 8)}...
                        </td>
                        <td className="py-2.5 px-4">
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                              attempt.status === 'SUBMITTED'
                                ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                : 'bg-slate-100 text-slate-700'
                            }`}
                          >
                            {attempt.status}
                            {attempt.late && ' (LATE)'}
                          </span>
                        </td>
                        <td className="py-2.5 px-4 font-bold text-slate-900">
                          {attempt.score !== null && attempt.score !== undefined ? `${attempt.score} pts` : '—'}
                        </td>
                        <td className="py-2.5 px-4 text-slate-500">{formatVietnamDateTime(attempt.startedAt)}</td>
                        <td className="py-2.5 px-4 text-slate-500">{formatVietnamDateTime(attempt.submittedAt)}</td>
                        <td className="py-2.5 px-4 text-right space-x-1.5">
                          <button
                            type="button"
                            onClick={() =>
                              setSelectedStudentForAdjust({
                                id: attempt.id, // Using attempt or student identifier
                                name: `Attempt ${attempt.id.slice(0, 6)}`,
                              })
                            }
                            className="px-2 py-1 rounded text-[11px] font-semibold text-indigo-700 hover:bg-indigo-50 border border-indigo-200"
                          >
                            Adjust Attempts
                          </button>
                          <button
                            type="button"
                            onClick={() =>
                              setSelectedStudentForOverride({
                                id: attempt.id,
                                name: `Student (${attempt.id.slice(0, 6)})`,
                                score: attempt.score,
                              })
                            }
                            className="px-2 py-1 rounded text-[11px] font-semibold text-emerald-700 hover:bg-emerald-50 border border-emerald-200"
                          >
                            Override Grade
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Settings Modal */}
      {isSettingsModalOpen && (
        <QuizFormModal
          isOpen={isSettingsModalOpen}
          onClose={() => setIsSettingsModalOpen(false)}
          onSubmit={handleSaveSettings}
          initialData={authoring}
          isLoading={updateMutation.isPending}
        />
      )}

      {/* Question Create/Edit Modal */}
      {isQuestionModalOpen && (
        <QuizQuestionModal
          isOpen={isQuestionModalOpen}
          onClose={() => setIsQuestionModalOpen(false)}
          onSave={handleSaveQuestion}
          initialQuestion={editingQuestion}
        />
      )}

      {/* Question Bank Modal */}
      {isBankModalOpen && (
        <QuestionBankImportModal
          isOpen={isBankModalOpen}
          onClose={() => setIsBankModalOpen(false)}
          onImport={handleImportQuestions}
          classId={classId}
          existingQuizzes={allQuizzes.filter((q) => q.id !== quizId)}
        />
      )}

      {/* Adjust Attempts Modal */}
      {selectedStudentForAdjust && (
        <AdjustAttemptsModal
          isOpen={Boolean(selectedStudentForAdjust)}
          onClose={() => setSelectedStudentForAdjust(null)}
          onSubmit={handleAdjustAttemptsSubmit}
          studentId={selectedStudentForAdjust.id}
          studentName={selectedStudentForAdjust.name}
          isLoading={adjustAttemptsMutation.isPending}
        />
      )}

      {/* Grade Override Modal */}
      {selectedStudentForOverride && (
        <GradeOverrideModal
          isOpen={Boolean(selectedStudentForOverride)}
          onClose={() => setSelectedStudentForOverride(null)}
          onSubmit={handleGradeOverrideSubmit}
          studentId={selectedStudentForOverride.id}
          studentName={selectedStudentForOverride.name}
          currentScore={selectedStudentForOverride.score}
          history={studentGradeHistory}
          isLoadingHistory={isLoadingHistory}
          isLoading={overrideMutation.isPending}
        />
      )}
    </ClassroomLayout>
  );
};
