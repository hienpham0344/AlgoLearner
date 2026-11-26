import { BackendStatusBanner } from '@/components/BackendStatusBanner';
import React, { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { RegisterPage } from '@/features/auth/pages/RegisterPage';
import { VerifyOtpPage } from '@/features/auth/pages/VerifyOtpPage';
import { ForgotPasswordPage } from '@/features/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/features/auth/pages/ResetPasswordPage';
import { AccountSecurityPage } from '@/features/auth/pages/AccountSecurityPage';
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage';
import { AuthExperience } from '@/features/auth/components/AuthExperience';
import { ClassroomSourceRoute } from '@/features/classroom/pages/ClassroomSourceRoute';
import { ClassroomRouterPage } from '@/features/classroom/pages/ClassroomRouterPage';
import { CoursePreviewPage } from '@/features/auth/pages/CoursePreviewPage';
import { ProfilePage } from '@/features/profile/pages/ProfilePage';
import { PublicProfilePage } from '@/features/profile/pages/PublicProfilePage';
import { AdminAccountsPage } from '@/features/admin/pages/AdminAccountsPage';
import { PlaceholderPage } from '@/components/common/PlaceholderPage';
import { NotificationsPage } from '@/features/dashboard/pages/NotificationsPage';
import { LoadingScreen } from '@/components/ui/LoadingScreen';
import { LoadingPreviewPage } from '@/features/dev/LoadingPreviewPage';
const LessonWorkspacePage = lazy(() => import('@/features/classroom/lessons/pages/LessonWorkspacePage').then(module => ({ default: module.LessonWorkspacePage })));
const TeacherAssignmentsHubPage = lazy(() => import('@/features/classroom/assignments').then(m => ({ default: m.TeacherAssignmentsHubPage })));
const TeacherAssignmentsPage = lazy(() => import('@/features/classroom/assignments').then(m => ({ default: m.TeacherAssignmentsPage })));
const TeacherAssignmentDetailPage = lazy(() => import('@/features/classroom/assignments').then(m => ({ default: m.TeacherAssignmentDetailPage })));
const TeacherSubmissionsPage = lazy(() => import('@/features/classroom/assignments').then(m => ({ default: m.TeacherSubmissionsPage })));
const StudentAssignmentsPage = lazy(() => import('@/features/classroom/assignments').then(m => ({ default: m.StudentAssignmentsPage })));
const StudentAssignmentDetailPage = lazy(() => import('@/features/classroom/assignments').then(m => ({ default: m.StudentAssignmentDetailPage })));
const StudentWorkspacePage = lazy(() => import('@/features/classroom/assignments').then(m => ({ default: m.StudentWorkspacePage })));
const StudentAttemptsHistoryPage = lazy(() => import('@/features/classroom/assignments').then(m => ({ default: m.StudentAttemptsHistoryPage })));

const TeacherQuizzesPage = lazy(() => import('@/features/classroom/quizzes').then(m => ({ default: m.TeacherQuizzesPage })));
const TeacherQuizCreatePage = lazy(() => import('@/features/classroom/quizzes').then(m => ({ default: m.TeacherQuizCreatePage })));
const TeacherQuizDetailPage = lazy(() => import('@/features/classroom/quizzes').then(m => ({ default: m.TeacherQuizDetailPage })));
const TeacherQuizEditPage = lazy(() => import('@/features/classroom/quizzes').then(m => ({ default: m.TeacherQuizEditPage })));
const TeacherQuizResultsPage = lazy(() => import('@/features/classroom/quizzes').then(m => ({ default: m.TeacherQuizResultsPage })));
const StudentQuizzesPage = lazy(() => import('@/features/classroom/quizzes').then(m => ({ default: m.StudentQuizzesPage })));
const StudentQuizDetailPage = lazy(() => import('@/features/classroom/quizzes').then(m => ({ default: m.StudentQuizDetailPage })));
const StudentQuizAttemptPage = lazy(() => import('@/features/classroom/quizzes').then(m => ({ default: m.StudentQuizAttemptPage })));
const StudentQuizResultPage = lazy(() => import('@/features/classroom/quizzes').then(m => ({ default: m.StudentQuizResultPage })));

const ProtectedRoute: React.FC<{
  children: React.ReactNode;
  allowedRoles?: Array<'STUDENT' | 'TEACHER' | 'ADMIN'>;
}> = ({ children, allowedRoles }) => {
  const { user, isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <LoadingScreen
        variant="fullscreen"
        message="Initializing workspace…"
        submessage="Syncing your session information"
      />
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && user?.role && !allowedRoles.includes(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <Suspense
      fallback={
        <LoadingScreen
          variant="page"
          message="Loading page…"
          submessage="Please wait a moment"
        />
      }
    >
      {children}
    </Suspense>
  );
};

export const App: React.FC = () => {
  return (
    <>
    <BackendStatusBanner />
    <Routes>
      <Route element={<AuthExperience />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-otp" element={<VerifyOtpPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/courses" element={<CoursePreviewPage />} />
      </Route>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/u/:username" element={<PublicProfilePage />} />
      <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
      <Route path="/account/security" element={<ProtectedRoute><AccountSecurityPage /></ProtectedRoute>} />
      <Route path="/admin/accounts" element={<ProtectedRoute><AdminAccountsPage /></ProtectedRoute>} />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />
      {/* Feature Placeholders & Workspace Sections */}
      <Route path="/notifications" element={<ProtectedRoute><NotificationsPage /></ProtectedRoute>} />
      <Route path="/student/courses" element={<ProtectedRoute><PlaceholderPage feature="public-courses" /></ProtectedRoute>} />
      <Route path="/practice" element={<ProtectedRoute><PlaceholderPage feature="practice" /></ProtectedRoute>} />
      <Route path="/teacher/assignments" element={<ProtectedRoute><TeacherAssignmentsHubPage /></ProtectedRoute>} />
      <Route path="/teacher/review-queue" element={<ProtectedRoute><PlaceholderPage feature="teacher-review-queue" /></ProtectedRoute>} />
      <Route path="/admin/submissions" element={<ProtectedRoute><PlaceholderPage feature="admin-submissions" /></ProtectedRoute>} />
      <Route path="/admin/system-logs" element={<ProtectedRoute><PlaceholderPage feature="admin-system-logs" /></ProtectedRoute>} />
      <Route path="/admin/security-audit" element={<ProtectedRoute><PlaceholderPage feature="admin-security-audit" /></ProtectedRoute>} />
      <Route path="/placeholder/:featureKey" element={<ProtectedRoute><PlaceholderPage /></ProtectedRoute>} />

      {/* Role-Specific Classroom Routes (FE 4.3 Refactor) */}
      <Route path="/teacher/classrooms/:id/lessons" element={<ProtectedRoute><LessonWorkspacePage role="TEACHER" /></ProtectedRoute>} />
      <Route path="/teacher/classrooms/:id/lessons/:lessonId" element={<ProtectedRoute><LessonWorkspacePage role="TEACHER" /></ProtectedRoute>} />
      <Route path="/student/classrooms/:id/lessons" element={<ProtectedRoute><LessonWorkspacePage role="STUDENT" /></ProtectedRoute>} />
      <Route path="/student/classrooms/:id/lessons/:lessonId" element={<ProtectedRoute><LessonWorkspacePage role="STUDENT" /></ProtectedRoute>} />

      {/* Classroom Assignments Routes (FE 4.6) */}
      <Route path="/teacher/classrooms/:id/assignments" element={<ProtectedRoute><TeacherAssignmentsPage /></ProtectedRoute>} />
      <Route path="/teacher/classrooms/:id/assignments/:assignmentId" element={<ProtectedRoute><TeacherAssignmentDetailPage /></ProtectedRoute>} />
      <Route path="/teacher/classrooms/:id/assignments/:assignmentId/submissions" element={<ProtectedRoute><TeacherSubmissionsPage /></ProtectedRoute>} />
      <Route path="/student/classrooms/:id/assignments" element={<ProtectedRoute><StudentAssignmentsPage /></ProtectedRoute>} />
      <Route path="/student/classrooms/:id/assignments/:assignmentId" element={<ProtectedRoute><StudentAssignmentDetailPage /></ProtectedRoute>} />
      <Route path="/student/classrooms/:id/assignments/:assignmentId/workspace" element={<ProtectedRoute><StudentWorkspacePage /></ProtectedRoute>} />
      <Route path="/student/classrooms/:id/assignments/:assignmentId/attempts" element={<ProtectedRoute><StudentAttemptsHistoryPage /></ProtectedRoute>} />

      {/* Classroom Quizzes Routes (FE 4.7) */}
      <Route path="/teacher/classrooms/:id/quizzes" element={<ProtectedRoute allowedRoles={['TEACHER']}><TeacherQuizzesPage /></ProtectedRoute>} />
      <Route path="/teacher/classrooms/:id/quizzes/new" element={<ProtectedRoute allowedRoles={['TEACHER']}><TeacherQuizCreatePage /></ProtectedRoute>} />
      <Route path="/teacher/classrooms/:id/quizzes/:quizId" element={<ProtectedRoute allowedRoles={['TEACHER']}><TeacherQuizDetailPage /></ProtectedRoute>} />
      <Route path="/teacher/classrooms/:id/quizzes/:quizId/edit" element={<ProtectedRoute allowedRoles={['TEACHER']}><TeacherQuizEditPage /></ProtectedRoute>} />
      <Route path="/teacher/classrooms/:id/quizzes/:quizId/results" element={<ProtectedRoute allowedRoles={['TEACHER']}><TeacherQuizResultsPage /></ProtectedRoute>} />
      <Route path="/student/classrooms/:id/quizzes" element={<ProtectedRoute allowedRoles={['STUDENT']}><StudentQuizzesPage /></ProtectedRoute>} />
      <Route path="/student/classrooms/:id/quizzes/:quizId" element={<ProtectedRoute allowedRoles={['STUDENT']}><StudentQuizDetailPage /></ProtectedRoute>} />
      <Route path="/student/classrooms/:id/quizzes/:quizId/attempt" element={<ProtectedRoute allowedRoles={['STUDENT']}><StudentQuizAttemptPage /></ProtectedRoute>} />
      <Route path="/student/classrooms/:id/quizzes/:quizId/result" element={<ProtectedRoute allowedRoles={['STUDENT']}><StudentQuizResultPage /></ProtectedRoute>} />
      <Route path="/teacher/classrooms" element={<ClassroomSourceRoute role="TEACHER" detail={false} />} />
      <Route path="/teacher/classrooms/:id" element={<ClassroomSourceRoute role="TEACHER" detail />} />

      <Route path="/student/classrooms" element={<ClassroomSourceRoute role="STUDENT" detail={false} />} />
      <Route path="/student/classrooms/:id" element={<ClassroomSourceRoute role="STUDENT" detail />} />

      <Route path="/admin/classrooms" element={<ClassroomSourceRoute role="ADMIN" detail={false} />} />
      <Route path="/admin/classrooms/:id" element={<ClassroomSourceRoute role="ADMIN" detail />} />

      {/* Entry Route Router */}
      <Route path="/classrooms" element={<ClassroomRouterPage />} />
      <Route path="/classrooms/:id" element={<ClassroomRouterPage />} />
      <Route path="/classrooms/:id/announcements/:announcementId" element={<ClassroomRouterPage />} />
      <Route path="/teacher/classrooms/:id/announcements/:announcementId" element={<ClassroomRouterPage />} />
      <Route path="/student/classrooms/:id/announcements/:announcementId" element={<ClassroomRouterPage />} />

      {/* Dev Loading Preview Route */}
      <Route path="/preview/loading" element={<LoadingPreviewPage />} />

      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
    </>
  );
};
