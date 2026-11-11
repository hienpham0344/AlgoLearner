import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Classroom,
  ClassroomAnnouncement,
  CreateAnnouncementInput,
  CreateClassroomInput,
  DemoPersona,
  DemoRole,
  InAppNotification,
  JoinConfigurationInput,
  JoinResultFeedback,
  UpdateAnnouncementInput,
  UpdateClassroomInput,
} from '../types';
import {
  DEMO_PERSONAS,
  INITIAL_MOCK_ANNOUNCEMENTS,
  INITIAL_MOCK_CLASSROOMS,
  INITIAL_MOCK_NOTIFICATIONS,
} from './classroomMockData';

const STORAGE_KEY_CLASSROOMS = 'algolearn_prototype_classrooms_v1';
const STORAGE_KEY_ROLE = 'algolearn_prototype_role_v1';
const STORAGE_KEY_PERSONA = 'algolearn_prototype_persona_v1';
const STORAGE_KEY_ANNOUNCEMENTS = 'algolearn_prototype_announcements_v1';
const STORAGE_KEY_NOTIFICATIONS = 'algolearn_prototype_notifications_v1';

function generateRandomCode(): string {
  let result = '';
  const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  for (let i = 0; i < 8; i++) {
    result += ALPHABET.charAt(Math.floor(Math.random() * ALPHABET.length));
  }
  return result;
}

function loadInitialClassrooms(): Classroom[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY_CLASSROOMS);
    if (raw) return JSON.parse(raw);
  } catch {
    // fallback
  }
  return INITIAL_MOCK_CLASSROOMS;
}

function loadInitialAnnouncements(): ClassroomAnnouncement[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY_ANNOUNCEMENTS);
    if (raw) return JSON.parse(raw);
  } catch {
    // fallback
  }
  return INITIAL_MOCK_ANNOUNCEMENTS;
}

function loadInitialNotifications(): InAppNotification[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY_NOTIFICATIONS);
    if (raw) return JSON.parse(raw);
  } catch {
    // fallback
  }
  return INITIAL_MOCK_NOTIFICATIONS;
}

function resolveRoleFromUrlOrStorage(): DemoRole {
  try {
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      const qRole = params.get('role')?.toUpperCase();
      if (qRole === 'TEACHER' || qRole === 'STUDENT' || qRole === 'ADMIN') {
        return qRole as DemoRole;
      }
      const pathname = window.location.pathname.toLowerCase();
      if (pathname.includes('/admin')) return 'ADMIN';
      if (pathname.includes('/student')) return 'STUDENT';
      if (pathname.includes('/teacher')) return 'TEACHER';
    }
    const saved = sessionStorage.getItem(STORAGE_KEY_ROLE);
    if (saved === 'TEACHER' || saved === 'STUDENT' || saved === 'ADMIN') {
      return saved;
    }
  } catch {
    // fallback
  }
  return 'TEACHER';
}

function loadInitialPersona(role: DemoRole): DemoPersona {
  try {
    const savedId = sessionStorage.getItem(STORAGE_KEY_PERSONA);
    if (savedId) {
      const match = DEMO_PERSONAS.find(p => p.id === savedId && p.role === role);
      if (match) return match;
    }
  } catch {
    // fallback
  }
  return DEMO_PERSONAS.find(p => p.role === role) || DEMO_PERSONAS[0];
}

export function useClassroomStore(roleOverride?: DemoRole) {
  const effectiveInitialRole = roleOverride || resolveRoleFromUrlOrStorage();

  const [classrooms, setClassrooms] = useState<Classroom[]>(loadInitialClassrooms);
  const [currentRole, setCurrentRoleState] = useState<DemoRole>(effectiveInitialRole);
  const [currentPersona, setCurrentPersonaState] = useState<DemoPersona>(() =>
    loadInitialPersona(effectiveInitialRole),
  );
  const [announcements, setAnnouncements] = useState<ClassroomAnnouncement[]>(loadInitialAnnouncements);
  const [notifications, setNotifications] = useState<InAppNotification[]>(loadInitialNotifications);

  // If roleOverride is supplied and changes, sync state
  useEffect(() => {
    if (roleOverride && roleOverride !== currentRole) {
      setCurrentRoleState(roleOverride);
      const matchingPersona = DEMO_PERSONAS.find(p => p.role === roleOverride) || DEMO_PERSONAS[0];
      setCurrentPersonaState(matchingPersona);
    }
  }, [roleOverride, currentRole]);

  // Sync to sessionStorage
  useEffect(() => {
    try {
      sessionStorage.setItem(STORAGE_KEY_CLASSROOMS, JSON.stringify(classrooms));
    } catch {
      // ignore
    }
  }, [classrooms]);

  useEffect(() => {
    try {
      sessionStorage.setItem(STORAGE_KEY_ROLE, currentRole);
    } catch {
      // ignore
    }
  }, [currentRole]);

  useEffect(() => {
    try {
      sessionStorage.setItem(STORAGE_KEY_PERSONA, currentPersona.id);
    } catch {
      // ignore
    }
  }, [currentPersona]);

  useEffect(() => {
    try {
      sessionStorage.setItem(STORAGE_KEY_ANNOUNCEMENTS, JSON.stringify(announcements));
    } catch {
      // ignore
    }
  }, [announcements]);

  useEffect(() => {
    try {
      sessionStorage.setItem(STORAGE_KEY_NOTIFICATIONS, JSON.stringify(notifications));
    } catch {
      // ignore
    }
  }, [notifications]);

  const setRole = useCallback((newRole: DemoRole) => {
    setCurrentRoleState(newRole);
    const defaultPersona = DEMO_PERSONAS.find(p => p.role === newRole) || DEMO_PERSONAS[0];
    setCurrentPersonaState(defaultPersona);
  }, []);

  const setPersona = useCallback((persona: DemoPersona) => {
    setCurrentPersonaState(persona);
    setCurrentRoleState(persona.role);
  }, []);

  const getClassroom = useCallback(
    (id: string): Classroom | undefined => {
      return classrooms.find(c => c.id === id);
    },
    [classrooms],
  );

  const createClassroom = useCallback(
    (input: CreateClassroomInput): Classroom => {
      let owner: DemoPersona;

      if (currentRole === 'ADMIN') {
        if (!input.targetTeacherId) {
          throw new Error('Admin must assign an active Teacher as classroom owner.');
        }
        const target = DEMO_PERSONAS.find(
          p => p.id === input.targetTeacherId && p.role === 'TEACHER',
        );
        if (!target) {
          throw new Error('Selected owner is not a valid active Teacher.');
        }
        owner = target;
      } else if (currentRole === 'TEACHER') {
        owner = currentPersona;
      } else {
        throw new Error('Only a Teacher or Admin may create a classroom.');
      }

      // Generate unique class code
      let code = generateRandomCode();
      while (classrooms.some(c => c.classCode === code)) {
        code = generateRandomCode();
      }

      const nowUtc = new Date().toISOString();
      const newClassroom: Classroom = {
        id: `cls-${Date.now()}`,
        name: input.name.trim(),
        description: input.description.trim(),
        ownerId: owner.id,
        ownerName: owner.fullName,
        ownerEmail: owner.email,
        status: 'ACTIVE',
        classCode: code,
        codeExpiresAt: input.codeExpiresAt || null,
        codeLocked: false,
        joiningOpen: true,
        joinMode: input.joinMode,
        createdAt: nowUtc,
        updatedAt: nowUtc,
        members: [],
        joinRequests: [],
        invitations: [],
        stats: {
          lessonsCount: 0,
          assignmentsCount: 0,
          quizzesCount: 0,
          announcementsCount: 0,
        },
      };

      setClassrooms(prev => [newClassroom, ...prev]);
      return newClassroom;
    },
    [currentRole, currentPersona, classrooms],
  );

  const updateClassroom = useCallback(
    (id: string, input: UpdateClassroomInput): void => {
      setClassrooms(prev =>
        prev.map(c =>
          c.id === id
            ? {
                ...c,
                name: input.name.trim(),
                description: input.description.trim(),
                updatedAt: new Date().toISOString(),
              }
            : c,
        ),
      );
    },
    [],
  );

  const archiveClassroom = useCallback((id: string): void => {
    setClassrooms(prev =>
      prev.map(c =>
        c.id === id
          ? {
              ...c,
              status: 'ARCHIVED',
              joiningOpen: false,
              codeLocked: true,
              updatedAt: new Date().toISOString(),
            }
          : c,
      ),
    );
  }, []);

  const deleteClassroom = useCallback((id: string): void => {
    setClassrooms(prev => {
      const target = prev.find(c => c.id === id);
      if (!target) return prev;
      if (target.members.length > 0) {
        throw new Error('Cannot delete a classroom that contains student records. Archive instead.');
      }
      return prev.filter(c => c.id !== id);
    });
  }, []);

  const regenerateCode = useCallback((id: string): string => {
    let newCode = generateRandomCode();
    setClassrooms(prev => {
      while (prev.some(c => c.classCode === newCode)) {
        newCode = generateRandomCode();
      }
      return prev.map(c =>
        c.id === id
          ? {
              ...c,
              classCode: newCode,
              codeLocked: false,
              updatedAt: new Date().toISOString(),
            }
          : c,
      );
    });
    return newCode;
  }, []);

  const updateJoinConfig = useCallback((id: string, input: JoinConfigurationInput): void => {
    setClassrooms(prev =>
      prev.map(c =>
        c.id === id
          ? {
              ...c,
              codeExpiresAt: input.codeExpiresAt,
              codeLocked: input.codeLocked,
              joiningOpen: input.joiningOpen,
              joinMode: input.joinMode,
              updatedAt: new Date().toISOString(),
            }
          : c,
      ),
    );
  }, []);

  const joinByCode = useCallback(
    (classCode: string, studentPersona: DemoPersona): JoinResultFeedback => {
      const codeUpper = classCode.trim().toUpperCase();
      const target = classrooms.find(c => c.classCode.toUpperCase() === codeUpper);

      if (!target) {
        throw new Error('Invalid class code. No classroom matching this code was found.');
      }

      if (target.status === 'ARCHIVED') {
        throw new Error('This classroom has been archived and is not accepting enrollments.');
      }

      if (target.codeLocked || !target.joiningOpen) {
        throw new Error('Enrollment is currently locked by the classroom instructor.');
      }

      if (target.codeExpiresAt) {
        const expiry = new Date(target.codeExpiresAt).getTime();
        if (Date.now() > expiry) {
          throw new Error('This class code has expired. Please contact the teacher for a renewed code.');
        }
      }

      // Check if already active member
      const existingMember = target.members.find(m => m.studentId === studentPersona.id);
      if (existingMember && existingMember.status === 'ACTIVE') {
        throw new Error('You are already an active enrolled member of this classroom.');
      }

      const nowUtc = new Date().toISOString();

      // Check if pending request exists
      const existingRequest = target.joinRequests.find(
        r => r.studentId === studentPersona.id && r.status === 'PENDING_APPROVAL',
      );
      if (existingRequest) {
        return {
          outcome: 'PENDING_APPROVAL',
          message: 'Your enrollment request is already pending teacher approval.',
          classroomId: target.id,
          requestId: existingRequest.id,
        };
      }

      if (target.joinMode === 'REQUIRE_APPROVAL') {
        const requestId = `req-${Date.now()}`;
        const newRequest = {
          id: requestId,
          studentId: studentPersona.id,
          username: studentPersona.username,
          fullName: studentPersona.fullName,
          email: studentPersona.email,
          status: 'PENDING_APPROVAL' as const,
          createdAt: nowUtc,
        };

        setClassrooms(prev =>
          prev.map(c =>
            c.id === target.id
              ? {
                  ...c,
                  joinRequests: [newRequest, ...c.joinRequests],
                  updatedAt: nowUtc,
                }
              : c,
          ),
        );

        return {
          outcome: 'PENDING_APPROVAL',
          message:
            'This classroom requires teacher approval. Your request has been queued for instructor review.',
          classroomId: target.id,
          requestId,
        };
      }

      // AUTO_JOIN mode
      const isRejoining = existingMember && existingMember.status !== 'ACTIVE';

      setClassrooms(prev =>
        prev.map(c => {
          if (c.id !== target.id) return c;

          let updatedMembers: typeof c.members;
          if (existingMember) {
            updatedMembers = c.members.map(m =>
              m.studentId === studentPersona.id
                ? {
                    ...m,
                    status: 'ACTIVE',
                    joinedAt: nowUtc,
                    endedAt: null,
                  }
                : m,
            );
          } else {
            updatedMembers = [
              ...c.members,
              {
                studentId: studentPersona.id,
                username: studentPersona.username,
                fullName: studentPersona.fullName,
                email: studentPersona.email,
                status: 'ACTIVE',
                joinedAt: nowUtc,
                endedAt: null,
              },
            ];
          }

          return {
            ...c,
            members: updatedMembers,
            updatedAt: nowUtc,
          };
        }),
      );

      return {
        outcome: isRejoining ? 'REJOINED' : 'JOINED',
        message: isRejoining
          ? `Welcome back to "${target.name}"! Your participation has been restored.`
          : `Successfully enrolled in "${target.name}". Welcome!`,
        classroomId: target.id,
      };
    },
    [classrooms],
  );

  const leaveClassroom = useCallback(
    (classroomId: string): void => {
      const nowUtc = new Date().toISOString();
      setClassrooms(prev =>
        prev.map(c => {
          if (c.id !== classroomId) return c;
          return {
            ...c,
            members: c.members.map(m =>
              m.studentId === currentPersona.id
                ? { ...m, status: 'LEFT', endedAt: nowUtc }
                : m,
            ),
            updatedAt: nowUtc,
          };
        }),
      );
    },
    [currentPersona],
  );

  const directAddMember = useCallback(
    (classroomId: string, studentId: string): void => {
      const student = DEMO_PERSONAS.find(p => p.id === studentId && p.role === 'STUDENT');
      if (!student) {
        throw new Error('Student not found in platform directory.');
      }

      const nowUtc = new Date().toISOString();
      setClassrooms(prev =>
        prev.map(c => {
          if (c.id !== classroomId) return c;

          const existing = c.members.find(m => m.studentId === student.id);
          let updatedMembers = c.members;

          if (existing) {
            if (existing.status === 'ACTIVE') {
              throw new Error(`${student.fullName} is already an active member of this classroom.`);
            }
            updatedMembers = c.members.map(m =>
              m.studentId === student.id
                ? { ...m, status: 'ACTIVE', joinedAt: nowUtc, endedAt: null }
                : m,
            );
          } else {
            updatedMembers = [
              ...c.members,
              {
                studentId: student.id,
                username: student.username,
                fullName: student.fullName,
                email: student.email,
                status: 'ACTIVE',
                joinedAt: nowUtc,
                endedAt: null,
              },
            ];
          }

          return {
            ...c,
            members: updatedMembers,
            updatedAt: nowUtc,
          };
        }),
      );
    },
    [],
  );

  const removeMember = useCallback((classroomId: string, studentId: string): void => {
    const nowUtc = new Date().toISOString();
    setClassrooms(prev =>
      prev.map(c => {
        if (c.id !== classroomId) return c;
        return {
          ...c,
          members: c.members.map(m =>
            m.studentId === studentId ? { ...m, status: 'REMOVED', endedAt: nowUtc } : m,
          ),
          updatedAt: nowUtc,
        };
      }),
    );
  }, []);

  const approveJoinRequest = useCallback((classroomId: string, requestId: string): void => {
    setClassrooms(prev =>
      prev.map(c => {
        if (c.id !== classroomId) return c;
        const request = c.joinRequests.find(r => r.id === requestId);
        if (!request) return c;

        const updatedRequests = c.joinRequests.map(r =>
          r.id === requestId ? { ...r, status: 'APPROVED' as const } : r,
        );

        const existingMember = c.members.find(m => m.studentId === request.studentId);
        let updatedMembers = c.members;

        if (existingMember) {
          updatedMembers = c.members.map(m =>
            m.studentId === request.studentId
              ? { ...m, status: 'ACTIVE', joinedAt: new Date().toISOString(), endedAt: null }
              : m,
          );
        } else {
          updatedMembers = [
            ...c.members,
            {
              studentId: request.studentId,
              username: request.username,
              fullName: request.fullName,
              email: request.email,
              status: 'ACTIVE',
              joinedAt: new Date().toISOString(),
            },
          ];
        }

        return {
          ...c,
          joinRequests: updatedRequests,
          members: updatedMembers,
          updatedAt: new Date().toISOString(),
        };
      }),
    );
  }, []);

  const rejectJoinRequest = useCallback((classroomId: string, requestId: string): void => {
    setClassrooms(prev =>
      prev.map(c => {
        if (c.id !== classroomId) return c;
        return {
          ...c,
          joinRequests: c.joinRequests.map(r =>
            r.id === requestId ? { ...r, status: 'REJECTED' as const } : r,
          ),
          updatedAt: new Date().toISOString(),
        };
      }),
    );
  }, []);

  const createInvitation = useCallback(
    (classroomId: string, email: string, expiresAt: string): void => {
      const cleanEmail = email.trim().toLowerCase();
      const student = DEMO_PERSONAS.find(
        p => p.role === 'STUDENT' && p.email.toLowerCase() === cleanEmail,
      );

      if (!student) {
        throw new Error(
          'Invitations may only be sent to registered Student email addresses in the platform.',
        );
      }

      setClassrooms(prev =>
        prev.map(c => {
          if (c.id !== classroomId) return c;
          const activeInvite = c.invitations.find(
            i => i.studentEmail.toLowerCase() === cleanEmail && i.status === 'PENDING',
          );
          if (activeInvite) {
            throw new Error('A pending invitation has already been sent to this student.');
          }

          const newInvite = {
            id: `inv-${Date.now()}`,
            studentEmail: cleanEmail,
            studentName: student.fullName,
            status: 'PENDING' as const,
            expiresAt,
            createdAt: new Date().toISOString(),
          };

          return {
            ...c,
            invitations: [newInvite, ...c.invitations],
            updatedAt: new Date().toISOString(),
          };
        }),
      );
    },
    [],
  );

  const revokeInvitation = useCallback((classroomId: string, invitationId: string): void => {
    setClassrooms(prev =>
      prev.map(c => {
        if (c.id !== classroomId) return c;
        return {
          ...c,
          invitations: c.invitations.map(i =>
            i.id === invitationId ? { ...i, status: 'REVOKED' as const } : i,
          ),
          updatedAt: new Date().toISOString(),
        };
      }),
    );
  }, []);

  const transferOwnership = useCallback(
    (classroomId: string, targetTeacherId: string): void => {
      const targetTeacher = DEMO_PERSONAS.find(
        p => p.id === targetTeacherId && p.role === 'TEACHER',
      );
      if (!targetTeacher) {
        throw new Error('Target owner must be an active registered Teacher.');
      }

      setClassrooms(prev =>
        prev.map(c =>
          c.id === classroomId
            ? {
                ...c,
                ownerId: targetTeacher.id,
                ownerName: targetTeacher.fullName,
                ownerEmail: targetTeacher.email,
                updatedAt: new Date().toISOString(),
              }
            : c,
        ),
      );
    },
    [],
  );

  // FR-ANN-004 & BR-ANN-003: Sorted announcements query
  const getAnnouncements = useCallback(
    (classroomId: string): ClassroomAnnouncement[] => {
      const list = announcements.filter(a => a.classroomId === classroomId);
      return [...list].sort((a, b) => {
        if (a.isPinned !== b.isPinned) {
          return a.isPinned ? -1 : 1;
        }
        return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      });
    },
    [announcements],
  );

  const getAnnouncement = useCallback(
    (id: string): ClassroomAnnouncement | undefined => {
      return announcements.find(a => a.id === id);
    },
    [announcements],
  );

  // FR-ANN-001 & FR-ANN-003: Create announcement & dispatch in-app notifications
  const createAnnouncement = useCallback(
    (input: CreateAnnouncementInput): ClassroomAnnouncement => {
      if (currentRole === 'STUDENT') {
        throw new Error('Students do not have permission to publish announcements.');
      }
      const nowUtc = new Date().toISOString();
      const id = `ann-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`;
      const newAnnouncement: ClassroomAnnouncement = {
        id,
        classroomId: input.classroomId,
        title: input.title.trim(),
        content: input.content.trim(),
        authorId: currentPersona.id,
        authorName: currentPersona.fullName,
        authorRole: currentRole === 'ADMIN' ? 'ADMIN' : 'TEACHER',
        isPinned: Boolean(input.isPinned),
        pinnedAt: input.isPinned ? nowUtc : null,
        createdAt: nowUtc,
        updatedAt: nowUtc,
      };

      setAnnouncements(prev => [newAnnouncement, ...prev]);

      // Update classroom stats.announcementsCount
      setClassrooms(prev =>
        prev.map(c => {
          if (c.id === input.classroomId) {
            const currentCount = c.stats?.announcementsCount ?? 0;
            return {
              ...c,
              stats: {
                lessonsCount: c.stats?.lessonsCount ?? 0,
                assignmentsCount: c.stats?.assignmentsCount ?? 0,
                quizzesCount: c.stats?.quizzesCount ?? 0,
                announcementsCount: currentCount + 1,
              },
              updatedAt: nowUtc,
            };
          }
          return c;
        }),
      );

      // FR-ANN-003 & BR-ANN-002: In-app Notification for all enrolled students
      const targetClassroom = classrooms.find(c => c.id === input.classroomId);
      if (targetClassroom) {
        const enrolledStudents = targetClassroom.members.filter(m => m.status === 'ACTIVE');
        if (enrolledStudents.length > 0) {
          const newNotifs: InAppNotification[] = enrolledStudents.map(m => ({
            id: `notif-${Date.now()}-${m.studentId}`,
            recipientId: m.studentId,
            recipientRole: 'STUDENT',
            title: 'New Classroom Announcement',
            message: `${currentPersona.fullName} posted: "${input.title.trim()}"`,
            type: 'CLASSROOM_ANNOUNCEMENT',
            classroomId: targetClassroom.id,
            classroomName: targetClassroom.name,
            announcementId: id,
            deepLink: `/student/classrooms/${targetClassroom.id}?tab=announcements&announcementId=${id}`,
            isRead: false,
            createdAt: nowUtc,
          }));
          setNotifications(prev => [...newNotifs, ...prev]);
        }
      }

      return newAnnouncement;
    },
    [currentRole, currentPersona, classrooms],
  );

  // FR-ANN-001: Edit announcement
  const updateAnnouncement = useCallback(
    (id: string, input: UpdateAnnouncementInput): ClassroomAnnouncement => {
      if (currentRole === 'STUDENT') {
        throw new Error('Students do not have permission to edit announcements.');
      }
      const target = announcements.find(a => a.id === id);
      if (!target) {
        throw new Error('Announcement not found.');
      }
      const nowUtc = new Date().toISOString();
      let isPinned = target.isPinned;
      let pinnedAt = target.pinnedAt;

      if (input.isPinned !== undefined && input.isPinned !== target.isPinned) {
        isPinned = input.isPinned;
        pinnedAt = input.isPinned ? nowUtc : null;
      }

      const updated: ClassroomAnnouncement = {
        ...target,
        title: input.title !== undefined ? input.title.trim() : target.title,
        content: input.content !== undefined ? input.content.trim() : target.content,
        isPinned,
        pinnedAt,
        updatedAt: nowUtc,
      };

      setAnnouncements(prev => prev.map(a => (a.id === id ? updated : a)));
      return updated;
    },
    [currentRole, announcements],
  );

  // FR-ANN-001: Delete announcement
  const deleteAnnouncement = useCallback(
    (id: string): void => {
      if (currentRole === 'STUDENT') {
        throw new Error('Students do not have permission to delete announcements.');
      }
      const target = announcements.find(a => a.id === id);
      if (!target) return;

      setAnnouncements(prev => prev.filter(a => a.id !== id));

      setClassrooms(prev =>
        prev.map(c => {
          if (c.id === target.classroomId) {
            const currentCount = c.stats?.announcementsCount ?? 1;
            return {
              ...c,
              stats: {
                lessonsCount: c.stats?.lessonsCount ?? 0,
                assignmentsCount: c.stats?.assignmentsCount ?? 0,
                quizzesCount: c.stats?.quizzesCount ?? 0,
                announcementsCount: Math.max(0, currentCount - 1),
              },
              updatedAt: new Date().toISOString(),
            };
          }
          return c;
        }),
      );
    },
    [currentRole, announcements],
  );

  // FR-ANN-001: Toggle pin status
  const togglePinAnnouncement = useCallback(
    (id: string): void => {
      if (currentRole === 'STUDENT') {
        throw new Error('Students do not have permission to pin announcements.');
      }
      const nowUtc = new Date().toISOString();
      setAnnouncements(prev =>
        prev.map(a => {
          if (a.id === id) {
            const nextPinned = !a.isPinned;
            return {
              ...a,
              isPinned: nextPinned,
              pinnedAt: nextPinned ? nowUtc : null,
              updatedAt: nowUtc,
            };
          }
          return a;
        }),
      );
    },
    [currentRole],
  );

  const getStudentNotifications = useCallback(
    (studentId: string): InAppNotification[] => {
      return notifications.filter(n => n.recipientId === studentId);
    },
    [notifications],
  );

  const markNotificationRead = useCallback((notificationId: string): void => {
    setNotifications(prev =>
      prev.map(n => (n.id === notificationId ? { ...n, isRead: true } : n)),
    );
  }, []);

  const resetMockData = useCallback((): void => {
    sessionStorage.removeItem(STORAGE_KEY_CLASSROOMS);
    sessionStorage.removeItem(STORAGE_KEY_ROLE);
    sessionStorage.removeItem(STORAGE_KEY_PERSONA);
    sessionStorage.removeItem(STORAGE_KEY_ANNOUNCEMENTS);
    sessionStorage.removeItem(STORAGE_KEY_NOTIFICATIONS);
    setClassrooms(INITIAL_MOCK_CLASSROOMS);
    setAnnouncements(INITIAL_MOCK_ANNOUNCEMENTS);
    setNotifications(INITIAL_MOCK_NOTIFICATIONS);
    setCurrentRoleState('TEACHER');
    setCurrentPersonaState(DEMO_PERSONAS[0]);
  }, []);

  const teachers = useMemo(() => DEMO_PERSONAS.filter(p => p.role === 'TEACHER'), []);
  const students = useMemo(() => DEMO_PERSONAS.filter(p => p.role === 'STUDENT'), []);

  return {
    classrooms,
    currentRole,
    currentPersona,
    setRole,
    setPersona,
    allPersonas: DEMO_PERSONAS,
    teachers,
    students,
    getClassroom,
    createClassroom,
    updateClassroom,
    archiveClassroom,
    deleteClassroom,
    regenerateCode,
    updateJoinConfig,
    joinByCode,
    leaveClassroom,
    directAddMember,
    removeMember,
    approveJoinRequest,
    rejectJoinRequest,
    createInvitation,
    revokeInvitation,
    transferOwnership,
    resetMockData,
    announcements,
    notifications,
    getAnnouncements,
    getAnnouncement,
    createAnnouncement,
    updateAnnouncement,
    deleteAnnouncement,
    togglePinAnnouncement,
    getStudentNotifications,
    markNotificationRead,
  };
}
