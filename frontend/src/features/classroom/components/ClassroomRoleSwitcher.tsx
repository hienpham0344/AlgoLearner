import React from 'react';
import { DemoRole, DemoPersona } from '../types';
import { Shield, GraduationCap, School, RotateCcw, UserCheck } from 'lucide-react';

interface ClassroomRoleSwitcherProps {
  currentRole: DemoRole;
  currentPersona: DemoPersona;
  allPersonas: DemoPersona[];
  onRoleChange: (role: DemoRole) => void;
  onPersonaChange: (persona: DemoPersona) => void;
  onResetData: () => void;
}

export const ClassroomRoleSwitcher: React.FC<ClassroomRoleSwitcherProps> = ({
  currentRole,
  currentPersona,
  allPersonas,
  onRoleChange,
  onPersonaChange,
  onResetData,
}) => {
  const personasForRole = allPersonas.filter(p => p.role === currentRole);

  return (
    <aside className="proto-role-banner" aria-label="Classroom Prototype Role Simulator">
      <div className="flex items-center gap-3">
        <span className="text-[10px] font-mono uppercase tracking-wider text-amber-400 font-bold flex items-center gap-1.5">
          <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />
          Prototype Simulator
        </span>

        <div className="proto-role-pill-group" role="radiogroup" aria-label="Select Demo Perspective">
          <button
            type="button"
            className="proto-role-btn flex items-center gap-1.5"
            data-active={currentRole === 'TEACHER'}
            onClick={() => onRoleChange('TEACHER')}
            aria-checked={currentRole === 'TEACHER'}
            role="radio"
          >
            <School size={13} aria-hidden="true" />
            <span>TEACHER</span>
          </button>

          <button
            type="button"
            className="proto-role-btn flex items-center gap-1.5"
            data-active={currentRole === 'STUDENT'}
            onClick={() => onRoleChange('STUDENT')}
            aria-checked={currentRole === 'STUDENT'}
            role="radio"
          >
            <GraduationCap size={13} aria-hidden="true" />
            <span>STUDENT</span>
          </button>

          <button
            type="button"
            className="proto-role-btn flex items-center gap-1.5"
            data-active={currentRole === 'ADMIN'}
            onClick={() => onRoleChange('ADMIN')}
            aria-checked={currentRole === 'ADMIN'}
            role="radio"
          >
            <Shield size={13} aria-hidden="true" />
            <span>ADMIN</span>
          </button>
        </div>
      </div>

      <div className="flex items-center gap-4">
        <div className="proto-persona-indicator">
          <UserCheck size={14} className="text-emerald-400" aria-hidden="true" />
          <span>Active Persona:</span>
          {personasForRole.length > 1 ? (
            <select
              value={currentPersona.id}
              onChange={e => {
                const selected = personasForRole.find(p => p.id === e.target.value);
                if (selected) onPersonaChange(selected);
              }}
              className="bg-[#23262b] text-white border border-[#373a40] rounded px-2 py-0.5 text-xs font-semibold focus:outline-none focus:border-white"
              aria-label="Change simulated persona"
            >
              {personasForRole.map(p => (
                <option key={p.id} value={p.id}>
                  {p.fullName} ({p.title || p.role})
                </option>
              ))}
            </select>
          ) : (
            <span className="proto-persona-name">{currentPersona.fullName}</span>
          )}
        </div>

        <button
          type="button"
          onClick={onResetData}
          title="Reset local prototype data to initial seed state"
          className="inline-flex items-center gap-1 text-[11px] text-slate-400 hover:text-white transition-colors"
        >
          <RotateCcw size={12} aria-hidden="true" />
          <span>Reset Demo Data</span>
        </button>
      </div>
    </aside>
  );
};
