import { useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { Link, Navigate } from 'react-router-dom';
import { ArrowLeft, ArrowUpRight, Camera, Check, Upload } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { profileApi, type OwnerProfile } from '../api/profileApi';
import { validateAvatar } from '../validateAvatar';
import { profileKeys } from '@/features/dashboard/api/dashboardApi';
import { FolderBackingArtwork } from '../components/FolderBackingArtwork';
import { FolderTabShape } from '../components/FolderTabShape';
import { LiveProfilePreview } from '../components/LiveProfilePreview';
import '../styles/profile.css';

const DISPLAY_TIME_ZONE = 'Asia/Bangkok';

const joinedDateFormatter = new Intl.DateTimeFormat('en', {
  dateStyle: 'long',
  timeZone: DISPLAY_TIME_ZONE,
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

type FolderTabId = 'profile' | 'privacy' | 'security' | 'account';

interface TabItem {
  id: FolderTabId;
  label: string;
}

const FOLDER_TABS: readonly TabItem[] = [
  { id: 'profile', label: 'Profile' },
  { id: 'privacy', label: 'Privacy' },
  { id: 'security', label: 'Security' },
  { id: 'account', label: 'Account' },
];

const MAX_BIO_LENGTH = 200;

function ProfileFolder({ profile }: { profile: OwnerProfile }) {
  const { token } = useAuth();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const tabRefs = useRef<Map<FolderTabId, HTMLButtonElement>>(new Map());

  // Layered sheet transition state
  const [activeTab, setActiveTab] = useState<FolderTabId>('profile');
  const [displayTab, setDisplayTab] = useState<FolderTabId>('profile');
  const [recedingTab, setRecedingTab] = useState<FolderTabId | null>(null);
  const [enteringTab, setEnteringTab] = useState<FolderTabId | null>(null);
  const [viewportHeight, setViewportHeight] = useState<number | null>(null);

  const panelMeasureRefs = useRef<Map<FolderTabId, HTMLDivElement>>(new Map());
  const transitionTimerRef = useRef<ReturnType<typeof setTimeout>>(null);
  const revealTimerRef = useRef<ReturnType<typeof setTimeout>>(null);
  const transitionFrameRef = useRef<number | null>(null);
  const revealFrameRef = useRef<number | null>(null);

  // Form field state
  const [fullName, setFullName] = useState(profile.fullName ?? '');
  const [shortBio, setShortBio] = useState(profile.shortBio ?? '');
  const [feedback, setFeedback] = useState<{ message: string; error: boolean } | null>(null);
  const [avatarError, setAvatarError] = useState('');
  const feedbackTimerRef = useRef<ReturnType<typeof setTimeout>>(null);

  // Auto-dismiss feedback notification after 3.5 seconds
  useEffect(() => {
    if (feedback) {
      if (feedbackTimerRef.current) clearTimeout(feedbackTimerRef.current);
      feedbackTimerRef.current = setTimeout(() => {
        setFeedback(null);
      }, 3500);
    }
    return () => {
      if (feedbackTimerRef.current) clearTimeout(feedbackTimerRef.current);
    };
  }, [feedback]);

  // Selected avatar file pending upload
  const [selectedAvatarFile, setSelectedAvatarFile] = useState<File | null>(null);
  const [previewAvatarUrl, setPreviewAvatarUrl] = useState<string | null>(null);
  const [serverAvatarSrc, setServerAvatarSrc] = useState<string | null>(null);
  const [avatarVersion, setAvatarVersion] = useState(0);
  const [avatarLoadError, setAvatarLoadError] = useState(false);
  const serverBlobRef = useRef<string | null>(null);
  const previewBlobRef = useRef<string | null>(null);
  previewBlobRef.current = previewAvatarUrl;

  // Synchronize form fields with incoming profile
  useEffect(() => {
    setFullName(profile.fullName ?? '');
    setShortBio(profile.shortBio ?? '');
  }, [profile.fullName, profile.shortBio]);

  // Load authenticated server avatar with cache-busting
  useEffect(() => {
    if (!profile.avatarUrl) {
      setServerAvatarSrc(null);
      return;
    }

    // Public external URL or inline data
    if (!profile.avatarUrl.startsWith('/api/v1/')) {
      setServerAvatarSrc(profile.avatarUrl);
      return;
    }

    if (!token) {
      setServerAvatarSrc(null);
      return;
    }

    const controller = new AbortController();
    const fetchUrl = `${profile.avatarUrl}?t=${Date.now()}`;

    fetch(fetchUrl, {
      signal: controller.signal,
      headers: { Authorization: `Bearer ${token}` },
      cache: 'no-store',
    })
      .then(response => {
        if (!response.ok) throw new Error('Avatar unavailable');
        return response.blob();
      })
      .then(blob => {
        const objectUrl = URL.createObjectURL(blob);
        setServerAvatarSrc(() => {
          if (serverBlobRef.current && serverBlobRef.current !== objectUrl) {
            URL.revokeObjectURL(serverBlobRef.current);
          }
          serverBlobRef.current = objectUrl;
          return objectUrl;
        });
        setAvatarLoadError(false);
      })
      .catch(() => {
        if (!controller.signal.aborted) setServerAvatarSrc(null);
      });

    return () => {
      controller.abort();
    };
  }, [profile.avatarUrl, token, avatarVersion]);

  // Reset image error state whenever effective avatar source changes
  useEffect(() => {
    setAvatarLoadError(false);
  }, [previewAvatarUrl, serverAvatarSrc]);

  // Clean up animation timers and blob URLs on unmount
  useEffect(() => {
    return () => {
      if (transitionTimerRef.current) clearTimeout(transitionTimerRef.current);
      if (revealTimerRef.current) clearTimeout(revealTimerRef.current);
      if (feedbackTimerRef.current) clearTimeout(feedbackTimerRef.current);
      if (transitionFrameRef.current !== null) cancelAnimationFrame(transitionFrameRef.current);
      if (revealFrameRef.current !== null) cancelAnimationFrame(revealFrameRef.current);
      if (previewBlobRef.current) URL.revokeObjectURL(previewBlobRef.current);
      if (serverBlobRef.current) URL.revokeObjectURL(serverBlobRef.current);
    };
  }, []);

  // The selected sheet can contract to its content, but long content scrolls
  // within a capped paper surface instead of stretching the whole folder.
  useLayoutEffect(() => {
    const panels = Array.from(panelMeasureRefs.current.values());
    const activePanel = panelMeasureRefs.current.get(activeTab);
    if (activePanel) activePanel.scrollTop = 0;
    const measure = () => {
      const width = window.innerWidth;
      const floor = width <= 680 ? 320 : width <= 900 ? 300 : 240;
      const ceiling = width <= 680 ? 560 : width <= 900 ? 620 : 540;
      const contentHeight = panelMeasureRefs.current.get(activeTab)?.scrollHeight ?? floor;
      setViewportHeight(Math.min(ceiling, Math.max(floor, contentHeight)));
    };
    measure();
    const observer = new ResizeObserver(measure);
    panels.forEach(panel => {
      if (panel.firstElementChild) observer.observe(panel.firstElementChild);
    });
    window.addEventListener('resize', measure);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', measure);
    };
  }, [activeTab]);

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
      setSelectedAvatarFile(null);
      setAvatarVersion(v => v + 1);
      setFeedback({ message: 'Avatar updated successfully.', error: false });
    },
    onError: error => setAvatarError(resolveErrorText(error)),
  });

  const hasFormChanges =
    fullName.trim() !== (profile.fullName ?? '').trim() ||
    shortBio.trim() !== (profile.shortBio ?? '').trim();
  const bioLength = [...shortBio].length;

  const hasUnsavedChanges = hasFormChanges || selectedAvatarFile !== null;

  // Crossfade panels while the paper resizes in one uninterrupted motion.
  const handleTabChange = (nextTab: FolderTabId) => {
    if (nextTab === activeTab) return;

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // 1. Immediately acknowledge tab selection on rail (0ms)
    setActiveTab(nextTab);

    // Cancel existing active transitions
    if (transitionTimerRef.current) clearTimeout(transitionTimerRef.current);
    if (revealTimerRef.current) clearTimeout(revealTimerRef.current);
    if (transitionFrameRef.current !== null) cancelAnimationFrame(transitionFrameRef.current);
    if (revealFrameRef.current !== null) cancelAnimationFrame(revealFrameRef.current);

    setRecedingTab(reduceMotion ? null : displayTab);
    setDisplayTab(nextTab);
    setEnteringTab(reduceMotion ? null : nextTab);

    if (reduceMotion) return;

    // Paint the incoming panel at its starting depth before interpolating it.
    transitionFrameRef.current = requestAnimationFrame(() => {
      revealFrameRef.current = requestAnimationFrame(() => {
        setEnteringTab(null);
        if (revealTimerRef.current) clearTimeout(revealTimerRef.current);
        revealFrameRef.current = null;
      });
      transitionFrameRef.current = null;
    });

    // Paused animation frames must not leave the selected panel transparent.
    revealTimerRef.current = setTimeout(() => {
      setEnteringTab(null);
      if (transitionFrameRef.current !== null) cancelAnimationFrame(transitionFrameRef.current);
      if (revealFrameRef.current !== null) cancelAnimationFrame(revealFrameRef.current);
      transitionFrameRef.current = null;
      revealFrameRef.current = null;
      revealTimerRef.current = null;
    }, 48);

    // Keep the outgoing panel in the stack until its fade has completed.
    transitionTimerRef.current = setTimeout(() => {
      setRecedingTab(null);
    }, 240);

  };

  const handleTabKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    let targetIndex = -1;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      targetIndex = (index + 1) % FOLDER_TABS.length;
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      targetIndex = (index - 1 + FOLDER_TABS.length) % FOLDER_TABS.length;
    } else if (event.key === 'Home') {
      targetIndex = 0;
    } else if (event.key === 'End') {
      targetIndex = FOLDER_TABS.length - 1;
    }

    if (targetIndex >= 0) {
      event.preventDefault();
      const targetTab = FOLDER_TABS[targetIndex];
      handleTabChange(targetTab.id);
      tabRefs.current.get(targetTab.id)?.focus();
    }
  };

  const handleFileSelect = (file?: File) => {
    if (!file) return;
    setAvatarError('');
    setFeedback(null);

    const validationError = validateAvatar(file);
    if (validationError) {
      setAvatarError(validationError);
      return;
    }

    if (previewAvatarUrl) {
      URL.revokeObjectURL(previewAvatarUrl);
    }

    const objectUrl = URL.createObjectURL(file);
    setSelectedAvatarFile(file);
    setPreviewAvatarUrl(objectUrl);
  };

  const handleDiscard = () => {
    setFullName(profile.fullName ?? '');
    setShortBio(profile.shortBio ?? '');
    setAvatarError('');
    setFeedback(null);
    if (previewAvatarUrl) {
      URL.revokeObjectURL(previewAvatarUrl);
      setPreviewAvatarUrl(null);
    }
    setSelectedAvatarFile(null);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFeedback(null);

    if (fullName.trim().length > 150 || [...shortBio.trim()].length > MAX_BIO_LENGTH) {
      setFeedback({
        message: 'Name must be at most 150 characters and bio at most 200.',
        error: true,
      });
      return;
    }

    if (selectedAvatarFile) {
      avatarMutation.mutate(selectedAvatarFile);
    }

    if (hasFormChanges) {
      saveMutation.mutate({
        fullName: fullName.trim(),
        shortBio: shortBio.trim(),
      });
    }
  };

  const effectiveAvatarSrc = previewAvatarUrl ?? serverAvatarSrc;
  const displayName = profile.fullName?.trim() || profile.username;
  const initial = profile.username.slice(0, 1).toUpperCase();
  const isPendingSave = saveMutation.isPending || avatarMutation.isPending;

  return (
    <div className="folder-workspace">
      {/* Left Column: Editorial Introduction */}
      <aside className="folder-editorial-col" aria-label="Profile introduction">
        <div className="folder-editorial-hero">
          <span className="folder-editorial-eyebrow">YOUR PERSONAL SPACE</span>
          <h1 className="folder-editorial-title">
            <span>Make it</span>
            <span>yours.</span>
          </h1>
          <p className="folder-editorial-description">
            Shape how you appear across AlgoLearn.
          </p>
        </div>

      </aside>

      {/* Right Column: The Layered Interactive Folder */}
      <main className="folder-interactive-col" aria-label="Personal learning folder">
        <div className="folder-container">
          {/* Independent backing sheets, paper edges and editorial linework */}
          <FolderBackingArtwork />

          {/* Main physical folder */}
          <div className="folder-main" data-active-tab={activeTab}>
            {/* Integrated Organic Folder Tabs */}
            <div
              className="folder-tabs-track"
              role="tablist"
              aria-label="Profile folder sections"
            >
              {FOLDER_TABS.map((tab, index) => {
                const isSelected = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    ref={el => {
                      if (el) tabRefs.current.set(tab.id, el);
                      else tabRefs.current.delete(tab.id);
                    }}
                    id={`folder-tab-${tab.id}`}
                    role="tab"
                    type="button"
                    className="folder-tab"
                    data-active={isSelected}
                    aria-selected={isSelected}
                    aria-controls={`folder-panel-${tab.id}`}
                    tabIndex={isSelected ? 0 : -1}
                    onClick={() => handleTabChange(tab.id)}
                    onKeyDown={e => handleTabKeyDown(e, index)}
                  >
                    <FolderTabShape active={isSelected} isFirst={index === 0} />
                    <span className="folder-tab-label">{tab.label}</span>
                  </button>
                );
              })}
            </div>

            {/* Active Sheet Surface with Smooth Height Viewport */}
            <div className="folder-sheet">
              <div
                className="folder-sheet-viewport"
                style={{ height: viewportHeight === null ? undefined : `${viewportHeight}px` }}
              >
                {/* TAB 1: PROFILE — Editor & Editorial Live Preview */}
                <div
                  ref={el => {
                    if (el) panelMeasureRefs.current.set('profile', el);
                    else panelMeasureRefs.current.delete('profile');
                  }}
                  className="folder-panel"
                  data-state={
                    displayTab === 'profile'
                      ? enteringTab === 'profile'
                        ? 'entering'
                        : 'active'
                      : recedingTab === 'profile'
                      ? 'receding'
                      : 'hidden'
                  }
                  role="tabpanel"
                  id="folder-panel-profile"
                  aria-labelledby="folder-tab-profile"
                  aria-hidden={activeTab !== 'profile'}
                  inert={activeTab !== 'profile'}
                >
                  <div className="folder-profile-grid">
                    {/* Left: Personal Information Editor */}
                    <form onSubmit={handleSubmit} className="folder-editor-pane">
                      <div className="folder-pane-header">
                        <span className="folder-pane-eyebrow">01 / EDIT PROFILE</span>
                        <h2 className="folder-pane-title">Personal information</h2>
                        <p className="folder-pane-subtitle">
                          Keep your name and introduction up to date.
                        </p>
                      </div>

                      <div className="folder-form-fields">
                        {/* Avatar Upload */}
                        <div className="folder-avatar-field">
                          <div className="folder-avatar-uploader">
                            <div
                              className="folder-avatar-circle"
                              onClick={() => fileInputRef.current?.click()}
                              role="button"
                              tabIndex={0}
                              aria-label="Upload profile photo"
                              onKeyDown={e => {
                                if (e.key === 'Enter' || e.key === ' ') {
                                  e.preventDefault();
                                  fileInputRef.current?.click();
                                }
                              }}
                            >
                              {effectiveAvatarSrc && !avatarLoadError ? (
                                <img
                                  src={effectiveAvatarSrc}
                                  alt={displayName}
                                  className="folder-avatar-img"
                                  onError={() => setAvatarLoadError(true)}
                                />
                              ) : (
                                <span className="folder-avatar-fallback">{initial}</span>
                              )}
                              <div className="folder-avatar-overlay" aria-hidden="true">
                                <Camera size={14} />
                              </div>
                            </div>

                            <div className="folder-avatar-actions">
                              <button
                                type="button"
                                className="folder-upload-btn"
                                onClick={() => fileInputRef.current?.click()}
                                disabled={isPendingSave}
                              >
                                <Upload size={16} aria-hidden="true" />
                                <span>Upload new image</span>
                              </button>
                              <span className="folder-avatar-hint">
                                PNG or JPEG · up to 5 MB
                              </span>
                              {avatarError && (
                                <p className="folder-avatar-error" role="alert">
                                  {avatarError}
                                </p>
                              )}
                            </div>

                            <input
                              ref={fileInputRef}
                              type="file"
                              className="folder-hidden-file-input"
                              accept="image/png,image/jpeg"
                              disabled={isPendingSave}
                              onChange={e => {
                                handleFileSelect(e.target.files?.[0]);
                                e.target.value = '';
                              }}
                            />
                          </div>
                        </div>

                        {/* Display Name */}
                        <label className="folder-field">
                          <span className="folder-field-label-text">Display name</span>
                          <input
                            className="folder-input"
                            value={fullName}
                            maxLength={150}
                            onChange={e => setFullName(e.target.value)}
                            autoComplete="name"
                            placeholder="Your display name"
                          />
                        </label>

                        {/* Short Bio */}
                        <label className="folder-field">
                          <div className="folder-field-header">
                            <span className="folder-field-label-text">Short bio</span>
                          </div>
                          <textarea
                            className="folder-textarea"
                            value={shortBio}
                            aria-invalid={bioLength > MAX_BIO_LENGTH}
                            onChange={e => {
                              const next = e.target.value;
                              const nextLength = [...next].length;
                              if (nextLength <= MAX_BIO_LENGTH || nextLength < bioLength) setShortBio(next);
                            }}
                            rows={4}
                            placeholder="A few words about your learning goals, favorite languages, or algorithms you're tackling..."
                          />
                          <span className="folder-char-count" data-over-limit={bioLength > MAX_BIO_LENGTH}>
                            {bioLength} / {MAX_BIO_LENGTH}
                            {bioLength > MAX_BIO_LENGTH && ' · Shorten your bio to save.'}
                          </span>
                        </label>
                      </div>

                      {/* Bottom-right aligned Save / Discard Actions */}
                      <div className="folder-form-actions">
                        {hasUnsavedChanges && (
                          <button
                            type="button"
                            className="folder-btn-discard"
                            onClick={handleDiscard}
                            disabled={isPendingSave}
                          >
                            Discard
                          </button>
                        )}
                        <button
                          type="submit"
                          className="folder-btn-save"
                          disabled={!hasUnsavedChanges || isPendingSave}
                        >
                          {isPendingSave ? 'Saving changes…' : 'Save changes'}
                        </button>
                      </div>
                    </form>

                    {/* Right: Editorial Identity Sheet Live Preview */}
                    <div className="folder-preview-pane">
                      <LiveProfilePreview
                        avatarSrc={effectiveAvatarSrc}
                        displayName={fullName}
                        username={profile.username}
                        bio={shortBio}
                        isPublic={profile.isPublic}
                        solvedCount={profile.publicPracticeStatistics.solved}
                        streakDays={profile.streak.currentStreak}
                      />
                    </div>
                  </div>
                </div>

                {/* TAB 2: PRIVACY */}
                <div
                  ref={el => {
                    if (el) panelMeasureRefs.current.set('privacy', el);
                    else panelMeasureRefs.current.delete('privacy');
                  }}
                  className="folder-panel"
                  data-state={
                    displayTab === 'privacy'
                      ? enteringTab === 'privacy'
                        ? 'entering'
                        : 'active'
                      : recedingTab === 'privacy'
                      ? 'receding'
                      : 'hidden'
                  }
                  role="tabpanel"
                  id="folder-panel-privacy"
                  aria-labelledby="folder-tab-privacy"
                  aria-hidden={activeTab !== 'privacy'}
                  inert={activeTab !== 'privacy'}
                >
                  <div className="folder-single-pane">
                    <div className="folder-pane-header">
                      <span className="folder-pane-eyebrow">02 / VISIBILITY</span>
                      <h2 className="folder-pane-title">Public profile</h2>
                      <p className="folder-pane-subtitle">
                        Control who can see your coding activity and practice accomplishments.
                      </p>
                    </div>

                    <div className="folder-settings-list">
                      {/* Editorial Setting Row: Visibility Switch */}
                      <div className="folder-editorial-row">
                        <div className="folder-row-info">
                          <strong className="folder-row-title">Public profile visibility</strong>
                          <p className="folder-row-desc">
                            When enabled, other learners can view your username, avatar, bio, solved problem
                            count, and streak at your personal public URL.
                          </p>
                        </div>
                        <label className="folder-switch" aria-label="Toggle public profile visibility">
                          <input
                            type="checkbox"
                            checked={profile.isPublic}
                            disabled={privacyMutation.isPending}
                            onChange={e => {
                              setFeedback(null);
                              privacyMutation.mutate(e.target.checked);
                            }}
                          />
                          <span className="folder-switch-slider" />
                        </label>
                      </div>

                      {/* Editorial Setting Row: Public URL Action */}
                      {profile.isPublic && (
                        <div className="folder-editorial-row folder-url-row">
                          <div className="folder-row-info">
                            <strong className="folder-row-title">Your public URL</strong>
                            <p className="folder-row-desc">
                              Share your learning journey with peers, mentors, or collaborators.
                            </p>
                          </div>
                          <Link
                            className="folder-url-badge"
                            to={`/u/${encodeURIComponent(profile.username)}`}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            <span>algo.learn/u/{profile.username}</span>
                            <ArrowUpRight size={13} aria-hidden="true" />
                          </Link>
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* TAB 3: SECURITY */}
                <div
                  ref={el => {
                    if (el) panelMeasureRefs.current.set('security', el);
                    else panelMeasureRefs.current.delete('security');
                  }}
                  className="folder-panel"
                  data-state={
                    displayTab === 'security'
                      ? enteringTab === 'security'
                        ? 'entering'
                        : 'active'
                      : recedingTab === 'security'
                      ? 'receding'
                      : 'hidden'
                  }
                  role="tabpanel"
                  id="folder-panel-security"
                  aria-labelledby="folder-tab-security"
                  aria-hidden={activeTab !== 'security'}
                  inert={activeTab !== 'security'}
                >
                  <div className="folder-single-pane">
                    <div className="folder-pane-header">
                      <span className="folder-pane-eyebrow">03 / SECURITY</span>
                      <h2 className="folder-pane-title">Account security</h2>
                      <p className="folder-pane-subtitle">
                        Manage authentication credentials and protect your learning workspace.
                      </p>
                    </div>

                    <div className="folder-settings-list">
                      <div className="folder-editorial-row">
                        <div className="folder-row-info">
                          <strong className="folder-row-title">Account password</strong>
                          <p className="folder-row-desc">
                            Change your password, review signed-in devices and deactivate your account.
                          </p>
                        </div>
                        <Link to="/account/security" className="folder-action-pill">
                          <span>Manage security</span>
                          <ArrowUpRight size={13} aria-hidden="true" />
                        </Link>
                      </div>
                    </div>
                  </div>
                </div>

                {/* TAB 4: ACCOUNT DETAILS */}
                <div
                  ref={el => {
                    if (el) panelMeasureRefs.current.set('account', el);
                    else panelMeasureRefs.current.delete('account');
                  }}
                  className="folder-panel"
                  data-state={
                    displayTab === 'account'
                      ? enteringTab === 'account'
                        ? 'entering'
                        : 'active'
                      : recedingTab === 'account'
                      ? 'receding'
                      : 'hidden'
                  }
                  role="tabpanel"
                  id="folder-panel-account"
                  aria-labelledby="folder-tab-account"
                  aria-hidden={activeTab !== 'account'}
                  inert={activeTab !== 'account'}
                >
                  <div className="folder-single-pane">
                    <div className="folder-pane-header">
                      <span className="folder-pane-eyebrow">04 / CREDENTIALS</span>
                      <h2 className="folder-pane-title">Account details</h2>
                      <p className="folder-pane-subtitle">
                        Read-only credentials and enrollment information associated with your account.
                      </p>
                    </div>

                    <div className="folder-settings-list">
                      <div className="folder-editorial-row">
                        <div className="folder-row-info">
                          <span className="folder-term-label">Username</span>
                        </div>
                        <span className="folder-value-text">@{profile.username}</span>
                      </div>

                      <div className="folder-editorial-row">
                        <div className="folder-row-info">
                          <span className="folder-term-label">Email address</span>
                        </div>
                        <span className="folder-value-text">{profile.email}</span>
                      </div>

                      <div className="folder-editorial-row">
                        <div className="folder-row-info">
                          <span className="folder-term-label">Account role</span>
                        </div>
                        <span className="folder-role-pill">{profile.role.toLowerCase()}</span>
                      </div>

                      <div className="folder-editorial-row">
                        <div className="folder-row-info">
                          <span className="folder-term-label">Member since</span>
                        </div>
                        <span className="folder-value-text">{formatJoinedDate(profile.joinedAt)}</span>
                      </div>
                    </div>

                    <p className="folder-editorial-disclaimer">
                      Username, registered email address, and role permissions are managed at the organization level
                      and cannot be altered directly from this folder.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Global Feedback Toast (Auto-dismisses in 3.5s or click to dismiss) */}
        {feedback && (
          <div
            className="folder-feedback-toast"
            role={feedback.error ? 'alert' : 'status'}
            onClick={() => setFeedback(null)}
            style={{ cursor: 'pointer' }}
            title="Click to dismiss"
          >
            {!feedback.error && <Check size={14} aria-hidden="true" />}
            <span>{feedback.message}</span>
          </div>
        )}
      </main>
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
            <span className="folder-editorial-eyebrow">ADMINISTRATOR WORKSPACE</span>
            <h1>Profile settings</h1>
            <p>Personal profile editing is available for Student and Teacher accounts.</p>
            <Link to="/dashboard" className="folder-btn-save">
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

        {query.isPending ? (
          <div className="profile-state-pane" role="status">
            <h2>Loading your folder…</h2>
          </div>
        ) : query.isError ? (
          <div className="profile-state-pane" role="alert">
            <h2>Folder unavailable</h2>
            <p>
              {isForbidden
                ? 'You do not have permission to view this profile.'
                : 'We could not load your profile folder.'}
            </p>
            {!isForbidden && (
              <button type="button" className="folder-btn-save" onClick={() => query.refetch()}>
                Try again
              </button>
            )}
          </div>
        ) : (
          <ProfileFolder profile={query.data} />
        )}
      </div>
    </div>
  );
}
