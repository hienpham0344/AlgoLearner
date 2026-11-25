import React, { useState } from 'react';
import { Link, useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { ClassroomLayout } from '../../components/ClassroomLayout';
import { ClassroomSummaryCard } from '../../components/ClassroomSummaryCard';
import { ConfirmationModal } from '../../components/ConfirmationModal';
import { ClassroomAnnouncementsList } from '../../components/ClassroomAnnouncementsList';
import { UserAvatar } from '../../components/UserAvatar';
import { AvailabilityBadge } from '../../components/AvailabilityBadge';
import { useClassroomStore } from '../../mock/useClassroomStore';
import { getLessonCountForClassroom, lessonMockStore } from '../../lessons/mock/lessonMockStore';
import { ArrowLeft, Users, LogOut, Calendar, BookOpen, BookOpenText, Bell, Pin, CheckCircle2, ArrowUpRight, FileText } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';

export const StudentClassroomDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const {
    currentRole,
    currentPersona,
    getClassroom,
    leaveClassroom,
    getAnnouncements,
    getStudentNotifications,
    markNotificationRead,
  } = useClassroomStore('STUDENT');

  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab')?.toUpperCase();
  const announcementIdParam = searchParams.get('announcementId');

  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'ANNOUNCEMENTS' | 'CLASSMATES'>(() => {
    if (tabParam === 'ANNOUNCEMENTS' || announcementIdParam) return 'ANNOUNCEMENTS';
    if (tabParam === 'CLASSMATES') return 'CLASSMATES';
    return 'OVERVIEW';
  });

  React.useEffect(() => {
    if (tabParam === 'ANNOUNCEMENTS' || announcementIdParam) {
      setActiveTab('ANNOUNCEMENTS');
    }
  }, [tabParam, announcementIdParam]);
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
  const classroomAnnouncements = getAnnouncements(classroom.id);
  const publishedLessons = lessonMockStore.list(classroom.id, 'STUDENT', currentPersona.id, 0).items;
  const completedLessonsCount = publishedLessons.filter(l => l.progress?.completed).length;
  const studentNotifs = getStudentNotifications(currentPersona.id);
  const unreadAnnouncementNotif = studentNotifs.find(
    n => n.classroomId === classroom.id && !n.isRead,
  );

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

      {/* In-App Announcement Notification Alert Banner (FR-ANN-003 & BR-ANN-002) */}
      {unreadAnnouncementNotif && (
        <aside
          aria-label="New Course Announcement Alert"
          className="mb-5 p-4 rounded-xl bg-blue-50/90 border border-blue-200/90 text-blue-900 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-3 animate-in fade-in"
        >
          <div className="flex items-start sm:items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-blue-600 text-white flex items-center justify-center shrink-0 shadow-xs">
              <Bell size={16} />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-blue-900 uppercase tracking-wider">
                  New Course Announcement
                </span>
                <span className="w-2 h-2 rounded-full bg-blue-600 animate-pulse" />
              </div>
              <p className="text-xs text-blue-800 font-medium mt-0.5">
                {unreadAnnouncementNotif.message}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 self-end sm:self-center">
            <Button
              size="sm"
              onClick={() => {
                markNotificationRead(unreadAnnouncementNotif.id);
                setActiveTab('ANNOUNCEMENTS');
                setSearchParams({ tab: 'announcements', announcementId: unreadAnnouncementNotif.announcementId });
              }}
              className="bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-xl h-8 px-3"
            >
              View Announcement →
            </Button>
            <button
              type="button"
              onClick={() => markNotificationRead(unreadAnnouncementNotif.id)}
              className="text-xs font-medium text-blue-600 hover:text-blue-900 px-2 py-1"
            >
              Dismiss
            </button>
          </div>
        </aside>
      )}

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
          Overview & Curriculum
        </button>
        {isEnrolled && (
          <Link
            to={`/student/classrooms/${encodeURIComponent(classroom.id)}/lessons`}
            className="classroom-tab-btn"
            role="tab"
          >
            <BookOpenText size={14} className="inline mr-1.5" />
            Class lessons ({getLessonCountForClassroom(classroom.id, 'STUDENT')})
          </Link>
        )}
        {isEnrolled && (
          <Link
            to={`/student/classrooms/${encodeURIComponent(classroom.id)}/assignments`}
            className="classroom-tab-btn"
            role="tab"
          >
            <FileText size={14} className="inline mr-1.5" />
            Class assignments
          </Link>
        )}
        {isEnrolled && (
          <Link
            to={`/student/classrooms/${encodeURIComponent(classroom.id)}/quizzes`}
            className="classroom-tab-btn"
            role="tab"
          >
            <CheckCircle2 size={14} className="inline mr-1.5" />
            Class quizzes
          </Link>
        )}
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
          data-active={activeTab === 'CLASSMATES'}
          onClick={() => {
            setActiveTab('CLASSMATES');
            setSearchParams({});
          }}
        >
          <Users size={14} className="inline mr-1.5" />
          Classmates ({activeMembers.length})
        </button>
      </div>

      {/* 04 / Tab Panels */}
      {activeTab === 'ANNOUNCEMENTS' && (
        <ClassroomAnnouncementsList
          classroomId={classroom.id}
          isTeacherOrAdmin={false}
          announcements={classroomAnnouncements}
          highlightedId={announcementIdParam}
        />
      )}

      {activeTab === 'OVERVIEW' && (() => {
        const nextLesson = publishedLessons.find(l => !l.progress?.completed) || null;
        const progressPercent =
          publishedLessons.length > 0
            ? Math.round((completedLessonsCount / publishedLessons.length) * 100)
            : 0;

        return (
          <div className="classroom-detail-grid">
            {/* Left Primary Column: Learning Curriculum First */}
            <div className="classroom-detail-main">
              <div className="classroom-glass-card p-6">
                <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
                  <div>
                    <h2 className="text-base font-bold text-slate-900">Learning Modules &amp; Practice</h2>
                    <p className="text-xs text-slate-600 mt-0.5">
                      Published curriculum lessons synced with this classroom
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="text-xs text-slate-700 font-semibold px-2.5 py-1 rounded-full bg-slate-100 border border-slate-300 font-mono">
                      {publishedLessons.length} Published · {completedLessonsCount} Completed
                    </span>
                    {isEnrolled && (
                      <Link
                        to={`/student/classrooms/${encodeURIComponent(classroom.id)}/lessons`}
                        className="text-xs font-bold text-slate-900 hover:underline inline-flex items-center gap-1"
                      >
                        View all →
                      </Link>
                    )}
                  </div>
                </div>

                {publishedLessons.length === 0 ? (
                  <div className="p-5 rounded-xl border border-dashed border-slate-300 bg-slate-50 text-center">
                    <p className="text-sm font-semibold text-slate-800">No published lessons yet</p>
                    <p className="text-xs text-slate-600 mt-1">
                      Lessons published by {classroom.ownerName} will appear here automatically.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {publishedLessons.slice(0, 6).map((lesson, idx) => {
                      const isDone = Boolean(lesson.progress?.completed);
                      const subtitle = `${
                        lesson.completionMode === 'MANUAL'
                          ? 'Manual completion'
                          : 'Linked activity required'
                      } · ${lesson.attachments.length} ${lesson.attachments.length === 1 ? 'material' : 'materials'}`;

                      const rowBody = (
                        <>
                          <div className="flex items-center gap-3.5 min-w-0">
                            <div className="classroom-module-number-card" aria-hidden="true">
                              {String(idx + 1).padStart(2, '0')}
                            </div>
                            <div className="min-w-0">
                              <h3 className="text-sm font-bold text-slate-900 truncate">
                                {lesson.title}
                              </h3>
                              <p className="text-xs text-slate-600 truncate">{subtitle}</p>
                            </div>
                          </div>
                          <div className="flex items-center gap-2 shrink-0">
                            {!isEnrolled ? (
                              <AvailabilityBadge status="UNAVAILABLE" />
                            ) : isDone ? (
                              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200 text-[11px] font-bold">
                                <CheckCircle2 size={12} aria-hidden="true" />
                                Completed
                              </span>
                            ) : (
                              <AvailabilityBadge status="AVAILABLE" />
                            )}
                            {isEnrolled && <ArrowUpRight size={15} className="text-slate-500" aria-hidden="true" />}
                          </div>
                        </>
                      );

                      return isEnrolled ? (
                        <Link
                          key={lesson.id}
                          to={`/student/classrooms/${encodeURIComponent(classroom.id)}/lessons/${encodeURIComponent(lesson.id)}`}
                          className="p-4 rounded-xl border border-slate-200 bg-white/70 hover:bg-white hover:border-slate-300 flex items-center justify-between gap-3 transition-all"
                        >
                          {rowBody}
                        </Link>
                      ) : (
                        <div
                          key={lesson.id}
                          className="p-4 rounded-xl border border-slate-200 bg-slate-50 flex items-center justify-between gap-3 opacity-80"
                        >
                          {rowBody}
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>

            {/* Right Supporting Rail: Progress + Next Action + Announcements */}
            <aside className="classroom-detail-rail">
              <div className="classroom-glass-card p-5 space-y-4">
                <div className="flex items-baseline justify-between gap-2">
                  <span className="text-[10.5px] font-mono font-bold uppercase tracking-wider text-slate-500">
                    Course Progress
                  </span>
                  <span className="text-lg font-extrabold text-slate-900 font-mono">
                    {progressPercent}%
                  </span>
                </div>
                <div className="w-full h-2 rounded-full bg-slate-200 overflow-hidden" aria-hidden="true">
                  <div
                    className="h-full rounded-full transition-all duration-300"
                    style={{
                      width: `${progressPercent}%`,
                      backgroundColor: progressPercent >= 100 && publishedLessons.length > 0 ? '#059669' : '#18181b',
                    }}
                  />
                </div>
                <p className="text-xs text-slate-600">
                  {completedLessonsCount} of {publishedLessons.length} published lessons completed
                </p>

                {isEnrolled && nextLesson && (
                  <div className="pt-3 border-t border-slate-200 space-y-2.5">
                    <span className="text-[10.5px] font-mono font-bold uppercase tracking-wider text-slate-500 block">
                      Up next
                    </span>
                    <p className="text-xs font-bold text-slate-900 line-clamp-2">
                      {nextLesson.title}
                    </p>
                    <Link
                      to={`/student/classrooms/${encodeURIComponent(classroom.id)}/lessons/${encodeURIComponent(nextLesson.id)}`}
                      className="inline-flex items-center justify-center gap-1.5 w-full px-3.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold transition-colors"
                    >
                      <span>Continue next lesson</span>
                      <ArrowUpRight size={14} aria-hidden="true" />
                    </Link>
                  </div>
                )}
              </div>

              {classroomAnnouncements.length > 0 && (
                <div className="classroom-glass-card p-5 space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <Bell size={14} className="text-slate-700" aria-hidden="true" />
                      <h2 className="text-xs font-bold text-slate-900 uppercase tracking-wide">
                        Instructor Announcements
                      </h2>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setActiveTab('ANNOUNCEMENTS');
                        setSearchParams({ tab: 'announcements' });
                      }}
                      className="text-xs font-bold text-slate-900 hover:underline flex items-center gap-1"
                    >
                      View All ({classroomAnnouncements.length}) →
                    </button>
                  </div>

                  <div
                    onClick={() => {
                      setActiveTab('ANNOUNCEMENTS');
                      setSearchParams({ tab: 'announcements', announcementId: classroomAnnouncements[0].id });
                    }}
                    className="p-3.5 rounded-xl border border-slate-200 bg-slate-50 hover:bg-white cursor-pointer transition-all"
                  >
                    <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                      {classroomAnnouncements[0].isPinned && (
                        <span className="inline-flex items-center gap-1 text-[10px] font-bold px-2 py-0.5 rounded-full bg-amber-50 text-amber-800 border border-amber-200">
                          <Pin size={10} className="rotate-45 fill-amber-500 text-amber-600" />
                          Pinned Notice
                        </span>
                      )}
                      <h3 className="text-xs font-bold text-slate-900">
                        {classroomAnnouncements[0].title}
                      </h3>
                      <span className="text-[11px] text-slate-500 ml-auto">
                        {new Date(classroomAnnouncements[0].createdAt).toLocaleDateString()}
                      </span>
                    </div>
                    <p className="text-xs text-slate-600 line-clamp-2 leading-relaxed">
                      {classroomAnnouncements[0].content.slice(0, 140)}...
                    </p>
                  </div>
                </div>
              )}

              <div className="classroom-glass-card p-5 flex items-center justify-between gap-3">
                <div>
                  <span className="text-[10.5px] font-mono font-bold uppercase tracking-wider text-slate-500 block">
                    Cohort
                  </span>
                  <span className="text-sm font-bold text-slate-900">
                    {activeMembers.length} active classmates
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => setActiveTab('CLASSMATES')}
                  className="text-xs font-bold text-indigo-700 hover:underline"
                >
                  View roster →
                </button>
              </div>
            </aside>
          </div>
        );
      })()}

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
