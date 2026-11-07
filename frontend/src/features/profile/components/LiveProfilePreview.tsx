interface LiveProfilePreviewProps {
  avatarSrc: string | null;
  displayName: string;
  username: string;
  bio: string;
  isPublic: boolean;
  solvedCount?: number;
  streakDays?: number | null;
}

const formatStatNumber = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return '00';
  if (value > 0 && value < 10) return `0${value}`;
  return String(value);
};

export function LiveProfilePreview({
  avatarSrc,
  displayName,
  username,
  bio,
  isPublic,
  solvedCount = 0,
  streakDays = 0,
}: LiveProfilePreviewProps) {
  const initial = username.slice(0, 1).toUpperCase();
  const effectiveName = displayName.trim() || username;

  return (
    <aside className="folder-identity-preview" aria-label="Live profile editorial preview">
      {/* Header Eyebrow */}
      <div className="identity-preview-header">
        <span className="identity-preview-eyebrow">LIVE PREVIEW</span>
        <span className="identity-visibility-pill" data-public={isPublic}>
          <span className="identity-visibility-dot" aria-hidden="true" />
          {isPublic ? 'Public' : 'Private'}
        </span>
      </div>

      {/* Identity Card Specimen */}
      <div className="identity-specimen-card">
        {/* Subtle monochrome generative contour waves in top-right */}
        <div className="identity-contour-waves" aria-hidden="true">
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

        {/* Squircle Profile Photo */}
        <div className="identity-avatar-squircle">
          {avatarSrc ? (
            <img src={avatarSrc} alt={effectiveName} className="identity-avatar-img" />
          ) : (
            <span className="identity-avatar-fallback">{initial}</span>
          )}
        </div>

        {/* Display Name and Username */}
        <div className="identity-meta">
          <h3 className="identity-name">{effectiveName}</h3>
          <span className="identity-handle">@{username}</span>
        </div>

        {/* Bio Text */}
        <div className="identity-bio-area">
          {bio.trim() ? (
            <p className="identity-bio-text">{bio.trim()}</p>
          ) : (
            <p className="identity-bio-placeholder">
              Learning to build, one small step at a time.
            </p>
          )}
        </div>

        {/* Hairline Divider */}
        <div className="identity-specimen-divider" aria-hidden="true" />

        {/* Coding Statistics Specimen */}
        <div className="identity-stats-row">
          <div className="identity-stat-cell">
            <strong className="identity-stat-num">{solvedCount}</strong>
            <span className="identity-stat-label">Solved</span>
          </div>
          <div className="identity-stats-separator" aria-hidden="true" />
          <div className="identity-stat-cell">
            <strong className="identity-stat-num">{formatStatNumber(streakDays)}</strong>
            <span className="identity-stat-label">Day streak</span>
          </div>
        </div>
      </div>
    </aside>
  );
}
