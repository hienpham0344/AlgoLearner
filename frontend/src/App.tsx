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
const LessonWorkspacePage = lazy(() => import('@/features/classroom/lessons/pages/LessonWorkspacePage').then(module => ({ default: module.LessonWorkspacePage })));

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#f3f4f6] flex items-center justify-center">
        <div className="w-8 h-8 rounded-full border-2 border-slate-900 border-t-transparent animate-spin" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Suspense fallback={<p role="status" className="p-8">Loading workspace…</p>}>{children}</Suspense>;
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
      <Route path="/teacher/assignments" element={<ProtectedRoute><PlaceholderPage feature="teacher-assignments" /></ProtectedRoute>} />
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
      <Route path="/teacher/classrooms" element={<ClassroomSourceRoute role="TEACHER" detail={false} />} />
      <Route path="/teacher/classrooms/:id" element={<ClassroomSourceRoute role="TEACHER" detail />} />

      <Route path="/student/classrooms" element={<ClassroomSourceRoute role="STUDENT" detail={false} />} />
      <Route path="/student/classrooms/:id" element={<ClassroomSourceRoute role="STUDENT" detail />} />

      <Route path="/admin/classrooms" element={<ClassroomSourceRoute role="ADMIN" detail={false} />} />
      <Route path="/admin/classrooms/:id" element={<ClassroomSourceRoute role="ADMIN" detail />} />

      {/* Entry Route Router */}
      <Route path="/classrooms" element={<ClassroomRouterPage />} />
      <Route path="/classrooms/:id" element={<ClassroomRouterPage />} />

      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
    </>
  );
};
