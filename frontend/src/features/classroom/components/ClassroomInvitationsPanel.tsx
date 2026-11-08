import React from 'react';
import { ClassroomInvitation } from '../types';
import { Mail, Clock, Plus, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge, BadgeProps } from '@/components/ui/Badge';

interface ClassroomInvitationsPanelProps {
  invitations: ClassroomInvitation[];
  isOwnerOrAdmin: boolean;
  isArchived: boolean;
  onRevokeInvitation: (invitationId: string) => void;
  onOpenInviteModal: () => void;
}

export const ClassroomInvitationsPanel: React.FC<ClassroomInvitationsPanelProps> = ({
  invitations,
  isOwnerOrAdmin,
  isArchived,
  onRevokeInvitation,
  onOpenInviteModal,
}) => {
  const formatDate = (iso: string) => {
    try {
      return new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      }).format(new Date(iso));
    } catch {
      return iso;
    }
  };

  const getBadgeVariant = (status: ClassroomInvitation['status']): BadgeProps['variant'] => {
    switch (status) {
      case 'ACCEPTED':
        return 'success';
      case 'PENDING':
        return 'warning';
      case 'REVOKED':
      case 'DECLINED':
      case 'EXPIRED':
      default:
        return 'default';
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-[#e7e7e5]">
        <div>
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
            Class Invitations
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Invitations sent directly to registered student emails with expiration control.
          </p>
        </div>

        {isOwnerOrAdmin && !isArchived && (
          <Button
            size="sm"
            onClick={onOpenInviteModal}
            className="text-xs bg-slate-900 text-white"
          >
            <Plus size={14} className="mr-1" aria-hidden="true" />
            Send Invitation
          </Button>
        )}
      </div>

      {invitations.length === 0 ? (
        <div className="classroom-empty-state">
          <div className="classroom-empty-icon text-slate-500 bg-slate-100">
            <Mail size={22} />
          </div>
          <h4 className="classroom-empty-title">No invitations sent</h4>
          <p className="classroom-empty-desc">
            You can invite registered students directly to this classroom using their AlgoLearn email.
          </p>
        </div>
      ) : (
        <div className="classroom-ledger" role="list" aria-label="Class invitations">
          {invitations.map(invite => {
            const isExpired = new Date(invite.expiresAt).getTime() <= Date.now();
            const displayStatus = isExpired && invite.status === 'PENDING' ? 'EXPIRED' : invite.status;

            return (
              <div key={invite.id} className="classroom-ledger-row" role="listitem">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center">
                    <Mail size={16} aria-hidden="true" />
                  </div>

                  <div>
                    <div className="flex items-center gap-2">
                      <strong className="text-slate-900 text-sm font-semibold">
                        {invite.studentName || invite.studentEmail}
                      </strong>
                      <Badge variant={getBadgeVariant(displayStatus)} size="sm">
                        {displayStatus}
                      </Badge>
                    </div>

                    <div className="classroom-ledger-meta">
                      <span className="font-mono text-[11px] text-slate-500">
                        {invite.studentEmail}
                      </span>
                      <span className="inline-flex items-center gap-1 text-[11px] text-slate-400">
                        <Clock size={12} aria-hidden="true" />
                        Expires {formatDate(invite.expiresAt)}
                      </span>
                    </div>
                  </div>
                </div>

                {isOwnerOrAdmin && !isArchived && displayStatus === 'PENDING' && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onRevokeInvitation(invite.id)}
                    className="text-xs text-rose-600 hover:bg-rose-50 h-8"
                    title="Revoke invitation"
                    aria-label={`Revoke invitation for ${invite.studentEmail}`}
                  >
                    <X size={13} className="mr-1" aria-hidden="true" />
                    Revoke
                  </Button>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
