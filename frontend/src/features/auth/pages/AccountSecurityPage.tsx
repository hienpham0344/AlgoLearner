import { Navigate } from 'react-router-dom';

/** Keep old security bookmarks pointing at the security tab in the profile. */
export function AccountSecurityPage() {
  return <Navigate to="/profile?tab=security" replace />;
}
