import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { Link, Navigate } from 'react-router-dom';
import { ArrowLeft, ArrowUpRight, Camera, Check, LockKeyhole, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { profileApi, type OwnerProfile } from '../api/profileApi';
import { validateAvatar } from '../validateAvatar';
import { profileKeys } from '@/features/dashboard/api/dashboardApi';
import '../styles/profile.css';

const joinedDateFormatter = new Intl.DateTimeFormat('en', {
  dateStyle: 'long',
  timeZone: 'Asia/Bangkok',
});

const formatJoinedDate = (value: string) => joinedDateFormatter.format(new Date(value));

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

function ProfileEditor({ profile }: { profile: OwnerProfile }) {
  const { token } = useAuth();
  const queryClient = useQueryClient();

  const [fullName, setFullName] = useState(profile.fullName ?? '');
  const [shortBio, setShortBio] = useState(profile.shortBio ?? '');
  const [feedback, setFeedback] = useState<{ message: string; error: boolean } | null>(null);
  const [avatarError, setAvatarError] = useState('');
  const [avatarSrc, setAvatarSrc] = useState<string | null>(null);

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

  return (
    <div className="account-grid">
      <div className="account-main">
        <section className="account-panel" aria-labelledby="personal-title">
          <div className="account-section-top">
            <span>01 / IDENTITY</span>
            <h2 id="personal-title">Personal information</h2>
            <p>Keep your name and introduction up to date.</p>
          </div>

          <div className="account-avatar-row">
            <div className="account-avatar-large">
              {avatarSrc ? <img src={avatarSrc} alt="Your avatar" /> : profile.username.slice(0, 1).toUpperCase()}
            </div>
            <div>
              <label className="account-upload">
                <Camera size={16} aria-hidden="true" />{' '}
                {avatarMutation.isPending ? 'Uploading…' : 'Upload new image'}
                <input
                  type="file"
                  accept="image/png,image/jpeg"
                  disabled={avatarMutation.isPending}
                  onChange={event => {
                    handleFileChange(event.target.files?.[0]);
                    event.target.value = '';
                  }}
                />
              </label>
              <p className="account-hint">PNG or JPEG · up to 5 MB</p>
              {avatarError && (
                <p className="account-error" role="alert">
                  {avatarError}
                </p>
              )}
            </div>
          </div>

          <form onSubmit={handleSubmit} className="account-form">
            <label>
              Full name
              <input
                value={fullName}
                maxLength={150}
                onChange={event => setFullName(event.target.value)}
                autoComplete="name"
              />
            </label>
            <label>
              Short bio
              <textarea
                value={shortBio}
                maxLength={500}
                onChange={event => setShortBio(event.target.value)}
                rows={4}
                placeholder="A few words about your learning journey"
              />
              <small>{shortBio.length}/500</small>
            </label>
            <div className="account-form-actions">
              <button className="account-primary" type="submit" disabled={saveMutation.isPending}>
                Save changes
              </button>
            </div>
          </form>
        </section>

        <section className="account-panel" aria-labelledby="privacy-title">
          <div className="account-section-top">
            <span>02 / VISIBILITY</span>
            <h2 id="privacy-title">Public profile</h2>
            <p>Share your coding progress with the community when you choose.</p>
          </div>

          <div className="account-toggle-row">
            <div>
              <strong>Show my public profile</strong>
              <p>Only your username, avatar, bio, solved count and streak can appear publicly.</p>
            </div>
            <label className="account-switch">
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
            <Link className="account-text-link" to={`/u/${encodeURIComponent(profile.username)}`}>
              Preview public profile <ArrowUpRight size={15} aria-hidden="true" />
            </Link>
          )}
        </section>

        <section className="account-panel" aria-labelledby="security-title">
          <div className="account-section-top">
            <span>03 / SECURITY</span>
            <h2 id="security-title">Account security</h2>
          </div>
          <div className="account-security-row">
            <LockKeyhole size={19} aria-hidden="true" />
            <div>
              <strong>Change password</strong>
              <p>Use the verified email reset flow to choose a new password.</p>
            </div>
            <Link to="/forgot-password">
              Reset password <ArrowUpRight size={15} aria-hidden="true" />
            </Link>
          </div>
        </section>
      </div>

      <aside className="account-side">
        <section className="account-panel">
          <div className="account-section-top">
            <span>ACCOUNT DETAILS</span>
            <h2>At a glance</h2>
          </div>
          <dl className="account-facts">
            <div>
              <dt>Username</dt>
              <dd>{profile.username}</dd>
            </div>
            <div>
              <dt>Email</dt>
              <dd>{profile.email}</dd>
            </div>
            <div>
              <dt>Role</dt>
              <dd>{profile.role.toLowerCase()}</dd>
            </div>
            <div>
              <dt>Joined</dt>
              <dd>{formatJoinedDate(profile.joinedAt)}</dd>
            </div>
          </dl>
          <p className="account-hint">
            <ShieldCheck size={14} aria-hidden="true" /> Username, email and role cannot be changed here.
          </p>
        </section>

        <section className="account-panel">
          <div className="account-section-top">
            <span>PUBLIC PRACTICE</span>
            <h2>Your progress</h2>
          </div>
          <div className="account-progress-stats">
            <div>
              <strong>{profile.publicPracticeStatistics.solved}</strong>
              <span>Solved</span>
            </div>
            <div>
              <strong>{profile.streak.currentStreak}</strong>
              <span>Day streak</span>
            </div>
          </div>
        </section>
      </aside>

      {feedback && (
        <p className="account-feedback" role={feedback.error ? 'alert' : 'status'}>
          {!feedback.error && <Check size={16} aria-hidden="true" />}
          {feedback.message}
        </p>
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
          <Link to="/dashboard">
            <ArrowLeft size={16} /> Dashboard
          </Link>
          <h1>Profile settings</h1>
          <p>Personal profile editing is available to Student and Teacher accounts.</p>
        </div>
      </div>
    );
  }

  const isForbidden = isAxiosError(query.error) && query.error.response?.status === 403;

  return (
    <div className="account-page">
      <div className="account-wrap">
        <header className="account-header">
          <Link to="/dashboard">
            <ArrowLeft size={16} aria-hidden="true" /> Workspace
          </Link>
          <span>ALGOLEARN / ACCOUNT</span>
        </header>

        <div className="account-intro">
          <span>YOUR SPACE, YOUR SETTINGS</span>
          <h1>Make it yours.</h1>
          <p>Manage the details people see and the details only you can see.</p>
        </div>

        {query.isPending ? (
          <div className="account-panel account-state" role="status">
            Loading your profile…
          </div>
        ) : query.isError ? (
          <div className="account-panel account-state" role="alert">
            <h2>Profile unavailable</h2>
            <p>
              {isForbidden
                ? 'You do not have permission to view this profile.'
                : 'We could not load your profile.'}
            </p>
            <button type="button" onClick={() => query.refetch()}>
              Try again
            </button>
          </div>
        ) : (
          <ProfileEditor profile={query.data} />
        )}
      </div>
    </div>
  );
}
