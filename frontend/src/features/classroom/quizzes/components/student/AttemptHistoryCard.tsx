import React from 'react';
import { Link } from 'react-router-dom';
import { Award, ArrowRight } from 'lucide-react';
import type { AttemptView, QuizKind } from '../../types';
import { formatVietnamDateTime } from '../../utils/timeUtils';

interface AttemptHistoryCardProps {
  attempts: AttemptView[];
  classroomId: string;
  quizId: string;
  quizKind: QuizKind;
  onSelectAttempt?: (attemptId: string) => void;
}

export const AttemptHistoryCard: React.FC<AttemptHistoryCardProps> = ({
  attempts,
  classroomId,
  quizId,
  quizKind,
}) => {
  if (attempts.length === 0) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-6 text-center text-sm text-slate-500">
        You have not started any attempts for this quiz yet.
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs space-y-3">
      <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">
        Your Attempt History
      </h3>

      <div className="divide-y divide-slate-100">
        {attempts.map((att) => {
          const isSubmitted = att.status === 'SUBMITTED';
          const isActive = att.status === 'ACTIVE';
          const isExpired = att.status === 'EXPIRED';

          return (
            <div
              key={att.id}
              className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 py-3 text-sm"
            >
              <div className="flex items-center gap-3">
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-slate-100 text-xs font-bold text-slate-800">
                  #{att.number}
                </span>

                <div>
                  <div className="flex items-center gap-2">
                    <span
                      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-bold ${
                        isSubmitted
                          ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                          : isActive
                            ? 'bg-indigo-50 text-indigo-700 border border-indigo-200 animate-pulse'
                            : 'bg-rose-50 text-rose-700 border border-rose-200'
                      }`}
                    >
                      {isSubmitted ? 'Submitted' : isActive ? 'In Progress' : 'Expired'}
                    </span>

                    {att.late && (
                      <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-bold text-amber-700 border border-amber-200">
                        Late
                      </span>
                    )}
                  </div>

                  <p className="mt-1 text-xs text-slate-500">
                    Started: {formatVietnamDateTime(att.startedAt)}
                    {att.submittedAt && ` · Submitted: ${formatVietnamDateTime(att.submittedAt)}`}
                  </p>
                </div>
              </div>

              {/* Score & Actions */}
              <div className="flex items-center gap-3">
                {quizKind === 'GRADED' && att.score != null && (
                  <span className="inline-flex items-center gap-1 font-bold text-slate-900">
                    <Award size={14} className="text-indigo-600" />
                    {Number(att.score).toFixed(2)}%
                  </span>
                )}

                {isActive && (
                  <Link
                    to={`/student/classrooms/${encodeURIComponent(classroomId)}/quizzes/${encodeURIComponent(quizId)}/attempt`}
                    className="inline-flex items-center gap-1 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-indigo-700"
                  >
                    Resume <ArrowRight size={13} />
                  </Link>
                )}

                {(isSubmitted || isExpired) && (
                  <Link
                    to={`/student/classrooms/${encodeURIComponent(classroomId)}/quizzes/${encodeURIComponent(quizId)}/result`}
                    className="inline-flex items-center gap-1 text-xs font-semibold text-slate-700 hover:text-slate-900 underline"
                  >
                    View Result
                  </Link>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
