import React from 'react';
import { Clock } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { formatVietnamDateTime } from '../../utils/quizTimeUtils';
import type { AttemptView } from '../../types';

interface AttemptHistoryCardProps {
  attempts: AttemptView[];
  maxAttempts: number;
  onContinueAttempt?: (attemptId: string) => void;
  onViewResult?: (attemptId: string) => void;
  className?: string;
}

export const AttemptHistoryCard: React.FC<AttemptHistoryCardProps> = ({
  attempts,
  maxAttempts,
  onContinueAttempt,
  onViewResult,
  className = '',
}) => {
  if (attempts.length === 0) {
    return (
      <div className={`p-6 text-center rounded-2xl bg-slate-50 border border-slate-200/80 ${className}`}>
        <Clock size={28} className="mx-auto text-slate-400 mb-2" />
        <h3 className="text-xs font-bold text-slate-700">No attempts recorded yet</h3>
        <p className="text-xs text-slate-500 mt-1">
          You have up to {maxAttempts} attempt(s) for this assessment.
        </p>
      </div>
    );
  }

  return (
    <div className={`bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs flex flex-col gap-4 ${className}`}>
      <div className="flex items-center justify-between border-b border-slate-100 pb-3">
        <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
          <Clock size={16} className="text-slate-500" />
          <span>Attempt History</span>
        </h2>
        <span className="text-xs font-medium text-slate-500">
          {attempts.length} of {maxAttempts} attempts used
        </span>
      </div>

      <div className="divide-y divide-slate-100">
        {attempts.map((att, index) => {
          const attemptNumber = attempts.length - index;
          const isInProgress = att.status === 'IN_PROGRESS';
          const isSubmitted = att.status === 'SUBMITTED';
          const isLocked = att.status === 'LOCKED';

          return (
            <div
              key={att.id}
              className="py-3.5 first:pt-0 last:pb-0 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
            >
              <div className="flex items-start gap-3">
                <span className="w-7 h-7 rounded-lg bg-slate-100 text-slate-700 font-bold text-xs flex items-center justify-center shrink-0">
                  #{attemptNumber}
                </span>
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-xs font-bold text-slate-900">
                      Attempt #{attemptNumber}
                    </span>

                    {/* Status badge */}
                    {isInProgress && (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-800 border border-amber-200">
                        In Progress
                      </span>
                    )}
                    {isSubmitted && (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-800 border border-emerald-200">
                        Submitted
                      </span>
                    )}
                    {isLocked && (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-800 border border-rose-200">
                        Locked
                      </span>
                    )}

                    {att.late && (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-50 text-purple-700 border border-purple-200">
                        Late
                      </span>
                    )}
                  </div>

                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-slate-500 mt-1">
                    <span>Started: {formatVietnamDateTime(att.startedAt)}</span>
                    {att.submittedAt && (
                      <span>Submitted: {formatVietnamDateTime(att.submittedAt)}</span>
                    )}
                  </div>
                </div>
              </div>

              {/* Right: Score & Actions */}
              <div className="flex items-center gap-3 self-end sm:self-center">
                {isSubmitted && (
                  <div className="text-right">
                    {att.gradePublished ? (
                      <div>
                        <span className="text-[10px] font-medium text-slate-400 block">Score</span>
                        <span className="text-sm font-bold text-indigo-600">
                          {att.score !== null && att.score !== undefined ? `${Number(att.score).toFixed(1)}/100` : '—'}
                        </span>
                      </div>
                    ) : (
                      <span className="text-[11px] font-medium text-slate-400 italic">
                        Grade pending release
                      </span>
                    )}
                  </div>
                )}

                {isInProgress && onContinueAttempt && (
                  <Button
                    size="sm"
                    onClick={() => onContinueAttempt(att.id)}
                    className="bg-amber-600 hover:bg-amber-700 text-white text-xs font-semibold rounded-xl h-8 px-3 shadow-xs"
                  >
                    Resume Attempt →
                  </Button>
                )}

                {isSubmitted && onViewResult && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onViewResult(att.id)}
                    className="text-xs font-semibold rounded-xl h-8 px-3 border-slate-200 text-slate-700 hover:bg-slate-50"
                  >
                    View Details
                  </Button>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
