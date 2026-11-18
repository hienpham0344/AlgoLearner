import React from 'react';
import { Link, Navigate } from 'react-router-dom';
import { ArrowRight, BookOpen } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { WorkspaceLayout } from '@/components/layout/WorkspaceLayout';
import { DashboardLessonLinks } from '../components/DashboardLessonLinks';
import type { LessonRole } from '../types';
import '@/features/dashboard/styles/dashboard.css';

export const ClassLessonsHubPage: React.FC<{ role: LessonRole }> = ({ role }) => {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return <p role="status" className="p-8">Loading workspace…</p>;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (user.role !== role) {
    return <Navigate to="/dashboard" replace />;
  }

  const classroomsBase = `/${role.toLowerCase()}/classrooms`;

  return (
    <WorkspaceLayout role={role}>
      <div className="dashboard-intro">
        <div>
          <span className="dashboard-eyebrow">CURRICULUM &amp; LESSONS</span>
          <h1>Class lessons.</h1>
          <p>
            {role === 'TEACHER'
              ? 'Select a classroom below to author, publish, and organize lesson modules.'
              : 'Select a classroom below to read published lessons and track your completion.'}
          </p>
        </div>
        <Link
          to={classroomsBase}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2 text-xs font-semibold text-slate-800 hover:bg-slate-50 transition-colors"
        >
          <BookOpen size={15} aria-hidden="true" />
          <span>Manage classrooms</span>
          <ArrowRight size={14} aria-hidden="true" />
        </Link>
      </div>

      <DashboardLessonLinks role={role} />
    </WorkspaceLayout>
  );
};

