import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ClassroomLayout } from '../../components/ClassroomLayout';
import { ClassroomSummaryCard } from '../../components/ClassroomSummaryCard';
import { ConfirmationModal } from '../../components/ConfirmationModal';
import { UserAvatar } from '../../components/UserAvatar';
import { AvailabilityBadge } from '../../components/AvailabilityBadge';
import { useClassroomStore } from '../../mock/useClassroomStore';
import { ArrowLeft, Users, LogOut, Calendar, BookOpen } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';

export const StudentClassroomDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { currentRole, currentPersona, getClassroom, leaveClassroom } =
    useClassroomStore('STUDENT');

  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'CLASSMATES'>('OVERVIEW');
  const [isLeaveModalOpen, setIsLeaveModalOpen] = useState(false);

  const classroom = id ? getClassroom(id) : undefined;

  if (!classroom) {
    return (
      <ClassroomLayout role={currentRole} persona={currentPersona}>
        <div className="classroom-empty-state">
          <BookOpen size={36} className="text-slate-400 mb-3" />
          <h2 className="classroom-empty-title">Classroom Not Found</h2>
          <p className="classroom-empty-desc">
            The requested classroom does not exist or you may not have permission to view it.
          </p>
          <Button
            size="sm"
            onClick={() => navigate('/student/classrooms')}
            className="mt-4 bg-slate-900 text-white rounded-xl"
          >
            <ArrowLeft size={14} className="mr-1.5 shrink-0" />
            Back to My Classrooms
          </Button>
        </div>
      </ClassroomLayout>
    );
  }

  const isEnrolled = classroom.members.some(
    m => m.studentId === currentPersona.id && m.status === 'ACTIVE',
  );

  const activeMembers = classroom.members.filter(m => m.status === 'ACTIVE');

  const handleConfirmLeave = () => {
    leaveClassroom(classroom.id);
    setIsLeaveModalOpen(false);
    navigate('/student/classrooms');
  };

  return (
    <ClassroomLayout role={currentRole} persona={currentPersona}>
      {/* 01 / Navigation breadcrumb */}
      <div className="mb-4">
        <button
          type="button"
          onClick={() => navigate('/student/classrooms')}
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors"
        >
          <ArrowLeft size={14} className="shrink-0" />
          Back to My Classrooms
        </button>
      </div>

      {/* 02 / Prominent Classroom Summary Card */}
      <ClassroomSummaryCard
        classroom={classroom}
        currentRole="STUDENT"
        currentUserId={currentPersona.id}
        actions={
          isEnrolled ? (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsLeaveModalOpen(true)}
              className="text-xs font-semibold text-rose-700 border-rose-200 hover:bg-rose-50 rounded-xl"
            >
              <LogOut size={14} className="mr-1.5 shrink-0" />
              Leave Classroom
            </Button>
          ) : undefined
        }
      />

      {/* 03 / Workspace Navigation Tabs */}
      <div className="classroom-tabs mb-6" role="tablist">
        <button
          type="button"
          role="tab"
          className="classroom-tab-btn"
          data-active={activeTab === 'OVERVIEW'}
          onClick={() => setActiveTab('OVERVIEW')}
        >
          <BookOpen size={14} className="inline mr-1.5" />
          Overview & Curriculum
        </button>
        <button
          type="button"
          role="tab"
          className="classroom-tab-btn"
          data-active={activeTab === 'CLASSMATES'}
          onClick={() => setActiveTab('CLASSMATES')}
        >
          <Users size={14} className="inline mr-1.5" />
          Classmates ({activeMembers.length})
        </button>
      </div>

      {/* 04 / Tab Panels */}
      {activeTab === 'OVERVIEW' && (
        <div className="space-y-6">
          {/* Planned Modules Preview */}
          <div className="classroom-glass-card p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-bold text-slate-900">Learning Modules & Practice</h2>
              <span className="text-xs text-slate-600 font-medium px-2.5 py-0.5 rounded-full bg-slate-100 border border-slate-200/60 font-mono">
                2 Available · 1 Scheduled · 1 Locked
              </span>
            </div>
            <div className="space-y-3">
              <div className="p-4 rounded-xl border border-white/70 bg-white/50 flex items-center justify-between">
                <div className="flex items-center gap-3.5 min-w-0">
                  <div className="classroom-module-number-card" aria-hidden="true">
                    01
                  </div>
                  <div className="min-w-0">
                    <h3 className="text-sm font-bold text-slate-900 truncate">
                      Foundations of Asymptotic Analysis
                    </h3>
                    <p className="text-xs text-slate-500 truncate">
                      Big-O notation, space complexity, recursion tree analysis
                    </p>
                  </div>
                </div>
                <AvailabilityBadge status="AVAILABLE" />
              </div>

              <div className="p-4 rounded-xl border border-white/70 bg-white/50 flex items-center justify-between">
                <div className="flex items-center gap-3.5 min-w-0">
                  <div className="classroom-module-number-card" aria-hidden="true">
                    02
                  </div>
                  <div className="min-w-0">
                    <h3 className="text-sm font-bold text-slate-900 truncate">
                      Two Pointers & Sliding Window
                    </h3>
                    <p className="text-xs text-slate-500 truncate">
                      Array manipulation, string matching, test runner verification
                    </p>
                  </div>
                </div>
                <AvailabilityBadge status="AVAILABLE" />
              </div>

              <div className="p-4 rounded-xl border border-white/70 bg-white/50 flex items-center justify-between">
                <div className="flex items-center gap-3.5 min-w-0">
                  <div className="classroom-module-number-card" aria-hidden="true">
                    03
                  </div>
                  <div className="min-w-0">
                    <h3 className="text-sm font-bold text-slate-900 truncate">
                      Dynamic Programming on Trees & DAGs
                    </h3>
                    <p className="text-xs text-slate-500 truncate">
                      Memoization vs Tabulation, automated judge test cases
                    </p>
                  </div>
                </div>
                <AvailabilityBadge status="SCHEDULED" />
              </div>

              <div className="p-4 rounded-xl border border-white/70 bg-white/50 flex items-center justify-between opacity-85">
                <div className="flex items-center gap-3.5 min-w-0">
                  <div className="classroom-module-number-card" aria-hidden="true">
                    04
                  </div>
                  <div className="min-w-0">
                    <h3 className="text-sm font-bold text-slate-900 truncate">
                      Advanced Graph Theory & Network Flow
                    </h3>
                    <p className="text-xs text-slate-500 truncate">
                      Push-relabel, bipartite matching, min-cut (Requires prerequisite completion)
                    </p>
                  </div>
                </div>
                <AvailabilityBadge status="UNAVAILABLE" />
              </div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'CLASSMATES' && (
        <div className="classroom-glass-card p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-base font-bold text-slate-900">Enrolled Classmates</h2>
              <p className="text-xs text-slate-500">
                Peers participating in this classroom
              </p>
            </div>
            <span className="text-xs font-semibold px-2.5 py-1 bg-slate-100 text-slate-700 rounded-lg">
              {activeMembers.length} Active Students
            </span>
          </div>

          <div className="divide-y divide-slate-100">
            {activeMembers.map((m, index) => {
              const isCurrentUser = m.studentId === currentPersona.id;
              const presence: 'ACTIVE' | 'OFFLINE' =
                isCurrentUser || index % 2 === 0 ? 'ACTIVE' : 'OFFLINE';

              return (
                <div
                  key={m.studentId}
                  className="py-3 flex items-center justify-between gap-4"
                >
                  <div className="flex items-center gap-3">
                    <UserAvatar name={m.fullName} size="md" presence={presence} />
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold text-slate-900">
                          {m.fullName}
                        </span>
                        {isCurrentUser && (
                          <Badge variant="outline" className="text-[10px] py-0 px-1.5 bg-blue-50 text-blue-700 border-blue-200">
                            You
                          </Badge>
                        )}
                      </div>
                      <span className="text-xs text-slate-400">@{m.username}</span>
                    </div>
                  </div>

                  <div className="text-right">
                    <span className="text-xs text-slate-500 flex items-center gap-1 justify-end font-medium">
                      <Calendar size={12} className="text-slate-400 shrink-0" />
                      Joined {new Date(m.joinedAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Leave Confirmation Modal */}
      <ConfirmationModal
        isOpen={isLeaveModalOpen}
        title="Leave Classroom"
        message={`Are you sure you want to leave "${classroom.name}"? Your submission history and quiz grades will be safely preserved in the course records. You may rejoin later using the class code if joining remains open.`}
        confirmLabel="Leave Classroom"
        variant="danger"
        onConfirm={handleConfirmLeave}
        onCancel={() => setIsLeaveModalOpen(false)}
      />
    </ClassroomLayout>
  );
};
