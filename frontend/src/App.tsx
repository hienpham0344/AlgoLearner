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
import { TeacherClassroomListPage } from '@/features/classroom/teacher/pages/TeacherClassroomListPage';
import { TeacherClassroomDetailPage } from '@/features/classroom/teacher/pages/TeacherClassroomDetailPage';
import { StudentClassroomListPage } from '@/features/classroom/student/pages/StudentClassroomListPage';
import { StudentClassroomDetailPage } from '@/features/classroom/student/pages/StudentClassroomDetailPage';
import { AdminClassroomListPage } from '@/features/classroom/admin/pages/AdminClassroomListPage';
import { AdminClassroomDetailPage } from '@/features/classroom/admin/pages/AdminClassroomDetailPage';
import { ClassroomRouterPage } from '@/features/classroom/pages/ClassroomRouterPage';

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
      </Route>
      <Route
        path="/"
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
      <Route path="/teacher/classrooms" element={<TeacherClassroomListPage />} />
      <Route path="/teacher/classrooms/:id" element={<TeacherClassroomDetailPage />} />

      <Route path="/student/classrooms" element={<StudentClassroomListPage />} />
      <Route path="/student/classrooms/:id" element={<StudentClassroomDetailPage />} />

      <Route path="/admin/classrooms" element={<AdminClassroomListPage />} />
      <Route path="/admin/classrooms/:id" element={<AdminClassroomDetailPage />} />

      {/* Entry Route Router */}
      <Route path="/classrooms" element={<ClassroomRouterPage />} />
      <Route path="/classrooms/:id" element={<ClassroomRouterPage />} />

      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
};
