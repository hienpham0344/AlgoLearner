export function ProfileWaveArtwork() {
  return (
    <div className="profile-hero-artwork" aria-hidden="true">
      <svg
        className="profile-wave-svg"
        viewBox="0 0 540 200"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        preserveAspectRatio="xMidYMid meet"
      >
        <defs>
          <linearGradient id="waveFade" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#222427" stopOpacity="0.02" />
            <stop offset="35%" stopColor="#222427" stopOpacity="0.16" />
            <stop offset="70%" stopColor="#222427" stopOpacity="0.14" />
            <stop offset="100%" stopColor="#222427" stopOpacity="0.03" />
          </linearGradient>
        </defs>

        {/* Delicate layered mathematical wave trajectories */}
        <path
          d="M0 130 C90 100 170 160 270 125 C370 90 450 145 540 110"
          stroke="url(#waveFade)"
          strokeWidth="0.85"
          className="profile-wave-line wave-line-1"
        />
        <path
          d="M0 120 C95 85 175 150 275 115 C375 80 455 135 540 100"
          stroke="url(#waveFade)"
          strokeWidth="0.9"
          className="profile-wave-line wave-line-2"
        />
        <path
          d="M0 110 C100 70 180 140 280 105 C380 70 460 125 540 90"
          stroke="url(#waveFade)"
          strokeWidth="1.0"
          className="profile-wave-line wave-line-3"
        />
        <path
          d="M0 100 C105 55 185 130 285 95 C385 60 465 115 540 80"
          stroke="url(#waveFade)"
          strokeWidth="1.05"
          className="profile-wave-line wave-line-4"
        />
        <path
          d="M0 90 C110 40 190 120 290 85 C390 50 470 105 540 70"
          stroke="url(#waveFade)"
          strokeWidth="1.1"
          className="profile-wave-line wave-line-5"
        />
        <path
          d="M0 80 C115 30 195 110 295 78 C395 45 475 98 540 62"
          stroke="url(#waveFade)"
          strokeWidth="1.05"
          className="profile-wave-line wave-line-6"
        />
        <path
          d="M0 72 C120 22 200 102 300 70 C400 38 480 90 540 55"
          stroke="url(#waveFade)"
          strokeWidth="0.95"
          className="profile-wave-line wave-line-7"
        />
        <path
          d="M0 64 C125 14 205 94 305 62 C405 32 485 82 540 48"
          stroke="url(#waveFade)"
          strokeWidth="0.9"
          className="profile-wave-line wave-line-8"
        />
        <path
          d="M0 58 C130 8 210 86 310 56 C410 26 490 76 540 42"
          stroke="url(#waveFade)"
          strokeWidth="0.8"
          className="profile-wave-line wave-line-9"
        />
        <path
          d="M0 52 C135 2 215 80 315 50 C415 20 495 70 540 36"
          stroke="url(#waveFade)"
          strokeWidth="0.75"
          className="profile-wave-line wave-line-10"
        />
      </svg>
    </div>
  );
}

