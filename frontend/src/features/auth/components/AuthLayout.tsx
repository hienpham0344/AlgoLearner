import React from 'react';
import { Terminal, Code2, ShieldCheck, Sparkles, GraduationCap } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';

interface AuthLayoutProps {
  children: React.ReactNode;
  title: string;
  subtitle: string;
}

export const AuthLayout: React.FC<AuthLayoutProps> = ({ children, title, subtitle }) => {
  return (
    <div className="min-h-screen w-full bg-slate-950 text-slate-100 flex flex-col justify-between relative overflow-hidden">
      {/* Background ambient gradient glow */}
      <div className="absolute top-0 left-1/4 w-[600px] h-[600px] bg-indigo-600/10 rounded-full blur-[120px] pointer-events-none -z-10" />
      <div className="absolute bottom-0 right-1/4 w-[500px] h-[500px] bg-purple-600/10 rounded-full blur-[140px] pointer-events-none -z-10" />

      {/* Top Navbar */}
      <header className="w-full max-w-7xl mx-auto px-6 py-6 flex items-center justify-between z-10">
        <a href="/" className="flex items-center gap-3 group">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-indigo-600/30 group-hover:scale-105 transition-transform duration-200">
            <Terminal className="w-5 h-5 text-white" />
          </div>
          <div className="flex flex-col">
            <span className="text-lg font-extrabold tracking-tight text-white flex items-center gap-1.5">
              Algo<span className="text-indigo-400">Learner</span>
            </span>
            <span className="text-[10px] font-medium text-slate-400 uppercase tracking-widest">
              LMS & Public Judge
            </span>
          </div>
        </a>

        <div className="flex items-center gap-3">
          <Badge variant="primary" size="sm" className="hidden sm:inline-flex">
            <Sparkles className="w-3 h-3 text-indigo-400" />
            SRS 4.1 Auth Compliant
          </Badge>
          <div className="h-4 w-px bg-slate-800 hidden sm:block" />
          <span className="text-xs text-slate-400">
            Múi giờ hệ thống: <strong className="text-slate-300">UTC+7 (Asia/Ho_Chi_Minh)</strong>
          </span>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 py-8 grid grid-cols-1 lg:grid-cols-12 gap-8 items-center z-10">
        {/* Left column: Value Proposition & Aesthetics */}
        <div className="hidden lg:flex lg:col-span-6 flex-col justify-center space-y-8 pr-8">
          <div className="space-y-4">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 text-xs font-semibold">
              <GraduationCap className="w-4 h-4 text-indigo-400" />
              Nền tảng học lập trình thế hệ mới
            </div>
            <h1 className="text-4xl xl:text-5xl font-black text-white leading-tight tracking-tight">
              Rèn luyện thuật toán & <br />
              <span className="bg-gradient-to-r from-indigo-400 via-purple-300 to-emerald-400 bg-clip-text text-transparent">
                Lớp học thông minh
              </span>
            </h1>
            <p className="text-base text-slate-400 leading-relaxed max-w-lg">
              Hệ thống kết hợp chấm mã nguồn tự động an toàn (Java/Python Sandbox), ngân hàng bài tập LeetCode-style, và quản lý bài giảng, kiểm tra tính giờ theo chuẩn SRS v1.1.
            </p>
          </div>

          {/* Code preview card snippet */}
          <div className="relative rounded-2xl bg-slate-900/90 border border-slate-800 shadow-2xl p-5 font-mono text-xs overflow-hidden group hover:border-slate-700/80 transition-colors">
            <div className="flex items-center justify-between border-b border-slate-800/80 pb-3 mb-3 text-slate-400 text-[11px]">
              <div className="flex items-center gap-2">
                <div className="w-2.5 h-2.5 rounded-full bg-rose-500/80" />
                <div className="w-2.5 h-2.5 rounded-full bg-amber-500/80" />
                <div className="w-2.5 h-2.5 rounded-full bg-emerald-500/80" />
                <span className="ml-2 text-slate-300 font-medium">Solution.java</span>
              </div>
              <Badge variant="success" size="sm">ACCEPTED (14ms, 41.2MB)</Badge>
            </div>
            <div className="space-y-1 text-slate-300">
              <p><span className="text-purple-400">public class</span> <span className="text-amber-300">Solution</span> &#123;</p>
              <p className="pl-4"><span className="text-purple-400">public int</span> <span className="text-blue-400">maxSubArray</span>(<span className="text-purple-400">int</span>[] nums) &#123;</p>
              <p className="pl-8 text-slate-400">// Thuật toán Kadane - Tối ưu thời gian O(n)</p>
              <p className="pl-8"><span className="text-purple-400">int</span> maxSoFar = nums[0], currMax = nums[0];</p>
              <p className="pl-8"><span className="text-purple-400">for</span> (<span className="text-purple-400">int</span> i = 1; i &lt; nums.length; i++) &#123;</p>
              <p className="pl-12">currMax = Math.max(nums[i], currMax + nums[i]);</p>
              <p className="pl-12">maxSoFar = Math.max(maxSoFar, currMax);</p>
              <p className="pl-8">&#125;</p>
              <p className="pl-8"><span className="text-purple-400">return</span> maxSoFar;</p>
              <p className="pl-4">&#125;</p>
              <p>&#125;</p>
            </div>
          </div>

          {/* Key features checklist */}
          <div className="grid grid-cols-2 gap-4 text-xs text-slate-300">
            <div className="flex items-center gap-2.5">
              <ShieldCheck className="w-4 h-4 text-indigo-400 shrink-0" />
              <span>Xác thực OTP Email 10 phút</span>
            </div>
            <div className="flex items-center gap-2.5">
              <Code2 className="w-4 h-4 text-emerald-400 shrink-0" />
              <span>Monaco Editor + Docker Sandbox</span>
            </div>
          </div>
        </div>

        {/* Right column: Form Card */}
        <div className="col-span-1 lg:col-span-6 flex justify-center">
          <div className="w-full max-w-md bg-slate-900/90 backdrop-blur-xl border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-2xl shadow-black/80">
            <div className="mb-6 space-y-1.5 text-left">
              <h2 className="text-2xl font-bold text-white tracking-tight">{title}</h2>
              <p className="text-sm text-slate-400">{subtitle}</p>
            </div>

            {children}
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full max-w-7xl mx-auto px-6 py-6 text-center text-xs text-slate-400 border-t border-slate-800/40 z-10">
        <p>&copy; {new Date().getFullYear()} AlgoLearner Platform. Đặc tả theo SRS ver 1.1 (Karl Wiegers Standard).</p>
      </footer>
    </div>
  );
};
