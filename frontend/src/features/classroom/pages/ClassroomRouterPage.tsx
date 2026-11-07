import React from 'react';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';

/**
 * Smart router for generic /classrooms and /classrooms/:id entrypoints.
 * Resolves destination role-specific workspace based on:
 * 1. Optional dev / demo query parameter (?role=admin|teacher|student)
 * 2. Authenticated user's role (user.role from AuthContext)
 * 3. Default fallback: 'teacher'
 */
export const ClassroomRouterPage: React.FC = () => {
  const { id } = useParams<{ id?: string }>();
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

  // Preserve any remaining query parameters except ?role if desired, or forward search string
  const destination = id
    ? `/${rolePrefix}/classrooms/${id}`
    : `/${rolePrefix}/classrooms`;

  return <Navigate to={destination} replace />;
};
