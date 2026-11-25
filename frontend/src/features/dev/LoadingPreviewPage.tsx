import React, { useState } from 'react';
import { LoadingScreen } from '@/components/ui/LoadingScreen';

export const LoadingPreviewPage: React.FC = () => {
  const [tab, setTab] = useState<'all' | 'fullscreen' | 'page' | 'inline'>('all');

  if (tab === 'fullscreen') {
    return (
      <div>
        <div className="fixed top-4 right-4 z-50">
          <button
            type="button"
            onClick={() => setTab('all')}
            className="px-4 py-2 bg-slate-900 text-white rounded-lg text-sm font-semibold shadow-lg hover:bg-slate-800"
          >
            Close Fullscreen Preview
          </button>
        </div>
        <LoadingScreen
          variant="fullscreen"
          message="Initializing workspace…"
          submessage="Syncing your data and learning progress"
        />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#f6f6f4] p-8 text-slate-900 font-sans">
      <div className="max-w-4xl mx-auto space-y-8">
        {/* Header */}
        <div className="flex items-center justify-between pb-6 border-b border-slate-200">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              AlgoLearner — Loading Screen Previews
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              Loading state patterns aligned with the AlgoLearner brand identity
            </p>
          </div>
          <button
            type="button"
            onClick={() => setTab('fullscreen')}
            className="px-4 py-2 bg-emerald-700 text-white rounded-lg text-sm font-semibold shadow-sm hover:bg-emerald-800 transition-colors"
          >
            Preview Fullscreen Mode
          </button>
        </div>

        {/* Variant 1: Page Loading in Workspace Card */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold tracking-wider text-slate-500 uppercase">
              1. Page Loading Mode (Inside Workspace / Content Frame)
            </span>
            <span className="text-xs text-slate-400">Replaces plain &quot;Loading workspace…&quot; text</span>
          </div>
          <div className="bg-white border border-slate-200/90 rounded-2xl shadow-xs overflow-hidden">
            <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
              <span className="text-xs font-semibold text-slate-600">Classroom: Data Structures &amp; Algorithms</span>
              <span className="text-xs text-slate-400">Tab: Lessons</span>
            </div>
            <LoadingScreen
              variant="page"
              message="Loading lessons & progress…"
              submessage="Please wait while connecting to the server"
            />
          </div>
        </div>

        {/* Variant 2: Fullscreen Preview in Box */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold tracking-wider text-slate-500 uppercase">
              2. Fullscreen Loading Preview (Auth Session Sync / Route Transitions)
            </span>
            <span className="text-xs text-slate-400">Application startup screen</span>
          </div>
          <div className="relative bg-[#f6f6f4] rounded-2xl border border-slate-300/80 shadow-xs overflow-hidden flex items-center justify-center p-8">
            <LoadingScreen
              variant="page"
              message="Preparing your learning workspace…"
              submessage="Verifying account permissions"
            />
          </div>
        </div>

        {/* Variant 3: Inline Widget / Card Loader */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold tracking-wider text-slate-500 uppercase">
              3. Inline Mode (Toolbars, Widgets, Modals)
            </span>
            <span className="text-xs text-slate-400">For compact UI sections</span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-2xs">
              <span className="text-xs font-medium text-slate-400 block mb-2">Streak Statistics</span>
              <LoadingScreen variant="inline" message="Loading streak data…" />
            </div>
            <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-2xs">
              <span className="text-xs font-medium text-slate-400 block mb-2">Judge Queue</span>
              <LoadingScreen variant="inline" message="Checking code evaluation results…" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
