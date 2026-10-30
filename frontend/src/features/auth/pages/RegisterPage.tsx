import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import logoSvg from '@/assets/logo.svg';
import { InteractiveDotGrid } from '@/components/ui/InteractiveDotGrid';
import { InteractiveHeroMascots } from '@/features/auth/components/InteractiveHeroMascots';
import { ArrowRight, ArrowUpRight, Eye, EyeOff, AlertCircle, GraduationCap, School } from 'lucide-react';

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const { register, isLoading } = useAuth();

  const [role, setRole] = useState<'STUDENT' | 'TEACHER'>('STUDENT');
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [privacyAccepted, setPrivacyAccepted] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!email.trim() || !email.includes('@')) {
      setErrorMessage('Please enter a valid email address.');
      return;
    }
    if (!username.trim() || username.length < 3) {
      setErrorMessage('Username must be at least 3 characters.');
      return;
    }
    if (password.length < 8) {
      setErrorMessage('Password must be at least 8 characters.');
      return;
    }
    if (password !== confirmPassword) {
      setErrorMessage('Passwords do not match.');
      return;
    }
    if (!termsAccepted || !privacyAccepted) {
      setErrorMessage('You must agree to the Terms of Service and Privacy Policy (SRS BR-ACC-001).');
      return;
    }

    try {
      await register({
        email: email.trim(),
        username: username.trim(),
        password,
        role,
        termsAccepted,
        privacyAccepted,
      });
      navigate(`/verify-otp?email=${encodeURIComponent(email.trim())}&purpose=REGISTER`);
    } catch {
      setErrorMessage('Registration failed. Please check your information and try again.');
    }
  };

  return (
    <div className="min-h-screen bg-[#F5F6F8] text-slate-900 flex flex-col justify-between selection:bg-slate-900 selection:text-white font-sans antialiased">
      {/* Top Header */}
      <header className="w-full max-w-[1400px] mx-auto px-6 sm:px-10 lg:px-12 py-5 sm:py-6 flex items-center justify-between">
        <Link to="/" className="flex items-center group">
          <img
            src={logoSvg}
            alt="AlgoLearn Logo"
            className="h-10 sm:h-11 md:h-12 w-auto object-contain transition-transform group-hover:scale-[1.02]"
          />
        </Link>

        <div className="flex items-center gap-2 text-sm">
          <span className="text-slate-500 font-normal">Already have an account?</span>
          <Link
            to="/login"
            className="font-bold text-black hover:text-slate-700 inline-flex items-center gap-1 transition-colors"
          >
            Sign in
            <ArrowUpRight className="w-4 h-4 stroke-[2.2]" />
          </Link>
        </div>
      </header>

      {/* Main Cards Section */}
      <main className="flex-1 w-full max-w-[1400px] mx-auto px-6 sm:px-10 lg:px-12 py-2 sm:py-4 flex flex-col justify-center">
        <div className="w-full grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-8 items-stretch my-auto">
          {/* Left Hero Card (~60% width) */}
          <div className="lg:col-span-7 rounded-[36px] bg-[#111215] text-white p-8 sm:p-12 lg:p-14 flex flex-col justify-between shadow-xl relative overflow-hidden min-h-[540px] lg:min-h-[600px] group cursor-default">
            {/* Interactive Dot Grid Background */}
            <InteractiveDotGrid
              dotGap={28}
              baseRadius={1.3}
              activeRadius={2.8}
              interactionRadius={140}
              repelStrength={7}
              enableRipple={true}
            />

            <div className="space-y-4 max-w-xl relative z-10 pointer-events-none select-none">
              <span className="text-[11px] font-bold tracking-[0.2em] uppercase text-slate-400 block">
                Join AlgoLearn Community
              </span>
              <h1 className="text-5xl sm:text-6xl lg:text-[68px] font-black tracking-tight leading-[1.05] text-white">
                Start your<br />
                coding<br />
                journey.
              </h1>
              <p className="text-sm sm:text-base text-slate-400 font-normal pt-2">
                Learn algorithms, compete with peers, and track your progress in real-time.
              </p>
            </div>

            {/* Interactive Hero Mascots (Clicking turns them ANGRY!) */}
            <InteractiveHeroMascots />
          </div>

          {/* Right Form Card (~40% width) */}
          <div className="lg:col-span-5 rounded-[36px] bg-white border border-slate-200/80 shadow-sm p-8 sm:p-10 lg:p-12 flex flex-col justify-center min-h-[540px] lg:min-h-[600px]">
            <div className="w-full max-w-[420px] mx-auto flex flex-col justify-center h-full">
            <div className="text-center mb-6">
              <h2 className="text-2xl sm:text-[28px] font-bold text-slate-900 tracking-tight">
                Create an account
              </h2>
              <p className="text-xs sm:text-sm text-slate-500 mt-2">
                Join as a student or teacher to get started.
              </p>
            </div>

            {errorMessage && (
              <div
                role="alert"
                className="mb-6 p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start gap-2.5"
              >
                <AlertCircle className="w-4 h-4 text-rose-500 shrink-0 mt-0.5" />
                <span className="leading-relaxed">{errorMessage}</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1.5 text-left">
                <label className="block text-xs font-semibold text-slate-800">
                  I am registering as
                </label>
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={() => setRole('STUDENT')}
                    className={`py-2.5 px-3 rounded-xl border text-xs font-semibold flex items-center justify-center gap-2 transition-all cursor-pointer ${
                      role === 'STUDENT'
                        ? 'border-slate-900 bg-slate-900 text-white shadow-sm'
                        : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                    }`}
                  >
                    <GraduationCap className="w-4 h-4" />
                    Student (Học viên)
                  </button>
                  <button
                    type="button"
                    onClick={() => setRole('TEACHER')}
                    className={`py-2.5 px-3 rounded-xl border text-xs font-semibold flex items-center justify-center gap-2 transition-all cursor-pointer ${
                      role === 'TEACHER'
                        ? 'border-slate-900 bg-slate-900 text-white shadow-sm'
                        : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                    }`}
                  >
                    <School className="w-4 h-4" />
                    Teacher (Giáo viên)
                  </button>
                </div>
              </div>

              <div className="space-y-1.5 text-left">
                <label htmlFor="reg-email" className="block text-xs font-semibold text-slate-800">
                  Email address
                </label>
                <input
                  id="reg-email"
                  type="email"
                  required
                  autoComplete="email"
                  placeholder="name@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full bg-white text-slate-900 placeholder:text-slate-400 text-sm rounded-xl px-4 py-2.5 border border-slate-200 hover:border-slate-300 focus:outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900 transition-all"
                />
              </div>

              <div className="space-y-1.5 text-left">
                <label htmlFor="reg-username" className="block text-xs font-semibold text-slate-800">
                  Username
                </label>
                <input
                  id="reg-username"
                  type="text"
                  required
                  autoComplete="username"
                  placeholder="Choose a username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="w-full bg-white text-slate-900 placeholder:text-slate-400 text-sm rounded-xl px-4 py-2.5 border border-slate-200 hover:border-slate-300 focus:outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900 transition-all"
                />
              </div>

              <div className="space-y-1.5 text-left">
                <label htmlFor="reg-password" className="block text-xs font-semibold text-slate-800">
                  Password
                </label>
                <div className="relative flex items-center">
                  <input
                    id="reg-password"
                    type={showPassword ? 'text' : 'password'}
                    required
                    autoComplete="new-password"
                    placeholder="At least 8 characters"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full bg-white text-slate-900 placeholder:text-slate-400 text-sm rounded-xl px-4 py-2.5 pr-11 border border-slate-200 hover:border-slate-300 focus:outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900 transition-all"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    className="absolute right-3.5 p-1 text-slate-400 hover:text-slate-600 cursor-pointer"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div className="space-y-1.5 text-left">
                <label htmlFor="reg-confirm-password" className="block text-xs font-semibold text-slate-800">
                  Confirm Password
                </label>
                <input
                  id="reg-confirm-password"
                  type={showPassword ? 'text' : 'password'}
                  required
                  autoComplete="new-password"
                  placeholder="Re-enter your password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className="w-full bg-white text-slate-900 placeholder:text-slate-400 text-sm rounded-xl px-4 py-2.5 border border-slate-200 hover:border-slate-300 focus:outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900 transition-all"
                />
              </div>

              <div className="space-y-2 pt-2 text-xs text-left">
                <label className="flex items-start gap-2.5 text-slate-600 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={termsAccepted}
                    onChange={(e) => setTermsAccepted(e.target.checked)}
                    className="mt-0.5 w-4 h-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900"
                  />
                  <span>
                    I agree to the <strong className="text-slate-900">Terms of Service</strong>
                  </span>
                </label>
                <label className="flex items-start gap-2.5 text-slate-600 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={privacyAccepted}
                    onChange={(e) => setPrivacyAccepted(e.target.checked)}
                    className="mt-0.5 w-4 h-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900"
                  />
                  <span>
                    I accept the <strong className="text-slate-900">Privacy Policy</strong>
                  </span>
                </label>
              </div>

              <div className="pt-2">
                <button
                  type="submit"
                  disabled={isLoading}
                  className="w-full relative flex items-center justify-center border border-slate-900 bg-white hover:bg-slate-50 text-slate-900 font-semibold py-3 px-5 rounded-xl transition-all duration-200 text-sm shadow-sm active:scale-[0.99] disabled:opacity-50 cursor-pointer group"
                >
                  <span>{isLoading ? 'Creating account...' : 'Create account'}</span>
                  {!isLoading && (
                    <ArrowRight className="w-4 h-4 text-slate-900 absolute right-4 transition-transform group-hover:translate-x-0.5" />
                  )}
                </button>
              </div>
            </form>

            <div className="mt-6 text-center">
              <span className="text-xs text-slate-500">Already registered? </span>
              <Link to="/login" className="text-xs font-bold text-slate-900 hover:underline">
                Sign in
              </Link>
            </div>
            </div>
          </div>
        </div>
      </main>

      <footer className="w-full max-w-[1400px] mx-auto px-6 sm:px-10 lg:px-12 py-5 sm:py-6 flex flex-col sm:flex-row items-center justify-between text-xs text-slate-500 gap-2 border-t border-slate-200/60">
        <div>&copy; 2026 AlgoLearn</div>
        <div>Byte, Bug, Kit, and you — learning to code, together.</div>
      </footer>
    </div>
  );
};
