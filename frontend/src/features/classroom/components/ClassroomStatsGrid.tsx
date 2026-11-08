import React from 'react';

export interface StatCardItem {
  id: string;
  label: string;
  value: number | string;
  helperText?: string;
  icon?: React.ReactNode;
  badge?: {
    text: string;
    variant?: 'neutral' | 'active';
  };
}

interface ClassroomStatsGridProps {
  stats: StatCardItem[];
}

export const ClassroomStatsGrid: React.FC<ClassroomStatsGridProps> = ({ stats }) => {
  return (
    <div className="classroom-stats-grid" role="region" aria-label="Classroom summary statistics">
      {stats.map(stat => (
        <div key={stat.id} className="classroom-stat-card">
          <div className="flex items-center justify-between gap-2 mb-2">
            <span className="text-[11px] font-bold tracking-wider uppercase text-slate-500 font-mono">
              {stat.label}
            </span>
            {stat.badge ? (
              <span
                className={`text-[10.5px] font-bold px-2 py-0.5 rounded-md ${
                  stat.badge.variant === 'active'
                    ? 'bg-slate-900 text-white'
                    : 'bg-slate-100 text-slate-700 border border-slate-200'
                }`}
              >
                {stat.badge.text}
              </span>
            ) : stat.icon ? (
              <div className="text-slate-400 shrink-0" aria-hidden="true">
                {stat.icon}
              </div>
            ) : null}
          </div>

          <div className="flex items-baseline gap-2">
            <span className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight font-mono">
              {stat.value}
            </span>
          </div>

          {stat.helperText && (
            <p className="text-[11.5px] text-slate-500 font-medium mt-1 truncate">
              {stat.helperText}
            </p>
          )}
        </div>
      ))}
    </div>
  );
};
