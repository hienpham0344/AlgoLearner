import React, { ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import {
  BookOpen,
  LayoutDashboard,
  Code2,
  ChevronRight,
} from 'lucide-react';
import logoMark from '@/assets/logo-mark.svg';
import { DemoPersona, DemoRole } from '../types';
import { UserAvatar } from './UserAvatar';
import '../styles/classroom.css';

interface ClassroomLayoutProps {
  children: ReactNode;
  breadcrumbChild?: ReactNode;
  role: DemoRole;
  persona: DemoPersona;
}

export const ClassroomLayout: React.FC<ClassroomLayoutProps> = ({
  children,
  breadcrumbChild,
  role,
  persona,
}) => {
  const location = useLocation();

  const roleBaseUrl = `/${role.toLowerCase()}/classrooms`;

  const navItems = [
    { label: 'Dashboard', href: '/dashboard', icon: LayoutDashboard },
    { label: 'Classrooms', href: roleBaseUrl, icon: BookOpen },
    { label: 'Practice', href: '#practice', icon: Code2 },
  ];

  return (
    <div className="classroom-workspace">
      <div className="classroom-shell">
        {/* Navigation Sidebar */}
        <aside className="classroom-sidebar" aria-label="Classroom Navigation">
          <div className="classroom-sidebar-top">
            <Link to={roleBaseUrl} className="classroom-brand">
              <img src={logoMark} alt="" className="w-7 h-7 invert" />
              <span>AlgoLearn</span>
            </Link>
          </div>

          <nav className="classroom-nav" aria-label="Main navigation">
            <span className="classroom-nav-label">{role} WORKSPACE</span>
            {navItems.map(({ label, href, icon: Icon }) => {
              const isActive =
                href === roleBaseUrl
                  ? location.pathname.startsWith(roleBaseUrl)
                  : location.pathname === href;

              return (
                <Link
                  key={label}
                  to={href}
                  className="classroom-nav-link"
                  data-active={isActive}
                  aria-current={isActive ? 'page' : undefined}
                >
                  <Icon size={17} strokeWidth={1.8} aria-hidden="true" />
                  <span>{label}</span>
                  {isActive && <span className="classroom-nav-current" aria-hidden="true" />}
                </Link>
              );
            })}
          </nav>

          <div className="classroom-sidebar-foot">
            <span>THINK. CODE. REPEAT.</span>
            <p>Classroom Management · {role}</p>
          </div>
        </aside>

        {/* Page Container */}
        <div className="classroom-page">
          <header className="classroom-topbar">
            <nav className="classroom-topbar-path" aria-label="Breadcrumb">
              <Link to={roleBaseUrl} className="hover:underline">
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
            </nav>

            <div className="classroom-topbar-actions">
              <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-100 border border-slate-200 text-xs">
                <span className="font-semibold text-slate-800">{persona.fullName}</span>
                <span className="text-slate-400 font-mono font-medium">({role})</span>
              </div>

              <UserAvatar
                name={persona.fullName}
                size="sm"
                presence="ACTIVE"
              />
            </div>
          </header>

          <main className="classroom-content">{children}</main>
        </div>
      </div>
    </div>
  );
};
