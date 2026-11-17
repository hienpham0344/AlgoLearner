import React, { useState } from 'react';
import { useParams, Link, useSearchParams } from 'react-router-dom';
import { ClassroomLayout } from '../../components/ClassroomLayout';
import { ClassroomSummaryCard } from '../../components/ClassroomSummaryCard';
import { ClassroomCodePanel } from '../../components/ClassroomCodePanel';
import { ClassroomMembersList } from '../../components/ClassroomMembersList';
import { ClassroomJoinRequestsPanel } from '../../components/ClassroomJoinRequestsPanel';
import { ClassroomInvitationsPanel } from '../../components/ClassroomInvitationsPanel';
import { DirectAddStudentModal } from '../../components/DirectAddStudentModal';
import { InviteStudentModal } from '../../components/InviteStudentModal';
import { ConfirmationModal } from '../../components/ConfirmationModal';
import { ClassroomAnnouncementsList } from '../../components/ClassroomAnnouncementsList';
import { useClassroomStore } from '../../mock/useClassroomStore';
import {
  Users,
  KeyRound,
  BookOpen,
  Settings,
  FileCode,
  CheckCircle2,
  Archive,
  ShieldAlert,
  Plus,
  Bell,
  Pin,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';

export const TeacherClassroomDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();

  const {
    getClassroom,
    currentRole,
    currentPersona,
    students,
    updateClassroom,
    archiveClassroom,
    regenerateCode,
    updateJoinConfig,
    directAddMember,
    removeMember,
    approveJoinRequest,
    rejectJoinRequest,
    createInvitation,
    revokeInvitation,
    getAnnouncements,
    createAnnouncement,
    updateAnnouncement,
    deleteAnnouncement,
    togglePinAnnouncement,
  } = useClassroomStore('TEACHER');

  const classroom = id ? getClassroom(id) : undefined;

  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab')?.toUpperCase();
  const announcementIdParam = searchParams.get('announcementId');

  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'ANNOUNCEMENTS' | 'MEMBERS' | 'CODE' | 'SETTINGS'>(() => {
    if (tabParam === 'ANNOUNCEMENTS' || announcementIdParam) return 'ANNOUNCEMENTS';
    if (tabParam === 'MEMBERS') return 'MEMBERS';
    if (tabParam === 'CODE') return 'CODE';
    if (tabParam === 'SETTINGS') return 'SETTINGS';
    return 'OVERVIEW';
  });

  React.useEffect(() => {
    if (tabParam === 'ANNOUNCEMENTS' || announcementIdParam) {
      setActiveTab('ANNOUNCEMENTS');
    }
  }, [tabParam, announcementIdParam]);
  const [membersSubTab, setMembersSubTab] = useState<'ROSTER' | 'REQUESTS' | 'INVITATIONS'>('ROSTER');

  // Modals
  const [isDirectAddModalOpen, setIsDirectAddModalOpen] = useState(false);
  const [isInviteModalOpen, setIsInviteModalOpen] = useState(false);
  const [isArchiveConfirmOpen, setIsArchiveConfirmOpen] = useState(false);
  const [isRegenerateConfirmOpen, setIsRegenerateConfirmOpen] = useState(false);

  // Edit settings form
  const [editName, setEditName] = useState(classroom?.name || '');
  const [editDescription, setEditDescription] = useState(classroom?.description || '');
  const [settingsSaved, setSettingsSaved] = useState(false);

  if (!classroom) {
    return (
      <ClassroomLayout breadcrumbChild="Not Found" role={currentRole} persona={currentPersona}>
        <div className="classroom-empty-state">
          <h2 className="text-xl font-bold text-slate-900 mb-2">Classroom Not Found</h2>
          <p className="text-sm text-slate-500 mb-6">
            The requested classroom does not exist or may have been removed.
          </p>
          <Link to="/teacher/classrooms">
            <Button size="sm">Back to My Classrooms</Button>
          </Link>
        </div>
      </ClassroomLayout>
    );
  }

  // Permission Check: Teacher must own the classroom (BR-CLS-001 & BR-CLS-013)
  const isOwner = classroom.ownerId === currentPersona.id;
  if (!isOwner) {
    return (
      <ClassroomLayout breadcrumbChild="Access Restricted" role={currentRole} persona={currentPersona}>
        <div className="classroom-empty-state">
          <div className="classroom-empty-icon bg-rose-50 text-rose-600">
            <ShieldAlert size={24} />
          </div>
          <h2 className="text-xl font-bold text-slate-900 mb-2">Access Restricted</h2>
          <p className="text-sm text-slate-500 max-w-md mb-6">
            Teacher accounts can only access and manage classrooms they own. This classroom belongs to {classroom.ownerName}.
          </p>
          <Link to="/teacher/classrooms">
            <Button size="sm">Return to My Classrooms</Button>
          </Link>
        </div>
      </ClassroomLayout>
    );
  }

  const classroomAnnouncements = getAnnouncements(classroom.id);
  const isArchived = classroom.status === 'ARCHIVED';
  const pendingRequestsCount = classroom.joinRequests.filter(
    r => r.status === 'PENDING_APPROVAL',
  ).length;
  const activeMembersCount = classroom.members.filter(m => m.status === 'ACTIVE').length;

  const handleSaveSettings = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editName.trim()) return;
    updateClassroom(classroom.id, {
      name: editName.trim(),
      description: editDescription.trim(),
    });
    setSettingsSaved(true);
    setTimeout(() => setSettingsSaved(false), 2500);
  };

  return (
    <ClassroomLayout breadcrumbChild={classroom.name} role={currentRole} persona={currentPersona}>
      {/* 01 / Prominent Classroom Summary Card */}
      <ClassroomSummaryCard
        classroom={classroom}
        currentRole="TEACHER"
        actions={
          <div className="flex flex-wrap items-center gap-2">
            {classroom.status === 'ACTIVE' && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsDirectAddModalOpen(true)}
                className="text-xs font-semibold rounded-xl text-slate-700 hover:bg-slate-100"
              >
                <Plus size={14} className="mr-1.5 shrink-0" />
                Add Student
              </Button>
            )}

            {classroom.status === 'ACTIVE' && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsArchiveConfirmOpen(true)}
                className="text-xs font-semibold rounded-xl text-slate-600 hover:text-slate-900"
              >
                <Archive size={14} className="mr-1.5 shrink-0" />
                Archive
              </Button>
            )}
          </div>
        }
      />

      {/* 02 / Subnavigation Tabs */}
      <nav className="classroom-subnav" role="tablist" aria-label="Classroom sections">
        <button
          type="button"
          className="classroom-subnav-link"
          data-active={activeTab === 'OVERVIEW'}
          onClick={() => {
            setActiveTab('OVERVIEW');
            setSearchParams({});
          }}
          role="tab"
          aria-selected={activeTab === 'OVERVIEW'}
        >
          <BookOpen size={16} aria-hidden="true" />
          <span>Overview & Modules</span>
        </button>

        <button
          type="button"
          className="classroom-subnav-link"
          data-active={activeTab === 'ANNOUNCEMENTS'}
          onClick={() => {
            setActiveTab('ANNOUNCEMENTS');
            setSearchParams({ tab: 'announcements' });
          }}
          role="tab"
          aria-selected={activeTab === 'ANNOUNCEMENTS'}
        >
          <Bell size={16} aria-hidden="true" />
          <span>Announcements</span>
          <span className="classroom-subnav-badge">{classroomAnnouncements.length}</span>
        </button>

        <button
          type="button"
          className="classroom-subnav-link"
          data-active={activeTab === 'MEMBERS'}
          onClick={() => setActiveTab('MEMBERS')}
          role="tab"
          aria-selected={activeTab === 'MEMBERS'}
        >
          <Users size={16} aria-hidden="true" />
          <span>Members & Access</span>
          <span className="classroom-subnav-badge">{activeMembersCount}</span>
          {pendingRequestsCount > 0 && (
            <span className="px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-500 text-white">
              {pendingRequestsCount}
            </span>
          )}
        </button>

        <button
          type="button"
          className="classroom-subnav-link"
          data-active={activeTab === 'CODE'}
          onClick={() => setActiveTab('CODE')}
          role="tab"
          aria-selected={activeTab === 'CODE'}
        >
          <KeyRound size={16} aria-hidden="true" />
          <span>Class Code & Security</span>
        </button>

        <button
          type="button"
          className="classroom-subnav-link"
          data-active={activeTab === 'SETTINGS'}
          onClick={() => setActiveTab('SETTINGS')}
          role="tab"
          aria-selected={activeTab === 'SETTINGS'}
        >
          <Settings size={16} aria-hidden="true" />
          <span>Settings</span>
        </button>
      </nav>

      {/* 03 / Tab Contents */}
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
        <div className="space-y-8">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <div className="classroom-glass-card p-4">
              <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                Lessons
              </div>
              <div className="text-2xl font-black text-slate-900 mt-1">
                {classroom.stats?.lessonsCount ?? 12}
              </div>
            </div>
            <div className="classroom-glass-card p-4">
              <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                Assignments
              </div>
              <div className="text-2xl font-black text-slate-900 mt-1">
                {classroom.stats?.assignmentsCount ?? 5}
              </div>
            </div>
            <div className="classroom-glass-card p-4">
              <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                Quizzes
              </div>
              <div className="text-2xl font-black text-slate-900 mt-1">
                {classroom.stats?.quizzesCount ?? 3}
              </div>
            </div>
            <div className="classroom-glass-card p-4">
              <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                Announcements
              </div>
              <div className="text-2xl font-black text-slate-900 mt-1">
                {classroomAnnouncements.length}
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 space-y-6">
              <div className="classroom-glass-card p-6 space-y-3">
                <h3 className="text-sm font-bold text-slate-900 uppercase tracking-wide">
                  Course Syllabus & Objectives
                </h3>
                <p className="text-sm text-slate-700 leading-relaxed">
                  {classroom.description}
                </p>
              </div>

              <div className="classroom-glass-card p-6 space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-bold text-slate-900">
                    Teaching & Evaluation Hub
                  </h3>
                  <span className="text-xs text-slate-400">Integrated Modules</span>
                </div>

                <div className="space-y-3">
                  <div className="p-3.5 rounded-xl bg-white/50 border border-white/70 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-800 flex items-center justify-center">
                        <FileCode size={16} />
                      </div>
                      <div>
                        <strong className="text-xs font-bold text-slate-900 block">
                          Problem Set: Minimum Spanning Trees (Kruskal & Prim)
                        </strong>
                        <span className="text-[11px] text-slate-500">
                          Automated Code Judge (Java / Python)
                        </span>
                      </div>
                    </div>
                    <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 px-2.5 py-1 rounded-md border border-emerald-200">
                      Open for Submission
                    </span>
                  </div>

                  <div className="p-3.5 rounded-xl bg-white/50 border border-white/70 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-blue-100 text-blue-800 flex items-center justify-center">
                        <CheckCircle2 size={16} />
                      </div>
                      <div>
                        <strong className="text-xs font-bold text-slate-900 block">
                          Quiz 3: Asymptotic Complexity & Recurrence Relations
                        </strong>
                        <span className="text-[11px] text-slate-500">
                          Timed 30 mins · Auto-saving answers enabled
                        </span>
                      </div>
                    </div>
                    <span className="text-xs font-semibold text-slate-600 bg-slate-100 px-2.5 py-1 rounded-md">
                      Due Friday
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <div className="space-y-6">
              <div className="classroom-glass-card p-6 space-y-3">
                <span className="text-[10px] uppercase font-mono tracking-widest text-slate-500 block">
                  Class Code for Students
                </span>
                <div className="font-mono font-bold text-2xl tracking-wider text-slate-900">
                  {classroom.classCode}
                </div>
                <p className="text-xs text-slate-500">
                  {classroom.joinMode === 'AUTO_JOIN'
                    ? 'Students join immediately using this code.'
                    : 'Requests require your approval.'}
                </p>
              </div>

              {/* Announcements Noticeboard Preview (FE 4.4) */}
              <div className="classroom-glass-card p-6 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] uppercase font-mono tracking-widest text-slate-500 block">
                    Announcements Noticeboard
                  </span>
                  <button
                    type="button"
                    onClick={() => {
                      setActiveTab('ANNOUNCEMENTS');
                      setSearchParams({ tab: 'announcements' });
                    }}
                    className="text-xs font-semibold text-slate-900 hover:underline"
                  >
                    View All ({classroomAnnouncements.length}) →
                  </button>
                </div>
                {classroomAnnouncements.length > 0 ? (
                  <div className="space-y-2 pt-1">
                    {classroomAnnouncements.slice(0, 2).map(ann => (
                      <div
                        key={ann.id}
                        onClick={() => {
                          setActiveTab('ANNOUNCEMENTS');
                          setSearchParams({ tab: 'announcements', announcementId: ann.id });
                        }}
                        className="p-3 rounded-xl bg-white/60 border border-slate-200/60 hover:bg-white cursor-pointer transition-colors"
                      >
                        <div className="flex items-center gap-1.5 mb-1">
                          {ann.isPinned && (
                            <span className="inline-flex items-center gap-0.5 text-[10px] font-bold text-amber-700 bg-amber-50 px-1.5 py-0.2 rounded border border-amber-200/70">
                              <Pin size={9} className="rotate-45" /> Pinned
                            </span>
                          )}
                          <span className="text-xs font-bold text-slate-900 truncate">
                            {ann.title}
                          </span>
                        </div>
                        <p className="text-[11px] text-slate-500 line-clamp-1">
                          {ann.content.slice(0, 80)}
                        </p>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-500">No announcements published yet.</p>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'MEMBERS' && (
        <div className="space-y-6">
          <div className="flex items-center gap-2 border-b border-slate-200 pb-3">
            <button
              type="button"
              onClick={() => setMembersSubTab('ROSTER')}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                membersSubTab === 'ROSTER'
                  ? 'bg-slate-900 text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
              }`}
            >
              Enrolled Students ({activeMembersCount})
            </button>

            <button
              type="button"
              onClick={() => setMembersSubTab('REQUESTS')}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all flex items-center gap-1.5 ${
                membersSubTab === 'REQUESTS'
                  ? 'bg-slate-900 text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
              }`}
            >
              <span>Join Requests</span>
              {pendingRequestsCount > 0 && (
                <span className="px-1.5 py-0.2 rounded-full text-[10px] font-bold bg-amber-500 text-white">
                  {pendingRequestsCount}
                </span>
              )}
            </button>

            <button
              type="button"
              onClick={() => setMembersSubTab('INVITATIONS')}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                membersSubTab === 'INVITATIONS'
                  ? 'bg-slate-900 text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
              }`}
            >
              Invitations ({classroom.invitations.length})
            </button>
          </div>

          {membersSubTab === 'ROSTER' && (
            <ClassroomMembersList
              members={classroom.members}
              isOwnerOrAdmin={true}
              isArchived={isArchived}
              onRemoveMember={studentId => removeMember(classroom.id, studentId)}
              onOpenDirectAddModal={() => setIsDirectAddModalOpen(true)}
            />
          )}

          {membersSubTab === 'REQUESTS' && (
            <ClassroomJoinRequestsPanel
              requests={classroom.joinRequests}
              isOwnerOrAdmin={true}
              isArchived={isArchived}
              onApprove={requestId => approveJoinRequest(classroom.id, requestId)}
              onReject={requestId => rejectJoinRequest(classroom.id, requestId)}
            />
          )}

          {membersSubTab === 'INVITATIONS' && (
            <ClassroomInvitationsPanel
              invitations={classroom.invitations}
              isOwnerOrAdmin={true}
              isArchived={isArchived}
              onRevokeInvitation={invitationId =>
                revokeInvitation(classroom.id, invitationId)
              }
              onOpenInviteModal={() => setIsInviteModalOpen(true)}
            />
          )}
        </div>
      )}

      {activeTab === 'CODE' && (
        <ClassroomCodePanel
          classroom={classroom}
          isOwnerOrAdmin={true}
          onRegenerateCode={() => setIsRegenerateConfirmOpen(true)}
          onUpdateConfig={config => updateJoinConfig(classroom.id, config)}
        />
      )}

      {activeTab === 'SETTINGS' && (
        <div className="space-y-8">
          <form
            onSubmit={handleSaveSettings}
            className="classroom-glass-card p-6 space-y-4"
          >
            <div className="border-b border-slate-100 pb-3 flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-slate-900">
                  Classroom Information
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  Update course title and syllabus description.
                </p>
              </div>
              {settingsSaved && (
                <span className="text-xs text-emerald-600 font-semibold flex items-center gap-1">
                  <CheckCircle2 size={14} /> Saved Successfully
                </span>
              )}
            </div>

            <div className="space-y-1.5">
              <label htmlFor="teacher-edit-name" className="text-xs font-bold text-slate-800 uppercase tracking-wide">
                Classroom Name *
              </label>
              <input
                id="teacher-edit-name"
                type="text"
                disabled={isArchived}
                required
                maxLength={150}
                value={editName}
                onChange={e => setEditName(e.target.value)}
                className="w-full px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900"
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="teacher-edit-desc" className="text-xs font-bold text-slate-800 uppercase tracking-wide">
                Classroom Description
              </label>
              <textarea
                id="teacher-edit-desc"
                rows={4}
                disabled={isArchived}
                maxLength={2000}
                value={editDescription}
                onChange={e => setEditDescription(e.target.value)}
                className="w-full px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900 resize-none"
              />
            </div>

            <div className="flex justify-end pt-2">
              <Button
                type="submit"
                size="sm"
                disabled={isArchived}
                className="bg-slate-900 text-white"
              >
                Save Changes
              </Button>
            </div>
          </form>

          <div className="bg-white rounded-2xl border border-rose-200 p-6 space-y-4">
            <h3 className="text-sm font-bold text-rose-900">
              Classroom Lifecycle Management
            </h3>

            <div className="flex items-center justify-between py-3 border-t border-rose-100">
              <div>
                <strong className="text-xs font-bold text-slate-900 block">
                  Archive Classroom
                </strong>
                <span className="text-xs text-slate-500">
                  Transitions this classroom to read-only status while preserving all student submissions and grades.
                </span>
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={isArchived}
                onClick={() => setIsArchiveConfirmOpen(true)}
                className="text-amber-700 hover:bg-amber-50 border-amber-300"
              >
                <Archive size={14} className="mr-1.5" />
                {isArchived ? 'Already Archived' : 'Archive Classroom'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Modals */}
      <DirectAddStudentModal
        isOpen={isDirectAddModalOpen}
        students={students}
        onClose={() => setIsDirectAddModalOpen(false)}
        onDirectAdd={identifier => directAddMember(classroom.id, identifier)}
      />

      <InviteStudentModal
        isOpen={isInviteModalOpen}
        students={students}
        onClose={() => setIsInviteModalOpen(false)}
        onInvite={(email, expiresAt) =>
          createInvitation(classroom.id, email, expiresAt)
        }
      />

      <ConfirmationModal
        isOpen={isArchiveConfirmOpen}
        title="Archive Classroom?"
        description="Archiving sets this classroom to read-only status. All student rosters, past submissions, and gradebook evaluations remain permanently preserved."
        confirmLabel="Archive Classroom"
        variant="danger"
        onConfirm={() => {
          archiveClassroom(classroom.id);
          setIsArchiveConfirmOpen(false);
        }}
        onCancel={() => setIsArchiveConfirmOpen(false)}
      />

      <ConfirmationModal
        isOpen={isRegenerateConfirmOpen}
        title="Regenerate Class Code?"
        description="Generating a new Class Code immediately invalidates the previous code. Existing enrolled students are not affected."
        confirmLabel="Regenerate Now"
        variant="default"
        onConfirm={() => {
          regenerateCode(classroom.id);
          setIsRegenerateConfirmOpen(false);
        }}
        onCancel={() => setIsRegenerateConfirmOpen(false)}
      />
    </ClassroomLayout>
  );
};
