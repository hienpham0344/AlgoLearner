import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Classroom,
  CreateClassroomInput,
  DemoPersona,
  DemoRole,
  JoinConfigurationInput,
  JoinResultFeedback,
  UpdateClassroomInput,
} from '../types';
import { DEMO_PERSONAS, INITIAL_MOCK_CLASSROOMS } from './classroomMockData';

const STORAGE_KEY_CLASSROOMS = 'algolearn_prototype_classrooms_v1';
const STORAGE_KEY_ROLE = 'algolearn_prototype_role_v1';
const STORAGE_KEY_PERSONA = 'algolearn_prototype_persona_v1';

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function generateRandomCode(): string {
  let result = '';
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

      const newClass: Classroom = {
        id: `cls-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
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
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
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

      setClassrooms(prev => [newClass, ...prev]);
      return newClass;
    },
    [classrooms, currentRole, currentPersona],
  );

  const updateClassroom = useCallback(
    (id: string, input: UpdateClassroomInput): Classroom => {
      const cls = classrooms.find(c => c.id === id);
      if (!cls) throw new Error('Classroom not found');
      if (cls.status === 'ARCHIVED') {
        throw new Error('Archived classroom is read-only.');
      }

      const updated: Classroom = {
        ...cls,
        name: input.name.trim(),
        description: input.description.trim(),
        updatedAt: new Date().toISOString(),
      };

      setClassrooms(prev => prev.map(c => (c.id === id ? updated : c)));
      return updated;
    },
    [classrooms],
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

  const deleteClassroom = useCallback(
    (id: string): void => {
      const target = classrooms.find(c => c.id === id);
      if (!target) throw new Error('Classroom not found');
      // Business Rule BR-CLS-005: Classroom with existing student work/members cannot be deleted
      const activeOrHistoricMembers = target.members.length;
      if (activeOrHistoricMembers > 0) {
        throw new Error(
          'Classroom with existing members or learning records cannot be deleted. Please Archive instead.',
        );
      }
      setClassrooms(prev => prev.filter(c => c.id !== id));
    },
    [classrooms],
  );

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
              updatedAt: new Date().toISOString(),
            }
          : c,
      );
    });
    return newCode;
  }, []);

  const updateJoinConfig = useCallback(
    (id: string, config: JoinConfigurationInput): void => {
      setClassrooms(prev =>
        prev.map(c => {
          if (c.id !== id) return c;
          if (c.status === 'ARCHIVED') {
            throw new Error('Archived classroom settings cannot be modified.');
          }
          return {
            ...c,
            codeExpiresAt: config.codeExpiresAt,
            codeLocked: config.codeLocked,
            joiningOpen: config.joiningOpen,
            joinMode: config.joinMode,
            updatedAt: new Date().toISOString(),
          };
        }),
      );
    },
    [],
  );

  const joinByCode = useCallback(
    (rawCode: string): JoinResultFeedback => {
      const cleanCode = rawCode.trim().toUpperCase();
      const target = classrooms.find(c => c.classCode.toUpperCase() === cleanCode);

      if (!target) {
        throw new Error('Class code is invalid or does not exist.');
      }

      if (target.status === 'ARCHIVED') {
        throw new Error('This classroom is archived and cannot accept new members.');
      }

      if (!target.joiningOpen) {
        throw new Error('Classroom is currently closed for joining.');
      }

      if (target.codeLocked) {
        throw new Error('Class code is locked by the instructor.');
      }

      if (target.codeExpiresAt && new Date(target.codeExpiresAt).getTime() <= Date.now()) {
        throw new Error('Class code has expired. Please ask the instructor for a new code.');
      }

      const existingMember = target.members.find(m => m.studentId === currentPersona.id);

      if (existingMember && existingMember.status === 'ACTIVE') {
        throw new Error('You are already an active member of this classroom.');
      }

      // Check if student previously left or was removed (Rejoin workflow per BR-CLS-012)
      const isRejoin = existingMember && existingMember.status !== 'ACTIVE';

      if (target.joinMode === 'REQUIRE_APPROVAL') {
        const existingReq = target.joinRequests.find(
          r => r.studentId === currentPersona.id && r.status === 'PENDING_APPROVAL',
        );

        if (existingReq) {
          return {
            outcome: 'PENDING_APPROVAL',
            message: 'You already have a pending join request awaiting instructor review.',
            classroomId: target.id,
            requestId: existingReq.id,
          };
        }

        const newRequest = {
          id: `req-${Date.now()}`,
          studentId: currentPersona.id,
          username: currentPersona.username,
          fullName: currentPersona.fullName,
          email: currentPersona.email,
          status: 'PENDING_APPROVAL' as const,
          createdAt: new Date().toISOString(),
        };

        setClassrooms(prev =>
          prev.map(c =>
            c.id === target.id
              ? {
                  ...c,
                  joinRequests: [newRequest, ...c.joinRequests],
                  updatedAt: new Date().toISOString(),
                }
              : c,
          ),
        );

        return {
          outcome: 'PENDING_APPROVAL',
          message: isRejoin
            ? 'Rejoin request submitted. Awaiting instructor approval.'
            : 'Join request submitted! Awaiting instructor approval.',
          classroomId: target.id,
          requestId: newRequest.id,
        };
      }

      // AUTO_JOIN mode
      setClassrooms(prev =>
        prev.map(c => {
          if (c.id !== target.id) return c;

          if (isRejoin) {
            // Reactivate existing record preserving history per BR-CLS-012
            return {
              ...c,
              members: c.members.map(m =>
                m.studentId === currentPersona.id
                  ? {
                      ...m,
                      status: 'ACTIVE',
                      joinedAt: new Date().toISOString(),
                      endedAt: null,
                    }
                  : m,
              ),
              updatedAt: new Date().toISOString(),
            };
          }

          // Brand new member
          return {
            ...c,
            members: [
              ...c.members,
              {
                studentId: currentPersona.id,
                username: currentPersona.username,
                fullName: currentPersona.fullName,
                email: currentPersona.email,
                status: 'ACTIVE',
                joinedAt: new Date().toISOString(),
              },
            ],
            updatedAt: new Date().toISOString(),
          };
        }),
      );

      return {
        outcome: isRejoin ? 'REJOINED' : 'JOINED',
        message: isRejoin
          ? 'Welcome back! You have rejoined the classroom.'
          : 'Successfully enrolled in the classroom!',
        classroomId: target.id,
      };
    },
    [classrooms, currentPersona],
  );

  const leaveClassroom = useCallback(
    (classroomId: string): void => {
      setClassrooms(prev =>
        prev.map(c => {
          if (c.id !== classroomId) return c;
          return {
            ...c,
            members: c.members.map(m =>
              m.studentId === currentPersona.id
                ? {
                    ...m,
                    status: 'LEFT',
                    endedAt: new Date().toISOString(),
                  }
                : m,
            ),
            updatedAt: new Date().toISOString(),
          };
        }),
      );
    },
    [currentPersona.id],
  );

  const directAddMember = useCallback(
    (classroomId: string, usernameOrEmail: string): void => {
      const query = usernameOrEmail.trim().toLowerCase();
      const student = DEMO_PERSONAS.find(
        p =>
          p.role === 'STUDENT' &&
          (p.username.toLowerCase() === query || p.email.toLowerCase() === query),
      );

      if (!student) {
        throw new Error(
          'Student not found. Target must be an existing registered Student account.',
        );
      }

      setClassrooms(prev =>
        prev.map(c => {
          if (c.id !== classroomId) return c;
          const existing = c.members.find(m => m.studentId === student.id);

          if (existing && existing.status === 'ACTIVE') {
            throw new Error(`${student.fullName} is already an active member.`);
          }

          if (existing) {
            return {
              ...c,
              members: c.members.map(m =>
                m.studentId === student.id
                  ? { ...m, status: 'ACTIVE', joinedAt: new Date().toISOString(), endedAt: null }
                  : m,
              ),
              updatedAt: new Date().toISOString(),
            };
          }

          return {
            ...c,
            members: [
              ...c.members,
              {
                studentId: student.id,
                username: student.username,
                fullName: student.fullName,
                email: student.email,
                status: 'ACTIVE',
                joinedAt: new Date().toISOString(),
              },
            ],
            updatedAt: new Date().toISOString(),
          };
        }),
      );
    },
    [],
  );

  const removeMember = useCallback((classroomId: string, studentId: string): void => {
    setClassrooms(prev =>
      prev.map(c => {
        if (c.id !== classroomId) return c;
        return {
          ...c,
          members: c.members.map(m =>
            m.studentId === studentId
              ? {
                  ...m,
                  status: 'REMOVED',
                  endedAt: new Date().toISOString(),
                }
              : m,
          ),
          updatedAt: new Date().toISOString(),
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

  const resetMockData = useCallback((): void => {
    sessionStorage.removeItem(STORAGE_KEY_CLASSROOMS);
    sessionStorage.removeItem(STORAGE_KEY_ROLE);
    sessionStorage.removeItem(STORAGE_KEY_PERSONA);
    setClassrooms(INITIAL_MOCK_CLASSROOMS);
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
  };
}
