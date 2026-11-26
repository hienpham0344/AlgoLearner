import React, { useState, useEffect } from 'react';
import { useParams, Link, Navigate } from 'react-router-dom';
import { Plus, Settings, Save, AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import { useQuizDetail } from '../hooks/useQuizDetail';
import { useUpdateQuiz } from '../hooks/useQuizzes';
import { useAttemptList } from '../hooks/useQuizAttempt';
import type { QuizConfig, QuizQuestion } from '../types';
import { QuestionCard } from '../components/teacher/QuestionCard';
import { QuestionAuthoringModal } from '../components/teacher/QuestionAuthoringModal';
import { QuizFormModal } from '../components/teacher/QuizFormModal';
import { validateQuizConfig } from '../utils/quizValidation';
import { quizError } from '../api/quizApi';

export const TeacherQuizEditPage: React.FC = () => {
  const { id: classId = '', quizId = '' } = useParams<{ id: string; quizId: string }>();
  const { user } = useAuth();

  const { data: quiz, isLoading, isError, error } = useQuizDetail(classId, quizId);
  const { data: attemptsData } = useAttemptList(classId, quizId, 0, 1);

  const [title, setTitle] = useState('');
  const [config, setConfig] = useState<QuizConfig | null>(null);

  const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState<{ index: number; question: QuizQuestion } | null>(null);
  const [isQuestionModalOpen, setIsQuestionModalOpen] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const updateMutation = useUpdateQuiz(classId, quizId);

  const attemptsExist = Boolean(attemptsData && attemptsData.pagination.totalElements > 0);

  useEffect(() => {
    if (quiz) {
      setTitle(quiz.title);
      if (quiz.teacherConfiguration) {
        setConfig(quiz.teacherConfiguration);
      }
    }
  }, [quiz]);

  if (user && user.role !== 'TEACHER') {
    return (
      <Navigate
        to={
          user.role === 'STUDENT' && classId
            ? `/student/classrooms/${encodeURIComponent(classId)}/quizzes`
            : '/dashboard'
        }
        replace
      />
    );
  }

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center text-xs text-slate-500">
        Loading quiz editor…
      </div>
    );
  }

  if (isError || !quiz || !config) {
    return (
      <div className="p-8 text-center text-sm text-rose-800">
        Error loading quiz: {quizError(error)}
      </div>
    );
  }

  const handleSaveSettings = (newTitle: string, newConfig: Partial<QuizConfig>) => {
    setTitle(newTitle);
    setConfig((prev) => (prev ? { ...prev, ...newConfig } : null));
  };

  const handleSaveQuestion = (question: QuizQuestion) => {
    if (editingQuestion !== null) {
      setConfig((prev) => {
        if (!prev) return null;
        const nextQ = [...prev.questions];
        nextQ[editingQuestion.index] = question;
        return { ...prev, questions: nextQ };
      });
    } else {
      setConfig((prev) => (prev ? { ...prev, questions: [...prev.questions, question] } : null));
    }
  };

  const handleDeleteQuestion = (index: number) => {
    setConfig((prev) =>
      prev ? { ...prev, questions: prev.questions.filter((_, i) => i !== index) } : null
    );
  };

  const handleMoveQuestion = (index: number, direction: 'UP' | 'DOWN') => {
    setConfig((prev) => {
      if (!prev) return null;
      const nextQ = [...prev.questions];
      const targetIndex = direction === 'UP' ? index - 1 : index + 1;
      if (targetIndex < 0 || targetIndex >= nextQ.length) return prev;
      const temp = nextQ[index];
      nextQ[index] = nextQ[targetIndex];
      nextQ[targetIndex] = temp;
      return { ...prev, questions: nextQ };
    });
  };

  const handleSaveQuiz = async () => {
    setActionError(null);
    setSuccessMessage(null);

    const validation = validateQuizConfig(title, config);
    if (!validation.isValid) {
      const errorList = Object.values(validation.errors);
      setActionError(errorList.length > 0 ? errorList[0] : 'Quiz configuration is invalid.');
      return;
    }

    try {
      await updateMutation.mutateAsync({
        title: title.trim(),
        config,
      });
      setSuccessMessage('Quiz updated successfully.');
    } catch (err) {
      setActionError(quizError(err));
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
    <ClassroomLayout role="TEACHER" persona={persona} breadcrumbChild="Edit Quiz">
      <div className="mx-auto max-w-4xl space-y-6 p-4 sm:p-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <Link
              to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quizId)}`}
              className="text-xs font-semibold text-slate-500 hover:text-slate-800"
            >
              ← Back to Quiz Detail
            </Link>
            <h1 className="mt-1 text-2xl font-bold text-slate-900">Edit Quiz</h1>
          </div>

          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsSettingsModalOpen(true)}
              className="flex items-center gap-1.5"
            >
              <Settings size={15} />
              Settings
            </Button>
            <Button
              type="button"
              disabled={updateMutation.isPending}
              onClick={handleSaveQuiz}
              className="flex items-center gap-1.5"
            >
              <Save size={16} />
              Save Changes
            </Button>
          </div>
        </div>

        {attemptsExist && (
          <div
            role="alert"
            className="flex items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 p-4 text-xs text-amber-900"
          >
            <AlertTriangle size={18} className="shrink-0 text-amber-600 mt-0.5" />
            <div>
              <p className="font-bold">Configuration Frozen</p>
              <p className="mt-0.5">
                Students have already started or submitted attempts for this quiz. Question content and evaluation rules are locked to guarantee integrity. You may still update the title and visibility options.
              </p>
            </div>
          </div>
        )}

        {actionError && (
          <div
            role="alert"
            className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs font-semibold text-rose-800"
          >
            {actionError}
          </div>
        )}

        {successMessage && (
          <div
            role="status"
            className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-xs font-semibold text-emerald-800"
          >
            {successMessage}
          </div>
        )}

        {/* Title Field */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs space-y-4">
          <div>
            <label htmlFor="edit-quiz-title" className="block text-xs font-bold text-slate-700 mb-1">
              Quiz Title *
            </label>
            <input
              id="edit-quiz-title"
              type="text"
              required
              maxLength={200}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3.5 py-2.5 text-base font-semibold text-slate-900 focus:border-slate-800 focus:outline-hidden"
            />
          </div>

          <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-slate-100 text-xs text-slate-600">
            <span className="font-semibold text-slate-900">Status: {quiz.status}</span>
            <span className="rounded-md bg-slate-100 px-2 py-0.5 font-medium">{config.kind}</span>
            <span className="rounded-md bg-slate-100 px-2 py-0.5 font-medium">Max {config.maxAttempts} attempt(s)</span>
            <span className="rounded-md bg-slate-100 px-2 py-0.5 font-medium">
              {config.timeLimitSeconds ? `${Math.round(config.timeLimitSeconds / 60)} mins` : 'Untimed'}
            </span>
            <button
              type="button"
              onClick={() => setIsSettingsModalOpen(true)}
              className="ml-auto text-xs font-bold text-indigo-600 hover:underline"
            >
              Configure settings →
            </button>
          </div>
        </div>

        {/* Questions Section */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold uppercase tracking-wider text-slate-600">
              Questions ({config.questions.length}/200)
            </h2>
            {!attemptsExist && (
              <Button
                type="button"
                size="sm"
                onClick={() => {
                  setEditingQuestion(null);
                  setIsQuestionModalOpen(true);
                }}
                className="flex items-center gap-1 font-bold"
              >
                <Plus size={15} /> Add Question
              </Button>
            )}
          </div>

          <div className="space-y-3">
            {config.questions.map((q, idx) => (
              <QuestionCard
                key={q.id || idx}
                question={q}
                index={idx}
                totalQuestions={config.questions.length}
                scoringMode={config.scoring}
                readOnly={attemptsExist}
                onEdit={
                  !attemptsExist
                    ? () => {
                        setEditingQuestion({ index: idx, question: q });
                        setIsQuestionModalOpen(true);
                      }
                    : undefined
                }
                onDelete={!attemptsExist ? () => handleDeleteQuestion(idx) : undefined}
                onMoveUp={!attemptsExist ? () => handleMoveQuestion(idx, 'UP') : undefined}
                onMoveDown={!attemptsExist ? () => handleMoveQuestion(idx, 'DOWN') : undefined}
              />
            ))}
          </div>
        </div>

        {/* Modals */}
        <QuizFormModal
          isOpen={isSettingsModalOpen}
          initialTitle={title}
          initialConfig={config}
          isAssessmentFrozen={attemptsExist}
          onSave={handleSaveSettings}
          onClose={() => setIsSettingsModalOpen(false)}
        />

        <QuestionAuthoringModal
          isOpen={isQuestionModalOpen}
          scoringMode={config.scoring}
          question={editingQuestion?.question}
          onSave={handleSaveQuestion}
          onClose={() => setIsQuestionModalOpen(false)}
        />
      </div>
    </ClassroomLayout>
  );
};
