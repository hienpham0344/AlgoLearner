import React from 'react';
import { X, History, User } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useGradeOverridesHistory } from '../../hooks/useQuizResult';
import { formatVietnamDateTime } from '../../utils/timeUtils';

interface GradeHistoryModalProps {
  isOpen: boolean;
  classroomId: string;
  quizId: string;
  studentId: string;
  studentName?: string;
  onClose: () => void;
}

export const GradeHistoryModal: React.FC<GradeHistoryModalProps> = ({
  isOpen,
  classroomId,
  quizId,
  studentId,
  studentName,
  onClose,
}) => {
  const { data, isLoading, isError } = useGradeOverridesHistory(
    classroomId,
    quizId,
    studentId,
    0,
    50
  );

  if (!isOpen) return null;

  const overrides = data?.items || [];

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="grade-history-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs"
    >
      <div className="flex max-h-[85vh] w-full max-w-xl flex-col rounded-xl border border-slate-200 bg-white p-6 shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-200 pb-3">
          <div className="flex items-center gap-2">
            <History size={18} className="text-slate-700" />
            <h2 id="grade-history-title" className="text-base font-bold text-slate-900">
              Grade Override Audit History
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            className="rounded p-1 text-slate-400 hover:text-slate-600"
          >
            <X size={18} />
          </button>
        </div>

        {studentName && (
          <p className="mt-2 text-xs font-medium text-slate-600">
            Audit log for student: <strong className="text-slate-900">{studentName}</strong>
          </p>
        )}

        <div className="mt-4 flex-1 overflow-y-auto space-y-3">
          {isLoading && (
            <div className="p-8 text-center text-xs text-slate-500">Loading audit history…</div>
          )}

          {isError && (
            <div role="alert" className="rounded-lg bg-rose-50 p-3 text-xs text-rose-800">
              Failed to load audit history.
            </div>
          )}

          {!isLoading && !isError && overrides.length === 0 && (
            <div className="p-8 text-center text-xs text-slate-500">
              No manual grade overrides have been recorded for this student.
            </div>
          )}

          {!isLoading &&
            !isError &&
            overrides.map((item) => (
              <div
                key={item.id}
                className="rounded-lg border border-slate-200 bg-slate-50 p-3.5 space-y-1.5"
              >
                <div className="flex items-center justify-between text-xs">
                  <div className="flex items-center gap-1 font-semibold text-slate-800">
                    <User size={13} className="text-slate-400" />
                    <span>Actor: {item.actorId.slice(0, 8)}…</span>
                  </div>
                  <span className="text-slate-500">{formatVietnamDateTime(item.createdAt)}</span>
                </div>

                <div className="flex items-center gap-2 text-sm font-bold">
                  <span className="text-slate-500 line-through">
                    {Number(item.originalScore).toFixed(2)}%
                  </span>
                  <span className="text-slate-400">→</span>
                  <span className="text-emerald-700">
                    {Number(item.finalScore).toFixed(2)}%
                  </span>
                </div>

                <p className="text-xs text-slate-700 bg-white p-2 rounded border border-slate-200">
                  <span className="font-semibold text-slate-900">Reason: </span>
                  {item.reason}
                </p>
              </div>
            ))}
        </div>

        <div className="mt-4 flex justify-end border-t border-slate-200 pt-3">
          <Button variant="outline" type="button" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </div>
  );
};
