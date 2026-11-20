import React from 'react';
import { Calendar, Clock, Award, AlertCircle, Play, History } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import type { AssignmentDetailView, AttemptView } from '../../types';
import { formatVietnamDateTime } from '../../utils/timeUtils';
import { canStudentStartAttempt } from '../../utils/assignmentRules';
import { AssignmentStatusBadge } from '../shared/AssignmentStatusBadge';
import { AssignmentTypeBadge } from '../shared/AssignmentTypeBadge';
import { DeadlineBadge } from '../shared/DeadlineBadge';
import { MarkdownRenderer } from '../shared/MarkdownRenderer';

interface StudentAssignmentBriefingProps {
  assignment: AssignmentDetailView;
  activeAttempt?: AttemptView | null;
  onStart: () => void;
  isStarting?: boolean;
}

export const StudentAssignmentBriefing: React.FC<StudentAssignmentBriefingProps> = ({
  assignment,
  activeAttempt,
  onStart,
  isStarting = false,
}) => {
  const { allowed, reason } = canStudentStartAttempt(assignment);

  const hasActiveAttempt = Boolean(activeAttempt && activeAttempt.status === 'IN_PROGRESS');

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header Card */}
      <div className="p-6 sm:p-8 rounded-3xl border border-slate-200 bg-white shadow-xs space-y-5">
        <div className="flex flex-wrap items-center gap-2">
          <AssignmentTypeBadge type={assignment.type} />
          <AssignmentStatusBadge
            status={assignment.status}
            startTime={assignment.startTime}
            deadline={assignment.deadline}
          />
          <DeadlineBadge deadline={assignment.deadline} allowLate={assignment.allowLateSubmission} />
        </div>

        <div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight">{assignment.title}</h1>
          {assignment.description && (
            <div className="mt-3 text-sm text-slate-600 leading-relaxed">
              <MarkdownRenderer content={assignment.description} />
            </div>
          )}
        </div>

        {/* Assessment Specs Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-4 border-t border-slate-100">
          <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100">
            <span className="text-[11px] font-semibold text-slate-500 uppercase block mb-1 flex items-center gap-1">
              <Clock size={12} /> Time Limit
            </span>
            <span className="text-sm font-bold text-slate-900">
              {assignment.durationMinutes ? `${assignment.durationMinutes} mins` : 'No Limit'}
            </span>
          </div>

          <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100">
            <span className="text-[11px] font-semibold text-slate-500 uppercase block mb-1 flex items-center gap-1">
              <Award size={12} /> Attempts
            </span>
            <span className="text-sm font-bold text-slate-900">
              {assignment.maxAttempts === 0
                ? 'Unlimited'
                : `${assignment.myRemainingAttempts ?? assignment.maxAttempts} remaining`}
            </span>
          </div>

          <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100">
            <span className="text-[11px] font-semibold text-slate-500 uppercase block mb-1 flex items-center gap-1">
              <Calendar size={12} /> Start Time
            </span>
            <span className="text-xs font-bold text-slate-900">
              {assignment.startTime ? formatVietnamDateTime(assignment.startTime) : 'Available Now'}
            </span>
          </div>

          <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100">
            <span className="text-[11px] font-semibold text-slate-500 uppercase block mb-1">
              My Best Score
            </span>
            <span className="text-sm font-bold text-slate-900">
              {assignment.myBestScore !== null && assignment.myBestScore !== undefined
                ? `${assignment.myBestScore} pts`
                : '—'}
            </span>
          </div>
        </div>

        {/* Action Controls */}
        <div className="pt-2 flex flex-col sm:flex-row items-center justify-between gap-4">
          <Link
            to="attempts"
            className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-600 hover:text-slate-900 hover:underline"
          >
            <History size={14} /> View Submission History
          </Link>

          <div>
            {hasActiveAttempt ? (
              <Link to="workspace">
                <Button size="lg" className="w-full sm:w-auto font-bold gap-2 bg-slate-900 hover:bg-slate-800 text-white">
                  <Play size={16} /> Resume In-Progress Attempt
                </Button>
              </Link>
            ) : allowed ? (
              <Button
                size="lg"
                onClick={onStart}
                disabled={isStarting}
                className="w-full sm:w-auto font-bold gap-2 bg-slate-900 hover:bg-slate-800 text-white"
              >
                <Play size={16} /> {isStarting ? 'Starting Assessment...' : 'Start Assessment'}
              </Button>
            ) : (
              <div className="flex items-center gap-2 p-3 bg-slate-100 rounded-xl text-xs text-slate-600 font-medium">
                <AlertCircle size={14} className="text-slate-500 shrink-0" />
                <span>{reason}</span>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
