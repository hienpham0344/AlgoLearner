import React, { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { WorkspaceLayout } from '@/components/layout/WorkspaceLayout';
import { DemoPersona, DemoRole } from '../types';
import '../styles/classroom.css';

interface ClassroomLayoutProps {
  className?: string;
  children: ReactNode;
  breadcrumbChild?: ReactNode;
  role: DemoRole;
  persona: DemoPersona;
}

export const ClassroomLayout: React.FC<ClassroomLayoutProps> = ({
  className = '',
  children,
  breadcrumbChild,
  role,
  persona,
}) => {
  const roleBaseUrl = `/${role.toLowerCase()}/classrooms`;

  return (
    <WorkspaceLayout
      role={role}
      displayNameOverride={persona.fullName}
      sidebarFooterText={`Classroom Management · ${role}`}
      mainClassName={`classroom-workspace ${className} classroom-content`.trim()}
      breadcrumb={
        <>
          <Link to="/dashboard" className="hover:underline text-inherit">
            Workspace
          </Link>
          <ChevronRight size={13} aria-hidden="true" />
          <Link to={roleBaseUrl} className="hover:underline font-semibold text-slate-800">
            {role === 'ADMIN'
              ? 'Admin Classroom Management'
              : role === 'TEACHER'
                ? 'Teaching Classrooms'
                : 'Enrolled Classrooms'}
          </Link>
          {breadcrumbChild && (
            <>
              <ChevronRight size={13} aria-hidden="true" />
              <span className="text-slate-900 font-bold max-w-xs truncate">
                {breadcrumbChild}
              </span>
            </>
          )}
        </>
      }
    >
      {children}
    </WorkspaceLayout>
  );
};
