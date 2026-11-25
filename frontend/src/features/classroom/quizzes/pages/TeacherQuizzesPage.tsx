import React, { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  Plus,
  HelpCircle,
  Copy,
  Clock,
  Calendar,
  Layers,
  ArrowRight,
  Search,
  AlertCircle,
  CheckCircle2,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ClassroomLayout } from '@/features/classroom/components/ClassroomLayout';
import { useAuth } from '@/hooks/useAuth';
import {
  useQuizList,
  useCreateQuiz,
  useDuplicateQuiz,
} from '../hooks/useQuizzes';
import type { QuizConfigureRequest, QuizStatus } from '../types';
import { QuizStatusBadge } from '../components/shared/QuizStatusBadge';
import { QuizFormModal } from '../components/teacher/QuizFormModal';
import { formatDurationMinutes, formatVietnamDateTime } from '../utils/quizTimeUtils';
import { quizError } from '../api/quizApi';
import { generateClientUuid } from '../utils/quizRules';

export const TeacherQuizzesPage: React.FC = () => {
  const { id: classId = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();

  const isTeacher = user?.role === 'TEACHER' || user?.role === 'ADMIN';

  const { data: quizzes = [], isLoading, error } = useQuizList(classId, isTeacher);
  const createMutation = useCreateQuiz(classId);
  const duplicateMutation = useDuplicateQuiz(classId);

  const [filter, setFilter] = useState<'ALL' | QuizStatus>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  if (!isTeacher) {
    return (
      <div className="p-12 text-center">
        <h2 className="text-base font-bold text-slate-900 mb-2">Access Denied</h2>
        <p className="text-xs text-slate-500 mb-4">
          Only classroom teachers and administrators can access the Quiz Management workspace.
        </p>
        <Link to={`/student/classrooms/${encodeURIComponent(classId)}`}>
          <Button size="sm">Return to Classroom</Button>
        </Link>
      </div>
    );
  }

  const filteredQuizzes = quizzes.filter((q) => {
    if (filter !== 'ALL' && q.status !== filter) return false;
    if (searchQuery.trim() && !q.title.toLowerCase().includes(searchQuery.toLowerCase())) {
      return false;
    }
    return true;
  });

  const handleCreateQuiz = async (data: QuizConfigureRequest) => {
    setActionError(null);
    try {
      // Ensure at least one starter question if creating from empty
      const payload: QuizConfigureRequest = {
        ...data,
        questions:
          data.questions && data.questions.length > 0
            ? data.questions
            : [
                {
                  id: generateClientUuid(),
                  prompt: 'Sample Question 1',
                  type: 'SINGLE_CHOICE',
                  scoring: 'ALL_OR_NOTHING',
                  points: 1,
                  options: [
                    { id: generateClientUuid(), text: 'Option A', correct: true, weight: null },
                    { id: generateClientUuid(), text: 'Option B', correct: false, weight: null },
                  ],
                },
              ],
      };
      const created = await createMutation.mutateAsync(payload);
      setIsCreateModalOpen(false);
      setActionSuccess('Quiz draft created successfully.');
      navigate(`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(created.id)}`);
    } catch (err) {
      setActionError(quizError(err));
    }
  };

  const handleDuplicate = async (quizId: string) => {
    setActionError(null);
    setActionSuccess(null);
    try {
      await duplicateMutation.mutateAsync(quizId);
      setActionSuccess('Quiz duplicated successfully as a new draft.');
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
      breadcrumbChild="Class Quizzes"
    >
      <div className="max-w-6xl mx-auto space-y-6">
        {/* Page Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h1 className="text-xl font-black text-slate-900 tracking-tight">Classroom Quizzes</h1>
            </div>
            <p className="text-xs text-slate-500">
              Create, configure, and evaluate objective quizzes, timers, and attempt allowances.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <Button
              size="sm"
              onClick={() => setIsCreateModalOpen(true)}
              className="bg-[#d64527] hover:bg-[#be3b20] text-white shadow-xs"
            >
              <Plus size={15} className="mr-1.5" />
              Create Quiz Draft
            </Button>
          </div>
        </div>

        {/* Feedback banners */}
        {actionError && (
          <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <AlertCircle size={16} className="shrink-0" />
              <span>{actionError}</span>
            </div>
            <button
              type="button"
              onClick={() => setActionError(null)}
              className="text-rose-600 hover:text-rose-800 text-xs font-bold"
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
              className="text-emerald-600 hover:text-emerald-800 text-xs font-bold"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Filter and Search Bar */}
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-white p-3 rounded-xl border border-slate-200">
          <div className="flex items-center gap-1.5 w-full sm:w-auto overflow-x-auto pb-1 sm:pb-0">
            {(['ALL', 'DRAFT', 'PUBLISHED', 'CLOSED'] as const).map((st) => (
              <button
                key={st}
                type="button"
                onClick={() => setFilter(st)}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold whitespace-nowrap transition-colors ${
                  filter === st
                    ? 'bg-slate-900 text-white'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {st === 'ALL' ? 'All Quizzes' : st}
              </button>
            ))}
          </div>

          <div className="relative w-full sm:w-64">
            <Search size={14} className="absolute left-3 top-2.5 text-slate-400" />
            <input
              type="text"
              placeholder="Search quizzes by title..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 text-xs border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
            />
          </div>
        </div>

        {/* Quiz List */}
        {isLoading ? (
          <div className="p-12 text-center text-xs text-slate-500">
            Loading quizzes...
          </div>
        ) : error ? (
          <div className="p-8 text-center text-xs text-rose-600 bg-rose-50 border border-rose-200 rounded-xl">
            Failed to load quizzes: {quizError(error)}
          </div>
        ) : filteredQuizzes.length === 0 ? (
          <div className="p-12 text-center bg-white border border-slate-200 rounded-2xl">
            <div className="w-12 h-12 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center mx-auto mb-3">
              <HelpCircle size={24} />
            </div>
            <h3 className="text-sm font-bold text-slate-900 mb-1">No quizzes found</h3>
            <p className="text-xs text-slate-500 max-w-sm mx-auto mb-4">
              {filter !== 'ALL' || searchQuery
                ? 'Try adjusting your search criteria or status filter.'
                : 'Get started by creating your first timed or practice quiz in this class.'}
            </p>
            {filter === 'ALL' && !searchQuery && (
              <Button
                size="sm"
                onClick={() => setIsCreateModalOpen(true)}
                className="bg-[#d64527] hover:bg-[#be3b20] text-white"
              >
                <Plus size={14} className="mr-1.5" />
                Create Quiz Draft
              </Button>
            )}
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {filteredQuizzes.map((quiz) => (
              <div
                key={quiz.id}
                className="p-5 bg-white border border-slate-200 rounded-2xl shadow-xs hover:border-slate-300 transition-all flex flex-col md:flex-row md:items-center justify-between gap-4"
              >
                <div className="space-y-2 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
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
                    {quiz.allowLate && (
                      <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-amber-50 text-amber-800 border border-amber-200">
                        Late Allowed
                      </span>
                    )}
                  </div>

                  <Link
                    to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quiz.id)}`}
                    className="text-base font-bold text-slate-900 hover:text-indigo-600 transition-colors block truncate"
                  >
                    {quiz.title}
                  </Link>

                  <div className="flex items-center gap-4 text-xs text-slate-500 flex-wrap">
                    <span className="flex items-center gap-1">
                      <Clock size={13} className="text-slate-400" />
                      {formatDurationMinutes(quiz.durationSeconds)}
                    </span>
                    <span className="flex items-center gap-1">
                      <Layers size={13} className="text-slate-400" />
                      Max {quiz.maxAttempts} {quiz.maxAttempts === 1 ? 'attempt' : 'attempts'}
                    </span>
                    {quiz.closesAt && (
                      <span className="flex items-center gap-1">
                        <Calendar size={13} className="text-slate-400" />
                        Closes: {formatVietnamDateTime(quiz.closesAt)}
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0 self-end md:self-center">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleDuplicate(quiz.id)}
                    className="h-8 text-xs text-slate-600 hover:bg-slate-50"
                    title="Duplicate into a new draft"
                  >
                    <Copy size={13} className="mr-1" />
                    Duplicate
                  </Button>

                  <Link
                    to={`/teacher/classrooms/${encodeURIComponent(classId)}/quizzes/${encodeURIComponent(quiz.id)}`}
                  >
                    <Button
                      size="sm"
                      className="h-8 text-xs bg-slate-900 hover:bg-slate-800 text-white"
                    >
                      <span>Manage &amp; Author</span>
                      <ArrowRight size={13} className="ml-1" />
                    </Button>
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Create Modal */}
      {isCreateModalOpen && (
        <QuizFormModal
          isOpen={isCreateModalOpen}
          onClose={() => setIsCreateModalOpen(false)}
          onSubmit={handleCreateQuiz}
          isLoading={createMutation.isPending}
        />
      )}
    </ClassroomLayout>
  );
};
