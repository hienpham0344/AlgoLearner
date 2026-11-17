import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Classroom, DemoRole, DemoPersona } from '../types';
import { UserAvatar } from './UserAvatar';
import {
  Users,
  Copy,
  Check,
  Lock,
  ArrowRight,
  Archive,
  LogOut,
  Share2,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface ClassroomCardProps {
  classroom: Classroom;
  currentRole: DemoRole;
  currentPersona: DemoPersona;
  onArchive?: (id: string) => void;
  onLeave?: (id: string) => void;
  onTransfer?: (id: string) => void;
}

export const ClassroomCard: React.FC<ClassroomCardProps> = ({
  classroom,
  currentRole,
  currentPersona,
  onArchive,
  onLeave,
  onTransfer,
}) => {
  const [copied, setCopied] = useState(false);

  const isOwner = classroom.ownerId === currentPersona.id;
  const isEnrolled = classroom.members.some(
    m => m.studentId === currentPersona.id && m.status === 'ACTIVE',
  );
  const isPendingApproval = classroom.joinRequests.some(
    r => r.studentId === currentPersona.id && r.status === 'PENDING_APPROVAL',
  );

  const activeMembersCount = classroom.members.filter(m => m.status === 'ACTIVE').length;

  const handleCopyCode = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    navigator.clipboard.writeText(classroom.classCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const rolePrefix = currentRole.toLowerCase();
  const destinationUrl = `/${rolePrefix}/classrooms/${classroom.id}`;

  return (
    <article className="classroom-card group" aria-labelledby={`class-title-${classroom.id}`}>
      <div>
        {/* Card Header & Status Badges */}
        <div className="classroom-card-header">
          <div className="flex items-center gap-1.5 flex-wrap">
            <span
              className={`inline-flex items-center text-[10.5px] font-bold px-2.5 py-0.5 rounded-md ${
                classroom.status === 'ACTIVE'
                  ? 'bg-slate-900 text-white'
                  : 'bg-slate-100 text-slate-500 border border-slate-200'
              }`}
            >
              {classroom.status}
            </span>

            <span className="inline-flex items-center text-[10.5px] font-medium px-2 py-0.5 rounded-md bg-white/80 text-slate-600 border border-slate-200/80">
              {classroom.joinMode === 'AUTO_JOIN' ? 'Auto Join' : 'Approval Required'}
            </span>

            {isEnrolled && currentRole === 'STUDENT' && (
              <span className="inline-flex items-center text-[10.5px] font-bold px-2 py-0.5 rounded-md bg-slate-900 text-white">
                Enrolled
              </span>
            )}

            {isPendingApproval && currentRole === 'STUDENT' && (
              <span className="inline-flex items-center text-[10.5px] font-semibold px-2 py-0.5 rounded-md bg-slate-100 text-slate-700 border border-slate-300">
                Pending Approval
              </span>
            )}
          </div>

          {(currentRole === 'TEACHER' || currentRole === 'ADMIN') && (
            <button
              type="button"
              onClick={handleCopyCode}
              className="classroom-code-chip"
              title="Click to copy Class Code"
              aria-label={`Copy class code ${classroom.classCode}`}
            >
              {classroom.codeLocked ? (
                <Lock size={12} className="text-slate-600" aria-hidden="true" />
              ) : null}
              <span>{classroom.classCode}</span>
              {copied ? (
                <Check size={12} className="text-slate-900" aria-hidden="true" />
              ) : (
                <Copy size={11} className="text-slate-400" aria-hidden="true" />
              )}
            </button>
          )}
        </div>

        {/* Primary Information: Classroom Name & Subject */}
        <h3 id={`class-title-${classroom.id}`} className="classroom-card-title">
          <Link
            to={destinationUrl}
            className="hover:text-slate-600 transition-colors"
          >
            {classroom.name}
          </Link>
        </h3>

        <p className="classroom-card-desc">
          {classroom.description || 'Comprehensive programming course with interactive assignments and problem sets.'}
        </p>
      </div>

      {/* Secondary Information & Actions */}
      <div>
        <div className="classroom-card-meta">
          <div className="flex items-center gap-2 min-w-0" title={`Instructor: ${classroom.ownerName}`}>
            <UserAvatar name={classroom.ownerName} size="xs" presence="ACTIVE" />
            <span className="font-semibold text-slate-800 truncate text-xs">{classroom.ownerName}</span>
          </div>

          <div className="flex items-center gap-1.5 shrink-0 text-xs text-slate-600 font-medium ml-auto" title="Active Enrolled Students">
            <Users size={13} className="text-slate-400 shrink-0" aria-hidden="true" />
            <span>
              <strong className="font-semibold text-slate-900">{activeMembersCount}</strong>{' '}
              {activeMembersCount === 1 ? 'student' : 'students'}
            </span>
          </div>
        </div>

        {/* Card Actions Footer */}
        <div className="classroom-card-foot">
          <div className="flex items-center gap-2">
            {currentRole === 'STUDENT' && isEnrolled && onLeave && (
              <Button
                variant="ghost"
                size="sm"
                className="text-xs text-slate-600 hover:bg-slate-100 hover:text-slate-900 h-8 px-2.5"
                onClick={() => onLeave(classroom.id)}
              >
                <LogOut size={13} className="mr-1 shrink-0" aria-hidden="true" />
                Leave
              </Button>
            )}

            {(isOwner || currentRole === 'ADMIN') &&
              classroom.status === 'ACTIVE' &&
              onArchive && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-xs text-slate-500 hover:text-slate-800 h-8 px-2.5"
                  onClick={() => onArchive(classroom.id)}
                  title="Archive this classroom"
                >
                  <Archive size={13} className="mr-1 shrink-0" aria-hidden="true" />
                  Archive
                </Button>
              )}

            {currentRole === 'ADMIN' && onTransfer && (
              <Button
                variant="ghost"
                size="sm"
                className="text-xs text-slate-600 hover:bg-slate-100 hover:text-slate-900 h-8 px-2.5"
                onClick={() => onTransfer(classroom.id)}
                title="Transfer classroom ownership"
              >
                <Share2 size={13} className="mr-1 shrink-0" aria-hidden="true" />
                Transfer
              </Button>
            )}
          </div>

          <Link
            to={destinationUrl}
            className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-900 hover:text-slate-700 px-3 py-1.5 rounded-lg bg-white/80 hover:bg-white transition-all border border-slate-200/80 shadow-2xs"
          >
            <span>Open Workspace</span>
            <ArrowRight size={13} className="transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
          </Link>
        </div>
      </div>
    </article>
  );
};
