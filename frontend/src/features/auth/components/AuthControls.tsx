import { useState, type InputHTMLAttributes, type ReactNode } from 'react';
import { Eye, EyeOff, LoaderCircle, LockKeyhole } from 'lucide-react';

interface AuthFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  id: string;
  label: string;
  message?: string;
  hint?: string;
  labelAction?: ReactNode;
}

export const AuthField = ({ id, label, type = 'text', message, hint, labelAction, ...props }: AuthFieldProps) => {
  const [visible, setVisible] = useState(false);
  const isPassword = type === 'password';
  return (
    <div className="auth-field">
      <div className="auth-field-heading"><label htmlFor={id}>{label}</label>{labelAction}</div>
      <div className="auth-input-wrap">
        <input {...props} id={id} type={isPassword && visible && !props.disabled ? 'text' : type} aria-invalid={Boolean(message) || props['aria-invalid']} aria-describedby={message ? `${id}-message` : hint ? `${id}-hint` : props['aria-describedby']} className={`auth-input${isPassword ? ' auth-input-password' : ''}`} />
        {isPassword && (
          <button type="button" className="auth-eye" disabled={props.disabled} aria-label={visible && !props.disabled ? 'Hide password' : 'Show password'} aria-controls={id} aria-pressed={visible && !props.disabled} onClick={() => setVisible(value => !value)}>
            {visible && !props.disabled ? <EyeOff size={17} /> : <Eye size={17} />}
          </button>
        )}
      </div>
      {message && <p className="auth-field-message" id={`${id}-message`}>{message}</p>}
      {!message && hint && <p className="auth-field-hint" id={`${id}-hint`}>{hint}</p>}
    </div>
  );
};

interface AuthSubmitProps {
  children: ReactNode;
  loading?: boolean;
  disabled?: boolean;
  locked?: boolean;
}

export const AuthSubmit = ({ children, loading, disabled, locked }: AuthSubmitProps) => (
  <button className="auth-submit" type="submit" disabled={disabled || loading || locked}>
    {loading ? <LoaderCircle size={16} className="auth-spinner" aria-hidden="true" /> : locked ? <LockKeyhole size={15} aria-hidden="true" /> : null}
    {children}
  </button>
);
