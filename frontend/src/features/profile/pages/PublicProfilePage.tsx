import { useState, useEffect, useRef, type MouseEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Check, Copy, Flame, RotateCcw } from 'lucide-react';
import { profileKeys } from '@/features/dashboard/api/dashboardApi';
import { profileApi } from '../api/profileApi';
import '../styles/profile.css';

const formatStreakNumber = (streak: number | null | undefined): string => {
  if (streak === null || streak === undefined) return '—';
  if (streak > 0 && streak < 10) return `0${streak}`;
  return String(streak);
};

export function PublicProfilePage() {
  const { username = '' } = useParams();
  const [copied, setCopied] = useState(false);
  const [isExtracted, setIsExtracted] = useState(false);
  const [avatarLoadError, setAvatarLoadError] = useState(false);
  const [parallax, setParallax] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const extractTimerRef = useRef<ReturnType<typeof setTimeout>>(null);

  const query = useQuery({
    queryKey: profileKeys.public(username),
    queryFn: ({ signal }) => profileApi.public(username, signal),
    enabled: Boolean(username),
    retry: false,
  });

  const isNotFound = isAxiosError(query.error) && query.error.response?.status === 404;
  const avatarSrc = query.data?.avatarUrl
    ? `${query.data.avatarUrl}?t=${query.dataUpdatedAt}`
    : null;

  useEffect(() => {
    setAvatarLoadError(false);
  }, [avatarSrc]);

  // Physical extraction choreograph: document glides up from behind the folder backing
  useEffect(() => {
    if (query.isSuccess) {
      setIsExtracted(false);
      extractTimerRef.current = setTimeout(() => {
        setIsExtracted(true);
      }, 760);
    }
    return () => {
      if (extractTimerRef.current) clearTimeout(extractTimerRef.current);
    };
  }, [query.isSuccess, query.data?.username]);

  // Restrained desktop pointer parallax (1-3px tactile paper depth)
  const handleMouseMove = (e: MouseEvent<HTMLDivElement>) => {
    if (!isExtracted) return;
    // Disable on touch / coarse pointers or when motion is reduced
    if (window.matchMedia('(hover: none) or (prefers-reduced-motion: reduce)').matches) return;

    const rect = e.currentTarget.getBoundingClientRect();
    if (!rect.width || !rect.height) return;

    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    const normX = Math.max(-1, Math.min(1, (e.clientX - centerX) / (rect.width / 2)));
    const normY = Math.max(-1, Math.min(1, (e.clientY - centerY) / (rect.height / 2)));
    setParallax({ x: normX, y: normY });
  };

  const handleMouseLeave = () => {
    setParallax({ x: 0, y: 0 });
  };

  const handleCopyLink = async () => {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(window.location.href);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = window.location.href;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
      }
      setCopied(true);
      setTimeout(() => setCopied(false), 2200);
    } catch {
      // Fallback
    }
  };

  return (
    <main className="public-profile-page">
      <div className="public-folio-wrap">
        {/* Minimal Editorial Top Navigation */}
        <header className="public-folio-header">
          <Link to="/dashboard" className="public-folio-back">
            <ArrowLeft size={16} aria-hidden="true" />
            <span>AlgoLearn</span>
          </Link>
          <span className="public-folio-badge">COMMUNITY PROFILE</span>
        </header>

        {query.isPending ? (
          /* SKELETON STATE: Pre-rendered physical paper stack eliminates layout jumps */
          <div className="public-folio-stage" role="status" aria-label="Loading learner folio">
            <div className="folio-charcoal-backing" aria-hidden="true" />
            <div className="folio-underlay-sheet" aria-hidden="true" />
            <article className="folio-document folio-document-skeleton">
              <div className="folio-doc-header">
                <div className="folio-skeleton-bar folio-skeleton-eyebrow" />
              </div>
              <div className="folio-identity-row">
                <div className="folio-skeleton-avatar" />
                <div className="folio-identity-text">
                  <div className="folio-skeleton-bar folio-skeleton-name" />
                  <div className="folio-skeleton-bar folio-skeleton-handle" />
                </div>
              </div>
              <div className="folio-bio-wrap">
                <div className="folio-skeleton-bar folio-skeleton-bio-1" />
                <div className="folio-skeleton-bar folio-skeleton-bio-2" />
              </div>
              <div className="folio-structural-divider" aria-hidden="true" />
              <div className="folio-stats-grid">
                <div className="folio-stat-block">
                  <div className="folio-skeleton-bar folio-skeleton-stat-num" />
                  <div className="folio-skeleton-bar folio-skeleton-stat-label" />
                </div>
                <div className="folio-stat-divider" aria-hidden="true" />
                <div className="folio-stat-block">
                  <div className="folio-skeleton-bar folio-skeleton-stat-num" />
                  <div className="folio-skeleton-bar folio-skeleton-stat-label" />
                </div>
              </div>
            </article>
          </div>
        ) : query.isError ? (
          /* ERROR & PRIVATE STATE: Editorial presentation on tactile paper */
          <div className="public-folio-stage" role="alert">
            <div className="folio-charcoal-backing" aria-hidden="true" />
            <div className="folio-underlay-sheet" aria-hidden="true" />
            <article className="folio-document folio-state-document">
              <div className="folio-doc-header">
                <div className="folio-eyebrow">
                  <span className="folio-eyebrow-brand">ALGOLEARN</span>
                  <span className="folio-eyebrow-slash">/</span>
                  <span className="folio-eyebrow-type">
                    {isNotFound ? 'PRIVATE FOLIO' : 'SYSTEM NOTICE'}
                  </span>
                </div>
              </div>

              <div className="folio-state-body">
                <h1 className="folio-state-title">
                  {isNotFound ? 'Profile unavailable.' : 'Could not load this profile.'}
                </h1>
                <p className="folio-state-desc">
                  {isNotFound
                    ? 'This learner profile is set to private or does not exist.'
                    : 'A network error occurred while retrieving this folio. Please try again.'}
                </p>
                {isNotFound ? (
                  <Link to="/dashboard" className="folio-state-action">
                    <ArrowLeft size={14} aria-hidden="true" />
                    <span>Back to Workspace</span>
                  </Link>
                ) : (
                  <button
                    type="button"
                    className="folio-state-action"
                    onClick={() => query.refetch()}
                  >
                    <RotateCcw size={14} aria-hidden="true" />
                    <span>Try again</span>
                  </button>
                )}
              </div>

              <footer className="folio-footer-signature">
                <span className="folio-footer-motto">THINK. CODE. REPEAT.</span>
                <span className="folio-footer-mark">ALGOLEARN</span>
              </footer>
            </article>
          </div>
        ) : (
          /* SUCCESS STATE: THE LEARNER FOLIO — Extracted Paper Portfolio */
          <div
            className="public-folio-stage"
            onMouseMove={handleMouseMove}
            onMouseLeave={handleMouseLeave}
          >
            {/* LAYER 1: Charcoal Folder Cover (Angled dark backing with smooth radii) */}
            <div
              className="folio-charcoal-backing"
              aria-hidden="true"
              style={{
                transform: `rotate(-1.5deg) translate3d(${parallax.x * 0.8}px, ${parallax.y * 0.8}px, 0)`,
              }}
            />

            {/* LAYER 2: Secondary Warm Paper Sheet (Subtle offset underlay) */}
            <div
              className="folio-underlay-sheet"
              aria-hidden="true"
              style={{
                transform: `rotate(-0.6deg) translate3d(${parallax.x * 1.4}px, ${parallax.y * 1.4}px, 0)`,
              }}
            />

            {/* LAYER 3: The Learner Folio Document (Extracted foreground sheet) */}
            <article
              className={`folio-document ${isExtracted ? 'folio-document-settled' : 'folio-document-extracting'}`}
              style={{
                transform: isExtracted
                  ? `translate3d(${parallax.x * 2.5}px, ${parallax.y * 2.5}px, 0)`
                  : undefined,
              }}
            >
              {/* Subtle Monochrome Generative Waves Texture */}
              <div className="folio-contour-waves" aria-hidden="true">
                <svg viewBox="0 0 280 190" fill="none" xmlns="http://www.w3.org/2000/svg">
                  {Array.from({ length: 12 }, (_, index) => (
                    <path
                      key={index}
                      d={`M ${40 + index * 7} ${-18 + index * 5} C ${140 + index * 3} ${8 + index * 4} ${162 + index * 2} ${104 + index * 3} 290 ${77 + index * 7}`}
                      stroke="currentColor"
                      strokeWidth="0.7"
                    />
                  ))}
                </svg>
              </div>

              {/* TOP: Document Identity Header & Copy Action */}
              <div className="folio-doc-header">
                <div className="folio-eyebrow">
                  <span className="folio-eyebrow-brand">ALGOLEARN</span>
                  <span className="folio-eyebrow-slash">/</span>
                  <span className="folio-eyebrow-type">LEARNER FOLIO</span>
                </div>

                <button
                  type="button"
                  className="folio-copy-btn"
                  onClick={handleCopyLink}
                  aria-label="Copy public profile link"
                  data-copied={copied}
                >
                  {copied ? (
                    <>
                      <Check size={12} aria-hidden="true" />
                      <span>Copied link</span>
                    </>
                  ) : (
                    <>
                      <Copy size={12} aria-hidden="true" />
                      <span>Share folio</span>
                    </>
                  )}
                </button>
              </div>

              {/* MIDDLE: Learner Identity (Prominent Squircle Avatar + Bold Name + Handle) */}
              <div className="folio-identity-row">
                <div className="folio-avatar-squircle">
                  {avatarSrc && !avatarLoadError ? (
                    <img
                      src={avatarSrc}
                      alt={query.data.username}
                      className="folio-avatar-image"
                      onError={() => setAvatarLoadError(true)}
                    />
                  ) : (
                    <span className="folio-avatar-fallback">
                      {query.data.username.slice(0, 1).toUpperCase()}
                    </span>
                  )}
                </div>

                <div className="folio-identity-text">
                  <h1 className="folio-learner-title">{query.data.username}</h1>
                  <span className="folio-learner-handle">@{query.data.username}</span>
                </div>
              </div>

              {/* BIOGRAPHY: Strictly constrained with overflow-wrap: anywhere to eliminate overflow */}
              <div className="folio-bio-wrap">
                <p className="folio-bio-content">
                  {query.data.shortBio?.trim() || 'Learning to build, one small step at a time.'}
                </p>
              </div>

              {/* Delicate Structural Divider */}
              <div className="folio-structural-divider" aria-hidden="true" />

              {/* BOTTOM: Coding Identity (Authorized Public Statistics with Clear Typography) */}
              <div className="folio-stats-grid">
                <div className="folio-stat-block">
                  <strong className="folio-stat-value">{query.data.solved}</strong>
                  <span className="folio-stat-caption">Problems solved</span>
                </div>

                <div className="folio-stat-divider" aria-hidden="true" />

                <div className="folio-stat-block">
                  <div className="folio-stat-value-row">
                    <strong className="folio-stat-value">
                      {formatStreakNumber(query.data.streak?.currentStreak)}
                    </strong>
                    <Flame size={24} className="folio-streak-icon" aria-hidden="true" />
                  </div>
                  <span className="folio-stat-caption">Day streak</span>
                </div>
              </div>

              {/* FOOTER: Restrained Editorial Document Signature */}
              <footer className="folio-footer-signature">
                <span className="folio-footer-motto">THINK. CODE. REPEAT.</span>
                <span className="folio-footer-mark">ALGOLEARN</span>
              </footer>
            </article>
          </div>
        )}
      </div>
    </main>
  );
}
