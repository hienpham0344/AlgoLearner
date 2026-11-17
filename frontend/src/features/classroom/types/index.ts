export type DemoRole = 'TEACHER' | 'STUDENT' | 'ADMIN';

export type ClassroomStatus = 'ACTIVE' | 'ARCHIVED';

export type JoinMode = 'AUTO_JOIN' | 'REQUIRE_APPROVAL';

export type MembershipStatus = 'ACTIVE' | 'LEFT' | 'REMOVED';

export type JoinRequestStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'REVOKED' | 'EXPIRED';

export interface DemoPersona {
  id: string;
  username: string;
  fullName: string;
  email: string;
  role: DemoRole;
  title?: string;
}

export interface ClassroomMember {
  studentId: string;
  username: string;
  fullName: string;
  email: string;
  status: MembershipStatus;
  joinedAt: string; // ISO-8601 UTC
  endedAt?: string | null;
}

export interface ClassroomJoinRequest {
  id: string;
  studentId: string;
  username: string;
  fullName: string;
  email: string;
  status: JoinRequestStatus;
  createdAt: string; // ISO-8601 UTC
}

export interface ClassroomInvitation {
  id: string;
  studentEmail: string;
  studentName?: string;
  status: InvitationStatus;
  expiresAt: string; // ISO-8601 UTC
  createdAt: string; // ISO-8601 UTC
}

export interface Classroom {
  id: string;
  name: string;
  description: string;
  ownerId: string;
  ownerName: string;
  ownerEmail: string;
  status: ClassroomStatus;
  classCode: string;
  codeExpiresAt: string | null; // ISO-8601 UTC or null (never expires)
  codeLocked: boolean;
  joiningOpen: boolean;
  joinMode: JoinMode;
  createdAt: string; // ISO-8601 UTC
  updatedAt: string; // ISO-8601 UTC
  members: ClassroomMember[];
  joinRequests: ClassroomJoinRequest[];
  invitations: ClassroomInvitation[];
  stats?: {
    lessonsCount: number;
    assignmentsCount: number;
    quizzesCount: number;
    announcementsCount: number;
  };
}

export interface CreateClassroomInput {
  name: string;
  description: string;
  joinMode: JoinMode;
  codeExpiresAt?: string | null;
  targetTeacherId?: string; // Only used when Admin creates on behalf of a Teacher
}

export interface UpdateClassroomInput {
  name: string;
  description: string;
}

export interface JoinConfigurationInput {
  codeExpiresAt: string | null;
  codeLocked: boolean;
  joiningOpen: boolean;
  joinMode: JoinMode;
}

export interface JoinResultFeedback {
  outcome: 'JOINED' | 'PENDING_APPROVAL' | 'REJOINED';
  message: string;
  classroomId: string;
  requestId?: string;
}

export * from './announcement';
