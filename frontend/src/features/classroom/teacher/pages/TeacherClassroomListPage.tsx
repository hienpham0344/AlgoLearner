import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClassroomLayout } from '../../components/ClassroomLayout';
import { ClassroomPageHeader } from '../../components/ClassroomPageHeader';
import { ClassroomCard } from '../../components/ClassroomCard';
import { ClassroomStatsGrid, StatCardItem } from '../../components/ClassroomStatsGrid';
import { CreateClassroomModal } from '../../components/CreateClassroomModal';
import { ConfirmationModal } from '../../components/ConfirmationModal';
import { useClassroomStore } from '../../mock/useClassroomStore';
import { Search, Plus, BookOpen, Users, Clock, Archive } from 'lucide-react';
import { Button } from '@/components/ui/Button';

export const TeacherClassroomListPage: React.FC = () => {
  const navigate = useNavigate();
  const {
    classrooms,
    currentRole,
    currentPersona,
    teachers,
    createClassroom,
    archiveClassroom,
  } = useClassroomStore('TEACHER');

  const [search, setSearch] = useState('');
  const [tab, setTab] = useState<'OWNED' | 'ACTIVE' | 'ARCHIVED'>('OWNED');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [archiveTargetId, setArchiveTargetId] = useState<string | null>(null);

  // Filter classrooms owned by this Teacher
  const ownedClassrooms = classrooms.filter(c => c.ownerId === currentPersona.id);

  const filteredClassrooms = ownedClassrooms.filter(c => {
    if (tab === 'ACTIVE' && c.status !== 'ACTIVE') return false;
    if (tab === 'ARCHIVED' && c.status !== 'ARCHIVED') return false;

    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return (
      c.name.toLowerCase().includes(q) ||
      c.description.toLowerCase().includes(q) ||
      c.classCode.toLowerCase().includes(q)
    );
  });

  const activeCount = ownedClassrooms.filter(c => c.status === 'ACTIVE').length;
  const totalStudents = ownedClassrooms.reduce(
    (acc, c) => acc + c.members.filter(m => m.status === 'ACTIVE').length,
    0,
  );
  const pendingRequestsCount = ownedClassrooms.reduce(
    (acc, c) => acc + c.joinRequests.filter(r => r.status === 'PENDING_APPROVAL').length,
    0,
  );
  const archivedCount = ownedClassrooms.filter(c => c.status === 'ARCHIVED').length;

  const stats: StatCardItem[] = [
    {
      id: 'active-courses',
      label: 'Active Courses',
      value: activeCount,
      helperText: 'Instructional spaces in session',
      icon: <BookOpen size={16} />,
      badge: {
        text: 'Teaching',
        variant: 'active',
      },
    },
    {
      id: 'total-students',
      label: 'Students Teaching',
      value: totalStudents,
      helperText: 'Total active student enrollments',
      icon: <Users size={16} />,
    },
    {
      id: 'pending-requests',
      label: 'Pending Requests',
      value: pendingRequestsCount,
      helperText:
        pendingRequestsCount > 0
          ? 'Awaiting instructor review'
          : 'All join requests cleared',
      icon: <Clock size={16} />,
      badge:
        pendingRequestsCount > 0
          ? {
              text: `${pendingRequestsCount} Pending`,
              variant: 'active',
            }
          : undefined,
    },
    {
      id: 'archived-courses',
      label: 'Archived Courses',
      value: archivedCount,
      helperText: 'Previous semester archives',
      icon: <Archive size={16} />,
    },
  ];

  return (
    <ClassroomLayout role={currentRole} persona={currentPersona}>
      {/* 01 / Page Header with Horizontally Aligned Primary Action */}
      <ClassroomPageHeader
        eyebrow="INSTRUCTOR WORKSPACE"
        title="Classrooms & Teaching Spaces"
        description="Manage your courses, enrolled student rosters, and join settings."
        primaryAction={
          <Button
            size="sm"
            onClick={() => setIsCreateModalOpen(true)}
            className="h-10 px-4 bg-slate-900 text-white hover:bg-slate-800 rounded-xl font-semibold text-xs shadow-xs"
          >
            <Plus size={15} className="mr-1.5 shrink-0" aria-hidden="true" />
            Create Class
          </Button>
        }
      />

      {/* 02 / Teaching Summary Statistics */}
      <ClassroomStatsGrid stats={stats} />

      {/* 03 / Filter Toolbar */}
      <div className="classroom-toolbar">
        <div className="classroom-tabs" role="tablist" aria-label="Classroom status filter">
          <button
            type="button"
            className="classroom-tab-btn"
            data-active={tab === 'OWNED'}
            onClick={() => setTab('OWNED')}
            role="tab"
          >
            All Classes ({ownedClassrooms.length})
          </button>
          <button
            type="button"
            className="classroom-tab-btn"
            data-active={tab === 'ACTIVE'}
            onClick={() => setTab('ACTIVE')}
            role="tab"
          >
            Active ({ownedClassrooms.filter(c => c.status === 'ACTIVE').length})
          </button>
          <button
            type="button"
            className="classroom-tab-btn"
            data-active={tab === 'ARCHIVED'}
            onClick={() => setTab('ARCHIVED')}
            role="tab"
          >
            Archived ({ownedClassrooms.filter(c => c.status === 'ARCHIVED').length})
          </button>
        </div>

        <div className="classroom-search-box">
          <Search size={14} className="classroom-search-icon" aria-hidden="true" />
          <input
            type="text"
            placeholder="Search your classes by name or code..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="classroom-search-input"
            aria-label="Filter teaching classrooms"
          />
        </div>
      </div>

      {/* 03 / Classroom Card Grid */}
      {filteredClassrooms.length === 0 ? (
        <div className="classroom-empty-state">
          <div className="classroom-empty-icon">
            <BookOpen size={24} />
          </div>
          <h3 className="classroom-empty-title">No teaching classrooms found</h3>
          <p className="classroom-empty-desc">
            {search
              ? 'No classroom matches your search query. Try clearing your search term.'
              : 'You have not created any classrooms yet. Click "Create Class" to set up your first course.'}
          </p>
          {!search && (
            <div className="mt-4">
              <Button size="sm" onClick={() => setIsCreateModalOpen(true)}>
                <Plus size={14} className="mr-1.5" />
                Create Class
              </Button>
            </div>
          )}
        </div>
      ) : (
        <div className="classroom-card-grid">
          {filteredClassrooms.map(classroom => (
            <ClassroomCard
              key={classroom.id}
              classroom={classroom}
              currentRole={currentRole}
              currentPersona={currentPersona}
              onArchive={id => setArchiveTargetId(id)}
            />
          ))}
        </div>
      )}

      {/* 04 / Modals */}
      <CreateClassroomModal
        isOpen={isCreateModalOpen}
        currentRole="TEACHER"
        teachers={teachers}
        onClose={() => setIsCreateModalOpen(false)}
        onSubmit={input => {
          const created = createClassroom(input);
          navigate(`/teacher/classrooms/${created.id}`);
        }}
      />

      {archiveTargetId && (
        <ConfirmationModal
          isOpen={Boolean(archiveTargetId)}
          title="Archive Classroom?"
          description="Archiving transitions this classroom into a read-only state. All student rosters, past submissions, and gradebook evaluations remain safely preserved."
          confirmLabel="Archive Classroom"
          variant="danger"
          onConfirm={() => {
            if (archiveTargetId) archiveClassroom(archiveTargetId);
            setArchiveTargetId(null);
          }}
          onCancel={() => setArchiveTargetId(null)}
        />
      )}
    </ClassroomLayout>
  );
};
