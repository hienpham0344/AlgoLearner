import React, { useState } from 'react';
import { Classroom, DemoRole } from '../types';
import { UserAvatar } from './UserAvatar';
import { Users, KeyRound, Copy, Check, Lock, Clock, UserCheck } from 'lucide-react';

interface ClassroomSummaryCardProps {
  classroom: Classroom;
  currentRole: DemoRole;
  currentUserId?: string;
  actions?: React.ReactNode;
}

export const ClassroomSummaryCard: React.FC<ClassroomSummaryCardProps> = ({
  classroom,
  currentRole,
  currentUserId,
  actions,
}) => {
  const [copied, setCopied] = useState(false);
  const [pointerOffset, setPointerOffset] = useState({ x: 0, y: 0 });
  const [isHovered, setIsHovered] = useState(false);

  const activeMembersCount = classroom.members.filter(m => m.status === 'ACTIVE').length;
  const pendingRequestsCount = classroom.joinRequests.filter(
    r => r.status === 'PENDING_APPROVAL',
  ).length;

  const isEnrolledStudent =
    currentRole === 'STUDENT' &&
    classroom.members.some(m => m.studentId === currentUserId && m.status === 'ACTIVE');

  const handleCopyCode = (e: React.MouseEvent) => {
    e.preventDefault();
    navigator.clipboard.writeText(classroom.classCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLElement>) => {
    if (e.pointerType === 'touch') return;
    const rect = e.currentTarget.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    const x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
    const y = ((e.clientY - rect.top) / rect.height) * 2 - 1;
    setPointerOffset({ x, y });
    setIsHovered(true);
  };

  const handlePointerLeave = () => {
    setPointerOffset({ x: 0, y: 0 });
    setIsHovered(false);
  };

  const layerTransition = isHovered
    ? 'transform 140ms ease-out'
    : 'transform 750ms cubic-bezier(0.16, 1, 0.3, 1)';

  return (
    <section
      className="classroom-summary-card relative overflow-hidden mb-7 p-6 sm:p-8 rounded-2xl bg-[#090909] text-white border border-white/12 shadow-[0_16px_36px_-12px_rgba(0,0,0,0.65),inset_0_1px_0_rgba(255,255,255,0.12)] cursor-default"
      aria-labelledby="classroom-title"
      onPointerMove={handlePointerMove}
      onPointerLeave={handlePointerLeave}
    >
      {/* 01 / Interactive Monochrome Starfield & Subtle Night Sky Texture */}
      <div
        className="absolute inset-0 pointer-events-none overflow-hidden rounded-2xl select-none"
        aria-hidden="true"
      >
        {/* Static Background Ambient Glow (Zero motion) */}
        <svg
          className="w-full h-full opacity-60"
          xmlns="http://www.w3.org/2000/svg"
          preserveAspectRatio="none"
        >
          <defs>
            <radialGradient id="mono-cosmic-glow-top" cx="80%" cy="15%" r="45%">
              <stop offset="0%" stopColor="#ffffff" stopOpacity="0.05" />
              <stop offset="100%" stopColor="#ffffff" stopOpacity="0" />
            </radialGradient>
            <radialGradient id="mono-cosmic-glow-bottom" cx="20%" cy="85%" r="40%">
              <stop offset="0%" stopColor="#ffffff" stopOpacity="0.03" />
              <stop offset="100%" stopColor="#ffffff" stopOpacity="0" />
            </radialGradient>
          </defs>
          <rect width="100%" height="100%" fill="url(#mono-cosmic-glow-top)" />
          <rect width="100%" height="100%" fill="url(#mono-cosmic-glow-bottom)" />
        </svg>

        {/* Deep Field Star Layer 1 (Subtle Far Depth Parallax: ~6px displacement) */}
        <div
          className="classroom-star-layer absolute -inset-3 pointer-events-none"
          style={{
            transform: `translate3d(${pointerOffset.x * 6}px, ${pointerOffset.y * 6}px, 0)`,
            transition: layerTransition,
            willChange: 'transform',
          }}
        >
          <svg className="w-full h-full" xmlns="http://www.w3.org/2000/svg">
            {/* Subtle deep-field constellation connection */}
            <line
              x1="18%"
              y1="28%"
              x2="27%"
              y2="36%"
              stroke="#ffffff"
              strokeWidth="0.5"
              strokeOpacity="0.10"
            />
            {/* Deep-field stars */}
            <circle cx="18%" cy="28%" r="0.75" fill="#a0a0a0" opacity="0.30" />
            <circle cx="27%" cy="36%" r="0.7" fill="#e5e5e5" opacity="0.25" />
            <circle cx="36%" cy="80%" r="0.7" fill="#a0a0a0" opacity="0.22" />
            <circle cx="44%" cy="32%" r="0.7" fill="#a0a0a0" opacity="0.24" />
            <circle cx="68%" cy="28%" r="0.75" fill="#e5e5e5" opacity="0.30" />
            <circle cx="84%" cy="88%" r="0.7" fill="#a0a0a0" opacity="0.25" />
            <circle cx="95%" cy="52%" r="0.75" fill="#e5e5e5" opacity="0.30" />
          </svg>
        </div>

        {/* Mid Field Constellation & Star Layer 2 (Responsive Near Depth Parallax: ~14px displacement) */}
        <div
          className="classroom-star-layer absolute -inset-4 pointer-events-none"
          style={{
            transform: `translate3d(${pointerOffset.x * 14}px, ${pointerOffset.y * 14}px, 0)`,
            transition: layerTransition,
            willChange: 'transform',
          }}
        >
          <svg className="w-full h-full" xmlns="http://www.w3.org/2000/svg">
            {/* Constellation Pattern 1 (Upper Right Arc) */}
            <line
              x1="74%"
              y1="22%"
              x2="84%"
              y2="16%"
              stroke="#ffffff"
              strokeWidth="0.6"
              strokeOpacity="0.16"
            />
            <line
              x1="84%"
              y1="16%"
              x2="92%"
              y2="24%"
              stroke="#ffffff"
              strokeWidth="0.6"
              strokeOpacity="0.16"
            />
            <line
              x1="92%"
              y1="24%"
              x2="88%"
              y2="37%"
              stroke="#ffffff"
              strokeWidth="0.6"
              strokeOpacity="0.14"
            />

            {/* Constellation Pattern 2 (Lower Right Segment) */}
            <line
              x1="78%"
              y1="72%"
              x2="88%"
              y2="66%"
              stroke="#ffffff"
              strokeWidth="0.6"
              strokeOpacity="0.14"
            />
            <line
              x1="88%"
              y1="66%"
              x2="94%"
              y2="79%"
              stroke="#ffffff"
              strokeWidth="0.6"
              strokeOpacity="0.14"
            />

            {/* Connected & standalone foreground stars */}
            <circle cx="74%" cy="22%" r="0.95" fill="#ffffff" opacity="0.45" />
            <circle cx="84%" cy="16%" r="1.1" fill="#ffffff" opacity="0.55" />
            <circle cx="92%" cy="24%" r="0.95" fill="#ffffff" opacity="0.48" />
            <circle cx="88%" cy="37%" r="0.85" fill="#e5e5e5" opacity="0.38" />

            <circle cx="78%" cy="72%" r="1.0" fill="#ffffff" opacity="0.45" />
            <circle cx="88%" cy="66%" r="0.85" fill="#e5e5e5" opacity="0.36" />
            <circle cx="94%" cy="79%" r="1.05" fill="#ffffff" opacity="0.50" />

            <circle cx="52%" cy="16%" r="0.85" fill="#ffffff" opacity="0.35" />
            <circle cx="63%" cy="84%" r="0.8" fill="#e5e5e5" opacity="0.32" />
            <circle cx="70%" cy="54%" r="0.9" fill="#ffffff" opacity="0.42" />
            <circle cx="97%" cy="38%" r="0.8" fill="#e5e5e5" opacity="0.34" />
          </svg>
        </div>
      </div>

      {/* 02 / Main Content Layout */}
      <div className="relative z-10 flex flex-col lg:flex-row lg:items-start justify-between gap-5">
        {/* Left: Primary Information Hierarchy */}
        <div className="space-y-3.5 flex-1 min-w-0">
          {/* Status & Metadata Badges - Minimal Monochrome */}
          <div className="flex items-center gap-2 flex-wrap">
            <span
              className={`inline-flex items-center text-[11.5px] font-semibold px-2.5 py-0.5 rounded-md border ${
                classroom.status === 'ACTIVE'
                  ? 'bg-white/10 text-[#FFFFFF] border-white/20'
                  : 'bg-[#171717] text-[#A0A0A0] border-white/10'
              }`}
            >
              {classroom.status}
            </span>

            <span className="text-[11.5px] font-medium px-2.5 py-0.5 rounded-md bg-white/5 text-[#E5E5E5] border border-white/10">
              {classroom.joinMode === 'AUTO_JOIN' ? 'Auto Join' : 'Approval Required'}
            </span>

            {classroom.codeLocked && (
              <span className="inline-flex items-center gap-1 text-[11.5px] font-medium px-2.5 py-0.5 rounded-md bg-white/5 text-[#E5E5E5] border border-white/10">
                <Lock size={11} className="shrink-0 text-[#A0A0A0]" />
                Code Locked
              </span>
            )}

            {isEnrolledStudent && (
              <span className="inline-flex items-center gap-1 text-[11.5px] font-semibold px-2.5 py-0.5 rounded-md bg-white/10 text-[#FFFFFF] border border-white/20">
                <UserCheck size={12} className="shrink-0 text-[#E5E5E5]" />
                Enrolled
              </span>
            )}
          </div>

          {/* Primary Focus: Course Title */}
          <h1
            id="classroom-title"
            className="text-2xl sm:text-3xl font-extrabold text-[#FFFFFF] tracking-tight leading-tight"
          >
            {classroom.name}
          </h1>

          {/* Directly Associated Instructor */}
          <div className="flex items-center gap-3 pt-0.5">
            <UserAvatar
              name={classroom.ownerName}
              size="md"
              presence="ACTIVE"
              ringColor="ring-[#141517]"
              className="[&>div:first-child]:bg-[#1c1c1c] [&>div:first-child]:border-white/15 [&>div:first-child]:text-[#E5E5E5] [&>div:first-child]:shadow-inner"
            />
            <div>
              <span className="text-[11px] font-semibold uppercase tracking-wider text-[#A0A0A0] block leading-tight">
                Instructor
              </span>
              <span className="text-sm font-bold text-[#FFFFFF] leading-tight">
                {classroom.ownerName}
              </span>
            </div>
          </div>

          {/* Course Syllabus / Summary */}
          {classroom.description && (
            <p className="text-xs sm:text-sm text-[#A0A0A0] max-w-3xl leading-relaxed pt-1">
              {classroom.description}
            </p>
          )}
        </div>

        {/* Right: Role-Specific Action Controls */}
        {actions && (
          <div className="classroom-header-actions flex items-center gap-2.5 shrink-0 self-start pt-1">
            {actions}
          </div>
        )}
      </div>

      {/* 03 / Summary Statistics & Metadata Row */}
      <div className="relative z-10 mt-6 pt-4 border-t border-white/10 flex flex-wrap items-center gap-4 sm:gap-6 text-xs text-[#A0A0A0]">
        <div className="inline-flex items-center gap-1.5">
          <Users size={14} className="text-[#A0A0A0] shrink-0" />
          <span>
            <strong className="font-semibold text-[#FFFFFF]">{activeMembersCount}</strong>{' '}
            {activeMembersCount === 1 ? 'student enrolled' : 'students enrolled'}
          </span>
        </div>

        <div className="inline-flex items-center gap-1.5 font-mono">
          <KeyRound size={14} className="text-[#A0A0A0] shrink-0" />
          <span className="text-[#A0A0A0]">Code:</span>
          <button
            type="button"
            onClick={handleCopyCode}
            className="classroom-code-copy-btn inline-flex items-center gap-1.5 font-bold text-[#FFFFFF] hover:text-[#E5E5E5] bg-white/10 hover:bg-white/15 border border-white/15 px-2.5 py-0.5 rounded transition-colors"
            title="Copy class code"
            aria-label={`Copy class code ${classroom.classCode}`}
          >
            <span>{classroom.classCode}</span>
            {copied ? (
              <Check size={12} className="text-[#FFFFFF]" />
            ) : (
              <Copy size={11} className="text-[#A0A0A0]" />
            )}
          </button>
        </div>

        {/* Pending Requests Indicator */}
        {(currentRole === 'TEACHER' || currentRole === 'ADMIN') && pendingRequestsCount > 0 && (
          <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-white/10 text-[#E5E5E5] border border-white/15 font-semibold text-[11.5px]">
            <Clock size={12} className="shrink-0 text-[#A0A0A0]" />
            <span>
              {pendingRequestsCount} pending join request{pendingRequestsCount > 1 ? 's' : ''}
            </span>
          </div>
        )}
      </div>
    </section>
  );
};
