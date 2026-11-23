import { clearSession } from '@/services/apiClient';
import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { useIsDemoMode } from '@/components/BackendStatusBanner';
import { WorkspaceLayout } from '@/components/layout/WorkspaceLayout';
import { DashboardSkeleton } from '../components/DashboardSkeleton';
import {
  dashboardApi,
  dashboardKeys,
  type AdminDashboardResponse,
  type TeacherDashboardResponse,
} from '../api/dashboardApi';
import { InteractiveCourseBackground } from '../components/InteractiveCourseBackground';
import { readStoredClassrooms, readStoredPersona } from '@/features/classroom/mock/useClassroomStore';
import { getClassroomLessonSummary, getLessonCountForClassroom } from '@/features/classroom/lessons/mock/lessonMockStore';
import '../styles/dashboard.css';
import '../styles/role-dashboard.css';

const DISPLAY_TIME_ZONE = 'Asia/Ho_Chi_Minh';
const VIETNAM_OFFSET_MS = 7 * 60 * 60 * 1000;

const shortDateFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  month: 'short',
  day: 'numeric',
});

const dayNumberFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  day: '2-digit',
});

const monthFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  month: 'short',
});

const timeFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  hour: 'numeric',
  minute: '2-digit',
});

const todayFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  weekday: 'long',
  month: 'short',
  day: 'numeric',
});

const toVietnamDateKey = (date: Date) =>
  new Date(date.getTime() + VIETNAM_OFFSET_MS).toISOString().slice(0, 10);

const getDeadlineUrgency = (dueAt: string): 'Overdue' | 'Due today' | 'Upcoming' => {
  const due = new Date(dueAt);
  if (due.getTime() < Date.now()) return 'Overdue';
  return toVietnamDateKey(due) === toVietnamDateKey(new Date()) ? 'Due today' : 'Upcoming';
};

const formatDayNumber = (iso: string) => dayNumberFormatter.format(new Date(iso));
const formatMonth = (iso: string) => monthFormatter.format(new Date(iso)).toUpperCase();
const formatScheduleTime = (iso: string) => timeFormatter.format(new Date(iso));
const formatShortDate = (iso: string) => shortDateFormatter.format(new Date(iso));

const formatRelativeTimestamp = (iso: string): string => {
  const createdMs = new Date(iso).getTime();
  const diffHours = Math.round((Date.now() - createdMs) / (60 * 60 * 1000));
  if (diffHours >= 0 && diffHours < 1) return 'Just now';
  if (diffHours >= 1 && diffHours < 24) return `${diffHours}h ago`;
  return formatShortDate(iso);
};

const formatSubmissionKind = (kind: string): string => {
  if (kind === 'ASSIGNMENT_WORK') return 'Assignment';
  if (kind === 'CODE_JUDGE') return 'Code judge';
  return kind;
};

const formatAuditAction = (action: string): string =>
  action
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');

const formatNumber = (value: number): string => {
  if (value > 0 && value < 10) return `0${value}`;
  return String(value);
};

function TeacherWorkspace({ data, firstName, isDemo }: { data: TeacherDashboardResponse; firstName: string; isDemo: boolean }) {
  const statsByClassId = new Map(data.classroomStatistics.items.map(stat => [stat.classroomId, stat]));
  const classNameById = new Map(data.classrooms.items.map(cls => [cls.classroomId, cls.name]));

  const totalActiveStudents = data.classroomStatistics.items.reduce(
    (sum, item) => sum + item.activeStudentCount,
    0,
  );
  const totalUngraded = data.classroomStatistics.items.reduce(
    (sum, item) => sum + item.ungradedWorkCount,
    0,
  );
  const totalPublishedAssignments = data.classroomStatistics.items.reduce(
    (sum, item) => sum + item.publishedAssignmentCount,
    0,
  );
  const totalPublishedQuizzes = data.classroomStatistics.items.reduce(
    (sum, item) => sum + item.publishedQuizCount,
    0,
  );

  const sortedAssignments = [...data.openOrUpcomingAssignments.items].sort(
    (a, b) => Date.parse(a.effectiveDueAt) - Date.parse(b.effectiveDueAt),
  );

  return (
    <>
      <div className="dashboard-intro">
        <div>
          <span className="dashboard-eyebrow">YOUR TEACHING SPACE</span>
          <h1>Keep the class moving.</h1>
          <p>Classes, student work, and updates in one focused view, {firstName}.</p>
        </div>
        <span className="dashboard-today">{todayFormatter.format(new Date())}</span>
      </div>

      <div className="dashboard-grid">
        <div className="dashboard-primary">
          {/* 01 / Featured Teaching Card */}
          <section aria-labelledby="teaching-overview-title" id="overview" className="dashboard-learning-section">
            <div className="dashboard-section-heading">
              <span className="dashboard-eyebrow">01 / TEACHING OVERVIEW</span>
              <h2 id="teaching-overview-title">Active term</h2>
            </div>

            <div className="dashboard-learning-card">
              <InteractiveCourseBackground />
              <div className="dashboard-learning-content">
                <span className="dashboard-learning-label">
                  INSTRUCTOR WORKSPACE · ACTIVE TERM
                </span>
                <h3>
                  {data.classrooms.totalElements}{' '}
                  {data.classrooms.totalElements === 1 ? 'active class' : 'active classes'}
                </h3>
                <p>
                  {totalActiveStudents} enrolled students · {totalUngraded} awaiting review
                </p>
                <p>Grading progress will appear once the submission module is connected.</p>
                <div className="flex flex-wrap items-center gap-4">
                  <Link className="dashboard-learning-action" to="/teacher/classrooms">
                    Manage classrooms &amp; lessons <ArrowRight size={15} aria-hidden="true" />
                  </Link>
                  <a className="inline-flex items-center gap-1 text-xs font-semibold text-white/80 hover:text-white underline underline-offset-4" href="#review">
                    Review submissions →
                  </a>
                </div>
              </div>
            </div>
          </section>

          {/* 02 / Open & Upcoming Assignments Timeline */}
          <section aria-labelledby="teacher-assignments-title" id="assignments" className="dashboard-section">
            <div className="dashboard-section-heading flex items-center justify-between">
              <div>
                <span className="dashboard-eyebrow">02 / PLANNING</span>
                <h2 id="teacher-assignments-title">Open & upcoming assignments</h2>
              </div>
              <Link to="/teacher/assignments" className="text-xs font-semibold text-slate-700 hover:text-slate-900 underline">
                Manage assignments →
              </Link>
            </div>

            {sortedAssignments.length ? (
              <div className="dashboard-timeline" role="list">
                {sortedAssignments.map(assignment => {
                  const urgency = getDeadlineUrgency(assignment.effectiveDueAt);
                  const isImmediate = urgency === 'Overdue' || urgency === 'Due today';

                  return (
                    <div
                      className="dashboard-timeline-item"
                      key={assignment.assignmentId}
                      role="listitem"
                      data-urgency={urgency}
                    >
                      <time className="dashboard-timeline-date" dateTime={assignment.effectiveDueAt}>
                        <strong>{formatDayNumber(assignment.effectiveDueAt)}</strong>
                        <span>{formatMonth(assignment.effectiveDueAt)}</span>
                      </time>
                      <div className="dashboard-timeline-axis" aria-hidden="true">
                        <span className="dashboard-timeline-node" data-immediate={isImmediate} />
                      </div>
                      <div className="dashboard-timeline-body">
                        <div className="dashboard-timeline-head">
                          <strong className="dashboard-timeline-title">{assignment.title}</strong>
                          <span className="dashboard-timeline-urgency" data-immediate={isImmediate}>
                            {urgency}
                          </span>
                        </div>
                        <p className="dashboard-timeline-meta">
                          <span>{assignment.classroomName}</span>
                          <span>Due {formatScheduleTime(assignment.effectiveDueAt)}</span>
                        </p>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="dashboard-empty dashboard-empty-line">
                <div>
                  <strong>No open or upcoming assignments.</strong>
                  <p>Published assignments for your active classrooms will appear here once connected.</p>
                </div>
              </div>
            )}
          </section>

          {/* 03 / My Classes */}
          <section aria-labelledby="teacher-classes-title" id="classes" className="dashboard-section">
            <div className="dashboard-section-heading flex items-center justify-between">
              <div>
                <span className="dashboard-eyebrow">03 / CLASSROOMS</span>
                <h2 id="teacher-classes-title">My classes</h2>
              </div>
              <Link to="/teacher/classrooms" className="text-xs font-semibold text-slate-700 hover:text-slate-900 underline">
                View all classrooms →
              </Link>
            </div>

            {data.classrooms.items.length ? (
              <div className="dashboard-class-list">
                {data.classrooms.items.map(classroom => {
                  const stat = statsByClassId.get(classroom.classroomId);
                  const totalPublished = stat
                    ? stat.publishedAssignmentCount + stat.publishedQuizCount
                    : 0;

                  return (
                    <Link
                      className="dashboard-class-row"
                      key={classroom.classroomId}
                      to={`/teacher/classrooms/${encodeURIComponent(classroom.classroomId)}`}
                    >
                      <div className="dashboard-class-main">
                        <div className="dashboard-class-header">
                          <strong>{classroom.name}</strong>
                          {stat && (
                            <span className="dashboard-class-code">
                              {stat.activeStudentCount} STUDENTS
                            </span>
                          )}
                        </div>
                        {stat && (
                          <span className="dashboard-class-next">
                            {isDemo
                              ? (() => {
                                  const lessonSummary = getClassroomLessonSummary(classroom.classroomId, 'TEACHER', '');
                                  return `${lessonSummary.publishedCount} published · ${lessonSummary.draftCount} draft lessons · ${stat.publishedQuizCount} quizzes`;
                                })()
                              : `${stat.publishedAssignmentCount} assignments · ${stat.publishedQuizCount} quizzes`}
                          </span>
                        )}
                      </div>

                      {stat && totalPublished > 0 && (
                        <div
                          className="dashboard-class-segments"
                          role="img"
                          aria-label={`${classroom.name}: ${stat.ungradedWorkCount} ungraded items`}
                        >
                          <span className="dashboard-class-segments-label">
                            {stat.ungradedWorkCount} to review
                          </span>
                          <div className="dashboard-class-segments-track" aria-hidden="true">
                            {Array.from({ length: Math.min(6, Math.max(3, totalPublished)) }, (_, idx) => {
                              const reviewedCount = Math.max(0, totalPublished - stat.ungradedWorkCount);
                              const state =
                                idx < reviewedCount
                                  ? 'completed'
                                  : idx === reviewedCount
                                    ? 'current'
                                    : 'upcoming';
                              return <span key={idx} data-state={state} />;
                            })}
                          </div>
                        </div>
                      )}
                    </Link>
                  );
                })}
              </div>
            ) : (
              <div className="dashboard-empty dashboard-empty-line">
                <div>
                  <strong>No active classrooms yet.</strong>
                  <p>Classrooms you manage will appear here.</p>
                </div>
              </div>
            )}
          </section>

          {/* 04 / Ungraded Work & Recent Submissions */}
          <section aria-labelledby="teacher-review-title" id="review" className="dashboard-section">
            <div className="dashboard-section-heading flex items-center justify-between">
              <div>
                <span className="dashboard-eyebrow">04 / REVIEW QUEUE</span>
                <h2 id="teacher-review-title">Recent submissions</h2>
              </div>
              <Link to="/teacher/review-queue" className="text-xs font-semibold text-slate-700 hover:text-slate-900 underline">
                Open review queue →
              </Link>
            </div>

            {data.ungradedSubmissions.items.length || data.recentSubmissions.items.length ? (
              <div className="role-ledger-list" role="list">
                {data.ungradedSubmissions.items.map(item => (
                  <div className="role-ledger-row" key={`ungraded-${item.submissionId}`} role="listitem">
                    <div className="role-ledger-main">
                      <div className="role-ledger-head">
                        <strong>@{item.studentUsername}</strong>
                        <span className="role-ledger-tag" data-emphasis="true">
                          Needs review
                        </span>
                      </div>
                      <p className="role-ledger-meta">
                        <span>{formatSubmissionKind(item.kind)}</span>
                        <span>{classNameById.get(item.classroomId) ?? 'Classroom'}</span>
                        <time dateTime={item.submittedAt}>
                          {formatRelativeTimestamp(item.submittedAt)}
                        </time>
                      </p>
                    </div>
                  </div>
                ))}

                {data.recentSubmissions.items
                  .filter(
                    recent =>
                      !data.ungradedSubmissions.items.some(
                        ungraded => ungraded.submissionId === recent.submissionId,
                      ),
                  )
                  .map(item => (
                    <div className="role-ledger-row" key={`recent-${item.submissionId}`} role="listitem">
                      <div className="role-ledger-main">
                        <div className="role-ledger-head">
                          <strong>@{item.studentUsername}</strong>
                          <span className="role-ledger-tag">Submitted</span>
                        </div>
                        <p className="role-ledger-meta">
                          <span>{formatSubmissionKind(item.kind)}</span>
                          <span>{classNameById.get(item.classroomId) ?? 'Classroom'}</span>
                          <time dateTime={item.submittedAt}>
                            {formatRelativeTimestamp(item.submittedAt)}
                          </time>
                        </p>
                      </div>
                    </div>
                  ))}
              </div>
            ) : (
              <div className="dashboard-empty dashboard-empty-line">
                <div>
                  <strong>No student submissions yet.</strong>
                  <p>Ungraded work and recent submissions will appear here once connected.</p>
                </div>
              </div>
            )}
          </section>
        </div>

        {/* Right Column: Teaching Pulse & Recent Updates */}
        <aside className="dashboard-secondary">
          <section aria-labelledby="teacher-pulse-title" id="health" className="dashboard-practice">
            <div className="dashboard-section-heading">
              <span className="dashboard-eyebrow">05 / CLASS HEALTH</span>
              <h2 id="teacher-pulse-title">Teaching pulse</h2>
            </div>

            <div className="dashboard-practice-studio">
              <div className="dashboard-streak-block">
                <strong className="dashboard-streak-number">{formatNumber(totalUngraded)}</strong>
                <span className="dashboard-streak-label">to review</span>
              </div>

              <div className="dashboard-solved-row">
                <span className="dashboard-solved-label">Students</span>
                <strong className="dashboard-solved-value">{totalActiveStudents}</strong>
              </div>

              <div className="dashboard-solved-row">
                <span className="dashboard-solved-label">Assignments</span>
                <strong className="dashboard-solved-value">{totalPublishedAssignments}</strong>
              </div>

              <div className="dashboard-solved-row">
                <span className="dashboard-solved-label">Quizzes</span>
                <strong className="dashboard-solved-value">{totalPublishedQuizzes}</strong>
              </div>

              <div className="dashboard-difficulty-block">
                <div className="dashboard-difficulty-header">
                  <span className="dashboard-difficulty-title">CONTRIBUTIONS</span>
                  <span className="dashboard-difficulty-readout">Pending</span>
                </div>
                <div className="role-kv-list">
                  <div className="role-kv-row">
                    <span>Courses</span>
                    <strong>{data.pendingContributions.courseCount}</strong>
                  </div>
                  <div className="role-kv-row">
                    <span>Problems</span>
                    <strong>{data.pendingContributions.problemCount}</strong>
                  </div>
                </div>
                <p>Public courses, coding practice, and content moderation data are not connected yet.</p>
              </div>
            </div>
          </section>

          <section aria-labelledby="teacher-notifications-title" id="notifications" className="dashboard-notifications">
            <div className="dashboard-section-heading">
              <span className="dashboard-eyebrow">06 / STAY IN THE LOOP</span>
              <h2 id="teacher-notifications-title">Recent updates</h2>
            </div>

            {data.recentNotifications.items.length ? (
              <div className="dashboard-notification-list" role="list">
                {data.recentNotifications.items.map(notification => {
                  const isUnread = notification.readAt === null;
                  return (
                    <div
                      className="dashboard-notification-row"
                      key={notification.notificationId}
                      role="listitem"
                      data-unread={isUnread}
                    >
                      <span
                        className="dashboard-notification-indicator"
                        data-unread={isUnread}
                        aria-label={isUnread ? 'Unread' : 'Read'}
                      />
                      <div className="dashboard-notification-body">
                        <div className="dashboard-notification-head">
                          <strong>{notification.title}</strong>
                          <time dateTime={notification.createdAt}>
                            {formatRelativeTimestamp(notification.createdAt)}
                          </time>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="dashboard-empty dashboard-empty-notifications">
                <strong>All caught up.</strong>
                <p>Submission alerts and updates will appear here.</p>
              </div>
            )}
          </section>
        </aside>
      </div>
    </>
  );
}

function AdminWorkspace({ data }: { data: AdminDashboardResponse }) {
  const [activeBarIndex, setActiveBarIndex] = useState<number | null>(null);

  const maxSubmissions = Math.max(
    1,
    ...data.charts.items.map(item => item.officialSubmissionCount),
  );

  const activeBar =
    activeBarIndex !== null ? (data.charts.items[activeBarIndex] ?? null) : null;

  const totalFlaggedAccounts =
    data.accountModeration.lockedAccountCount
    + data.accountModeration.suspendedAccountCount
    + data.accountModeration.confirmedViolationAccountCount;

  const activeAccountPercent =
    data.totals.studentCount > 0
      ? Math.max(
          0,
          Math.min(
            100,
            Math.round(((data.totals.studentCount - totalFlaggedAccounts) / data.totals.studentCount) * 100),
          ),
        )
      : 100;

  return (
    <>
      <div className="dashboard-intro">
        <div>
          <span className="dashboard-eyebrow">SYSTEM OVERVIEW</span>
          <h1>A clearer view of AlgoLearn.</h1>
          <p>Activity and moderation, scoped to your administrator account.</p>
        </div>
        <span className="dashboard-today">{todayFormatter.format(new Date())}</span>
      </div>

      <div className="dashboard-grid">
        <div className="dashboard-primary">
          {/* 01 / Featured System Overview Card */}
          <section aria-labelledby="admin-overview-title" id="overview" className="dashboard-learning-section">
            <div className="dashboard-section-heading">
              <span className="dashboard-eyebrow">01 / PLATFORM TELEMETRY</span>
              <h2 id="admin-overview-title">System pulse</h2>
            </div>

            <div className="dashboard-learning-card">
              <InteractiveCourseBackground />
              <div className="dashboard-learning-content">
                <span className="dashboard-learning-label">
                  SYSTEM OVERVIEW · PLATFORM STATUS
                </span>
                <h3>{data.totals.officialSubmissionCount.toLocaleString()} submissions</h3>
                <p>
                  {data.totals.studentCount} students · {data.totals.teacherCount} teachers ·{' '}
                  {data.totals.classroomCount} classes
                </p>
                <div className="dashboard-progress-info">
                  <span>Account health</span>
                  <strong>{activeAccountPercent}%</strong>
                </div>
                <div
                  className="dashboard-progress-track"
                  role="progressbar"
                  aria-label="Account health ratio"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={activeAccountPercent}
                >
                  <span style={{ width: `${activeAccountPercent}%` }} />
                </div>
                <Link className="dashboard-learning-action" to="/admin/submissions">
                  Inspect activity <ArrowRight size={15} aria-hidden="true" />
                </Link>
              </div>
            </div>
          </section>

          {/* 02 / Official Submissions Over Time */}
          <section
            aria-labelledby="admin-chart-title"
            id="submissions"
            className="dashboard-section"
            onMouseLeave={() => setActiveBarIndex(null)}
          >
            <div className="dashboard-section-heading flex items-center justify-between">
              <div>
                <span className="dashboard-eyebrow">02 / ACTIVITY OVER TIME</span>
                <h2 id="admin-chart-title">Official submissions</h2>
              </div>
              <Link to="/admin/submissions" className="text-xs font-semibold text-slate-700 hover:text-slate-900 underline">
                View all submissions →
              </Link>
            </div>

            <div className="role-chart-studio">
              <div className="role-chart-header">
                <span>
                  {formatShortDate(data.charts.from)} – {formatShortDate(data.charts.to)} · UTC+7
                </span>
                <strong aria-live="polite">
                  {activeBar
                    ? `${formatShortDate(activeBar.startsAt)} · ${activeBar.officialSubmissionCount} submissions`
                    : `Peak: ${maxSubmissions} / ${data.charts.interval}`}
                </strong>
              </div>

              {data.charts.items.length ? (
                <div
                  className="role-chart-bars"
                  role="group"
                  aria-label="Official submissions by period"
                  data-has-active={activeBarIndex !== null}
                >
                  {data.charts.items.map((item, idx) => {
                    const heightPct = Math.max(
                      6,
                      Math.round((item.officialSubmissionCount / maxSubmissions) * 100),
                    );
                    const isActive = activeBarIndex === idx;

                    return (
                      <button
                        key={item.startsAt}
                        type="button"
                        className="role-chart-bar-btn"
                        data-active={isActive}
                        aria-label={`${formatShortDate(item.startsAt)}: ${item.officialSubmissionCount} official submissions`}
                        onMouseEnter={() => setActiveBarIndex(idx)}
                        onFocus={() => setActiveBarIndex(idx)}
                        onBlur={() => setActiveBarIndex(current => (current === idx ? null : current))}
                        onClick={() => setActiveBarIndex(current => (current === idx ? null : idx))}
                      >
                        <span className="role-chart-bar-fill" style={{ height: `${heightPct}%` }} />
                        <small>{formatDayNumber(item.startsAt)}</small>
                      </button>
                    );
                  })}
                </div>
              ) : (
                <p className="role-empty-inline">No submission chart data for this period.</p>
              )}
            </div>
          </section>

          {/* 03 / Recent System Activity */}
          <section aria-labelledby="admin-activity-title" id="activity" className="dashboard-section">
            <div className="dashboard-section-heading flex items-center justify-between">
              <div>
                <span className="dashboard-eyebrow">03 / SYSTEM LOG</span>
                <h2 id="admin-activity-title">Recent activity</h2>
              </div>
              <Link to="/admin/system-logs" className="text-xs font-semibold text-slate-700 hover:text-slate-900 underline">
                View all logs →
              </Link>
            </div>

            {data.recentActivities.items.length ? (
              <div className="role-ledger-list" role="list">
                {data.recentActivities.items.map(item => (
                  <div className="role-ledger-row" key={item.eventId} role="listitem">
                    <div className="role-ledger-main">
                      <div className="role-ledger-head">
                        <strong>{item.summary}</strong>
                        <time dateTime={item.occurredAt}>
                          {formatRelativeTimestamp(item.occurredAt)}
                        </time>
                      </div>
                      <p className="role-ledger-meta">
                        <span className="role-mono-code">{item.sourceModule}</span>
                        <span className="role-mono-code">{item.action}</span>
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="dashboard-empty dashboard-empty-line">
                <div>
                  <strong>No recent system activity.</strong>
                  <p>Platform events from classrooms and judge runs will appear here.</p>
                </div>
              </div>
            )}
          </section>

          {/* 04 / Important Audit Events */}
          <section aria-labelledby="admin-audit-title" id="audit" className="dashboard-section">
            <div className="dashboard-section-heading flex items-center justify-between">
              <div>
                <span className="dashboard-eyebrow">04 / SECURITY AUDIT</span>
                <h2 id="admin-audit-title">Audit events</h2>
              </div>
              <Link to="/admin/security-audit" className="text-xs font-semibold text-slate-700 hover:text-slate-900 underline">
                Audit details →
              </Link>
            </div>

            {data.importantAuditEvents.items.length ? (
              <div className="role-ledger-list" role="list">
                {data.importantAuditEvents.items.map(item => (
                  <div className="role-ledger-row" key={item.auditId} role="listitem">
                    <div className="role-ledger-main">
                      <div className="role-ledger-head">
                        <strong>{formatAuditAction(item.action)}</strong>
                        <span className="role-ledger-tag" data-emphasis={item.outcome === 'SUCCESS'}>
                          {item.outcome}
                        </span>
                      </div>
                      <p className="role-ledger-meta">
                        <span className="role-mono-code">{item.action}</span>
                        <time dateTime={item.occurredAt}>
                          {formatRelativeTimestamp(item.occurredAt)}
                        </time>
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="dashboard-empty dashboard-empty-line">
                <div>
                  <strong>No audit events recorded.</strong>
                  <p>Administrative locks and configuration updates will appear here.</p>
                </div>
              </div>
            )}
          </section>
        </div>

        {/* Right Column: Moderation, Catalog & System Configuration */}
        <aside className="dashboard-secondary">
          <section aria-labelledby="admin-moderation-title" id="moderation" className="dashboard-practice">
            <div className="dashboard-section-heading flex items-center justify-between">
              <div>
                <span className="dashboard-eyebrow">05 / MODERATION</span>
                <h2 id="admin-moderation-title">Accounts to watch</h2>
              </div>
              <Link to="/admin/accounts" className="text-xs font-semibold text-slate-700 hover:text-slate-900 underline">
                Manage accounts →
              </Link>
            </div>

            <div className="dashboard-practice-studio">
              <div className="dashboard-streak-block">
                <strong className="dashboard-streak-number">
                  {formatNumber(totalFlaggedAccounts)}
                </strong>
                <span className="dashboard-streak-label">flagged</span>
              </div>

              <div className="dashboard-solved-row">
                <span className="dashboard-solved-label">Locked</span>
                <strong className="dashboard-solved-value">
                  {data.accountModeration.lockedAccountCount}
                </strong>
              </div>

              <div className="dashboard-solved-row">
                <span className="dashboard-solved-label">Suspended</span>
                <strong className="dashboard-solved-value">
                  {data.accountModeration.suspendedAccountCount}
                </strong>
              </div>

              <div className="dashboard-solved-row">
                <span className="dashboard-solved-label">Violations</span>
                <strong className="dashboard-solved-value">
                  {data.accountModeration.confirmedViolationAccountCount}
                </strong>
              </div>

              <div className="dashboard-difficulty-block">
                <div className="dashboard-difficulty-header">
                  <span className="dashboard-difficulty-title">CATALOG</span>
                  <span className="dashboard-difficulty-readout">Published & pending</span>
                </div>
                <div className="role-kv-list">
                  <div className="role-kv-row">
                    <span>Public courses</span>
                    <strong>{data.totals.publicCourseCount}</strong>
                  </div>
                  <div className="role-kv-row">
                    <span>Practice problems</span>
                    <strong>{data.totals.publicProblemCount}</strong>
                  </div>
                  <div className="role-kv-row">
                    <span>Pending review</span>
                    <strong>
                      {data.pendingTeacherContributions.courseCount
                        + data.pendingTeacherContributions.problemCount}
                    </strong>
                  </div>
                </div>
                <p>Public courses, coding practice, and content moderation data are not connected yet.</p>
              </div>
            </div>
          </section>

          <section aria-labelledby="admin-config-title" id="configuration" className="dashboard-notifications">
            <div className="dashboard-section-heading">
              <span className="dashboard-eyebrow">06 / CONFIGURATION</span>
              <h2 id="admin-config-title">System settings</h2>
            </div>

            {data.configurationShortcuts.length ? (
              <div className="dashboard-notification-list" role="list">
                {data.configurationShortcuts.map(item => (
                  <div className="dashboard-notification-row" key={item.code} role="listitem">
                    <span className="dashboard-notification-indicator" data-unread="true" aria-hidden="true" />
                    <div className="dashboard-notification-body">
                      <div className="dashboard-notification-head">
                        <strong>{item.label}</strong>
                        <time>{item.code}</time>
                      </div>
                      <p className="dashboard-notification-sub">
                        <span>{item.path}</span>
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="dashboard-empty dashboard-empty-notifications">
                <strong>No configuration shortcuts.</strong>
                <p>System modules will register links here when connected.</p>
              </div>
            )}
          </section>
        </aside>
      </div>
    </>
  );
}

export function RoleDashboardPage({ role }: { role: 'TEACHER' | 'ADMIN' }) {
  const { user, logout } = useAuth();
  const isDemo = useIsDemoMode();
  const navigate = useNavigate();

  const teacherQuery = useQuery({
    queryKey: dashboardKeys.teacher(),
    queryFn: ({ signal }) => dashboardApi.teacher(signal),
    enabled: role === 'TEACHER',
    retry: false,
  });

  const adminQuery = useQuery({
    queryKey: dashboardKeys.admin(),
    queryFn: ({ signal }) => dashboardApi.admin(signal),
    enabled: role === 'ADMIN',
    retry: false,
  });

  const query = role === 'TEACHER' ? teacherQuery : adminQuery;

  useEffect(() => {
    if (isAxiosError(query.error) && query.error.response?.status === 401) {
      clearSession();
      navigate('/login', { replace: true });
    }
  }, [query.error, logout, navigate]);


  const rawDisplayName = user?.fullName || user?.username || role;
  const displayName = rawDisplayName.replace(/^Demo\s+/i, '') || role;
  const firstName = displayName.split(' ')[0] || 'Instructor';

  const unreadCount =
    role === 'TEACHER' && teacherQuery.data
      ? teacherQuery.data.recentNotifications.items.filter(n => n.readAt === null).length
      : 0;


  const isForbidden = isAxiosError(query.error) && query.error.response?.status === 403;

  const syncedTeacherData: TeacherDashboardResponse | undefined =
    role === 'TEACHER' && teacherQuery.data
      ? isDemo
        ? (() => {
            const teacherPersona = readStoredPersona('TEACHER');
            const owned = readStoredClassrooms().filter(
              c => c.ownerId === teacherPersona.id && c.status === 'ACTIVE',
            );
            return {
              ...teacherQuery.data,
              classrooms: {
                items: owned.map(c => ({ classroomId: c.id, name: c.name })),
                totalElements: owned.length,
                hasMore: false,
              },
              classroomStatistics: {
                items: owned.map(c => ({
                  classroomId: c.id,
                  name: c.name,
                  activeStudentCount: c.members.filter(m => m.status === 'ACTIVE').length,
                  publishedAssignmentCount: getLessonCountForClassroom(c.id, 'TEACHER'),
                  publishedQuizCount: c.stats?.quizzesCount ?? 0,
                  ungradedWorkCount: teacherQuery.data.classroomStatistics.items.find(
                    s => s.classroomId === c.id,
                  )?.ungradedWorkCount ?? 0,
                })),
                totalElements: owned.length,
                hasMore: false,
              },
            };
          })()
        : teacherQuery.data
      : undefined;

  return (
    <WorkspaceLayout role={role} unreadCount={unreadCount} displayNameOverride={displayName}>
      {query.isPending ? (
        <DashboardSkeleton role={role} />
      ) : query.isError ? (
        <div className="dashboard-fetch-state" role="alert">
          <span className="dashboard-eyebrow">{role} WORKSPACE</span>
          <h2>{isForbidden ? 'Access restricted.' : 'Dashboard unavailable.'}</h2>
          <p>
            {isForbidden
              ? 'This account cannot access this dashboard.'
              : 'We could not load the current workspace data.'}
          </p>
          {!isForbidden && (
            <button type="button" onClick={() => query.refetch()}>
              Try again
            </button>
          )}
        </div>
      ) : role === 'TEACHER' && syncedTeacherData ? (
        <TeacherWorkspace data={syncedTeacherData} firstName={firstName} isDemo={isDemo} />
      ) : adminQuery.data ? (
        <AdminWorkspace data={adminQuery.data} />
      ) : null}
    </WorkspaceLayout>
  );
}
