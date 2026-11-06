import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { Link, Navigate } from 'react-router-dom';
import { ArrowLeft, ArrowUpRight, Camera, Check } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { profileApi, type OwnerProfile } from '../api/profileApi';
import { validateAvatar } from '../validateAvatar';
import { profileKeys } from '@/features/dashboard/api/dashboardApi';
import { ProfileWaveArtwork } from '../components/ProfileWaveArtwork';
import '../styles/profile.css';

const DISPLAY_TIME_ZONE = 'Asia/Bangkok';

const joinedDateFormatter = new Intl.DateTimeFormat('en', {
  dateStyle: 'long',
  timeZone: DISPLAY_TIME_ZONE,
});

const formatJoinedDate = (value: string) => joinedDateFormatter.format(new Date(value));

const formatStreakNumber = (streak: number | null): string => {
  if (streak === null) return '—';
  if (streak > 0 && streak < 10) return `0${streak}`;
  return String(streak);
};

const resolveErrorText = (error: unknown) => {
  if (isAxiosError(error)) {
    if (error.response?.status === 413) return 'Image is larger than the 5 MB limit.';
    if (error.response?.status === 415) return 'This image could not be accepted. Choose a PNG or JPEG file.';
    if (error.response?.status === 403) return 'This account cannot change this setting.';
    if (error.response?.status === 503) return 'Image storage is unavailable. Your previous avatar is unchanged.';
    if (error.response?.status === 400) return 'Check the information and try again.';
  }
  return 'Could not save this change. Please try again.';
};

type SettingsSectionId = 'profile' | 'visibility' | 'security' | 'details';

interface NavItem {
  id: SettingsSectionId;
  label: string;
}

const SETTINGS_NAV: readonly NavItem[] = [
  { id: 'profile', label: 'Profile' },
  { id: 'visibility', label: 'Visibility' },
  { id: 'security', label: 'Security' },
  { id: 'details', label: 'Account details' },
];

function ProfileEditor({ profile }: { profile: OwnerProfile }) {
  const { token } = useAuth();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [fullName, setFullName] = useState(profile.fullName ?? '');
  const [shortBio, setShortBio] = useState(profile.shortBio ?? '');
  const [feedback, setFeedback] = useState<{ message: string; error: boolean } | null>(null);
  const [avatarError, setAvatarError] = useState('');
  const [avatarSrc, setAvatarSrc] = useState<string | null>(null);
  const [activeSection, setActiveSection] = useState<SettingsSectionId>('profile');

  useEffect(() => {
    setFullName(profile.fullName ?? '');
    setShortBio(profile.shortBio ?? '');
  }, [profile.fullName, profile.shortBio]);

  useEffect(() => {
    if (!profile.avatarUrl || !profile.avatarUrl.startsWith('/api/v1/') || !token) {
      setAvatarSrc(profile.avatarUrl);
      return;
    }

    const controller = new AbortController();
    let objectUrl: string | null = null;

    fetch(profile.avatarUrl, {
      signal: controller.signal,
      headers: { Authorization: `Bearer ${token}` },
    })
      .then(response => {
        if (!response.ok) throw new Error('Avatar unavailable');
        return response.blob();
      })
      .then(blob => {
        objectUrl = URL.createObjectURL(blob);
        setAvatarSrc(objectUrl);
      })
      .catch(() => {
        if (!controller.signal.aborted) setAvatarSrc(null);
      });

    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [profile.avatarUrl, token]);

  useEffect(() => {
    const sectionIds: SettingsSectionId[] = ['profile', 'visibility', 'security', 'details'];
    const elements = sectionIds
      .map(id => document.getElementById(id))
      .filter((el): el is HTMLElement => el !== null);

    if (!elements.length) return;

    const observer = new IntersectionObserver(
      entries => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setActiveSection(entry.target.id as SettingsSectionId);
            break;
          }
        }
      },
      {
        rootMargin: '-15% 0px -65% 0px',
        threshold: 0,
      },
    );

    elements.forEach(el => observer.observe(el));
    return () => observer.disconnect();
  }, []);

  const updateCache = (next: OwnerProfile) => {
    queryClient.setQueryData(profileKeys.own(), next);
    queryClient.invalidateQueries({ queryKey: profileKeys.public(profile.username) });
  };

  const saveMutation = useMutation({
    mutationFn: profileApi.update,
    onSuccess: next => {
      updateCache(next);
      setFeedback({ message: 'Profile changes saved.', error: false });
    },
    onError: error => setFeedback({ message: resolveErrorText(error), error: true }),
  });

  const privacyMutation = useMutation({
    mutationFn: profileApi.privacy,
    onSuccess: next => {
      updateCache(next);
      setFeedback({
        message: next.isPublic ? 'Your public profile is visible.' : 'Your public profile is hidden.',
        error: false,
      });
    },
    onError: error => setFeedback({ message: resolveErrorText(error), error: true }),
  });

  const avatarMutation = useMutation({
    mutationFn: profileApi.avatar,
    onSuccess: next => {
      updateCache(next);
      setAvatarError('');
      setFeedback({ message: 'Avatar updated.', error: false });
    },
    onError: error => setAvatarError(resolveErrorText(error)),
  });

  const hasUnsavedChanges =
    fullName.trim() !== (profile.fullName ?? '').trim()
    || shortBio.trim() !== (profile.shortBio ?? '').trim();

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFeedback(null);

    if (fullName.trim().length > 150 || shortBio.trim().length > 500) {
      setFeedback({
        message: 'Name must be at most 150 characters and bio at most 500.',
        error: true,
      });
      return;
    }

    saveMutation.mutate({
      fullName: fullName.trim(),
      shortBio: shortBio.trim(),
    });
  };

  const handleFileChange = (file?: File) => {
    if (!file) return;
    setAvatarError('');
    setFeedback(null);

    const validationError = validateAvatar(file);
    if (validationError) {
      setAvatarError(validationError);
      return;
    }

    avatarMutation.mutate(file);
  };

  const scrollToSection = (id: SettingsSectionId) => {
    setActiveSection(id);
    const target = document.getElementById(id);
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const displayName = profile.fullName?.trim() || profile.username;
  const initial = profile.username.slice(0, 1).toUpperCase();
  const streakDays = profile.streak.currentStreak;

  return (
    <div className="profile-container">
      {/* 1. Profile Identity Strip */}
      <section className="profile-identity-strip" aria-label="Profile identity and coding summary">
        <div className="profile-identity-user">
          <div className="profile-avatar-anchor">
            <div
              className="profile-avatar-frame"
              onClick={() => fileInputRef.current?.click()}
              role="button"
              tabIndex={0}
              aria-label="Change profile photo"
              onKeyDown={e => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  fileInputRef.current?.click();
                }
              }}
            >
              {avatarSrc ? (
                <img src={avatarSrc} alt={displayName} />
              ) : (
                <span className="profile-avatar-fallback">{initial}</span>
              )}
            </div>

            <button
              type="button"
              className="profile-avatar-edit-affordance"
              aria-label="Upload new photo"
              onClick={() => fileInputRef.current?.click()}
              disabled={avatarMutation.isPending}
            >
              <Camera size={13} aria-hidden="true" />
            </button>

            <input
              ref={fileInputRef}
              type="file"
              className="profile-file-input"
              accept="image/png,image/jpeg"
              disabled={avatarMutation.isPending}
              onChange={event => {
                handleFileChange(event.target.files?.[0]);
                event.target.value = '';
              }}
            />
          </div>

          <div className="profile-identity-meta">
            <div className="profile-identity-headline">
              <h2 className="profile-display-name">{displayName}</h2>
              <span className="profile-role-pill">{profile.role}</span>
            </div>
            <p className="profile-joined-text">Joined {formatJoinedDate(profile.joinedAt)}</p>
            {avatarMutation.isPending && (
              <span className="profile-avatar-status" role="status">
                Uploading new avatar…
              </span>
            )}
            {avatarError && (
              <p className="profile-avatar-error" role="alert">
                {avatarError}
              </p>
            )}
          </div>
        </div>

        <div className="profile-identity-divider" aria-hidden="true" />

        <div className="profile-coding-glance">
          <span className="profile-glance-eyebrow">CODING AT A GLANCE</span>
          <div className="profile-glance-metrics">
            <div className="profile-glance-item">
              <strong className="profile-glance-number">
                {profile.publicPracticeStatistics.solved}
              </strong>
              <span className="profile-glance-label">Problems solved</span>
            </div>
            <div className="profile-glance-item">
              <strong className="profile-glance-number">{formatStreakNumber(streakDays)}</strong>
              <span className="profile-glance-label">Day streak</span>
            </div>
          </div>

          {/* Minimal 12-segment activity sparkline */}
          <div
            className="profile-glance-spark"
            role="img"
            aria-label={`Coding activity consistency: ${streakDays ?? 0} days streak`}
          >
            {Array.from({ length: 12 }, (_, i) => {
              const active = streakDays !== null && i >= 12 - Math.min(streakDays, 12);
              return <span key={i} data-active={active} />;
            })}
          </div>
        </div>
      </section>

      {/* 2. Editorial Settings Layout */}
      <div className="profile-settings-layout">
        {/* Left Column: Section Navigation */}
        <aside className="profile-settings-nav-pane">
          <nav className="profile-settings-nav" aria-label="Settings sections">
            {SETTINGS_NAV.map(item => {
              const isActive = activeSection === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  className="profile-nav-btn"
                  data-active={isActive}
                  onClick={() => scrollToSection(item.id)}
                  aria-current={isActive ? 'true' : undefined}
                >
                  <span>{item.label}</span>
                </button>
              );
            })}
          </nav>
        </aside>

        {/* Right Column: Settings Content */}
        <main className="profile-settings-content">
          <form onSubmit={handleSubmit} className="profile-form-main">
            {/* Section 1: Personal Information */}
            <section id="profile" className="profile-settings-section" aria-labelledby="personal-heading">
              <div className="profile-section-header">
                <span className="profile-section-eyebrow">01 / PROFILE INFORMATION</span>
                <h3 id="personal-heading">Personal information</h3>
                <p>Keep your name and introduction up to date.</p>
              </div>

              <div className="profile-form-fields">
                <label className="profile-field-label">
                  <span>Full name</span>
                  <input
                    className="profile-input"
                    value={fullName}
                    maxLength={150}
                    onChange={event => setFullName(event.target.value)}
                    autoComplete="name"
                    placeholder="Enter your full name"
                  />
                </label>

                <label className="profile-field-label">
                  <span>Short bio</span>
                  <div className="profile-textarea-wrap">
                    <textarea
                      className="profile-textarea"
                      value={shortBio}
                      maxLength={500}
                      onChange={event => setShortBio(event.target.value)}
                      rows={4}
                      placeholder="A few words about your learning journey, goals, or technologies you love"
                    />
                    <small className="profile-char-counter">{shortBio.length}/500</small>
                  </div>
                </label>
              </div>
            </section>

            {/* Section 2: Visibility */}
            <section id="visibility" className="profile-settings-section" aria-labelledby="visibility-heading">
              <div className="profile-section-header">
                <span className="profile-section-eyebrow">02 / VISIBILITY</span>
                <h3 id="visibility-heading">Public profile</h3>
                <p>Share your coding progress with the community when you choose.</p>
              </div>

              <div className="profile-setting-row">
                <div className="profile-setting-lead">
                  <strong>Show my public profile</strong>
                  <p>Only your username, avatar, bio, solved count and streak can appear publicly.</p>
                </div>
                <label className="profile-switch">
                  <input
                    type="checkbox"
                    checked={profile.isPublic}
                    disabled={privacyMutation.isPending}
                    onChange={event => {
                      setFeedback(null);
                      privacyMutation.mutate(event.target.checked);
                    }}
                    aria-label="Show my public profile"
                  />
                  <span />
                </label>
              </div>

              {profile.isPublic && (
                <div className="profile-link-row">
                  <Link
                    className="profile-text-link"
                    to={`/u/${encodeURIComponent(profile.username)}`}
                  >
                    Preview public profile <ArrowUpRight size={14} aria-hidden="true" />
                  </Link>
                </div>
              )}
            </section>

            {/* Section 3: Security */}
            <section id="security" className="profile-settings-section" aria-labelledby="security-heading">
              <div className="profile-section-header">
                <span className="profile-section-eyebrow">03 / SECURITY</span>
                <h3 id="security-heading">Account security</h3>
                <p>Manage your password and keep your account safe.</p>
              </div>

              <div className="profile-setting-row">
                <div className="profile-setting-lead">
                  <strong>Change password</strong>
                  <p>Use the verified email reset flow to choose a new password.</p>
                </div>
                <Link to="/forgot-password" className="profile-action-link">
                  Reset password <ArrowUpRight size={14} aria-hidden="true" />
                </Link>
              </div>
            </section>

            {/* Section 4: Account Details */}
            <section id="details" className="profile-settings-section" aria-labelledby="details-heading">
              <div className="profile-section-header">
                <span className="profile-section-eyebrow">04 / CREDENTIALS</span>
                <h3 id="details-heading">Account details</h3>
                <p>Read-only credentials associated with your AlgoLearn workspace.</p>
              </div>

              <dl className="profile-details-list">
                <div className="profile-details-row">
                  <dt>Username</dt>
                  <dd>@{profile.username}</dd>
                </div>
                <div className="profile-details-row">
                  <dt>Email</dt>
                  <dd>{profile.email}</dd>
                </div>
                <div className="profile-details-row">
                  <dt>Role</dt>
                  <dd>{profile.role.toLowerCase()}</dd>
                </div>
                <div className="profile-details-row">
                  <dt>Joined</dt>
                  <dd>{formatJoinedDate(profile.joinedAt)}</dd>
                </div>
              </dl>
              <p className="profile-details-hint">
                Username, email, and role cannot be changed directly here.
              </p>
            </section>

            {/* Form Actions Footer */}
            <div className="profile-form-footer">
              <button
                className="account-primary"
                type="submit"
                disabled={!hasUnsavedChanges || saveMutation.isPending}
              >
                {saveMutation.isPending ? 'Saving changes…' : 'Save changes'}
              </button>
            </div>
          </form>
        </main>
      </div>

      {feedback && (
        <div className="account-feedback" role={feedback.error ? 'alert' : 'status'}>
          {!feedback.error && <Check size={15} aria-hidden="true" />}
          <span>{feedback.message}</span>
        </div>
      )}
    </div>
  );
}

export function ProfilePage() {
  const { user, logout } = useAuth();

  const query = useQuery({
    queryKey: profileKeys.own(),
    queryFn: ({ signal }) => profileApi.own(signal),
    enabled: user?.role === 'STUDENT' || user?.role === 'TEACHER',
    retry: false,
  });

  useEffect(() => {
    if (isAxiosError(query.error) && query.error.response?.status === 401) {
      logout();
    }
  }, [query.error, logout]);

  if (!user) return <Navigate to="/login" replace />;

  if (user.role === 'ADMIN') {
    return (
      <div className="account-page">
        <div className="account-wrap">
          <header className="profile-topbar">
            <Link to="/dashboard" className="profile-back-link">
              <ArrowLeft size={15} aria-hidden="true" />
              <span>Workspace</span>
            </Link>
            <span className="profile-topbar-tag">ALGOLEARN / ACCOUNT</span>
          </header>
          <div className="profile-admin-message">
            <span className="profile-hero-eyebrow">ADMINISTRATOR WORKSPACE</span>
            <h1>Profile settings</h1>
            <p>Personal profile editing is available for Student and Teacher accounts.</p>
            <Link to="/dashboard" className="account-primary">
              Return to dashboard
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const isForbidden = isAxiosError(query.error) && query.error.response?.status === 403;

  return (
    <div className="account-page">
      <div className="account-wrap">
        {/* Minimal top navigation */}
        <header className="profile-topbar">
          <Link to="/dashboard" className="profile-back-link">
            <ArrowLeft size={15} aria-hidden="true" />
            <span>Workspace</span>
          </Link>
          <span className="profile-topbar-tag">ALGOLEARN / ACCOUNT</span>
        </header>

        {/* Editorial hero */}
        <section className="profile-hero" aria-labelledby="profile-hero-title">
          <div className="profile-hero-text">
            <span className="profile-hero-eyebrow">YOUR SPACE, YOUR SETTINGS</span>
            <h1 id="profile-hero-title">Make it yours.</h1>
            <p>Manage your profile, visibility, and account security.</p>
          </div>
          <ProfileWaveArtwork />
        </section>

        {query.isPending ? (
          <div className="profile-state-pane" role="status">
            <h2>Loading your profile…</h2>
          </div>
        ) : query.isError ? (
          <div className="profile-state-pane" role="alert">
            <h2>Profile unavailable</h2>
            <p>
              {isForbidden
                ? 'You do not have permission to view this profile.'
                : 'We could not load your profile.'}
            </p>
            {!isForbidden && (
              <button type="button" className="account-primary" onClick={() => query.refetch()}>
                Try again
              </button>
            )}
          </div>
        ) : (
          <ProfileEditor profile={query.data} />
        )}
      </div>
    </div>
  );
}
