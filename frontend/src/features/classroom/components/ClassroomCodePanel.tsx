import React, { useState } from 'react';
import { Classroom, JoinConfigurationInput } from '../types';
import { EnrollmentModeOption } from './EnrollmentModeOption';
import {
  Copy,
  Check,
  RotateCcw,
  Lock,
  Unlock,
  Clock,
  ShieldCheck,
  CheckCircle2,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface ClassroomCodePanelProps {
  classroom: Classroom;
  isOwnerOrAdmin: boolean;
  onRegenerateCode: () => void;
  onUpdateConfig: (config: JoinConfigurationInput) => void;
}

export const ClassroomCodePanel: React.FC<ClassroomCodePanelProps> = ({
  classroom,
  isOwnerOrAdmin,
  onRegenerateCode,
  onUpdateConfig,
}) => {
  const [copied, setCopied] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // Local form state for join configuration
  const [codeLocked, setCodeLocked] = useState(classroom.codeLocked);
  const [joiningOpen, setJoiningOpen] = useState(classroom.joiningOpen);
  const [joinMode, setJoinMode] = useState(classroom.joinMode);
  const [expiresAt, setExpiresAt] = useState<string>(
    classroom.codeExpiresAt ? classroom.codeExpiresAt.slice(0, 10) : '',
  );

  const isArchived = classroom.status === 'ARCHIVED';
  const isExpired =
    classroom.codeExpiresAt && new Date(classroom.codeExpiresAt).getTime() <= Date.now();

  const handleCopy = () => {
    navigator.clipboard.writeText(classroom.classCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleSaveConfig = (e: React.FormEvent) => {
    e.preventDefault();
    onUpdateConfig({
      codeLocked,
      joiningOpen,
      joinMode,
      codeExpiresAt: expiresAt ? new Date(`${expiresAt}T23:59:59Z`).toISOString() : null,
    });
    setSaveSuccess(true);
    setTimeout(() => setSaveSuccess(false), 2500);
  };

  return (
    <div className="space-y-6">
      {/* 01 / Primary Code Display Box */}
      <div className="classroom-code-studio">
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div>
            <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
              Class Code & Direct Access
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Share this unique 8-character code with your students for automated or managed enrollment.
            </p>
          </div>
        </div>

        <div className="classroom-code-display">
          <div>
            <span className="text-[10px] uppercase font-mono tracking-widest text-slate-400 block mb-1">
              Active Class Code
            </span>
            <div className="classroom-code-big">{classroom.classCode}</div>
          </div>

          <div className="flex items-center gap-3">
            <Button
              variant="outline"
              size="sm"
              onClick={handleCopy}
              className="bg-white/10 hover:bg-white/20 text-white border-white/20 h-10 px-4"
              aria-label="Copy class code to clipboard"
            >
              {copied ? (
                <>
                  <Check size={15} className="mr-2 text-emerald-400" aria-hidden="true" />
                  <span>Copied!</span>
                </>
              ) : (
                <>
                  <Copy size={15} className="mr-2 text-slate-300" aria-hidden="true" />
                  <span>Copy Code</span>
                </>
              )}
            </Button>

            {isOwnerOrAdmin && !isArchived && (
              <Button
                variant="outline"
                size="sm"
                onClick={onRegenerateCode}
                className="bg-transparent hover:bg-white/10 text-slate-300 border-white/20 h-10 px-4"
                title="Regenerate unique code (invalidates old code immediately)"
              >
                <RotateCcw size={14} className="mr-2" aria-hidden="true" />
                <span>Regenerate</span>
              </Button>
            )}
          </div>
        </div>

        {/* Status flags summary */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2">
          <div className="p-3 rounded-xl bg-white/50 border border-white/70 flex items-center gap-3">
            <div
              className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                classroom.joiningOpen
                  ? 'bg-emerald-100 text-emerald-700'
                  : 'bg-rose-100 text-rose-700'
              }`}
            >
              <ShieldCheck size={16} aria-hidden="true" />
            </div>
            <div>
              <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                Joining Status
              </div>
              <div className="text-xs font-bold text-slate-900">
                {classroom.joiningOpen ? 'Open for Joining' : 'Closed for Joining'}
              </div>
            </div>
          </div>

          <div className="p-3 rounded-xl bg-white/50 border border-white/70 flex items-center gap-3">
            <div
              className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                classroom.codeLocked
                  ? 'bg-amber-100 text-amber-700'
                  : 'bg-emerald-100 text-emerald-700'
              }`}
            >
              {classroom.codeLocked ? (
                <Lock size={16} aria-hidden="true" />
              ) : (
                <Unlock size={16} aria-hidden="true" />
              )}
            </div>
            <div>
              <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                Code Lock
              </div>
              <div className="text-xs font-bold text-slate-900">
                {classroom.codeLocked ? 'Locked (Inactive)' : 'Unlocked (Active)'}
              </div>
            </div>
          </div>

          <div className="p-3 rounded-xl bg-white/50 border border-white/70 flex items-center gap-3">
            <div
              className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                isExpired
                  ? 'bg-rose-100 text-rose-700'
                  : classroom.codeExpiresAt
                  ? 'bg-blue-100 text-blue-700'
                  : 'bg-slate-200 text-slate-700'
              }`}
            >
              <Clock size={16} aria-hidden="true" />
            </div>
            <div>
              <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                Expiration
              </div>
              <div className="text-xs font-bold text-slate-900">
                {isExpired
                  ? 'Expired'
                  : classroom.codeExpiresAt
                  ? `Expires ${new Date(classroom.codeExpiresAt).toLocaleDateString()}`
                  : 'Never Expires'}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 02 / Join & Access Settings Configuration Form */}
      {isOwnerOrAdmin && (
        <form
          onSubmit={handleSaveConfig}
          className="classroom-glass-card p-6 space-y-6"
        >
          <div className="border-b border-slate-100 pb-3 flex items-center justify-between">
            <div>
              <h3 className="text-sm font-bold text-slate-900">
                Join Mode & Security Parameters
              </h3>
              <p className="text-xs text-slate-500 mt-0.5">
                Configure enrollment policies, code lockout, and expiration rules.
              </p>
            </div>
            {saveSuccess && (
              <span className="text-xs text-emerald-600 font-semibold flex items-center gap-1">
                <CheckCircle2 size={14} /> Settings Saved
              </span>
            )}
          </div>

          {/* Join Mode & Code Expiration Settings */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Enrollment Mode Selection Area */}
            <div className="space-y-2">
              <label className="text-xs font-bold text-slate-800 uppercase tracking-wide block">
                Enrollment Mode
              </label>
              <div
                className="flex items-center gap-2.5 flex-wrap"
                role="radiogroup"
                aria-label="Enrollment Mode"
              >
                <EnrollmentModeOption
                  id="mode-auto-join"
                  label="Auto Join"
                  description="Immediate enrollment upon entering valid code."
                  isSelected={joinMode === 'AUTO_JOIN'}
                  disabled={isArchived}
                  onClick={() => setJoinMode('AUTO_JOIN')}
                />
                <EnrollmentModeOption
                  id="mode-require-approval"
                  label="Require Approval"
                  description="Teacher must manually review every request."
                  isSelected={joinMode === 'REQUIRE_APPROVAL'}
                  disabled={isArchived}
                  onClick={() => setJoinMode('REQUIRE_APPROVAL')}
                />
              </div>
            </div>

            {/* Code Expiration Date */}
            <div className="space-y-2">
              <label
                htmlFor="code-expiry-input"
                className="text-xs font-bold text-slate-800 uppercase tracking-wide block"
              >
                Code Expiration Date
              </label>
              <div className="flex items-center gap-2">
                <input
                  id="code-expiry-input"
                  type="date"
                  disabled={isArchived}
                  value={expiresAt}
                  onChange={e => setExpiresAt(e.target.value)}
                  className="flex-1 px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900"
                />
                {expiresAt && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={isArchived}
                    onClick={() => setExpiresAt('')}
                    className="text-xs text-slate-500"
                  >
                    Clear (Never Expires)
                  </Button>
                )}
              </div>
              <p className="text-[11px] text-slate-400">
                Leave empty for an evergreen code that never expires.
              </p>
            </div>
          </div>

          {/* Toggle Switches */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-4 border-t border-slate-100">
            <label className="flex items-start gap-3 p-3 rounded-xl bg-slate-50 border border-slate-200/80 cursor-pointer">
              <input
                type="checkbox"
                disabled={isArchived}
                checked={codeLocked}
                onChange={e => setCodeLocked(e.target.checked)}
                className="mt-1 w-4 h-4 rounded text-slate-900 focus:ring-slate-900"
              />
              <div>
                <span className="text-xs font-bold text-slate-900 block">
                  Lock Class Code
                </span>
                <span className="text-[11px] text-slate-500 block mt-0.5">
                  Temporarily disable new signups with this code without deleting it.
                </span>
              </div>
            </label>

            <label className="flex items-start gap-3 p-3 rounded-xl bg-slate-50 border border-slate-200/80 cursor-pointer">
              <input
                type="checkbox"
                disabled={isArchived}
                checked={joiningOpen}
                onChange={e => setJoiningOpen(e.target.checked)}
                className="mt-1 w-4 h-4 rounded text-slate-900 focus:ring-slate-900"
              />
              <div>
                <span className="text-xs font-bold text-slate-900 block">
                  Allow New Enrollments
                </span>
                <span className="text-[11px] text-slate-500 block mt-0.5">
                  When unchecked, the classroom rejects all join attempts even if code is valid.
                </span>
              </div>
            </label>
          </div>

          <div className="flex justify-end pt-2">
            <Button
              type="submit"
              size="sm"
              disabled={isArchived}
              className="bg-slate-900 text-white"
            >
              Save Join Configuration
            </Button>
          </div>
        </form>
      )}
    </div>
  );
};
