import React, { useEffect, useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { apiClient } from '@/services/apiClient';
import { ApiResponse } from '@/types/api';
import { LogOut, Flame, CheckCircle2, BookOpen, Clock, Award, Terminal } from 'lucide-react';
import { Link } from 'react-router-dom';

interface UserProfileData {
  fullName: string;
  username: string;
  email: string;
  role: string;
  isPublic: boolean;
  publicPracticeStatistics?: {
    solved: number;
  };
  streak?: {
    currentStreak: number;
    highestStreak: number;
  };
}


export const DashboardPage: React.FC = () => {
  const { user, logout } = useAuth();
  const [profile, setProfile] = useState<UserProfileData | null>(null);

  useEffect(() => {
    const fetchLiveBackendData = async () => {
      try {
        const profileRes = await apiClient.get<ApiResponse<UserProfileData>>('/users/me/profile');
        setProfile(profileRes.data.data);
      } catch (e) {
        console.error('Failed to load profile data', e);
      }
    };

    fetchLiveBackendData();
  }, []);

  return (
    <div className="min-h-screen bg-[#f3f4f6] text-slate-900 flex flex-col justify-between font-sans">
      {/* Top Navbar */}
      <header className="bg-white border-b border-slate-200/80 sticky top-0 z-20">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-black flex items-center justify-center text-white font-mono font-bold text-sm">
              &gt;_
            </div>
            <span className="text-xl font-bold tracking-tight text-black">
              AlgoLearn
            </span>
          </Link>

          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-100 border border-slate-200 text-xs">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span className="font-semibold text-slate-800">{user?.fullName || profile?.fullName || user?.username}</span>
              <span className="text-slate-400 font-medium">({user?.role})</span>
            </div>

            <button
              onClick={logout}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-rose-600 px-3 py-1.5 rounded-lg hover:bg-rose-50 transition-colors"
            >
              <LogOut className="w-3.5 h-3.5" />
              Sign out
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-7xl w-full mx-auto px-6 py-8 flex-1 space-y-8">
        {/* Welcome Banner */}
        <div className="bg-[#111215] text-white rounded-3xl p-8 sm:p-10 shadow-lg relative overflow-hidden">
          <div className="relative z-10 space-y-2">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/10 text-xs font-semibold text-slate-300">
              <Terminal className="w-3.5 h-3.5 text-emerald-400" />
              Connected to Spring Boot Backend (Port 8081)
            </div>
            <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight">
              Welcome back, {profile?.fullName || user?.fullName || 'Coder'}!
            </h1>
            <p className="text-sm text-slate-400 max-w-xl">
              Ready to solve your daily algorithmic challenge or continue with your classroom assignments?
            </p>
          </div>
        </div>

        {/* Stats Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
          <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-emerald-50 flex items-center justify-center text-emerald-600">
              <CheckCircle2 className="w-6 h-6" />
            </div>
            <div>
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Problems Solved
              </div>
              <div className="text-2xl font-black text-slate-900">
                {profile?.publicPracticeStatistics?.solved ?? 2}
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-amber-50 flex items-center justify-center text-amber-600">
              <Flame className="w-6 h-6" />
            </div>
            <div>
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Current Streak
              </div>
              <div className="text-2xl font-black text-slate-900">
                {profile?.streak?.currentStreak ?? 3} Days
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-indigo-50 flex items-center justify-center text-indigo-600">
              <Award className="w-6 h-6" />
            </div>
            <div>
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Highest Streak
              </div>
              <div className="text-2xl font-black text-slate-900">
                {profile?.streak?.highestStreak ?? 4} Days
              </div>
            </div>
          </div>
        </div>

        {/* Dashboard Sections */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Active Classes */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <BookOpen className="w-4 h-4 text-slate-700" />
                Active Classrooms
              </h2>
              <span className="text-xs text-slate-400">SRS 4.3</span>
            </div>

            <div className="space-y-3">
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-between">
                <div>
                  <h3 className="font-semibold text-sm text-slate-900">Algorithms & Data Structures</h3>
                  <p className="text-xs text-slate-500 mt-0.5">Instructor: Teacher Demo (Class Code: ALGO-2026)</p>
                </div>
                <span className="px-2.5 py-1 rounded-md bg-emerald-100 text-emerald-800 text-[11px] font-bold">
                  Enrolled
                </span>
              </div>

              <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-between">
                <div>
                  <h3 className="font-semibold text-sm text-slate-900">Competitive Programming Lab</h3>
                  <p className="text-xs text-slate-500 mt-0.5">Instructor: Teacher 2 (Class Code: CP-ADV)</p>
                </div>
                <span className="px-2.5 py-1 rounded-md bg-emerald-100 text-emerald-800 text-[11px] font-bold">
                  Enrolled
                </span>
              </div>
            </div>
          </div>

          {/* Upcoming Deadlines */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Clock className="w-4 h-4 text-slate-700" />
                Upcoming Deadlines
              </h2>
              <span className="text-xs text-slate-400">SRS 4.6</span>
            </div>

            <div className="space-y-3">
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-between">
                <div>
                  <h3 className="font-semibold text-sm text-slate-900">Quiz 2: Dynamic Programming</h3>
                  <p className="text-xs text-slate-500 mt-0.5">Time limit: 45 mins • Auto-saving enabled</p>
                </div>
                <span className="text-xs font-semibold text-amber-600 bg-amber-50 px-2 py-1 rounded">
                  In 2 days
                </span>
              </div>

              <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-between">
                <div>
                  <h3 className="font-semibold text-sm text-slate-900">Assignment: Graph Shortest Path</h3>
                  <p className="text-xs text-slate-500 mt-0.5">Java / Python Submission</p>
                </div>
                <span className="text-xs font-semibold text-slate-600 bg-slate-100 px-2 py-1 rounded">
                  In 5 days
                </span>
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full max-w-7xl mx-auto px-6 py-6 flex items-center justify-between text-xs text-slate-500 border-t border-slate-200/60 mt-12">
        <div>&copy; 2026 AlgoLearn</div>
        <div>Byte, Bug, Kit, and you — learning to code, together.</div>
      </footer>
    </div>
  );
};
