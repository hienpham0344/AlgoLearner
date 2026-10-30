import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import byteSvg from '@/assets/mascot-byte.svg';
import logoSvg from '@/assets/logo.svg';
import { InteractiveDotGrid } from '@/components/ui/InteractiveDotGrid';
import { InteractiveHeroMascots } from '@/features/auth/components/InteractiveHeroMascots';
import {
  MascotAlertBubble,
  getRandomWrongMascot,
  WRONG_MASCOTS,
  WrongMascot,
} from '@/features/auth/components/MascotAlertBubble';
import { ArrowRight, ArrowUpRight, Eye, EyeOff, CheckCircle2, Sparkles, RotateCcw } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { login, quickLogin, isLoading } = useAuth();

  const [usernameOrEmail, setUsernameOrEmail] = useState('lananh@minh.vn');
  const [password, setPassword] = useState('password123');
  const [showPassword, setShowPassword] = useState(false);

  // Failure and Lockout state (SRS BR-ACC-002)
  const [failedAttempts, setFailedAttempts] = useState(0);
  const [isLocked, setIsLocked] = useState(false);
  const [lockoutSeconds, setLockoutSeconds] = useState(900); // 15:00 minutes
  const [wrongMascot, setWrongMascot] = useState<WrongMascot>(() => getRandomWrongMascot());

  // Success state ("You're all set.")
  const [isSuccess, setIsSuccess] = useState(false);
  const [successUser, setSuccessUser] = useState<{ name: string; role: string } | null>(null);
  const [redirectCountdown, setRedirectCountdown] = useState(3);
  const [redirectProgress, setRedirectProgress] = useState(0);

  // Lockout Countdown Timer
  useEffect(() => {
    if (!isLocked || lockoutSeconds <= 0) return;
    const interval = setInterval(() => {
      setLockoutSeconds((prev) => {
        if (prev <= 1) {
          setIsLocked(false);
          setFailedAttempts(0);
          return 900;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [isLocked, lockoutSeconds]);

  // Success Redirect Progress and Timer
  useEffect(() => {
    if (!isSuccess) return;

    const startTime = Date.now();
    const duration = 3000;

    const progressInterval = setInterval(() => {
      const elapsed = Date.now() - startTime;
      const pct = Math.min(100, (elapsed / duration) * 100);
      setRedirectProgress(pct);

      const remainingSecs = Math.max(1, Math.ceil((duration - elapsed) / 1000));
      setRedirectCountdown(remainingSecs);

      if (elapsed >= duration) {
        clearInterval(progressInterval);
        navigate('/');
      }
    }, 50);

    return () => clearInterval(progressInterval);
  }, [isSuccess, navigate]);

  const formatLockoutTime = (totalSeconds: number) => {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isLocked) return;

    if (!usernameOrEmail.trim() || !password) {
      handleFailure();
      return;
    }

    try {
      await login({
        usernameOrEmail: usernameOrEmail.trim(),
        password,
        rememberMe: true,
      });
      triggerSuccess('Lan Anh', 'Student · Verified');
    } catch {
      handleFailure();
    }
  };

  const pickRandomWrongMascot = () => {
    setWrongMascot((prev) => {
      const candidates = WRONG_MASCOTS.filter((m) => m.name !== prev.name);
      return candidates[Math.floor(Math.random() * candidates.length)] || getRandomWrongMascot();
    });
  };

  const handleFailure = () => {
    pickRandomWrongMascot();
    const nextFailed = failedAttempts + 1;
    if (nextFailed >= 5) {
      setFailedAttempts(5);
      setIsLocked(true);
      setLockoutSeconds(900);
    } else {
      setFailedAttempts(nextFailed);
    }
  };

  const triggerSuccess = (name: string, role: string) => {
    setFailedAttempts(0);
    setIsLocked(false);
    setSuccessUser({ name, role });
    setRedirectCountdown(3);
    setRedirectProgress(0);
    setIsSuccess(true);
  };

  const handleQuickDemo = async (role: 'STUDENT' | 'TEACHER' | 'ADMIN') => {
    try {
      await quickLogin(role);
      const name = role === 'TEACHER' ? 'Thầy Minh' : role === 'ADMIN' ? 'Quản trị viên' : 'Lan Anh';
      const roleBadge = role === 'TEACHER' ? 'Teacher · Verified' : role === 'ADMIN' ? 'Staff · Verified' : 'Student · Verified';
      triggerSuccess(name, roleBadge);
    } catch {
      // Mock fallback
      const name = role === 'TEACHER' ? 'Thầy Minh' : role === 'ADMIN' ? 'Quản trị viên' : 'Lan Anh';
      const roleBadge = role === 'TEACHER' ? 'Teacher · Verified' : role === 'ADMIN' ? 'Staff · Verified' : 'Student · Verified';
      triggerSuccess(name, roleBadge);
    }
  };

  const handleSimulateError = () => {
    setIsSuccess(false);
    handleFailure();
  };

  const handleSimulateLock = () => {
    setIsSuccess(false);
    pickRandomWrongMascot();
    setFailedAttempts(5);
    setIsLocked(true);
    setLockoutSeconds(899); // 14:59
  };

  const handleResetState = () => {
    setIsSuccess(false);
    setIsLocked(false);
    setFailedAttempts(0);
    setLockoutSeconds(900);
  };

  return (
    <div className="min-h-screen bg-[#F5F6F8] text-slate-900 flex flex-col justify-between selection:bg-slate-900 selection:text-white font-sans antialiased">
      {/* Top Header - Aligned with main container */}
      <header className="w-full max-w-[1400px] mx-auto px-6 sm:px-10 lg:px-12 py-5 sm:py-6 flex items-center justify-between">
        {/* Animated Vector Logo */}
        <Link to="/" className="flex items-center group">
          <img
            src={logoSvg}
            alt="AlgoLearn"
            className="h-10 sm:h-11 md:h-12 w-auto object-contain transition-transform group-hover:scale-[1.02]"
          />
        </Link>

        {/* Top-Right Link */}
        <div className="flex items-center gap-2 text-sm">
          <span className="text-slate-500 font-normal">New to AlgoLearn?</span>
          <Link
            to="/register"
            className="font-bold text-black hover:text-slate-700 inline-flex items-center gap-1 transition-colors"
          >
            Create an account
            <ArrowUpRight className="w-4 h-4 stroke-[2.2]" />
          </Link>
        </div>
      </header>

      {/* Main Cards Section - Wide, balanced 7:5 ratio */}
      <main className="flex-1 w-full max-w-[1400px] mx-auto px-6 sm:px-10 lg:px-12 py-2 sm:py-4 flex flex-col justify-center">
        <div className="w-full grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-8 items-stretch my-auto">
          {/* Left Hero Card (~60% width on desktop) */}
          <div className="lg:col-span-7 rounded-[36px] bg-[#111215] text-white p-8 sm:p-12 lg:p-14 flex flex-col justify-between shadow-xl relative overflow-hidden min-h-[520px] lg:min-h-[580px] group cursor-default">
            {/* Interactive Dot Grid Background */}
            <InteractiveDotGrid
              dotGap={28}
              baseRadius={1.3}
              activeRadius={2.8}
              interactionRadius={140}
              repelStrength={7}
              enableRipple={true}
            />

            {/* Top Text Content */}
            <div className="space-y-4 max-w-xl relative z-10 pointer-events-none select-none">
              <span className="text-[11px] font-bold tracking-[0.2em] uppercase text-slate-400 block">
                Coding Education
              </span>
              <h1 className="text-5xl sm:text-6xl lg:text-[68px] font-black tracking-tight leading-[1.05] text-white">
                Think.<br />
                Code.<br />
                Repeat.
              </h1>
              <p className="text-sm sm:text-base text-slate-400 font-normal pt-2">
                Small steps. Big ideas. Better together.
              </p>
            </div>

            {/* Interactive Hero Mascots (Clicking turns them ANGRY!) */}
            <InteractiveHeroMascots />
          </div>

          {/* Right Sign-in Form Card (~40% width on desktop) */}
          <div className="lg:col-span-5 rounded-[36px] bg-white border border-slate-200/80 shadow-sm p-8 sm:p-10 lg:p-12 flex flex-col justify-center min-h-[520px] lg:min-h-[580px] transition-all">
            <div className="w-full max-w-[380px] mx-auto flex flex-col justify-center h-full">

              {/* CASE 1: SUCCESS STATE ("You're all set.") */}
              {isSuccess && (
                <div className="space-y-6 animate-in fade-in zoom-in-95 duration-300">
                  <div className="text-center">
                    <h2 className="text-2xl sm:text-[32px] font-bold text-slate-900 tracking-tight">
                      You’re all set.
                    </h2>
                    <p className="text-xs sm:text-sm text-slate-500 mt-2">
                      Signed in successfully. AlgoLearn will take you to the right place.
                    </p>
                  </div>

                  {/* Profile Card with Byte Mascot & Status */}
                  <div className="rounded-2xl bg-[#F4F4F6] p-5 space-y-4">
                    {/* User profile row */}
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div className="w-12 h-12 rounded-2xl bg-white flex items-center justify-center p-1.5 shadow-[0_1px_3px_rgba(0,0,0,0.05)] shrink-0">
                          <img src={byteSvg} alt="Avatar" className="w-9 h-9 object-contain" />
                        </div>
                        <div className="text-left">
                          <div className="text-sm font-bold text-slate-900">
                            {successUser?.name || 'Lan Anh'}
                          </div>
                          <div className="text-xs text-slate-500 font-medium">
                            {successUser?.role || 'Staff · Verified'}
                          </div>
                        </div>
                      </div>
                      <CheckCircle2 className="w-5 h-5 text-slate-900 shrink-0" />
                    </div>

                    {/* Speech Bubble */}
                    <div className="bg-white rounded-xl p-3.5 text-left shadow-[0_2px_8px_-2px_rgba(0,0,0,0.05),0_1px_3px_rgba(0,0,0,0.03)] text-xs font-semibold text-slate-800">
                      You’re verified! I’ll take you to your workspace.
                    </div>

                    {/* Progress Bar & Countdown */}
                    <div className="space-y-2 text-left pt-1">
                      <div className="w-full bg-slate-200 h-1 rounded-full overflow-hidden">
                        <div
                          className="bg-slate-900 h-full rounded-full transition-all duration-100 ease-linear"
                          style={{ width: `${redirectProgress}%` }}
                        />
                      </div>
                      <p className="text-[11px] text-slate-400">
                        Redirecting in {redirectCountdown} seconds...
                      </p>
                    </div>
                  </div>
                </div>
              )}

              {/* CASE 2: LOCKED STATE (5 failed attempts) */}
              {isLocked && !isSuccess && (
                <div className="space-y-5 animate-in fade-in duration-200">
                  {/* Header for Locked */}
                  <div className="text-center mb-6">
                    <h2 className="text-2xl sm:text-[32px] font-bold text-slate-900 tracking-tight">
                      Sign-in is locked.
                    </h2>
                    <p className="text-xs sm:text-sm text-slate-500 mt-2">
                      Your account is safe. Please try again later.
                    </p>
                  </div>

                  {/* Speech Bubble Alert at top */}
                  <MascotAlertBubble
                    mascot={wrongMascot}
                    title="Hey! All 5 attempts failed."
                    description={`Sign-in is temporarily locked. Try again in ${formatLockoutTime(lockoutSeconds)}.`}
                    className="mb-5"
                  />

                  {/* Disabled Inputs */}
                  <div className="space-y-4">
                    <div className="space-y-1.5 text-left">
                      <label className="block text-xs font-semibold text-slate-800">
                        Username / Email
                      </label>
                      <input
                        type="text"
                        disabled
                        value={usernameOrEmail}
                        className="w-full bg-[#f6f6f8] text-slate-500 text-sm rounded-xl px-4 py-3 border border-slate-200 cursor-not-allowed select-none"
                      />
                    </div>

                    <div className="space-y-1.5 text-left">
                      <label className="block text-xs font-semibold text-slate-800">
                        Password
                      </label>
                      <div className="relative flex items-center">
                        <input
                          type="password"
                          disabled
                          value="••••••••••••"
                          className="w-full bg-[#f6f6f8] text-slate-500 text-sm rounded-xl px-4 py-3 pr-11 border border-slate-200 cursor-not-allowed select-none"
                        />
                        <EyeOff className="w-4 h-4 text-slate-400 absolute right-3.5" />
                      </div>

                      <div className="text-right pt-1">
                        <Link
                          to="/forgot-password"
                          className="text-xs font-medium text-slate-900 hover:underline"
                        >
                          Forgot password?
                        </Link>
                      </div>
                    </div>

                    {/* Disabled Locked Button */}
                    <div className="pt-2">
                      <div className="w-full py-3.5 px-5 rounded-xl bg-slate-100 border border-slate-200 text-slate-400 font-medium text-sm text-center cursor-not-allowed select-none">
                        Sign-in temporarily locked
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* CASE 3: NORMAL OR FAILED STATE (1-4 failed attempts) */}
              {!isLocked && !isSuccess && (
                <div>
                  {/* Header inside card */}
                  <div className="text-center mb-7">
                    <h2 className="text-2xl sm:text-[28px] font-bold text-slate-900 tracking-tight">
                      Sign in to AlgoLearn
                    </h2>
                    <p className="text-xs sm:text-sm text-slate-500 mt-2">
                      Enter your details to continue with AlgoLearn.
                    </p>
                  </div>

                  {/* Form */}
                  <form onSubmit={handleSubmit} className="space-y-4">
                    {/* Username / Email Field */}
                    <div className="space-y-1.5 text-left">
                      <label
                        htmlFor="login-username"
                        className="block text-xs font-semibold text-slate-800"
                      >
                        Username / Email
                      </label>
                      <input
                        id="login-username"
                        type="text"
                        required
                        autoComplete="username"
                        placeholder="Enter username or email"
                        value={usernameOrEmail}
                        onChange={(e) => setUsernameOrEmail(e.target.value)}
                        className="w-full bg-white text-slate-900 placeholder:text-slate-400 text-sm rounded-xl px-4 py-3 border border-slate-200 hover:border-slate-300 focus:outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900 transition-all"
                      />
                    </div>

                    {/* Password Field */}
                    <div className="space-y-1.5 text-left">
                      <label
                        htmlFor="login-password"
                        className="block text-xs font-semibold text-slate-800"
                      >
                        Password
                      </label>
                      <div className="relative flex items-center">
                        <input
                          id="login-password"
                          type={showPassword ? 'text' : 'password'}
                          required
                          autoComplete="current-password"
                          placeholder="Enter password"
                          value={password}
                          onChange={(e) => setPassword(e.target.value)}
                          className={`w-full bg-white text-slate-900 placeholder:text-slate-400 text-sm rounded-xl px-4 py-3 pr-11 border transition-all ${
                            failedAttempts > 0
                              ? 'border-slate-900 ring-1 ring-slate-900'
                              : 'border-slate-200 hover:border-slate-300 focus:border-slate-900 focus:ring-1 focus:ring-slate-900'
                          }`}
                        />
                        <button
                          type="button"
                          onClick={() => setShowPassword(!showPassword)}
                          aria-label={showPassword ? 'Hide password' : 'Show password'}
                          className="absolute right-3.5 p-1 text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
                        >
                          {showPassword ? (
                            <Eye className="w-4 h-4" />
                          ) : (
                            <EyeOff className="w-4 h-4" />
                          )}
                        </button>
                      </div>

                      <div className="text-right pt-1">
                        <Link
                          to="/forgot-password"
                          className="text-xs font-medium text-slate-900 hover:underline"
                        >
                          Forgot password?
                        </Link>
                      </div>
                    </div>

                    {/* Mascot Speech Bubble Alert on Failed Attempts (1-4 tries) */}
                    {failedAttempts > 0 && failedAttempts < 5 && (
                      <div className="pt-2 animate-in fade-in duration-200">
                        <MascotAlertBubble
                          mascot={wrongMascot}
                          title="Hey! Username/email or password is incorrect."
                          description={`${failedAttempts} of 5 attempts failed. You have ${
                            5 - failedAttempts
                          } try left before a 15-minute lockout.`}
                        />
                      </div>
                    )}

                    {/* Sign in Button */}
                    <div className="pt-2">
                      <button
                        type="submit"
                        disabled={isLoading}
                        className="w-full relative flex items-center justify-center border border-slate-900 bg-white hover:bg-slate-50 text-slate-900 font-semibold py-3 px-5 rounded-xl transition-all duration-200 text-sm shadow-sm active:scale-[0.99] disabled:opacity-50 cursor-pointer group"
                      >
                        <span>{isLoading ? 'Signing in...' : 'Sign in'}</span>
                        {!isLoading && (
                          <ArrowRight className="w-4 h-4 text-slate-900 absolute right-4 transition-transform group-hover:translate-x-0.5" />
                        )}
                      </button>
                    </div>
                  </form>

                  {/* Bottom Links inside card */}
                  <div className="mt-7 text-center flex flex-col items-center space-y-1.5">
                    <Link
                      to="/register"
                      className="text-sm font-bold text-slate-900 hover:underline"
                    >
                      Create an account
                    </Link>
                    <Link
                      to="/courses"
                      className="text-xs sm:text-sm text-slate-500 hover:text-slate-800 transition-colors"
                    >
                      Explore courses
                    </Link>
                  </div>
                </div>
              )}

            </div>
          </div>
        </div>
      </main>

      {/* Bottom Footer - Aligned with main container */}
      <footer className="w-full max-w-[1400px] mx-auto px-6 sm:px-10 lg:px-12 py-5 sm:py-6 flex flex-col sm:flex-row items-center justify-between text-xs text-slate-500 gap-2 border-t border-slate-200/60">
        <div>&copy; 2026 AlgoLearn</div>
        <div>Byte, Bug, Kit, and you — learning to code, together.</div>
      </footer>

      {/* Interactive Testing Toolbar (Floating pill for quick verification) */}
      <div className="fixed bottom-3 right-4 z-40 bg-white/95 backdrop-blur-md border border-slate-200 shadow-lg rounded-full px-3.5 py-1.5 flex items-center gap-2 text-xs text-slate-600">
        <Sparkles className="w-3.5 h-3.5 text-indigo-500" />
        <span className="font-semibold text-[11px] text-slate-700 hidden sm:inline">Preview States:</span>
        <button
          type="button"
          onClick={() => handleQuickDemo('STUDENT')}
          className="hover:text-emerald-600 font-medium px-1.5 py-0.5 rounded hover:bg-slate-100 transition-colors cursor-pointer"
          title="Show You're all set success state"
        >
          Success
        </button>
        <span className="text-slate-300">|</span>
        <button
          type="button"
          onClick={handleSimulateError}
          className="hover:text-amber-600 font-medium px-1.5 py-0.5 rounded hover:bg-slate-100 transition-colors cursor-pointer"
          title="Simulate failed attempt (Randomizes WRONG mascot: Byte, Bug, Kit)"
        >
          Error ({failedAttempts}/5 · {wrongMascot.name})
        </button>
        <span className="text-slate-300">|</span>
        <button
          type="button"
          onClick={handleSimulateLock}
          className="hover:text-rose-600 font-medium px-1.5 py-0.5 rounded hover:bg-slate-100 transition-colors cursor-pointer"
          title="Simulate 5th attempt account lockout"
        >
          Lock (15m)
        </button>
        <span className="text-slate-300">|</span>
        <button
          type="button"
          onClick={handleResetState}
          className="hover:text-slate-900 p-1 rounded hover:bg-slate-100 transition-colors cursor-pointer"
          title="Reset to idle state"
        >
          <RotateCcw className="w-3 h-3 text-slate-500" />
        </button>
      </div>
    </div>
  );
};
