import React, { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Edit2, Copy, BarChart2, CheckCircle2, Lock, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import { useQuizDetail } from '../hooks/useQuizDetail';
import {
  useChangeQuizStatus,
  useDuplicateQuiz,
  usePublishGrades,
  useDeleteQuiz,
} from '../hooks/useQuizzes';
import { QuizStatusBadge } from '../components/shared/QuizStatusBadge';
import { QuizKindBadge } from '../components/shared/QuizKindBadge';
import { formatVietnamDateTime, formatDurationHuman } from '../utils/timeUtils';
import { quizError } from '../api/quizApi';

export const TeacherQuizDetailPage: React.FC = () => {
  const { id: classId = '', quizId = '' } = useParams<{ id: string; quizId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();

  const { data: quiz, isLoading, isError, error } = useQuizDetail(classId, quizId);
  const statusMutation = useChangeQuizStatus(classId, quizId);
  const duplicateMutation = useDuplicateQuiz(classId);
  const publishGradesMutation = usePublishGrades(classId, quizId);
  const deleteMutation = useDeleteQuiz(classId);

  const [actionError, setActionError] = useState<string | null>(null);

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center text-xs text-slate-500">
        Loading quiz details…
      </div>
    );
  }

  if (isError || !quiz) {
    return (
      <div className="p-8 text-center text-sm text-rose-800">
        Error loading quiz: {quizError(error)}
      </div>
    );
  }

  const config = quiz.teacherConfiguration;

  const handleStatusChange = async (newStatus: 'DRAFT' | 'PUBLISHED' | 'CLOSED') => {
    setActionError(null);
    try {
      await statusMutation.mutateAsync(newStatus);
    } catch (err) {
      setActionError(quizError(err));
    }
  };

  const handleDuplicate = async () => {
    setActionError(null);
    try {
      const duplicated = await duplicateMutation.mutateAsync(quizId);
      navigate(
        `/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(duplicated.id)}/edit`
      );
    } catch (err) {
      setActionError(quizError(err));
    }
  };

  const handlePublishGradesToggle = async (publish: boolean) => {
    setActionError(null);
    try {
      await publishGradesMutation.mutateAsync(publish);
    } catch (err) {
      setActionError(quizError(err));
    }
  };

  const handleDelete = async () => {
    if (window.confirm('Are you sure you want to delete this draft quiz?')) {
      setActionError(null);
      try {
        await deleteMutation.mutateAsync(quizId);
        navigate(`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes`);
      } catch (err) {
        setActionError(quizError(err));
      }
    }
  };

  const persona = {
    id: user?.id || '',
    username: user?.username || '',
    email: user?.email || '',
    fullName: user?.fullName || user?.username || 'Teacher',
    role: 'TEACHER' as const,
  };

  return (
    <ClassroomLayout role="TEACHER" persona={persona} breadcrumbChild={quiz.title}>
      <div className="mx-auto max-w-4xl space-y-6 p-4 sm:p-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
          <div>
            <Link
              to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes`}
              className="text-xs font-semibold text-slate-500 hover:text-slate-800"
            >
              ← Back to Quizzes
            </Link>
            <div className="mt-2 flex items-center gap-2">
              <QuizStatusBadge status={quiz.status} />
              <QuizKindBadge kind={quiz.kind} />
            </div>
            <h1 className="mt-1.5 text-2xl font-bold text-slate-900">{quiz.title}</h1>
          </div>

          {/* Quick Actions */}
          <div className="flex flex-wrap items-center gap-2">
            <Link
              to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}/results`}
              className="inline-flex items-center gap-1.5 rounded-lg bg-slate-900 px-3.5 py-2 text-xs font-bold text-white hover:bg-slate-800"
            >
              <BarChart2 size={15} />
              Student Results
            </Link>

            <Link
              to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}/edit`}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-800 hover:bg-slate-50"
            >
              <Edit2 size={14} />
              Edit
            </Link>

            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleDuplicate}
              className="flex items-center gap-1"
            >
              <Copy size={14} />
              Duplicate
            </Button>

            {quiz.status === 'DRAFT' && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleDelete}
                className="text-rose-600 hover:text-rose-700 hover:bg-rose-50"
              >
                <Trash2 size={14} />
              </Button>
            )}
          </div>
        </div>

        {actionError && (
          <div
            role="alert"
            className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs font-semibold text-rose-800"
          >
            {actionError}
          </div>
        )}

        {/* Status & Publication Control Card */}
        <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold text-slate-700">Quiz Status:</span>
            {quiz.status === 'DRAFT' && (
              <Button
                type="button"
                size="sm"
                onClick={() => handleStatusChange('PUBLISHED')}
                className="flex items-center gap-1 font-bold text-xs"
              >
                <CheckCircle2 size={14} /> Publish Quiz
              </Button>
            )}
            {quiz.status === 'PUBLISHED' && (
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => handleStatusChange('CLOSED')}
                className="flex items-center gap-1 font-bold text-xs text-rose-700 border-rose-200 hover:bg-rose-50"
              >
                <Lock size={14} /> Close Quiz
              </Button>
            )}
            {quiz.status === 'CLOSED' && (
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => handleStatusChange('PUBLISHED')}
                className="flex items-center gap-1 font-bold text-xs"
              >
                Reopen Quiz
              </Button>
            )}
          </div>

          {config?.publication === 'MANUAL' && (
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-700">Manual Grade Publication:</span>
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => handlePublishGradesToggle(true)}
                className="text-xs font-bold"
              >
                Publish All Grades
              </Button>
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => handlePublishGradesToggle(false)}
                className="text-xs font-bold text-slate-600"
              >
                Withdraw Grades
              </Button>
            </div>
          )}
        </div>

        {/* Configuration Metadata Grid */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 rounded-xl border border-slate-200 bg-white p-5 shadow-xs text-xs">
          <div>
            <h3 className="font-bold uppercase tracking-wider text-slate-500 mb-2">Schedule & Limits</h3>
            <ul className="space-y-1.5 text-slate-700">
              <li>
                <strong>Start time: </strong>
                {quiz.startTime ? formatVietnamDateTime(quiz.startTime) : 'No start restriction'}
              </li>
              <li>
                <strong>Deadline: </strong>
                {quiz.deadline ? formatVietnamDateTime(quiz.deadline) : 'No deadline'}
              </li>
              <li>
                <strong>Late attempts: </strong>
                {config?.allowLateAttempts ? 'Allowed (marked Late)' : 'Blocked after deadline'}
              </li>
              <li>
                <strong>Time limit: </strong>
                {formatDurationHuman(quiz.timeLimitSeconds)}
              </li>
              <li>
                <strong>Max attempts: </strong>
                {quiz.maxAttempts}
              </li>
              <li>
                <strong>Timeout action: </strong>
                {config?.timeout === 'AUTO_SUBMIT' ? 'Auto-submit on timeout' : 'Lock without submit'}
              </li>
            </ul>
          </div>

          <div>
            <h3 className="font-bold uppercase tracking-wider text-slate-500 mb-2">Evaluation & Rules</h3>
            <ul className="space-y-1.5 text-slate-700">
              <li>
                <strong>Scoring mode: </strong>
                {config?.scoring === 'ALL_OR_NOTHING' ? 'All or Nothing' : 'Partial Credit'}
              </li>
              <li>
                <strong>Completion rule: </strong>
                {config?.completion === 'SUBMIT_IS_ENOUGH'
                  ? 'Submit is enough'
                  : `Passing Score (≥ ${config?.passingScore}%)`}
              </li>
              <li>
                <strong>Publication mode: </strong>
                {config?.publication === 'AUTO' ? 'Auto publish on submit' : 'Manual teacher release'}
              </li>
              <li>
                <strong>Randomization: </strong>
                {config?.shuffleQuestions ? 'Shuffle questions; ' : ''}
                {config?.shuffleAnswers ? 'Shuffle answers' : ''}
                {!config?.shuffleQuestions && !config?.shuffleAnswers ? 'None' : ''}
              </li>
              <li>
                <strong>Student review: </strong>
                {config?.showCorrectAnswers ? 'Correct answers visible; ' : 'Correct answers hidden; '}
                {config?.showExplanations ? 'Explanations visible' : 'Explanations hidden'}
              </li>
            </ul>
          </div>
        </div>

        {/* Question Preview List */}
        <div className="space-y-3">
          <h2 className="text-sm font-bold uppercase tracking-wider text-slate-600">
            Questions ({quiz.questions?.length || 0})
          </h2>

          {(!quiz.questions || quiz.questions.length === 0) && (
            <div className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center text-xs text-slate-500">
              No questions configured yet. Click Edit to add questions.
            </div>
          )}

          {quiz.questions?.map((q, idx) => (
            <div key={q.id || idx} className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-slate-900">
                  Question {idx + 1} ({q.points} {q.points === 1 ? 'pt' : 'pts'})
                </span>
                <span className="rounded-md bg-slate-100 px-2 py-0.5 font-medium text-slate-600">
                  {q.type}
                </span>
              </div>
              <p className="text-sm font-semibold text-slate-900">{q.text}</p>
              <div className="grid gap-1.5 pt-1">
                {q.options.map((opt) => (
                  <div
                    key={opt.id}
                    className={`flex items-center justify-between rounded-md border px-3 py-1.5 text-xs ${
                      opt.correct
                        ? 'border-emerald-300 bg-emerald-50 text-emerald-900 font-semibold'
                        : 'border-slate-200 bg-slate-50 text-slate-700'
                    }`}
                  >
                    <span>{opt.text}</span>
                    {opt.correct && <span className="text-emerald-700 font-bold">✓ Correct</span>}
                  </div>
                ))}
              </div>
              {q.explanation && (
                <p className="text-xs text-slate-500 pt-1">
                  <strong>Explanation: </strong>
                  {q.explanation}
                </p>
              )}
            </div>
          ))}
        </div>
      </div>
    </ClassroomLayout>
  );
};
