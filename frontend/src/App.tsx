import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { RegisterPage } from '@/features/auth/pages/RegisterPage';
import { VerifyOtpPage } from '@/features/auth/pages/VerifyOtpPage';
import { ForgotPasswordPage } from '@/features/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/features/auth/pages/ResetPasswordPage';
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage';
import { AuthExperience } from '@/features/auth/components/AuthExperience';
import { ClassroomSourceRoute } from '@/features/classroom/pages/ClassroomSourceRoute';
import { ClassroomRouterPage } from '@/features/classroom/pages/ClassroomRouterPage';
import { CoursePreviewPage } from '@/features/auth/pages/CoursePreviewPage';
import { ProfilePage } from '@/features/profile/pages/ProfilePage';
import { PublicProfilePage } from '@/features/profile/pages/PublicProfilePage';

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

  return <>{children}</>;
};

export const App: React.FC = () => {
  return (
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
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />

      {/* Role-Specific Classroom Routes (FE 4.3 Refactor) */}
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
  );
};
