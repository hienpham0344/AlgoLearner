import { useEffect, useState, type ReactNode } from 'react';
import byteWrong from '@/assets/mascot-byte-wrong.svg';
import bugWrong from '@/assets/mascot-bug-wrong.svg';
import kitWrong from '@/assets/mascot-kit-wrong.svg';

const mascots = [byteWrong, bugWrong, kitWrong];

interface Dialogue {
  title: string;
  description: ReactNode;
  tone: 'error' | 'locked';
  mascotCue: number;
}

interface LoginFeedbackBubbleProps {
  dialogue: Dialogue | null;
}

export const LoginFeedbackBubble = ({ dialogue }: LoginFeedbackBubbleProps) => {
  const [leavingDialogue, setLeavingDialogue] = useState<Dialogue | null>(null);

  useEffect(() => {
    if (dialogue) { setLeavingDialogue(dialogue); return; }
    const timer = window.setTimeout(() => setLeavingDialogue(null), 240);
    return () => window.clearTimeout(timer);
  }, [dialogue]);

  const shownDialogue = dialogue || leavingDialogue;
  return (
    <div className="auth-login-dialogue-slot" data-visible={Boolean(dialogue)}>
      <div className="auth-login-dialogue-clip">
        {shownDialogue && (
          <div className="auth-login-dialogue" data-tone={shownDialogue.tone} role={dialogue ? 'alert' : undefined} aria-hidden={!dialogue}>
            <span className="auth-login-dialogue-mascot" aria-hidden="true">
              <img key={shownDialogue.mascotCue} src={mascots[(shownDialogue.mascotCue - 1) % mascots.length]} alt="" width="280" height="280" />
            </span>
            <div className="auth-login-dialogue-bubble">
              <p className="auth-login-dialogue-title">{shownDialogue.title}</p>
              <p className="auth-login-dialogue-description">{shownDialogue.description}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
