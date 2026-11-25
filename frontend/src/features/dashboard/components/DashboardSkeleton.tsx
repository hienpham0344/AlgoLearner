import React from 'react';

interface DashboardSkeletonProps {
  role: 'STUDENT' | 'TEACHER' | 'ADMIN';
}

const Bone: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`animate-pulse rounded-lg bg-slate-200/80 ${className}`.trim()} aria-hidden="true" />
);

const DarkBone: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`animate-pulse rounded-lg bg-white/15 ${className}`.trim()} aria-hidden="true" />
);

export const DashboardSkeleton: React.FC<DashboardSkeletonProps> = ({ role }) => {
  return (
    <div role="status" aria-busy="true" aria-label={`Loading ${role.toLowerCase()} dashboard`}>
      <span className="sr-only">Loading dashboard data…</span>

      {role !== 'STUDENT' && (
        <div className="dashboard-intro">
          <div className="space-y-2.5">
            <Bone className="h-3 w-36" />
            <Bone className="h-9 w-64" />
            <Bone className="h-4 w-80 max-w-full" />
          </div>
          <Bone className="h-4 w-32" />
        </div>
      )}

      <div className="dashboard-grid">
        <div className="dashboard-primary">
          {/* 01 / Hero Learning or Workspace Card */}
          <section className="dashboard-learning-section">
            <div className="dashboard-section-heading space-y-2">
              <Bone className="h-3 w-32" />
              <Bone className="h-6 w-48" />
            </div>

            <div className="dashboard-learning-card min-h-[220px] flex flex-col justify-between p-6">
              <div className="space-y-3 max-w-lg">
                <DarkBone className="h-3 w-40" />
                <DarkBone className="h-7 w-72 max-w-full" />
                <DarkBone className="h-4 w-56 max-w-full" />
              </div>
              <div className="space-y-3 pt-4">
                <DarkBone className="h-2 w-full rounded-full" />
                <DarkBone className="h-9 w-36 rounded-xl" />
              </div>
            </div>
          </section>

          {/* 02 / Deadlines or Assignments */}
          <section className="dashboard-section">
            <div className="dashboard-section-heading space-y-2">
              <Bone className="h-3 w-28" />
              <Bone className="h-6 w-44" />
            </div>
            <div className="space-y-2.5">
              {[0, 1].map(row => (
                <div key={row} className="dashboard-class-row">
                  <div className="dashboard-class-main space-y-2">
                    <Bone className="h-4 w-52 max-w-full" />
                    <Bone className="h-3 w-64 max-w-full" />
                  </div>
                  <Bone className="h-6 w-20 rounded-full shrink-0" />
                </div>
              ))}
            </div>
          </section>

          {/* 03 / Classrooms */}
          <section className="dashboard-section">
            <div className="dashboard-section-heading space-y-2">
              <Bone className="h-3 w-28" />
              <Bone className="h-6 w-36" />
            </div>
            <div className="dashboard-class-list">
              {[0, 1].map(row => (
                <div key={row} className="dashboard-class-row">
                  <div className="dashboard-class-main space-y-2">
                    <div className="flex items-center gap-2">
                      <Bone className="h-4 w-48" />
                      <Bone className="h-4 w-20 rounded-full" />
                    </div>
                    <Bone className="h-3 w-60 max-w-full" />
                  </div>
                  <Bone className="h-5 w-24 rounded-full shrink-0" />
                </div>
              ))}
            </div>
          </section>
        </div>

        <aside className="dashboard-aside">
          {[0, 1].map(panel => (
            <section key={panel} className="dashboard-section space-y-3">
              <div className="dashboard-section-heading space-y-2">
                <Bone className="h-3 w-28" />
                <Bone className="h-5 w-40" />
              </div>
              <div className="rounded-2xl border border-slate-200/80 bg-white/85 p-4 space-y-3">
                <Bone className="h-12 w-full rounded-xl" />
                <Bone className="h-12 w-full rounded-xl" />
                <Bone className="h-10 w-full rounded-xl" />
              </div>
            </section>
          ))}
        </aside>
      </div>
    </div>
  );
};
