import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { AuthField, AuthSubmit } from '../components/AuthControls';
import { AuthFeedback } from '../components/AuthFeedback';

export const ForgotPasswordPage = () => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inFlight = useRef(false);
  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (inFlight.current) return;
    setError(null);
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) { setError('Enter a valid email address.'); return; }
    inFlight.current = true;
    setIsLoading(true);
    try {
      await authApi.forgotPassword({ email: email.trim() });
      navigate(`/verify-otp?email=${encodeURIComponent(email.trim())}&purpose=RESET_PASSWORD`);
    } catch {
      setError('We couldn’t send a reset code. Please try again.');
    } finally { setIsLoading(false); inFlight.current = false; }
  };
  return (
    <>
      <h1 tabIndex={-1}>Forgot your password?</h1>
      <AuthFeedback title={error ? 'Check your email address.' : undefined} description={error || 'We’ll send you a code to reset it.'} />
      <form className="auth-form" onSubmit={handleSubmit} noValidate aria-busy={isLoading}>
        <AuthField id="forgot-email" label="Email address" type="email" autoComplete="email" placeholder="you@example.com" required value={email} onChange={event => setEmail(event.target.value)} disabled={isLoading} aria-invalid={Boolean(error)} aria-describedby="auth-feedback" />
        <AuthSubmit loading={isLoading}>{isLoading ? 'Sending code…' : 'Send reset code'}</AuthSubmit>
      </form>
      <Link to="/login" className="auth-back-link">Back to sign in</Link>
    </>
  );
};
