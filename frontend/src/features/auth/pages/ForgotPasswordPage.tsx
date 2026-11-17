import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CircleAlert } from 'lucide-react';
import { authApi } from '../api/authApi';
import { AuthField, AuthSubmit } from '../components/AuthControls';

export const ForgotPasswordPage = () => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [requestError, setRequestError] = useState<string | null>(null);
  const inFlight = useRef(false);
  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (inFlight.current) return;
    setFieldError(null);
    setRequestError(null);
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setFieldError(email.trim() ? 'Enter a valid email address.' : 'Enter your email address.');
      document.getElementById('forgot-email')?.focus();
      return;
    }
    inFlight.current = true;
    setIsLoading(true);
    try {
      await authApi.forgotPassword({ email: email.trim() });
      navigate(`/verify-otp?email=${encodeURIComponent(email.trim())}&purpose=RESET_PASSWORD`);
    } catch {
      setRequestError('We could not send the reset code. Please try again.');
    } finally { setIsLoading(false); inFlight.current = false; }
  };
  return (
    <>
      <div className="auth-form-intro">
        <h1 tabIndex={-1}>Forgot your password?</h1>
        <p className="auth-description">We’ll send you a code to reset it.</p>
        {requestError && <p className="auth-inline-feedback" role="alert"><CircleAlert size={15} strokeWidth={1.8} aria-hidden="true" />{requestError}</p>}
      </div>
      <form className="auth-form" onSubmit={handleSubmit} noValidate aria-busy={isLoading}>
        <AuthField id="forgot-email" label="Email address" type="email" autoComplete="email" placeholder="you@example.com" required value={email} onChange={event => { setEmail(event.target.value); setFieldError(null); setRequestError(null); }} disabled={isLoading} message={fieldError || undefined} />
        <AuthSubmit loading={isLoading}>{isLoading ? 'Sending code…' : 'Send reset code'}</AuthSubmit>
      </form>
      <Link to="/login" className="auth-back-link">Back to sign in</Link>
    </>
  );
};
