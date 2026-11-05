import type { ReactNode } from 'react';
import { CircleAlert, LockKeyhole, CircleCheck } from 'lucide-react';

interface AuthFeedbackProps {
  title?: string;
  description: ReactNode;
  tone?: 'error' | 'locked' | 'success';
}

export const AuthFeedback = ({ title, description, tone = 'error' }: AuthFeedbackProps) => {
  const Icon = tone === 'locked' ? LockKeyhole : tone === 'success' ? CircleCheck : CircleAlert;
  return (
    <div className="auth-feedback-slot" id="auth-feedback">
      {title ? (
        <div className={`auth-feedback auth-feedback-${tone}`} role={tone === 'success' ? 'status' : 'alert'}>
          <span className="auth-feedback-icon" aria-hidden="true"><Icon size={17} strokeWidth={1.9} /></span>
          <div className="auth-feedback-copy">
            <span className="auth-feedback-kicker"><span className="auth-feedback-kicker-dot" aria-hidden="true" />{tone === 'locked' ? 'ACCOUNT PAUSED' : tone === 'success' ? 'ALL SET' : 'HEADS UP'}</span>
            <p className="auth-feedback-title">{title}</p>
            <p className="auth-feedback-description">{description}</p>
          </div>
        </div>
      ) : <p className="auth-description">{description}</p>}
    </div>
  );
};
