import React, { useState } from 'react';
import { ClassroomLayout } from '../../components/ClassroomLayout';
import { ClassroomPageHeader } from '../../components/ClassroomPageHeader';
import { ClassroomCard } from '../../components/ClassroomCard';
import { ClassroomStatsGrid, StatCardItem } from '../../components/ClassroomStatsGrid';
import { CreateClassroomModal } from '../../components/CreateClassroomModal';
import { TransferOwnershipModal } from '../../components/TransferOwnershipModal';
import { ConfirmationModal } from '../../components/ConfirmationModal';
import { useClassroomStore } from '../../mock/useClassroomStore';
import { Classroom } from '../../types';
import { Search, Plus, BookOpen, Layers, CheckCircle2, Users, GraduationCap } from 'lucide-react';
import { Button } from '@/components/ui/Button';

export const AdminClassroomListPage: React.FC = () => {
  const {
    classrooms,
    currentRole,
    currentPersona,
    teachers,
    createClassroom,
    archiveClassroom,
    transferOwnership,
  } = useClassroomStore('ADMIN');

  const [search, setSearch] = useState('');
  const [tab, setTab] = useState<'ALL' | 'ACTIVE' | 'ARCHIVED'>('ALL');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [transferTarget, setTransferTarget] = useState<Classroom | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<Classroom | null>(null);

  const filteredClassrooms = classrooms.filter(c => {
    if (tab === 'ACTIVE' && c.status !== 'ACTIVE') return false;
    if (tab === 'ARCHIVED' && c.status !== 'ARCHIVED') return false;

    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return (
      c.name.toLowerCase().includes(q) ||
      c.description.toLowerCase().includes(q) ||
      c.ownerName.toLowerCase().includes(q) ||
      c.classCode.toLowerCase().includes(q)
    );
  });

  const handleConfirmArchive = () => {
    if (archiveTarget) {
      archiveClassroom(archiveTarget.id);
      setArchiveTarget(null);
    }
  };

  const activeCount = classrooms.filter(c => c.status === 'ACTIVE').length;
  const archivedCount = classrooms.filter(c => c.status === 'ARCHIVED').length;
  const totalStudents = classrooms.reduce(
    (acc, c) => acc + c.members.filter(m => m.status === 'ACTIVE').length,
    0,
  );

  const stats: StatCardItem[] = [
    {
      id: 'total-classrooms',
      label: 'Total Classrooms',
      value: classrooms.length,
      helperText: 'Platform-wide cohorts',
      icon: <Layers size={16} />,
    },
    {
      id: 'active-spaces',
      label: 'Active Spaces',
      value: activeCount,
      helperText: `${archivedCount} archived spaces`,
      icon: <CheckCircle2 size={16} />,
      badge: {
        text: 'Live',
        variant: 'active',
      },
    },
    {
      id: 'enrolled-students',
      label: 'Institutional Students',
      value: totalStudents,
      helperText: 'Active student roster seats',
      icon: <Users size={16} />,
    },
    {
      id: 'instructors',
      label: 'Faculty & Instructors',
      value: teachers.length,
      helperText: 'Authorized teaching staff',
      icon: <GraduationCap size={16} />,
    },
  ];

  return (
    <ClassroomLayout role={currentRole} persona={currentPersona}>
      {/* 01 / Page Header with Horizontally Aligned Primary Action */}
      <ClassroomPageHeader
        eyebrow="ADMINISTRATION"
        title="Global Classroom Administration"
        description="Overview and governance across all institutional classrooms and instructors."
        primaryAction={
          <Button
            size="sm"
            onClick={() => setIsCreateModalOpen(true)}
            className="h-10 px-4 bg-slate-900 text-white hover:bg-slate-800 rounded-xl font-semibold text-xs shadow-xs"
          >
            <Plus size={15} className="mr-1.5 shrink-0" aria-hidden="true" />
            Create for Teacher
          </Button>
        }
      />

      {/* 02 / Institutional Summary Statistics */}
      <ClassroomStatsGrid stats={stats} />

      {/* 03 / Filter Toolbar */}
      <div className="classroom-toolbar">
        <div className="classroom-tabs" role="tablist" aria-label="Classroom status filter">
          <button
            type="button"
            className="classroom-tab-btn"
            data-active={tab === 'ALL'}
            onClick={() => setTab('ALL')}
            role="tab"
          >
            All Classrooms ({classrooms.length})
          </button>
          <button
            type="button"
            className="classroom-tab-btn"
            data-active={tab === 'ACTIVE'}
            onClick={() => setTab('ACTIVE')}
            role="tab"
          >
            Active ({classrooms.filter(c => c.status === 'ACTIVE').length})
          </button>
          <button
            type="button"
            className="classroom-tab-btn"
            data-active={tab === 'ARCHIVED'}
            onClick={() => setTab('ARCHIVED')}
            role="tab"
          >
            Archived ({classrooms.filter(c => c.status === 'ARCHIVED').length})
          </button>
        </div>

        <div className="classroom-search-box">
          <Search size={14} className="classroom-search-icon" aria-hidden="true" />
          <input
            type="text"
            placeholder="Search across all classrooms, teachers, or codes..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="classroom-search-input"
            aria-label="Filter administrative classrooms"
          />
        </div>
      </div>

      {/* 03 / Classroom Grid */}
      {filteredClassrooms.length === 0 ? (
        <div className="classroom-empty-state">
          <div className="classroom-empty-icon">
            <BookOpen size={24} />
          </div>
          <h3 className="classroom-empty-title">No classrooms found</h3>
          <p className="classroom-empty-desc">
            {search
              ? 'No classroom records matched your administrative query.'
              : 'There are currently no classrooms configured on the platform.'}
          </p>
          {!search && (
            <Button
              size="sm"
              onClick={() => setIsCreateModalOpen(true)}
              className="mt-3 bg-slate-900 text-white hover:bg-slate-800 rounded-xl font-semibold text-xs"
            >
              <Plus size={14} className="mr-1.5" />
              Create First Classroom
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
              onArchive={id => {
                const target = classrooms.find(item => item.id === id);
                if (target) setArchiveTarget(target);
              }}
              onTransfer={id => {
                const target = classrooms.find(item => item.id === id);
                if (target) setTransferTarget(target);
              }}
            />
          ))}
        </div>
      )}

      {/* 04 / Modals */}
      <CreateClassroomModal
        isOpen={isCreateModalOpen}
        currentRole="ADMIN"
        teachers={teachers}
        onClose={() => setIsCreateModalOpen(false)}
        onSubmit={createClassroom}
      />

      {transferTarget && (
        <TransferOwnershipModal
          isOpen={Boolean(transferTarget)}
          classroom={transferTarget}
          teachers={teachers}
          onClose={() => setTransferTarget(null)}
          onTransfer={targetTeacherId => {
            transferOwnership(transferTarget.id, targetTeacherId);
            setTransferTarget(null);
          }}
        />
      )}

      <ConfirmationModal
        isOpen={Boolean(archiveTarget)}
        title="Archive Classroom (Admin)"
        message={`Are you sure you want to archive "${archiveTarget?.name}"? Active students and assignments will be set to read-only, and class code joining will be locked.`}
        confirmLabel="Archive Classroom"
        variant="warning"
        onConfirm={handleConfirmArchive}
        onCancel={() => setArchiveTarget(null)}
      />
    </ClassroomLayout>
  );
};
