import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { CircleCheck } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import type { ApiErrorResponse } from '@/types/api';
import { AuthField, AuthSubmit } from '../components/AuthControls';
import { AuthFeedback } from '../components/AuthFeedback';
import { LoginFeedbackBubble } from '../components/LoginFeedbackBubble';

type Notice = { title: string; description: string; tone: 'error' | 'locked'; retryAt?: number };

function savedIdentity() {
  try { return sessionStorage.getItem('algolearner_login_identity') || ''; } catch { return ''; }
}

function retryTime(value: string | undefined, now: number): number | undefined {
  if (!value) return undefined;
  const seconds = Number(value);
  const timestamp = Number.isFinite(seconds) ? now + seconds * 1000 : Date.parse(value);
  return Number.isFinite(timestamp) && timestamp > now ? timestamp : undefined;
}

export const LoginPage = () => {
  const navigate = useNavigate();
  const [search] = useSearchParams();
  const { login, user, isLoading } = useAuth();
  const [usernameOrEmail, setUsernameOrEmail] = useState(savedIdentity);
  const [password, setPassword] = useState('');
  const [fieldError, setFieldError] = useState<{ identity?: string; password?: string }>({});
  const [notice, setNotice] = useState<Notice | null>(null);
  const [mascotCue, setMascotCue] = useState(0);
  const [success, setSuccess] = useState(false);
  const [now, setNow] = useState(Date.now);
  const inFlight = useRef(false);
  const locked = notice?.tone === 'locked' && Boolean(notice.retryAt && notice.retryAt > now);

  useEffect(() => {
    if (!success) return;
    const timer = setTimeout(() => navigate('/', { replace: true }), 1800);
    return () => clearTimeout(timer);
  }, [success, navigate]);

  useEffect(() => {
    if (!locked) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [locked]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (locked || isLoading || inFlight.current) return;
    setNotice(null);
    const nextErrors = {
      identity: usernameOrEmail.trim() ? undefined : 'Enter your username or email.',
      password: password ? undefined : 'Enter your password.',
    };
    setFieldError(nextErrors);
    if (nextErrors.identity || nextErrors.password) {
      document.getElementById(nextErrors.identity ? 'login-username' : 'login-password')?.focus();
      return;
    }
    inFlight.current = true;
    try {
      try { sessionStorage.setItem('algolearner_login_identity', usernameOrEmail.trim()); } catch { /* Optional convenience only. */ }
      await login({ usernameOrEmail: usernameOrEmail.trim(), password, rememberMe: true });
      setSuccess(true);
    } catch (failure: unknown) {
      setMascotCue(current => current + 1);
      if (isAxiosError<ApiErrorResponse>(failure) && failure.response) {
        const { status, data, headers } = failure.response;
        const isLocked = status === 423 || data?.errorCode?.toUpperCase().includes('LOCK');
        if (isLocked) {
          const responseTime = Date.now();
          setNow(responseTime);
          setNotice({ title: 'Sign-in is temporarily unavailable.', description: data?.message || 'This account is temporarily locked. Please try again later.', tone: 'locked', retryAt: retryTime(headers?.['retry-after'], responseTime) });
        } else if (status === 401) {
          setNotice({ title: "Those details don't look quite right.", description: data?.message || 'Check your details and try again.', tone: 'error' });
        } else {
          setNotice({ title: 'Unable to sign in', description: data?.message || 'Please try again in a moment.', tone: 'error' });
        }
      } else {
        setNotice({ title: 'Unable to sign in', description: 'Please check your connection and try again.', tone: 'error' });
      }
    } finally {
      inFlight.current = false;
    }
  };

  const resetSuccess = search.get('resetSuccess') === 'true';
  const remainingSeconds = notice?.retryAt ? Math.max(0, Math.ceil((notice.retryAt - now) / 1000)) : 0;
  const lockDescription = locked
    ? <>Too many attempts. <span className="auth-countdown" aria-hidden="true">Try again in {Math.floor(remainingSeconds / 60).toString().padStart(2, '0')}:{(remainingSeconds % 60).toString().padStart(2, '0')}.</span><span className="sr-only">Please try again when the temporary lock expires.</span></>
    : notice?.description || '';

  if (success) {
    return <div className="auth-success" role="status">
      <div className="auth-success-icon"><CircleCheck size={23} strokeWidth={1.6} aria-hidden="true" /></div>
      <h1 tabIndex={-1}>You’re all set.</h1>
      <p className="auth-description">Signed in successfully.</p>
      <div className="auth-profile">
        <span className="auth-avatar" aria-hidden="true">{(user?.fullName || user?.username || 'A').slice(0, 1).toUpperCase()}</span>
        <div><p className="auth-profile-name">{user?.fullName || user?.username}</p><p className="auth-profile-role">{user?.role?.toLowerCase()}</p></div>
      </div>
      <button type="button" className="auth-submit" onClick={() => navigate('/', { replace: true })}>Continue to your workspace</button>
    </div>;
  }

  return <>
    <div className="auth-form-intro"><h1 tabIndex={-1}>Welcome back</h1><p className="auth-description">Sign in to keep learning with AlgoLearn.</p></div>
    <LoginFeedbackBubble dialogue={notice ? { title: notice.title, description: lockDescription, tone: notice.tone, mascotCue } : null} />
    {!notice && resetSuccess && <AuthFeedback title="Password updated" tone="success" description="Sign in with your new password." />}
    <form className="auth-form" onSubmit={handleSubmit} noValidate aria-busy={isLoading}>
      <AuthField id="login-username" label="Username or email" autoComplete="username" placeholder="Enter your username or email" value={usernameOrEmail} onChange={event => { setUsernameOrEmail(event.target.value); setFieldError(current => ({ ...current, identity: undefined })); setNotice(null); }} required disabled={isLoading} message={fieldError.identity} />
      <AuthField id="login-password" label="Password" type="password" autoComplete="current-password" placeholder="Enter your password" value={password} onChange={event => { setPassword(event.target.value); setFieldError(current => ({ ...current, password: undefined })); setNotice(current => current?.tone === 'locked' ? current : null); }} required disabled={isLoading} message={fieldError.password} labelAction={<Link className="auth-text-link" to="/forgot-password">Forgot password?</Link>} />
      <AuthSubmit loading={isLoading} locked={locked}>{locked ? 'Sign-in temporarily unavailable' : isLoading ? 'Signing in…' : 'Sign in'}</AuthSubmit>
    </form>
    <div className="auth-secondary"><span className="auth-description">New to AlgoLearn? </span><Link to="/register" className="auth-text-link">Create an account</Link></div>
    <Link className="auth-explore-link" to="/courses">Explore courses <span aria-hidden="true">↗</span></Link>
  </>;
};
