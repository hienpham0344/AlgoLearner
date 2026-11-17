import React, { useState } from 'react';
import { ClassroomMember } from '../types';
import {
  Search,
  UserX,
  UserMinus,
  Mail,
  Calendar,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ConfirmationModal } from './ConfirmationModal';
import { UserAvatar } from './UserAvatar';

interface ClassroomMembersListProps {
  members: ClassroomMember[];
  isOwnerOrAdmin: boolean;
  isArchived: boolean;
  onRemoveMember: (studentId: string) => void;
  onOpenDirectAddModal: () => void;
}

export const ClassroomMembersList: React.FC<ClassroomMembersListProps> = ({
  members,
  isOwnerOrAdmin,
  isArchived,
  onRemoveMember,
  onOpenDirectAddModal,
}) => {
  const [search, setSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState<'ACTIVE' | 'ALL'>('ACTIVE');
  const [targetStudent, setTargetStudent] = useState<ClassroomMember | null>(null);

  const filteredMembers = members.filter(m => {
    if (filterStatus === 'ACTIVE' && m.status !== 'ACTIVE') return false;
    if (!search) return true;
    const q = search.toLowerCase();
    return (
      m.fullName.toLowerCase().includes(q) ||
      m.username.toLowerCase().includes(q) ||
      m.email.toLowerCase().includes(q)
    );
  });

  const activeCount = members.filter(m => m.status === 'ACTIVE').length;

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
    <div className="space-y-4">
      {/* Search & Actions Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-[#e7e7e5]">
        <div className="flex items-center gap-3">
          <div className="relative min-w-[220px]">
            <Search size={14} className="classroom-search-icon" aria-hidden="true" />
            <input
              type="text"
              placeholder="Search students..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="classroom-search-input text-xs"
              aria-label="Filter students by name or email"
            />
          </div>

          <div className="inline-flex gap-1 bg-[#ecece9] p-0.5 rounded-lg text-xs">
            <button
              type="button"
              onClick={() => setFilterStatus('ACTIVE')}
              className={`px-2.5 py-1 rounded-md font-semibold text-[11px] transition-all ${
                filterStatus === 'ACTIVE'
                  ? 'bg-white text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Active ({activeCount})
            </button>
            <button
              type="button"
              onClick={() => setFilterStatus('ALL')}
              className={`px-2.5 py-1 rounded-md font-semibold text-[11px] transition-all ${
                filterStatus === 'ALL'
                  ? 'bg-white text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              All Records ({members.length})
            </button>
          </div>
        </div>

        {isOwnerOrAdmin && !isArchived && (
          <Button
            size="sm"
            onClick={onOpenDirectAddModal}
            className="text-xs bg-slate-900 text-white"
          >
            + Direct Add Student
          </Button>
        )}
      </div>

      {/* Members Ledger */}
      {filteredMembers.length === 0 ? (
        <div className="classroom-empty-state">
          <div className="classroom-empty-icon">
            <UserX size={22} />
          </div>
          <h4 className="classroom-empty-title">No students found</h4>
          <p className="classroom-empty-desc">
            {search
              ? 'No member matches your filter query.'
              : 'There are currently no students enrolled in this classroom.'}
          </p>
        </div>
      ) : (
        <div className="classroom-ledger" role="list" aria-label="Classroom members">
          {filteredMembers.map(member => (
            <div key={member.studentId} className="classroom-ledger-row" role="listitem">
              <div className="flex items-center gap-3 min-w-0">
                <UserAvatar
                  name={member.fullName}
                  size="md"
                  presence={member.status === 'ACTIVE' ? 'ACTIVE' : 'OFFLINE'}
                />

                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <strong className="text-slate-900 text-sm font-semibold truncate">
                      {member.fullName}
                    </strong>
                    <span className="font-mono text-xs text-slate-400">@{member.username}</span>

                    {member.status !== 'ACTIVE' && (
                      <Badge
                        variant={member.status === 'LEFT' ? 'default' : 'danger'}
                        size="sm"
                      >
                        {member.status === 'LEFT' ? 'Left Class' : 'Removed'}
                      </Badge>
                    )}
                  </div>

                  <div className="classroom-ledger-meta">
                    <span className="inline-flex items-center gap-1 font-mono text-[11px]">
                      <Mail size={12} className="text-slate-400" aria-hidden="true" />
                      {member.email}
                    </span>
                    <span className="inline-flex items-center gap-1 text-[11px]">
                      <Calendar size={12} className="text-slate-400" aria-hidden="true" />
                      Joined {formatDate(member.joinedAt)}
                    </span>
                    {member.endedAt && (
                      <span className="text-rose-600 text-[11px]">
                        Departed {formatDate(member.endedAt)}
                      </span>
                    )}
                  </div>
                </div>
              </div>

              {isOwnerOrAdmin && !isArchived && member.status === 'ACTIVE' && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setTargetStudent(member)}
                  className="text-xs text-rose-600 hover:bg-rose-50 hover:text-rose-700 h-8"
                  title="Remove student from classroom"
                  aria-label={`Remove student ${member.fullName}`}
                >
                  <UserMinus size={14} className="mr-1" aria-hidden="true" />
                  Remove
                </Button>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Confirmation Modal for Removing Student */}
      {targetStudent && (
        <ConfirmationModal
          isOpen={Boolean(targetStudent)}
          title="Remove Student from Classroom?"
          description={`Are you sure you want to remove ${targetStudent.fullName} (${targetStudent.email})? All their past assignment submissions, quiz grades, and learning records will remain safely preserved in the classroom history.`}
          confirmLabel="Remove Student"
          variant="danger"
          onConfirm={() => {
            onRemoveMember(targetStudent.studentId);
            setTargetStudent(null);
          }}
          onCancel={() => setTargetStudent(null)}
        />
      )}
    </div>
  );
};
