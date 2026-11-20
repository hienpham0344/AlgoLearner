/**
 * AlgoLearner Module 4.7 — Classroom Quizzes Feature Module
 */

// Types
export * from './types';

// API & Keys
export { quizApi, quizError } from './api/quizApi';
export { quizKeys } from './api/quizKeys';

// Utilities
export * from './utils/timeUtils';
export * from './utils/quizValidation';
export * from './utils/autosaveQueue';

// Hooks
export * from './hooks/useQuizzes';
export * from './hooks/useQuizDetail';
export * from './hooks/useQuizAttempt';
export * from './hooks/useQuizTimer';
export * from './hooks/useQuizAutosave';
export * from './hooks/useQuizResult';

// Components
export { QuizStatusBadge } from './components/shared/QuizStatusBadge';
export { QuizKindBadge } from './components/shared/QuizKindBadge';
export { QuizScoreBadge } from './components/shared/QuizScoreBadge';
export { AutosaveIndicator } from './components/shared/AutosaveIndicator';
export { QuizTimerDisplay } from './components/shared/QuizTimerDisplay';

export { QuizFormModal } from './components/teacher/QuizFormModal';
export { QuestionAuthoringModal } from './components/teacher/QuestionAuthoringModal';
export { QuestionCard } from './components/teacher/QuestionCard';
export { AccessOverrideModal } from './components/teacher/AccessOverrideModal';
export { GradeOverrideModal } from './components/teacher/GradeOverrideModal';
export { GradeHistoryModal } from './components/teacher/GradeHistoryModal';

export { FocusModeHeader } from './components/student/FocusModeHeader';
export { QuestionNavPalette } from './components/student/QuestionNavPalette';
export { QuestionRenderer } from './components/student/QuestionRenderer';
export { SubmitConfirmationDialog } from './components/student/SubmitConfirmationDialog';
export { AttemptHistoryCard } from './components/student/AttemptHistoryCard';
export { QuizResultSummary } from './components/student/QuizResultSummary';

// Pages
export { TeacherQuizzesPage } from './pages/TeacherQuizzesPage';
export { TeacherQuizCreatePage } from './pages/TeacherQuizCreatePage';
export { TeacherQuizEditPage } from './pages/TeacherQuizEditPage';
export { TeacherQuizDetailPage } from './pages/TeacherQuizDetailPage';
export { TeacherQuizResultsPage } from './pages/TeacherQuizResultsPage';

export { StudentQuizzesPage } from './pages/StudentQuizzesPage';
export { StudentQuizDetailPage } from './pages/StudentQuizDetailPage';
export { StudentQuizAttemptPage } from './pages/StudentQuizAttemptPage';
export { StudentQuizResultPage } from './pages/StudentQuizResultPage';
