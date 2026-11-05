import { useEffect, useRef, useState, type ClipboardEvent, type FormEvent, type KeyboardEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { authApi } from '../api/authApi';
import type { OtpPurpose } from '../types';
import { AuthSubmit } from '../components/AuthControls';
import { AuthFeedback } from '../components/AuthFeedback';

export const VerifyOtpPage = () => {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { verifyOtp, isLoading } = useAuth();
  const email = params.get('email') || '';
  const purpose: OtpPurpose = params.get('purpose') === 'RESET_PASSWORD' ? 'RESET_PASSWORD' : 'REGISTER';
  const [otp, setOtp] = useState(['', '', '', '', '', '']);
  const [retryAt, setRetryAt] = useState(() => Date.now() + 60000);
  const [secondsLeft, setSecondsLeft] = useState(60);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isResending, setIsResending] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const busy = isLoading || isVerifying;
  const refs = useRef<(HTMLInputElement | null)[]>([]);
  const inFlight = useRef(false);

  useEffect(() => {
    const update = () => setSecondsLeft(Math.max(0, Math.ceil((retryAt - Date.now()) / 1000)));
    update();
    const timer = setInterval(update, 1000);
    return () => clearInterval(timer);
  }, [retryAt]);

  const handleChange = (index: number, value: string) => {
    if (!/^\d*$/.test(value)) return;
    if (/^\d{6}$/.test(value)) { setOtp(value.split('')); refs.current[5]?.focus(); return; }
    setOtp(current => current.map((digit, position) => position === index ? value.slice(-1) : digit));
    if (value && index < 5) refs.current[index + 1]?.focus();
  };
  const handlePaste = (event: ClipboardEvent) => {
    const value = event.clipboardData.getData('text').trim();
    if (/^\d{6}$/.test(value)) { event.preventDefault(); setOtp(value.split('')); refs.current[5]?.focus(); }
  };
  const handleKey = (index: number, event: KeyboardEvent<HTMLInputElement>) => {
    if ((event.key === 'Backspace' && !otp[index]) || event.key === 'ArrowLeft') { if (index > 0) { event.preventDefault(); refs.current[index - 1]?.focus(); } }
    if (event.key === 'ArrowRight' && index < 5) { event.preventDefault(); refs.current[index + 1]?.focus(); }
  };
  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (inFlight.current || busy) return;
    setError(null); setNotice(null);
    if (!/^\d{6}$/.test(otp.join(''))) { setError('Enter all 6 digits from your email.'); return; }
    if (!email) { setError(purpose === 'RESET_PASSWORD' ? 'Request a reset code first.' : 'Return to registration to request a code.'); return; }
    inFlight.current = true;
    setIsVerifying(true);
    try {
      if (purpose === 'RESET_PASSWORD') {
        const { resetToken } = await authApi.verifyResetOtp({ email, code: otp.join(''), purpose });
        navigate('/reset-password', { replace: true, state: { email, resetToken } });
      } else {
        await verifyOtp({ email, code: otp.join(''), purpose });
        navigate('/', { replace: true });
      }
    }
    catch { setError('That code is invalid or expired. Please try again.'); }
    finally { inFlight.current = false; setIsVerifying(false); }
  };
  const resend = async () => {
    if (secondsLeft > 0 || !email || inFlight.current || isResending) return;
    inFlight.current = true; setIsResending(true); setError(null); setNotice(null);
    try { await authApi.resendOtp({ email, purpose }); setRetryAt(Date.now() + 60000); setNotice('A new code is on its way.'); }
    catch { setError('We couldn’t send a new code. Please try again.'); }
    finally { inFlight.current = false; setIsResending(false); }
  };
  return (
    <>
      <h1 tabIndex={-1}>{purpose === 'RESET_PASSWORD' ? 'Verify your reset code.' : 'Check your inbox.'}</h1>
      {import.meta.env.MODE === 'auth-demo' && purpose === 'RESET_PASSWORD' && <p className="auth-demo-code">Demo code: <strong>123456</strong></p>}
      <AuthFeedback title={error ? 'Check your code.' : notice ? 'Code sent.' : undefined} tone={notice && !error ? 'success' : 'error'} description={error || notice || (email ? <>Enter the 6-digit code sent to <strong>{email}</strong>.</> : purpose === 'RESET_PASSWORD' ? 'Request a reset code to get started.' : 'Return to registration to request a code.')} />
      <form className="auth-form" onSubmit={handleSubmit} aria-busy={busy}>
        <div className="auth-otp-row" onPaste={handlePaste} role="group" aria-label="6-digit verification code">
          {otp.map((digit, index) => <input key={index} ref={node => { refs.current[index] = node; }} className="auth-input" type="text" inputMode="numeric" autoComplete={index === 0 ? 'one-time-code' : 'off'} maxLength={1} aria-label={`Digit ${index + 1}`} aria-invalid={Boolean(error)} aria-describedby="auth-feedback" value={digit} onChange={event => handleChange(index, event.target.value)} onKeyDown={event => handleKey(index, event)} disabled={busy || isResending} />)}
        </div>
        <AuthSubmit loading={busy} disabled={!email || isResending}>{busy ? 'Verifying…' : 'Verify code'}</AuthSubmit>
      </form>
      <div className="auth-resend"><button type="button" onClick={resend} disabled={secondsLeft > 0 || isResending || busy || !email}>{isResending ? 'Sending code…' : secondsLeft > 0 ? `Resend code in ${secondsLeft}s` : 'Resend code'}</button></div>
      <div className="auth-secondary"><Link to={purpose === 'RESET_PASSWORD' ? '/forgot-password' : '/register'}>Use a different email</Link></div>
    </>
  );
};
