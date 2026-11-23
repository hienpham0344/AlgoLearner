import React from 'react';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';

/**
 * Smart router for generic /classrooms, /classrooms/:id, and /classrooms/:id/announcements/:announcementId entrypoints.
 * Resolves destination role-specific workspace based on:
 * 1. Optional dev / demo query parameter (?role=admin|teacher|student)
 * 2. Authenticated user's role (user.role from AuthContext)
 * 3. Default fallback: 'teacher'
 */
export const ClassroomRouterPage: React.FC = () => {
  const { id, announcementId } = useParams<{ id?: string; announcementId?: string }>();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();

  const queryRole = searchParams.get('role')?.toUpperCase();
  const effectiveRole =
    queryRole === 'ADMIN' || queryRole === 'STUDENT' || queryRole === 'TEACHER'
      ? queryRole
      : user?.role || 'TEACHER';

  let rolePrefix = 'teacher';
  if (effectiveRole === 'ADMIN') {
    rolePrefix = 'admin';
  } else if (effectiveRole === 'STUDENT') {
    rolePrefix = 'student';
  }

  const nextParams = new URLSearchParams(searchParams);
  nextParams.delete('role');
  if (announcementId) {
    nextParams.set('tab', 'announcements');
    nextParams.set('announcementId', announcementId);
  }
  const queryString = nextParams.toString();

  const baseDestination = id
    ? `/${rolePrefix}/classrooms/${encodeURIComponent(id)}`
    : `/${rolePrefix}/classrooms`;

  const destination = queryString ? `${baseDestination}?${queryString}` : baseDestination;

  return <Navigate to={destination} replace />;
};
