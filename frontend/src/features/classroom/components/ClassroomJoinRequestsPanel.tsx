import React from 'react';
import { ClassroomJoinRequest } from '../types';
import { Check, X, Clock } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface ClassroomJoinRequestsPanelProps {
  requests: ClassroomJoinRequest[];
  isOwnerOrAdmin: boolean;
  isArchived: boolean;
  onApprove: (requestId: string) => void;
  onReject: (requestId: string) => void;
}

export const ClassroomJoinRequestsPanel: React.FC<ClassroomJoinRequestsPanelProps> = ({
  requests,
  isOwnerOrAdmin,
  isArchived,
  onApprove,
  onReject,
}) => {
  const pendingRequests = requests.filter(r => r.status === 'PENDING_APPROVAL');

  const formatDate = (iso: string) => {
    try {
      return new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
      }).format(new Date(iso));
    } catch {
      return iso;
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between pb-3 border-b border-[#e7e7e5]">
        <div>
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
            Pending Join Requests
            {pendingRequests.length > 0 && (
              <span className="px-2 py-0.5 rounded-full text-xs font-mono font-bold bg-amber-100 text-amber-800">
                {pendingRequests.length}
              </span>
            )}
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Students waiting for approval to join this class.
          </p>
        </div>
      </div>

      {pendingRequests.length === 0 ? (
        <div className="classroom-empty-state">
          <div className="classroom-empty-icon text-emerald-600 bg-emerald-50">
            <Check size={22} />
          </div>
          <h4 className="classroom-empty-title">All caught up</h4>
          <p className="classroom-empty-desc">
            No pending enrollment requests. New student requests will appear here for verification.
          </p>
        </div>
      ) : (
        <div className="classroom-ledger" role="list" aria-label="Pending student join requests">
          {pendingRequests.map(request => (
            <div key={request.id} className="classroom-ledger-row" role="listitem">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-amber-50 text-amber-700 flex items-center justify-center font-bold text-xs">
                  {request.fullName.charAt(0).toUpperCase()}
                </div>

                <div>
                  <div className="flex items-center gap-2">
                    <strong className="text-slate-900 text-sm font-semibold">
                      {request.fullName}
                    </strong>
                    <span className="font-mono text-xs text-slate-400">@{request.username}</span>
                  </div>

                  <div className="classroom-ledger-meta">
                    <span className="font-mono text-[11px] text-slate-500">{request.email}</span>
                    <span className="inline-flex items-center gap-1 text-[11px] text-slate-400">
                      <Clock size={12} aria-hidden="true" />
                      Requested {formatDate(request.createdAt)}
                    </span>
                  </div>
                </div>
              </div>

              {isOwnerOrAdmin && !isArchived && (
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onReject(request.id)}
                    className="text-xs text-slate-600 hover:text-rose-600 hover:bg-rose-50 h-8"
                    aria-label={`Reject request from ${request.fullName}`}
                  >
                    <X size={13} className="mr-1" aria-hidden="true" />
                    Decline
                  </Button>

                  <Button
                    size="sm"
                    onClick={() => onApprove(request.id)}
                    className="text-xs bg-slate-900 text-white hover:bg-slate-800 h-8"
                    aria-label={`Approve request from ${request.fullName}`}
                  >
                    <Check size={13} className="mr-1" aria-hidden="true" />
                    Approve
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
