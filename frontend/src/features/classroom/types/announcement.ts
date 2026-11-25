export interface ClassroomAnnouncement {
  id: string;
  classroomId: string;
  title: string;
  content: string; // Text-only format (safe markdown / plain text, BR-ANN-001)
  authorId: string;
  authorName: string;
  authorRole?: 'TEACHER' | 'ADMIN';
  isPinned: boolean;
  pinnedAt: string | null; // ISO-8601 UTC or null
  createdAt: string; // ISO-8601 UTC
  updatedAt: string; // ISO-8601 UTC
}

export interface CreateAnnouncementInput {
  title: string;
  content: string;
}

export interface UpdateAnnouncementInput {
  title: string;
  content: string;
}

export interface PrototypeCreateAnnouncementInput extends CreateAnnouncementInput {
  classroomId: string;
  isPinned?: boolean;
}

export interface PrototypeUpdateAnnouncementInput extends UpdateAnnouncementInput {
  isPinned?: boolean;
}

export interface InAppNotification {
  id: string;
  recipientId: string;
  recipientRole: 'STUDENT' | 'TEACHER';
  title: string;
  message: string;
  type: 'CLASSROOM_ANNOUNCEMENT';
  classroomId: string;
  classroomName: string;
  announcementId: string;
  deepLink: string;
  isRead: boolean;
  createdAt: string; // ISO-8601 UTC
}
