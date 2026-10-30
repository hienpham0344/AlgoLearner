import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/authApi';
import logoSvg from '@/assets/logo.svg';
import { InteractiveDotGrid } from '@/components/ui/InteractiveDotGrid';
import { InteractiveHeroMascots } from '@/features/auth/components/InteractiveHeroMascots';
import { ArrowRight, ArrowUpRight, AlertCircle, KeyRound } from 'lucide-react';

export const ForgotPasswordPage: React.FC = () => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!email.trim() || !email.includes('@')) {
      setErrorMessage('Please enter a valid email address.');
      return;
    }

    setIsLoading(true);
    try {
      await authApi.forgotPassword({ email: email.trim() });
      navigate(`/reset-password?email=${encodeURIComponent(email.trim())}`);
    } catch {
      setErrorMessage('Failed to send reset code. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#F5F6F8] text-slate-900 flex flex-col justify-between selection:bg-slate-900 selection:text-white font-sans antialiased">
      <header className="w-full max-w-[1400px] mx-auto px-6 sm:px-10 lg:px-12 py-5 sm:py-6 flex items-center justify-between">
        <Link to="/" className="flex items-center group">
          <img
            src={logoSvg}
            alt="AlgoLearn Logo"
            className="h-10 sm:h-11 md:h-12 w-auto object-contain transition-transform group-hover:scale-[1.02]"
          />
        </Link>

        <div className="flex items-center gap-2 text-sm">
          <span className="text-slate-500 font-normal">Remember your password?</span>
          <Link
            to="/login"
            className="font-bold text-black hover:text-slate-700 inline-flex items-center gap-1 transition-colors"
          >
            Sign in
            <ArrowUpRight className="w-4 h-4 stroke-[2.2]" />
          </Link>
        </div>
      </header>

      <main className="flex-1 w-full max-w-[1400px] mx-auto px-6 sm:px-10 lg:px-12 py-2 sm:py-4 flex flex-col justify-center">
        <div className="w-full grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-8 items-stretch my-auto">
          {/* Left Hero Card (~60%) */}
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

            <div className="space-y-4 max-w-xl relative z-10 pointer-events-none select-none">
              <span className="text-[11px] font-bold tracking-[0.2em] uppercase text-slate-400 block">
                Account Recovery
              </span>
              <h1 className="text-5xl sm:text-6xl lg:text-[68px] font-black tracking-tight leading-[1.05] text-white">
                Reset your<br />
                password.
              </h1>
              <p className="text-sm sm:text-base text-slate-400 font-normal pt-2">
                Enter your email address and we will send you a one-time OTP to recover your account.
              </p>
            </div>

            {/* Interactive Hero Mascots (Clicking turns them ANGRY!) */}
            <InteractiveHeroMascots />
          </div>

          {/* Right Form Card (~40%) */}
          <div className="lg:col-span-5 rounded-[36px] bg-white border border-slate-200/80 shadow-sm p-8 sm:p-10 lg:p-12 flex flex-col justify-center min-h-[520px] lg:min-h-[580px]">
            <div className="w-full max-w-[380px] mx-auto flex flex-col justify-center h-full">
            <div className="text-center mb-6">
              <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center mx-auto mb-3 text-slate-800">
                <KeyRound className="w-6 h-6 text-slate-900" />
              </div>
              <h2 className="text-2xl sm:text-[28px] font-bold text-slate-900 tracking-tight">
                Forgot password?
              </h2>
              <p className="text-xs sm:text-sm text-slate-500 mt-2">
                We'll email you a 6-digit recovery code.
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
                <label htmlFor="forgot-email" className="block text-xs font-semibold text-slate-800">
                  Email address
                </label>
                <input
                  id="forgot-email"
                  type="email"
                  required
                  autoComplete="email"
                  placeholder="Enter your registered email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full bg-white text-slate-900 placeholder:text-slate-400 text-sm rounded-xl px-4 py-3 border border-slate-200 hover:border-slate-300 focus:outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900 transition-all"
                />
              </div>

              <div className="pt-2">
                <button
                  type="submit"
                  disabled={isLoading}
                  className="w-full relative flex items-center justify-center border border-slate-900 bg-white hover:bg-slate-50 text-slate-900 font-semibold py-3 px-5 rounded-xl transition-all duration-200 text-sm shadow-sm active:scale-[0.99] disabled:opacity-50 cursor-pointer group"
                >
                  <span>{isLoading ? 'Sending code...' : 'Send reset code'}</span>
                  {!isLoading && (
                    <ArrowRight className="w-4 h-4 text-slate-900 absolute right-4 transition-transform group-hover:translate-x-0.5" />
                  )}
                </button>
              </div>
            </form>

            <div className="mt-8 text-center">
              <Link to="/login" className="text-xs font-bold text-slate-900 hover:underline">
                Return to sign in
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
