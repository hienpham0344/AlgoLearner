import { useEffect, useRef, useState } from 'react';
import byteSvg from '@/assets/mascot-byte.svg';
import bugSvg from '@/assets/mascot-bug.svg';
import kitSvg from '@/assets/mascot-kit.svg';

const companions = [
  { name: 'Byte', src: byteSvg },
  { name: 'Bug', src: bugSvg },
  { name: 'Kit', src: kitSvg },
] as const;

export const InteractiveHeroMascots = () => {
  const [active, setActive] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  const greet = (name: string) => {
    if (timerRef.current) clearTimeout(timerRef.current);
    setActive(name);
    timerRef.current = setTimeout(() => setActive(null), 1000);
  };

  return (
    <div className="auth-mascots" aria-label="Meet Byte, Bug, and Kit">
      {companions.map(({ name, src }) => (
        <button type="button" className="auth-mascot" key={name} data-reacting={active === name} aria-label={`Say hello to ${name}`} onClick={() => greet(name)}>
          <span className="auth-mascot-name" aria-hidden="true">{name}</span>
          <img src={src} alt={name} width="280" height="280" draggable={false} />
        </button>
      ))}
    </div>
  );
};
