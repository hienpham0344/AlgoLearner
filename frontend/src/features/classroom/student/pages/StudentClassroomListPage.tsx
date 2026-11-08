import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClassroomLayout } from '../../components/ClassroomLayout';
import { ClassroomPageHeader } from '../../components/ClassroomPageHeader';
import { ClassroomCard } from '../../components/ClassroomCard';
import { ClassroomStatsGrid, StatCardItem } from '../../components/ClassroomStatsGrid';
import { JoinClassroomModal } from '../../components/JoinClassroomModal';
import { ConfirmationModal } from '../../components/ConfirmationModal';
import { useClassroomStore } from '../../mock/useClassroomStore';
import { Classroom, JoinResultFeedback } from '../../types';
import { Search, Plus, BookOpen, CheckCircle, Info, Clock, Compass } from 'lucide-react';
import { Button } from '@/components/ui/Button';

export const StudentClassroomListPage: React.FC = () => {
  const navigate = useNavigate();
  const {
    classrooms,
    currentRole,
    currentPersona,
    joinByCode,
    leaveClassroom,
  } = useClassroomStore('STUDENT');

  const [search, setSearch] = useState('');
  const [tab, setTab] = useState<'ENROLLED' | 'ALL'>('ENROLLED');
  const [isJoinModalOpen, setIsJoinModalOpen] = useState(false);
  const [leaveTarget, setLeaveTarget] = useState<Classroom | null>(null);
  const [feedback, setFeedback] = useState<JoinResultFeedback | null>(null);

  // Filter classrooms where current student is an ACTIVE member
  const enrolledClassrooms = classrooms.filter(c =>
    c.members.some(m => m.studentId === currentPersona.id && m.status === 'ACTIVE'),
  );

  const displayedList = tab === 'ENROLLED' ? enrolledClassrooms : classrooms;

  const filteredClassrooms = displayedList.filter(c => {
    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return (
      c.name.toLowerCase().includes(q) ||
      c.description.toLowerCase().includes(q) ||
      c.ownerName.toLowerCase().includes(q) ||
      c.classCode.toLowerCase().includes(q)
    );
  });

  const handleLeaveConfirm = () => {
    if (leaveTarget) {
      leaveClassroom(leaveTarget.id);
      setLeaveTarget(null);
    }
  };

  const enrolledCount = enrolledClassrooms.length;
  const pendingApprovalsCount = classrooms.filter(c =>
    c.joinRequests.some(r => r.studentId === currentPersona.id && r.status === 'PENDING_APPROVAL'),
  ).length;
  const availableCount = classrooms.length;

  const stats: StatCardItem[] = [
    {
      id: 'enrolled-classes',
      label: 'Enrolled Workspaces',
      value: enrolledCount,
      helperText: 'Active course cohorts & problem sets',
      icon: <BookOpen size={16} />,
      badge: {
        text: 'Enrolled',
        variant: 'active',
      },
    },
    {
      id: 'pending-approvals',
      label: 'Pending Approvals',
      value: pendingApprovalsCount,
      helperText:
        pendingApprovalsCount > 0
          ? 'Awaiting instructor admission'
          : 'No pending requests',
      icon: <Clock size={16} />,
      badge:
        pendingApprovalsCount > 0
          ? {
              text: `${pendingApprovalsCount} Awaiting`,
              variant: 'active',
            }
          : undefined,
    },
    {
      id: 'available-courses',
      label: 'Explore Directory',
      value: availableCount,
      helperText: 'Total institutional classrooms available',
      icon: <Compass size={16} />,
    },
  ];

  return (
    <ClassroomLayout role={currentRole} persona={currentPersona}>
      {/* 01 / Page Header with Horizontally Aligned Primary Action */}
      <ClassroomPageHeader
        eyebrow="STUDENT WORKSPACE"
        title="My Classrooms & Learning Spaces"
        description="Access your enrolled courses, view assignments, and track your practice progress."
        primaryAction={
          <Button
            size="sm"
            onClick={() => setIsJoinModalOpen(true)}
            className="h-10 px-4 bg-slate-900 text-white hover:bg-slate-800 rounded-xl font-semibold text-xs shadow-xs"
          >
            <Plus size={15} className="mr-1.5 shrink-0" aria-hidden="true" />
            Join Class
          </Button>
        }
      />

      {/* 02 / Student Learning Summary Statistics */}
      <ClassroomStatsGrid stats={stats} />

      {/* Join Result Feedback Banner */}
      {feedback && (
        <div
          className={`mb-6 p-4 rounded-xl border flex items-start gap-3 ${
            feedback.outcome === 'PENDING_APPROVAL'
              ? 'bg-amber-50 border-amber-200 text-amber-900'
              : 'bg-emerald-50 border-emerald-200 text-emerald-900'
          }`}
        >
          {feedback.outcome === 'PENDING_APPROVAL' ? (
            <Info size={18} className="mt-0.5 text-amber-600 shrink-0" />
          ) : (
            <CheckCircle size={18} className="mt-0.5 text-emerald-600 shrink-0" />
          )}
          <div className="flex-1">
            <p className="font-semibold text-sm">
              {feedback.outcome === 'PENDING_APPROVAL'
                ? 'Join Request Submitted'
                : 'Enrollment Successful'}
            </p>
            <p className="text-xs mt-0.5">{feedback.message}</p>
          </div>
          <button
            type="button"
            onClick={() => setFeedback(null)}
            className="text-xs underline hover:no-underline font-medium ml-2"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* 02 / Filter Toolbar */}
      <div className="classroom-toolbar">
        <div className="classroom-tabs" role="tablist" aria-label="Classroom scope filter">
          <button
            type="button"
            className="classroom-tab-btn"
            data-active={tab === 'ENROLLED'}
            onClick={() => setTab('ENROLLED')}
            role="tab"
          >
            Enrolled Classes ({enrolledClassrooms.length})
          </button>
          <button
            type="button"
            className="classroom-tab-btn"
            data-active={tab === 'ALL'}
            onClick={() => setTab('ALL')}
            role="tab"
          >
            All Courses ({classrooms.length})
          </button>
        </div>

        <div className="classroom-search-box">
          <Search size={14} className="classroom-search-icon" aria-hidden="true" />
          <input
            type="text"
            placeholder="Search classes by name, instructor, or code..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="classroom-search-input"
            aria-label="Filter student classrooms"
          />
        </div>
      </div>

      {/* 03 / Classroom Card Grid */}
      {filteredClassrooms.length === 0 ? (
        <div className="classroom-empty-state">
          <div className="classroom-empty-icon">
            <BookOpen size={24} />
          </div>
          <h3 className="classroom-empty-title">
            {tab === 'ENROLLED' ? 'You are not enrolled in any classes' : 'No courses found'}
          </h3>
          <p className="classroom-empty-desc">
            {tab === 'ENROLLED'
              ? 'Enter an 8-character class code provided by your instructor to join a classroom.'
              : 'No classrooms matched your query. Try a different search term.'}
          </p>
          {tab === 'ENROLLED' && (
            <Button
              size="sm"
              onClick={() => setIsJoinModalOpen(true)}
              className="mt-3 bg-slate-900 text-white hover:bg-slate-800 rounded-xl font-semibold text-xs"
            >
              <Plus size={14} className="mr-1.5" />
              Join with Class Code
            </Button>
          )}
        </div>
      ) : (
        <div className="classroom-card-grid">
          {filteredClassrooms.map(c => (
            <ClassroomCard
              key={c.id}
              classroom={c}
              currentRole={currentRole}
              currentPersona={currentPersona}
              onLeave={id => {
                const target = classrooms.find(item => item.id === id);
                if (target) setLeaveTarget(target);
              }}
            />
          ))}
        </div>
      )}

      {/* 04 / Modals */}
      <JoinClassroomModal
        isOpen={isJoinModalOpen}
        onClose={() => setIsJoinModalOpen(false)}
        onJoin={code => {
          const res = joinByCode(code);
          setFeedback(res);
          return res;
        }}
        onSuccess={classroomId => {
          navigate(`/student/classrooms/${classroomId}`);
        }}
      />

      <ConfirmationModal
        isOpen={Boolean(leaveTarget)}
        title="Leave Classroom"
        message={`Are you sure you want to leave "${leaveTarget?.name}"? Your submission history and grades will be safely preserved, and you can rejoin at any time.`}
        confirmLabel="Leave Classroom"
        variant="danger"
        onConfirm={handleLeaveConfirm}
        onCancel={() => setLeaveTarget(null)}
      />
    </ClassroomLayout>
  );
};
