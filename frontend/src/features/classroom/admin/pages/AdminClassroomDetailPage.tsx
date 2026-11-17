import React, { useState } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { ClassroomLayout } from '../../components/ClassroomLayout';
import { ClassroomSummaryCard } from '../../components/ClassroomSummaryCard';
import { UserAvatar } from '../../components/UserAvatar';
import { TransferOwnershipModal } from '../../components/TransferOwnershipModal';
import { ConfirmationModal } from '../../components/ConfirmationModal';
import { ClassroomAnnouncementsList } from '../../components/ClassroomAnnouncementsList';
import { useClassroomStore } from '../../mock/useClassroomStore';
import {
  ArrowLeft,
  Users,
  Archive,
  Trash2,
  Share2,
  Calendar,
  Lock,
  Unlock,
  ShieldCheck,
  BookOpen,
  AlertTriangle,
  Bell,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';

export const AdminClassroomDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const {
    currentRole,
    currentPersona,
    teachers,
    getClassroom,
    archiveClassroom,
    deleteClassroom,
    transferOwnership,
    getAnnouncements,
    createAnnouncement,
    updateAnnouncement,
    deleteAnnouncement,
    togglePinAnnouncement,
  } = useClassroomStore('ADMIN');

  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab')?.toUpperCase();
  const announcementIdParam = searchParams.get('announcementId');

  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'ANNOUNCEMENTS' | 'MEMBERS' | 'SECURITY'>(() => {
    if (tabParam === 'ANNOUNCEMENTS' || announcementIdParam) return 'ANNOUNCEMENTS';
    if (tabParam === 'MEMBERS') return 'MEMBERS';
    if (tabParam === 'SECURITY') return 'SECURITY';
    return 'OVERVIEW';
  });

  React.useEffect(() => {
    if (tabParam === 'ANNOUNCEMENTS' || announcementIdParam) {
      setActiveTab('ANNOUNCEMENTS');
    }
  }, [tabParam, announcementIdParam]);
  const [isTransferModalOpen, setIsTransferModalOpen] = useState(false);
  const [isArchiveModalOpen, setIsArchiveModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const classroom = id ? getClassroom(id) : undefined;

  if (!classroom) {
    return (
      <ClassroomLayout role={currentRole} persona={currentPersona}>
        <div className="classroom-empty-state">
          <BookOpen size={36} className="text-slate-400 mb-3" />
          <h2 className="classroom-empty-title">Classroom Not Found</h2>
          <p className="classroom-empty-desc">
            The requested classroom record does not exist in the administrative database.
          </p>
          <Button
            size="sm"
            onClick={() => navigate('/admin/classrooms')}
            className="mt-4 bg-slate-900 text-white rounded-xl"
          >
            <ArrowLeft size={14} className="mr-1.5" />
            Back to Global List
          </Button>
        </div>
      </ClassroomLayout>
    );
  }

  const classroomAnnouncements = getAnnouncements(classroom.id);
  const canHardDelete = classroom.members.length === 0;

  const handleConfirmArchive = () => {
    archiveClassroom(classroom.id);
    setIsArchiveModalOpen(false);
  };

  const handleConfirmDelete = () => {
    try {
      deleteClassroom(classroom.id);
      setIsDeleteModalOpen(false);
      navigate('/admin/classrooms');
    } catch (err: unknown) {
      setDeleteError(err instanceof Error ? err.message : 'Deletion failed.');
      setIsDeleteModalOpen(false);
    }
  };

  return (
    <ClassroomLayout role={currentRole} persona={currentPersona}>
      {/* 01 / Navigation breadcrumb */}
      <div className="mb-4">
        <button
          type="button"
          onClick={() => navigate('/admin/classrooms')}
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors"
        >
          <ArrowLeft size={14} />
          Back to Global Classroom List
        </button>
      </div>

      {deleteError && (
        <div className="mb-4 p-4 rounded-xl border border-rose-200 bg-rose-50 text-rose-800 text-xs flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle size={16} className="text-rose-600" />
            <span>{deleteError}</span>
          </div>
          <button
            type="button"
            onClick={() => setDeleteError(null)}
            className="font-bold underline ml-2"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* 02 / Prominent Classroom Summary Card */}
      <ClassroomSummaryCard
        classroom={classroom}
        currentRole="ADMIN"
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsTransferModalOpen(true)}
              className="text-xs font-semibold rounded-xl text-slate-700 hover:bg-slate-100"
            >
              <Share2 size={14} className="mr-1.5 text-indigo-600 shrink-0" />
              Transfer Owner
            </Button>

            {classroom.status === 'ACTIVE' && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsArchiveModalOpen(true)}
                className="text-xs font-semibold rounded-xl text-amber-700 border-amber-200 hover:bg-amber-50"
              >
                <Archive size={14} className="mr-1.5 shrink-0" />
                Archive
              </Button>
            )}

            <Button
              variant="outline"
              size="sm"
              disabled={!canHardDelete}
              onClick={() => setIsDeleteModalOpen(true)}
              className={`text-xs font-semibold rounded-xl ${
                canHardDelete
                  ? 'text-rose-700 border-rose-200 hover:bg-rose-50'
                  : 'text-slate-400 border-slate-200 cursor-not-allowed opacity-60'
              }`}
              title={
                canHardDelete
                  ? 'Permanently delete this classroom'
                  : 'Classrooms with active or historical members cannot be deleted'
              }
            >
              <Trash2 size={14} className="mr-1.5 shrink-0" />
              Delete Classroom
            </Button>
          </div>
        }
      />

      {/* 03 / Workspace Navigation Tabs */}
      <div className="classroom-tabs mb-6" role="tablist">
        <button
          type="button"
          role="tab"
          className="classroom-tab-btn"
          data-active={activeTab === 'OVERVIEW'}
          onClick={() => {
            setActiveTab('OVERVIEW');
            setSearchParams({});
          }}
        >
          <BookOpen size={14} className="inline mr-1.5" />
          Administrative Overview
        </button>
        <button
          type="button"
          role="tab"
          className="classroom-tab-btn"
          data-active={activeTab === 'ANNOUNCEMENTS'}
          onClick={() => {
            setActiveTab('ANNOUNCEMENTS');
            setSearchParams({ tab: 'announcements' });
          }}
        >
          <Bell size={14} className="inline mr-1.5" />
          Announcements ({classroomAnnouncements.length})
        </button>
        <button
          type="button"
          role="tab"
          className="classroom-tab-btn"
          data-active={activeTab === 'MEMBERS'}
          onClick={() => setActiveTab('MEMBERS')}
        >
          <Users size={14} className="inline mr-1.5" />
          Membership Audit ({classroom.members.length})
        </button>
        <button
          type="button"
          role="tab"
          className="classroom-tab-btn"
          data-active={activeTab === 'SECURITY'}
          onClick={() => setActiveTab('SECURITY')}
        >
          <ShieldCheck size={14} className="inline mr-1.5" />
          Security & Access Settings
        </button>
      </div>

      {/* 04 / Tab Panels */}
      {activeTab === 'ANNOUNCEMENTS' && (
        <ClassroomAnnouncementsList
          classroomId={classroom.id}
          isTeacherOrAdmin={true}
          announcements={classroomAnnouncements}
          highlightedId={announcementIdParam}
          onCreate={data => createAnnouncement({ classroomId: classroom.id, ...data })}
          onUpdate={(annId, data) => updateAnnouncement(annId, data)}
          onDelete={annId => deleteAnnouncement(annId)}
          onTogglePin={annId => togglePinAnnouncement(annId)}
        />
      )}

      {activeTab === 'OVERVIEW' && (
        <div className="space-y-6">
          <div className="classroom-glass-card p-6">
            <h2 className="text-base font-bold text-slate-900 mb-2">Classroom Policy & Access Overview</h2>
            <div className="space-y-3 text-xs text-slate-600">
              <div className="p-3.5 rounded-xl bg-white/50 border border-white/70 flex items-start gap-2.5">
                <ShieldCheck size={16} className="text-emerald-600 mt-0.5 shrink-0" />
                <div>
                  <strong className="text-slate-800">Private Classroom Isolation:</strong> All student submissions, grades, and code executions are isolated strictly to this classroom instance.
                </div>
              </div>

              <div className="p-3.5 rounded-xl bg-white/50 border border-white/70 flex items-start gap-2.5">
                <ShieldCheck size={16} className="text-emerald-600 mt-0.5 shrink-0" />
                <div>
                  <strong className="text-slate-800">Deletion Safety Policy:</strong> Hard deletion is blocked if any member records exist. Current member count: {classroom.members.length}. {canHardDelete ? 'Classroom is empty and eligible for deletion.' : 'Classroom contains student history and can only be archived.'}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'MEMBERS' && (
        <div className="classroom-glass-card p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-base font-bold text-slate-900">Membership Audit</h2>
              <p className="text-xs text-slate-500">
                Active and historical enrollment records for this classroom
              </p>
            </div>
            <span className="text-xs font-semibold px-2.5 py-1 bg-slate-100 text-slate-700 rounded-lg">
              {classroom.members.length} Total Records
            </span>
          </div>

          {classroom.members.length === 0 ? (
            <div className="text-center py-10 text-slate-400 text-xs">
              No students have enrolled in this classroom yet.
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {classroom.members.map(m => (
                <div
                  key={m.studentId}
                  className="py-3 flex items-center justify-between gap-4"
                >
                  <div className="flex items-center gap-3">
                    <UserAvatar
                      name={m.fullName}
                      size="md"
                      presence={m.status === 'ACTIVE' ? 'ACTIVE' : 'OFFLINE'}
                    />
                    <div>
                      <span className="text-sm font-semibold text-slate-900 block">
                        {m.fullName}
                      </span>
                      <span className="text-xs text-slate-400">{m.email}</span>
                    </div>
                  </div>

                  <div className="text-right text-xs text-slate-500">
                    <span className="flex items-center gap-1 justify-end font-medium">
                      <Calendar size={12} className="text-slate-400" />
                      Joined {new Date(m.joinedAt).toLocaleDateString()}
                    </span>
                    {m.endedAt && (
                      <span className="text-rose-600 block text-[11px] font-medium">
                        Left on {new Date(m.endedAt).toLocaleDateString()}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {activeTab === 'SECURITY' && (
        <div className="classroom-glass-card p-6 space-y-6">
          <div>
            <h2 className="text-base font-bold text-slate-900">Security & Join Configuration</h2>
            <p className="text-xs text-slate-500">
              Class code access and enrollment rules
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="p-4 rounded-xl border border-white/70 bg-white/50">
              <span className="text-xs font-semibold text-slate-500 block mb-1">Class Code</span>
              <span className="font-mono text-xl font-bold tracking-widest text-slate-900">
                {classroom.classCode}
              </span>
              <p className="text-xs text-slate-500 mt-1">
                Unique 8-character code for student enrollment
              </p>
            </div>

            <div className="p-4 rounded-xl border border-white/70 bg-white/50">
              <span className="text-xs font-semibold text-slate-500 block mb-1">Join Policy</span>
              <div className="flex items-center gap-2">
                <Badge variant={classroom.joinMode === 'AUTO_JOIN' ? 'success' : 'warning'}>
                  {classroom.joinMode}
                </Badge>
                <Badge variant={classroom.joiningOpen ? 'outline' : 'danger'}>
                  {classroom.joiningOpen ? 'Joining Open' : 'Closed for Joining'}
                </Badge>
              </div>
              <p className="text-xs text-slate-500 mt-1">
                {classroom.joinMode === 'AUTO_JOIN'
                  ? 'Students join immediately upon entering code'
                  : 'Teacher approval required before student gains access'}
              </p>
            </div>

            <div className="p-4 rounded-xl border border-slate-200 bg-slate-50/50">
              <span className="text-xs font-semibold text-slate-500 block mb-1">Code Lock Status</span>
              <div className="flex items-center gap-2">
                {classroom.codeLocked ? (
                  <span className="inline-flex items-center gap-1.5 text-xs font-bold text-rose-700">
                    <Lock size={14} /> Locked (Enrollment Blocked)
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-700">
                    <Unlock size={14} /> Unlocked (Accepting Valid Code)
                  </span>
                )}
              </div>
            </div>

            <div className="p-4 rounded-xl border border-slate-200 bg-slate-50/50">
              <span className="text-xs font-semibold text-slate-500 block mb-1">Code Expiration</span>
              <span className="text-xs font-medium text-slate-800">
                {classroom.codeExpiresAt
                  ? new Date(classroom.codeExpiresAt).toLocaleString()
                  : 'No expiration date configured'}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Transfer Ownership Modal */}
      <TransferOwnershipModal
        isOpen={isTransferModalOpen}
        classroom={classroom}
        teachers={teachers}
        onClose={() => setIsTransferModalOpen(false)}
        onTransfer={targetTeacherId => {
          transferOwnership(classroom.id, targetTeacherId);
          setIsTransferModalOpen(false);
        }}
      />

      {/* Archive Modal */}
      <ConfirmationModal
        isOpen={isArchiveModalOpen}
        title="Archive Classroom"
        message={`Are you sure you want to archive "${classroom.name}"? The classroom and student submissions will be set to read-only.`}
        confirmLabel="Archive Classroom"
        variant="warning"
        onConfirm={handleConfirmArchive}
        onCancel={() => setIsArchiveModalOpen(false)}
      />

      {/* Hard Delete Modal */}
      <ConfirmationModal
        isOpen={isDeleteModalOpen}
        title="Permanently Delete Classroom"
        message={`Are you sure you want to permanently delete "${classroom.name}"? This action cannot be undone and is only permitted because this classroom has no enrolled students or submission records.`}
        confirmLabel="Delete Permanently"
        variant="danger"
        onConfirm={handleConfirmDelete}
        onCancel={() => setIsDeleteModalOpen(false)}
      />
    </ClassroomLayout>
  );
};
