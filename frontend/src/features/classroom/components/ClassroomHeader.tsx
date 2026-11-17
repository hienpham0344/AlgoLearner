import React from 'react';
import { Link } from 'react-router-dom';
import { Classroom, DemoRole, DemoPersona } from '../types';
import {
  ArrowLeft,
  Users,
  Calendar,
  Lock,
  Archive,
  LogOut,
  Share2,
} from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';

interface ClassroomHeaderProps {
  classroom: Classroom;
  currentRole: DemoRole;
  currentPersona: DemoPersona;
  onArchive: () => void;
  onLeave: () => void;
  onOpenTransferModal: () => void;
  onOpenDirectAddModal: () => void;
}

export const ClassroomHeader: React.FC<ClassroomHeaderProps> = ({
  classroom,
  currentRole,
  currentPersona,
  onArchive,
  onLeave,
  onOpenTransferModal,
  onOpenDirectAddModal,
}) => {
  const isOwner = classroom.ownerId === currentPersona.id;
  const isEnrolled = classroom.members.some(
    m => m.studentId === currentPersona.id && m.status === 'ACTIVE',
  );
  const activeMembersCount = classroom.members.filter(m => m.status === 'ACTIVE').length;

  const formatDate = (iso: string) => {
    try {
      return new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      }).format(new Date(iso));
    } catch {
      return iso;
    }
  };

  return (
    <div className="classroom-intro-header border-b border-[#e7e7e5] pb-6 mb-6">
      <div className="space-y-3 max-w-3xl">
        <Link
          to="/classrooms"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors"
        >
          <ArrowLeft size={14} aria-hidden="true" />
          <span>Back to All Classrooms</span>
        </Link>

        <div className="flex items-center gap-2.5 flex-wrap">
          <Badge variant={classroom.status === 'ACTIVE' ? 'success' : 'default'} size="sm">
            {classroom.status}
          </Badge>

          <span className="text-[11px] font-mono font-semibold px-2 py-0.5 rounded bg-slate-100 text-slate-600 border border-slate-200">
            {classroom.joinMode === 'AUTO_JOIN' ? 'Auto Join' : 'Require Approval'}
          </span>

          {classroom.codeLocked && (
            <span className="inline-flex items-center gap-1 text-[11px] font-mono font-semibold px-2 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-200">
              <Lock size={11} aria-hidden="true" />
              Code Locked
            </span>
          )}

          {!classroom.joiningOpen && (
            <span className="text-[11px] font-mono font-semibold px-2 py-0.5 rounded bg-rose-50 text-rose-700 border border-rose-200">
              Joining Closed
            </span>
          )}
        </div>

        <h1 className="classroom-title">{classroom.name}</h1>

        <p className="classroom-subtitle">{classroom.description}</p>

        <div className="flex items-center gap-4 text-xs text-slate-500 flex-wrap pt-1">
          <div className="flex items-center gap-1.5">
            <span className="text-slate-400">Instructor:</span>
            <strong className="text-slate-800 font-semibold">{classroom.ownerName}</strong>
            <span className="text-slate-400 font-mono">({classroom.ownerEmail})</span>
          </div>

          <span className="text-slate-300">·</span>

          <div className="flex items-center gap-1.5">
            <Users size={14} className="text-slate-400" aria-hidden="true" />
            <span>{activeMembersCount} {activeMembersCount === 1 ? 'student enrolled' : 'students enrolled'}</span>
          </div>

          <span className="text-slate-300">·</span>

          <div className="flex items-center gap-1.5">
            <Calendar size={14} className="text-slate-400" aria-hidden="true" />
            <span>Created {formatDate(classroom.createdAt)}</span>
          </div>
        </div>
      </div>

      {/* Role-gated Header Actions */}
      <div className="flex items-center gap-2.5 self-start">
        {currentRole === 'STUDENT' && isEnrolled && (
          <Button
            variant="outline"
            size="sm"
            onClick={onLeave}
            className="text-rose-600 hover:text-rose-700 hover:bg-rose-50 border-rose-200"
          >
            <LogOut size={14} className="mr-1.5 shrink-0" aria-hidden="true" />
            Leave Classroom
          </Button>
        )}

        {(isOwner || currentRole === 'ADMIN') && classroom.status === 'ACTIVE' && (
          <Button
            variant="outline"
            size="sm"
            onClick={onOpenDirectAddModal}
            className="text-slate-700"
          >
            + Direct Add Student
          </Button>
        )}

        {currentRole === 'ADMIN' && (
          <Button
            variant="outline"
            size="sm"
            onClick={onOpenTransferModal}
            className="text-indigo-600 hover:bg-indigo-50 border-indigo-200"
            title="Transfer classroom ownership to another Teacher"
          >
            <Share2 size={14} className="mr-1.5 shrink-0" aria-hidden="true" />
            Transfer Ownership
          </Button>
        )}

        {(isOwner || currentRole === 'ADMIN') && classroom.status === 'ACTIVE' && (
          <Button
            variant="outline"
            size="sm"
            onClick={onArchive}
            className="text-slate-600 hover:text-slate-900"
          >
            <Archive size={14} className="mr-1.5 shrink-0" aria-hidden="true" />
            Archive
          </Button>
        )}
      </div>
    </div>
  );
};
