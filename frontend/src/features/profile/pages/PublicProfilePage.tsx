import { useQuery } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Code2, Flame } from 'lucide-react';
import { profileKeys } from '@/features/dashboard/api/dashboardApi';
import { profileApi } from '../api/profileApi';
import '../styles/profile.css';

export function PublicProfilePage() {
  const { username = '' } = useParams();
  const query = useQuery({ queryKey: profileKeys.public(username), queryFn: ({ signal }) => profileApi.public(username, signal), enabled: Boolean(username), retry: false });
  return <main className="public-profile-page"><div className="public-profile-wrap"><header><Link to="/dashboard"><ArrowLeft size={16} aria-hidden="true" /> AlgoLearn</Link><span>COMMUNITY PROFILE</span></header>
    {query.isPending ? <div className="public-profile-state" role="status">Loading profile…</div> : query.isError ? <div className="public-profile-state" role="alert"><h1>{isAxiosError(query.error) && query.error.response?.status === 404 ? 'Profile unavailable.' : 'Could not load this profile.'}</h1><p>{isAxiosError(query.error) && query.error.response?.status === 404 ? 'This profile may be private or unavailable.' : 'Please try again later.'}</p>{!(isAxiosError(query.error) && query.error.response?.status === 404) && <button type="button" onClick={() => query.refetch()}>Try again</button>}</div> : <article className="public-profile-card"><span className="account-eyebrow">ALGOLEARN / BUILDER</span><div className="public-profile-identity"><div className="account-avatar-large">{query.data.avatarUrl ? <img src={query.data.avatarUrl} alt="Profile avatar" /> : query.data.username.slice(0, 1).toUpperCase()}</div><div><h1>{query.data.username}</h1><p>@{query.data.username}</p></div></div><p className="public-profile-bio">{query.data.shortBio || 'Learning, one step at a time.'}</p><div className="public-profile-stats"><div><Code2 size={19} aria-hidden="true" /><strong>{query.data.solved}</strong><span>Problems solved</span></div><div><Flame size={19} aria-hidden="true" /><strong>{query.data.streak.currentStreak}</strong><span>Current streak</span></div></div></article>}
  </div></main>;
}
