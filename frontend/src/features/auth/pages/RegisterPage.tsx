import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { GraduationCap, School } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import type { ApiErrorResponse } from '@/types/api';
import { AuthField, AuthSubmit } from '../components/AuthControls';
import { AuthFeedback } from '../components/AuthFeedback';

type FieldName = 'email' | 'username' | 'password' | 'confirmPassword';
type FieldErrors = Partial<Record<FieldName, string>>;

export const RegisterPage = () => {
  const navigate = useNavigate();
  const { register } = useAuth();
  const [role, setRole] = useState<'STUDENT' | 'TEACHER'>('STUDENT');
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [privacyAccepted, setPrivacyAccepted] = useState(false);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [notice, setNotice] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const inFlight = useRef(false);
  const clearField = (field: FieldName) => setErrors(current => ({ ...current, [field]: undefined }));

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (inFlight.current) return;
    setNotice(null);
    const nextErrors: FieldErrors = {};
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) nextErrors.email = 'Enter a valid email address.';
    if (username.trim().length < 3) nextErrors.username = 'Use at least 3 characters.';
    if (password.length < 8 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) nextErrors.password = 'Use at least 8 characters, including a letter and a number.';
    if (password !== confirmPassword || !confirmPassword) nextErrors.confirmPassword = 'Passwords must match.';
    setErrors(nextErrors);
    const firstError = (Object.keys(nextErrors) as FieldName[])[0];
    if (firstError) {
      document.getElementById(`reg-${firstError === 'confirmPassword' ? 'confirm-password' : firstError}`)?.focus();
      return;
    }
    if (!termsAccepted || !privacyAccepted) {
      setNotice('Accept the Terms of Service and Privacy Policy to continue.');
      document.getElementById(!termsAccepted ? 'reg-terms' : 'reg-privacy')?.focus();
      return;
    }
    inFlight.current = true;
    setIsSubmitting(true);
    try {
      await register({ email: email.trim(), username: username.trim(), password, confirmPassword, role, termsAccepted, privacyAccepted });
      navigate(`/verify-otp?email=${encodeURIComponent(email.trim())}&purpose=REGISTER`);
    } catch (failure: unknown) {
      if (isAxiosError<ApiErrorResponse>(failure) && failure.response?.data) {
        const response = failure.response.data;
        if (import.meta.env.MODE === 'backend-mock' && failure.response.status === 401 && response.errorCode === 'UNAUTHORIZED_ACCESS') {
          setNotice('Account creation isn’t available in this preview yet. Sign in with a demo account to try profiles and classrooms.');
        } else {
          const serverErrors: FieldErrors = {};
          for (const item of response.errors || []) {
            if (item.field === 'email' || item.field === 'username' || item.field === 'password' || item.field === 'confirmPassword') serverErrors[item.field] = item.message;
          }
          setErrors(serverErrors);
          setNotice(response.message || 'We couldn’t create your account. Please try again.');
        }
      } else {
        setNotice('We couldn’t create your account. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
      inFlight.current = false;
    }
  };

  return <>
    <div className="auth-form-intro"><h1 tabIndex={-1}>Create your account</h1><p className="auth-description">A little practice goes a long way. Start here.</p></div>
    {notice && <AuthFeedback title="Check your details" description={notice} />}
    <form className="auth-form auth-register-form" onSubmit={handleSubmit} noValidate aria-busy={isSubmitting}>
      <div className="auth-role-field"><span className="auth-group-label" id="role-label">I’m joining as a</span>
        <div className="auth-role-switch" role="group" aria-labelledby="role-label">
          <button type="button" aria-pressed={role === 'STUDENT'} disabled={isSubmitting} onClick={() => setRole('STUDENT')}><GraduationCap size={17} aria-hidden="true" />Student</button>
          <button type="button" aria-pressed={role === 'TEACHER'} disabled={isSubmitting} onClick={() => setRole('TEACHER')}><School size={17} aria-hidden="true" />Teacher</button>
        </div>
      </div>
      <AuthField id="reg-email" label="Email address" type="email" autoComplete="email" placeholder="you@example.com" required value={email} onChange={event => { setEmail(event.target.value); clearField('email'); }} disabled={isSubmitting} message={errors.email} />
      <AuthField id="reg-username" label="Username" autoComplete="username" placeholder="Choose a username" required value={username} onChange={event => { setUsername(event.target.value); clearField('username'); }} disabled={isSubmitting} message={errors.username} />
      <div className="auth-field-row">
        <AuthField id="reg-password" label="Password" type="password" autoComplete="new-password" placeholder="8+ characters" required value={password} onChange={event => { setPassword(event.target.value); clearField('password'); }} disabled={isSubmitting} message={errors.password} />
        <AuthField id="reg-confirm-password" label="Confirm password" type="password" autoComplete="new-password" placeholder="Repeat password" required value={confirmPassword} onChange={event => { setConfirmPassword(event.target.value); clearField('confirmPassword'); }} disabled={isSubmitting} message={errors.confirmPassword} />
      </div>
      <div className="auth-consents" aria-label="Agreements">
        <label htmlFor="reg-terms"><input id="reg-terms" type="checkbox" checked={termsAccepted} onChange={event => { setTermsAccepted(event.target.checked); setNotice(null); }} disabled={isSubmitting} required /><span>I agree to the <strong>Terms of Service</strong></span></label>
        <label htmlFor="reg-privacy"><input id="reg-privacy" type="checkbox" checked={privacyAccepted} onChange={event => { setPrivacyAccepted(event.target.checked); setNotice(null); }} disabled={isSubmitting} required /><span>I accept the <strong>Privacy Policy</strong></span></label>
      </div>
      <AuthSubmit loading={isSubmitting}>{isSubmitting ? 'Creating account…' : 'Create account'}</AuthSubmit>
    </form>
    <div className="auth-secondary"><span className="auth-description">Already have an account? </span><Link to="/login" className="auth-text-link">Sign in</Link></div>
  </>;
};
