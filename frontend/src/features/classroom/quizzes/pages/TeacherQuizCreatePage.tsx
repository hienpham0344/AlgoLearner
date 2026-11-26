import React, { useState } from 'react';
import { useParams, Link, useNavigate, Navigate } from 'react-router-dom';
import { Plus, Settings, CheckCircle2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import { useCreateQuiz } from '../hooks/useQuizzes';
import type { QuizConfig, QuizQuestion } from '../types';
import { QuestionCard } from '../components/teacher/QuestionCard';
import { QuestionAuthoringModal } from '../components/teacher/QuestionAuthoringModal';
import { QuizFormModal } from '../components/teacher/QuizFormModal';
import { validateQuizConfig } from '../utils/quizValidation';
import { quizApi, quizError } from '../api/quizApi';

export const TeacherQuizCreatePage: React.FC = () => {
  const { id: classId = '' } = useParams<{ id: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [title, setTitle] = useState('');
  const [config, setConfig] = useState<QuizConfig>({
    kind: 'GRADED',
    maxAttempts: 1,
    startTime: null,
    deadline: null,
    allowLateAttempts: false,
    timeLimitSeconds: 3600, // 60 mins default
    timeout: 'AUTO_SUBMIT',
    shuffleQuestions: false,
    shuffleAnswers: false,
    scoring: 'ALL_OR_NOTHING',
    completion: 'SUBMIT_IS_ENOUGH',
    passingScore: 80,
    publication: 'AUTO',
    showCorrectAnswers: true,
    showExplanations: true,
    questions: [],
  });

  const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState<{ index: number; question: QuizQuestion } | null>(null);
  const [isQuestionModalOpen, setIsQuestionModalOpen] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const createMutation = useCreateQuiz(classId);

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

  const handleSaveSettings = (newTitle: string, newConfig: Partial<QuizConfig>) => {
    setTitle(newTitle);
    setConfig((prev) => ({ ...prev, ...newConfig }));
  };

  const handleSaveQuestion = (question: QuizQuestion) => {
    if (editingQuestion !== null) {
      // Edit existing
      setConfig((prev) => {
        const nextQ = [...prev.questions];
        nextQ[editingQuestion.index] = question;
        return { ...prev, questions: nextQ };
      });
    } else {
      // Add new
      setConfig((prev) => ({
        ...prev,
        questions: [...prev.questions, question],
      }));
    }
  };

  const handleDeleteQuestion = (index: number) => {
    setConfig((prev) => ({
      ...prev,
      questions: prev.questions.filter((_, i) => i !== index),
    }));
  };

  const handleMoveQuestion = (index: number, direction: 'UP' | 'DOWN') => {
    setConfig((prev) => {
      const nextQ = [...prev.questions];
      const targetIndex = direction === 'UP' ? index - 1 : index + 1;
      if (targetIndex < 0 || targetIndex >= nextQ.length) return prev;
      const temp = nextQ[index];
      nextQ[index] = nextQ[targetIndex];
      nextQ[targetIndex] = temp;
      return { ...prev, questions: nextQ };
    });
  };

  const handleSaveQuiz = async (publishImmediately: boolean = false) => {
    setActionError(null);
    const validation = validateQuizConfig(title, config);
    if (!validation.isValid) {
      const errorList = Object.values(validation.errors);
      setActionError(errorList.length > 0 ? errorList[0] : 'Quiz configuration is invalid.');
      return;
    }

    try {
      const created = await createMutation.mutateAsync({
        title: title.trim(),
        config,
      });

      if (publishImmediately) {
        await quizApi.changeStatus(classId, created.id, 'PUBLISHED');
      }

      navigate(`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(created.id)}`);
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
    <ClassroomLayout role="TEACHER" persona={persona} breadcrumbChild="Create Quiz">
      <div className="mx-auto max-w-4xl space-y-6 p-4 sm:p-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <Link
              to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes`}
              className="text-xs font-semibold text-slate-500 hover:text-slate-800"
            >
              ← Back to Quizzes
            </Link>
            <h1 className="mt-1 text-2xl font-bold text-slate-900">Create New Quiz</h1>
          </div>

          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsSettingsModalOpen(true)}
              className="flex items-center gap-1.5"
            >
              <Settings size={15} />
              Quiz Settings
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={createMutation.isPending}
              onClick={() => handleSaveQuiz(false)}
            >
              Save as Draft
            </Button>
            <Button
              type="button"
              disabled={createMutation.isPending}
              onClick={() => handleSaveQuiz(true)}
              className="flex items-center gap-1.5"
            >
              <CheckCircle2 size={16} />
              Publish Quiz
            </Button>
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

        {/* Basic Title Field */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs space-y-4">
          <div>
            <label htmlFor="create-quiz-title" className="block text-xs font-bold text-slate-700 mb-1">
              Quiz Title *
            </label>
            <input
              id="create-quiz-title"
              type="text"
              required
              maxLength={200}
              placeholder="e.g. Weekly Quiz 1: Asymptotic Complexity & Basic Data Structures"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3.5 py-2.5 text-base font-semibold text-slate-900 focus:border-slate-800 focus:outline-hidden"
            />
          </div>

          {/* Quick config summary pill bar */}
          <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-slate-100 text-xs text-slate-600">
            <span className="font-semibold text-slate-900">Current Configuration:</span>
            <span className="rounded-md bg-slate-100 px-2 py-0.5 font-medium">{config.kind}</span>
            <span className="rounded-md bg-slate-100 px-2 py-0.5 font-medium">Max {config.maxAttempts} attempt(s)</span>
            <span className="rounded-md bg-slate-100 px-2 py-0.5 font-medium">
              {config.timeLimitSeconds ? `${Math.round(config.timeLimitSeconds / 60)} mins` : 'Untimed'}
            </span>
            <span className="rounded-md bg-slate-100 px-2 py-0.5 font-medium">Scoring: {config.scoring}</span>
            <span className="rounded-md bg-slate-100 px-2 py-0.5 font-medium">Publication: {config.publication}</span>
            <button
              type="button"
              onClick={() => setIsSettingsModalOpen(true)}
              className="ml-auto text-xs font-bold text-indigo-600 hover:underline"
            >
              Edit all settings →
            </button>
          </div>
        </div>

        {/* Questions Section */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold uppercase tracking-wider text-slate-600">
              Questions ({config.questions.length}/200)
            </h2>
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
          </div>

          {config.questions.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50/50 p-12 text-center">
              <p className="text-sm font-semibold text-slate-700">No questions added yet</p>
              <p className="mt-1 text-xs text-slate-500">
                Click &quot;Add Question&quot; to build Single Choice, Multiple Choice, or True/False questions.
              </p>
              <Button
                type="button"
                size="sm"
                onClick={() => {
                  setEditingQuestion(null);
                  setIsQuestionModalOpen(true);
                }}
                className="mt-4"
              >
                <Plus size={15} className="mr-1" /> Add Question
              </Button>
            </div>
          ) : (
            <div className="space-y-3">
              {config.questions.map((q, idx) => (
                <QuestionCard
                  key={q.id || idx}
                  question={q}
                  index={idx}
                  totalQuestions={config.questions.length}
                  scoringMode={config.scoring}
                  onEdit={() => {
                    setEditingQuestion({ index: idx, question: q });
                    setIsQuestionModalOpen(true);
                  }}
                  onDelete={() => handleDeleteQuestion(idx)}
                  onMoveUp={() => handleMoveQuestion(idx, 'UP')}
                  onMoveDown={() => handleMoveQuestion(idx, 'DOWN')}
                />
              ))}
            </div>
          )}
        </div>

        {/* Bottom Save Bar */}
        <div className="flex items-center justify-end gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
          <Button
            type="button"
            variant="outline"
            disabled={createMutation.isPending}
            onClick={() => handleSaveQuiz(false)}
          >
            Save as Draft
          </Button>
          <Button
            type="button"
            disabled={createMutation.isPending}
            onClick={() => handleSaveQuiz(true)}
            className="flex items-center gap-1.5"
          >
            <CheckCircle2 size={16} />
            Publish Quiz
          </Button>
        </div>

        {/* Modals */}
        <QuizFormModal
          isOpen={isSettingsModalOpen}
          initialTitle={title}
          initialConfig={config}
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
