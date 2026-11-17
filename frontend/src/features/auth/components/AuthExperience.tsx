import { useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import logoMark from '@/assets/logo-mark.svg';
import { InteractiveDotGrid } from '@/components/ui/InteractiveDotGrid';
import { InteractiveHeroMascots } from './InteractiveHeroMascots';
import '../styles/auth-experience.css';

export const AuthExperience = () => {
  const { pathname } = useLocation();
  const panelRef = useRef<HTMLDivElement>(null);
  const previousPath = useRef(pathname);
  const [reducedMotion, setReducedMotion] = useState(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  const isRegister = pathname === '/register';

  useEffect(() => {
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = () => setReducedMotion(query.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);

  useEffect(() => {
    if (previousPath.current !== pathname) {
      panelRef.current?.querySelector<HTMLElement>('h1')?.focus({ preventScroll: true });
      window.scrollTo({ top: 0, behavior: 'instant' });
      previousPath.current = pathname;
    }
  }, [pathname]);

  return (
    <div className="auth-experience" lang="en">
      <a className="auth-skip" href="#auth-panel">Skip to form</a>
      <header className="auth-header">
        <Link to="/login" className="auth-logo" aria-label="AlgoLearn sign in">
          <img className="auth-logo-mark" src={logoMark} alt="" width="76" height="80" />
          <span className="auth-logo-wordmark" aria-hidden="true">AlgoLearn</span>
        </Link>
        <span className="auth-header-note">A little practice, every day.</span>
      </header>

      <main className="auth-main">
        <div className="auth-composition">
          <aside className="auth-showcase" aria-label="AlgoLearn coding education">
            {!reducedMotion && <InteractiveDotGrid dotGap={26} baseRadius={1} activeRadius={1.7} interactionRadius={100} repelStrength={2} enableRipple={false} className="auth-dot-grid" />}
            <div className="auth-showcase-copy">
              <p className="auth-eyebrow">A space to learn code</p>
              <h2>Think.<br />Code.<br /><span>Repeat.</span></h2>
              <p className="auth-showcase-description">{isRegister ? 'Start with a little curiosity. See where it takes you.' : 'Learn the fundamentals. Make something of your own.'}</p>
            </div>
            <div className="auth-showcase-bottom">
              <InteractiveHeroMascots />
              <div className="auth-showcase-footnote"><span>Small steps. Real progress.</span><span aria-hidden="true">&lt; / &gt;</span></div>
            </div>
          </aside>

          <section className="auth-panel" id="auth-panel" ref={panelRef} tabIndex={-1} aria-label="Account access">
            <div className="auth-panel-content" key={pathname}><Outlet /></div>
          </section>
        </div>
      </main>

      <footer className="auth-footer">
        <span>© {new Date().getFullYear()} AlgoLearn</span>
        <span>For the way you learn.</span>
      </footer>
    </div>
  );
};
