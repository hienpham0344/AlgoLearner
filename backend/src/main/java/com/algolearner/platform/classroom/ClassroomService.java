package com.algolearner.platform.classroom;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.common.exception.ResourceNotFoundException;
import com.algolearner.platform.practice.ClassroomPracticeRepository;
import com.algolearner.platform.notification.NotificationEntity;
import com.algolearner.platform.notification.NotificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.algolearner.platform.audit.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static com.algolearner.platform.classroom.ClassroomDtos.*;

@Service
@org.springframework.context.annotation.Profile("!mock")
@Transactional(readOnly = true)
public class ClassroomService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final ClassroomRepository classrooms;
    private final ClassroomMembershipRepository memberships;
    private final ClassroomJoinRequestRepository requests;
    private final ClassroomInvitationRepository invitations;
    private final NotificationRepository notifications;
    private final AccountRepository accounts;
    private final ClassroomPracticeRepository practices;
    private final ClassroomPairLock pairLock;
    private final EntityManager entityManager;
    private final Clock clock;
    private final AuditService audit;
    private final AccountIdentityResolver identityResolver;
    private final SecureRandom random = new SecureRandom();

    public ClassroomService(ClassroomRepository classrooms, ClassroomMembershipRepository memberships,
                            ClassroomJoinRequestRepository requests, ClassroomInvitationRepository invitations,
                            NotificationRepository notifications, AccountRepository accounts, Clock clock,
                            ClassroomPairLock pairLock, EntityManager entityManager, AuditService audit, AccountIdentityResolver identityResolver,
                            ClassroomPracticeRepository practices) {
        this.classrooms = classrooms; this.memberships = memberships; this.requests = requests;
        this.invitations = invitations; this.notifications = notifications; this.accounts = accounts; this.clock = clock;
        this.pairLock = pairLock; this.entityManager = entityManager;
        this.audit = audit; this.identityResolver = identityResolver;
        this.practices = practices;
    }

    public View get(UUID actorId, UUID classroomId) {
        var classroom = classroom(classroomId);
        var actor = activeAccount(actorId);
        if (!isAdmin(actor) && !classroom.getOwner().getId().equals(actorId)
                && !memberships.findByClassroomIdAndStudentId(classroomId, actorId).map(m -> m.getStatus() == MembershipStatus.ACTIVE).orElse(false)) {
            throw forbidden("Classroom is private");
        }
        return view(classroom, isAdmin(actor) || classroom.getOwner().getId().equals(actorId));
    }

    public PageResponse<View> list(UUID actorId, Pageable pageable) {
        var actor = activeAccount(actorId);
        Page<View> page;
        if (isAdmin(actor)) page = classrooms.findAll(pageable).map(c -> view(c, true));
        else if (actor.getRole() == AccountRole.TEACHER) page = classrooms.findByOwnerId(actorId, pageable).map(c -> view(c, true));
        else page = memberships.findByStudentIdAndStatus(actorId, MembershipStatus.ACTIVE, pageable)
                .map(m -> view(m.getClassroom(), false));
        return PageResponse.fromPage(page);
    }

    public PageResponse<Member> activeMembers(UUID actorId, UUID classroomId, Pageable pageable) {
        ownedOrAdmin(actorId, classroomId);
        Page<Member> page = memberships.findByClassroomIdAndStatus(classroomId, MembershipStatus.ACTIVE, pageable)
                .map(m -> new Member(m.getStudent().getId(), m.getStudent().getUsername(), m.getStudent().getEmail(), m.getStatus(), m.getJoinedAt()));
        return PageResponse.fromPage(page);
    }

    public PageResponse<JoinRequest> pendingRequests(UUID actorId, UUID classroomId, Pageable pageable) {
        owned(actorId, classroomId);
        Page<JoinRequest> page = requests.findByClassroomIdAndStatus(classroomId, JoinRequestStatus.PENDING_APPROVAL, pageable)
                .map(r -> new JoinRequest(r.getId(), r.getStudent().getId(), r.getStudent().getUsername(), r.getCreatedAt()));
        return PageResponse.fromPage(page);
    }

    public PageResponse<InvitationView> sentInvitations(UUID actorId, UUID classroomId, Pageable pageable) {
        owned(actorId, classroomId);
        return PageResponse.fromPage(invitations.findByClassroomId(classroomId, pageable).map(this::invitationView));
    }

    public PageResponse<InvitationView> myInvitations(UUID studentId, Pageable pageable) {
        activeStudent(studentId);
        return PageResponse.fromPage(invitations.findByStudentId(studentId, pageable).map(this::invitationView));
    }

    @Transactional
    public View create(UUID actorId, Create input) {
        var actor = activeAccount(actorId);
        AccountEntity owner = isAdmin(actor) && input.ownerId() != null ? activeTeacher(input.ownerId()) : actor;
        if (owner.getRole() != AccountRole.TEACHER) throw forbidden("Only an active Teacher may own a classroom");
        validateExpiry(input.codeExpiresAt());
        var classroom = new ClassroomEntity();
        classroom.setId(UUID.randomUUID()); classroom.setOwner(owner); classroom.setName(clean(input.name()));
        classroom.setDescription(clean(input.description())); classroom.setStatus(ClassroomStatus.ACTIVE);
        classroom.setClassCode(nextCode()); classroom.setCodeExpiresAt(input.codeExpiresAt());
        classroom.setCodeLocked(false); classroom.setJoiningOpen(true); classroom.setJoinMode(input.joinMode());
        return view(classrooms.save(classroom), true);
    }

    @Transactional
    public View update(UUID actorId, UUID classroomId, Update input) {
        var classroom = ownedOrAdminWritable(actorId, classroomId);
        requireActiveClass(classroom);
        classroom.setName(clean(input.name())); classroom.setDescription(clean(input.description()));
        return view(classroom, true);
    }

    @Transactional
    public void archive(UUID actorId, UUID classroomId) {
        var classroom = ownedOrAdminWritable(actorId, classroomId);
        classroom.setStatus(ClassroomStatus.ARCHIVED);
    }

    @Transactional
    public void delete(UUID actorId, UUID classroomId) {
        var classroom = ownedOrAdminWritable(actorId, classroomId);
        if (memberships.existsByClassroomId(classroomId) || requests.existsByClassroomId(classroomId)
                || invitations.existsByClassroomId(classroomId) || practices.existsByClassroomId(classroomId)
                || classrooms.hasQuizData(classroomId)) {
            throw conflict("CLASS_HAS_RETAINED_DATA", "Archive this classroom to preserve related classroom data");
        }
        classrooms.delete(classroom);
    }

    @Transactional
    public View configureCode(UUID actorId, UUID classroomId, CodeConfig input) {
        var classroom = ownedWritable(actorId, classroomId);
        requireActiveClass(classroom); validateExpiry(input.codeExpiresAt());
        classroom.setCodeExpiresAt(input.codeExpiresAt()); classroom.setCodeLocked(input.codeLocked());
        classroom.setJoiningOpen(input.joiningOpen()); classroom.setJoinMode(input.joinMode());
        return view(classroom, true);
    }

    @Transactional
    public View regenerateCode(UUID actorId, UUID classroomId) {
        var classroom = ownedWritable(actorId, classroomId); requireActiveClass(classroom);
        classroom.setClassCode(nextCode());
        return view(classroom, true);
    }

    @Transactional
    public JoinResult join(UUID studentId, JoinByCode input) {
        var student = activeStudent(studentId);
        String suppliedCode = input.classCode().strip().toUpperCase(Locale.ROOT);
        UUID classroomId = classrooms.findByClassCode(suppliedCode)
                .map(ClassroomEntity::getId)
                .orElseThrow(() -> notFound("CLASS_CODE_NOT_FOUND", "Class code is invalid"));
        var classroom = lockWritable(classroomId);
        if (!classroom.getClassCode().equals(suppliedCode))
            throw notFound("CLASS_CODE_NOT_FOUND", "Class code is invalid");
        pairLock.lock(classroom.getId(), studentId);
        requireJoinable(classroom);
        var membership = memberships.findByClassroomIdAndStudentId(classroom.getId(), studentId).orElse(null);
        if (membership != null && membership.getStatus() == MembershipStatus.ACTIVE) throw conflict("ALREADY_MEMBER", "Student is already a member");
        if (classroom.getJoinMode() == JoinMode.REQUIRE_APPROVAL) {
            var existing = requests.findByClassroomIdAndStudentIdAndStatus(classroom.getId(), studentId, JoinRequestStatus.PENDING_APPROVAL);
            if (existing.isPresent()) return new JoinResult("PENDING_APPROVAL", classroom.getId(), existing.get().getId());
            var request = new ClassroomJoinRequestEntity(); request.setId(UUID.randomUUID()); request.setClassroom(classroom);
            request.setStudent(student); request.setStatus(JoinRequestStatus.PENDING_APPROVAL); requests.save(request);
            return new JoinResult("PENDING_APPROVAL", classroom.getId(), request.getId());
        }
        activateMembership(classroom, student, membership);
        return new JoinResult("JOINED", classroom.getId(), null);
    }

    @Transactional
    public void leave(UUID studentId, UUID classroomId) {
        activeStudent(studentId);
        var classroom = lockWritable(classroomId);
        requireActiveClass(classroom); requireOwnerActiveForWrite(classroom);
        pairLock.lock(classroomId, studentId);
        var membership = memberships.findByClassroomIdAndStudentId(classroomId, studentId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE).orElseThrow(() -> notFound("MEMBERSHIP_NOT_FOUND", "Active membership not found"));
        membership.setStatus(MembershipStatus.LEFT); membership.setEndedAt(clock.instant());
        revokePendingInvitations(classroomId, studentId);
    }

    @Transactional
    public void directAdd(UUID actorId, UUID classroomId, DirectAdd input) {
        var classroom = ownedWritable(actorId, classroomId); requireActiveClass(classroom);
        var student = identityResolver.resolve(input.usernameOrEmail(), input.identityType())
                .orElseThrow(() -> notFound("STUDENT_NOT_FOUND", "Registered Student not found"));
        if (student.getRole() != AccountRole.STUDENT || student.getStatus() != AccountStatus.ACTIVE) throw bad("INVALID_STUDENT", "Target must be an active Student");
        pairLock.lock(classroomId, student.getId());
        activateMembership(classroom, student, memberships.findByClassroomIdAndStudentId(classroomId, student.getId()).orElse(null));
        requests.findByClassroomIdAndStudentIdAndStatus(classroomId, student.getId(), JoinRequestStatus.PENDING_APPROVAL)
                .ifPresent(request -> request.setStatus(JoinRequestStatus.APPROVED));
    }

    @Transactional
    public void removeStudent(UUID actorId, UUID classroomId, UUID studentId) {
        var classroom = ownedWritable(actorId, classroomId); requireActiveClass(classroom);
        pairLock.lock(classroomId, studentId);
        var membership = memberships.findByClassroomIdAndStudentId(classroomId, studentId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE).orElseThrow(() -> notFound("MEMBERSHIP_NOT_FOUND", "Active membership not found"));
        membership.setStatus(MembershipStatus.REMOVED); membership.setEndedAt(clock.instant());
        revokePendingInvitations(classroomId, studentId);
    }

    @Transactional
    public void approve(UUID actorId, UUID classroomId, UUID requestId) {
        var classroom = ownedWritable(actorId, classroomId); requireActiveClass(classroom);
        if (!classroom.isJoiningOpen()) throw bad("JOINING_CLOSED", "Classroom is closed for joining");
        var request = requests.findById(requestId).filter(r -> r.getClassroom().getId().equals(classroomId) && r.getStatus() == JoinRequestStatus.PENDING_APPROVAL)
                .orElseThrow(() -> notFound("JOIN_REQUEST_NOT_FOUND", "Pending join request not found"));
        var student = activeStudent(request.getStudent().getId());
        pairLock.lock(classroomId, student.getId());
        entityManager.flush();
        entityManager.refresh(request);
        if (request.getStatus() != JoinRequestStatus.PENDING_APPROVAL)
            throw notFound("JOIN_REQUEST_NOT_FOUND", "Pending join request not found");
        activateMembership(classroom, student, memberships.findByClassroomIdAndStudentId(classroomId, student.getId()).orElse(null));
        request.setStatus(JoinRequestStatus.APPROVED);
    }

    @Transactional
    public void reject(UUID actorId, UUID classroomId, UUID requestId) {
        var classroom = ownedWritable(actorId, classroomId);
        requireActiveClass(classroom);
        var request = requests.findById(requestId)
                .filter(r -> r.getClassroom().getId().equals(classroomId) && r.getStatus() == JoinRequestStatus.PENDING_APPROVAL)
                .orElseThrow(() -> notFound("JOIN_REQUEST_NOT_FOUND", "Pending join request not found"));
        pairLock.lock(classroomId, request.getStudent().getId());
        entityManager.flush();
        entityManager.refresh(request);
        if (request.getStatus() != JoinRequestStatus.PENDING_APPROVAL) {
            throw notFound("JOIN_REQUEST_NOT_FOUND", "Pending join request not found");
        }
        request.setStatus(JoinRequestStatus.REJECTED);
    }

    @Transactional
    public UUID invite(UUID actorId, UUID classroomId, Invite input) {
        var classroom = ownedWritable(actorId, classroomId); requireActiveClass(classroom);
        var student = accounts.findByEmailIgnoreCase(input.email()).orElseThrow(() -> notFound("STUDENT_NOT_FOUND", "Registered Student not found"));
        if (student.getRole() != AccountRole.STUDENT || student.getStatus() != AccountStatus.ACTIVE) throw bad("INVALID_STUDENT", "Invitation target must be an active Student");
        pairLock.lock(classroomId, student.getId());
        var invitation = new ClassroomInvitationEntity(); invitation.setId(UUID.randomUUID()); invitation.setClassroom(classroom);
        invitation.setStudent(student); invitation.setStatus(InvitationStatus.PENDING); invitation.setExpiresAt(input.expiresAt());
        invitations.save(invitation);
        var notification = new NotificationEntity();
        notification.setId(UUID.randomUUID()); notification.setRecipient(student);
        notification.setInvitation(invitation); notification.setType("CLASS_INVITATION");
        notifications.save(notification);
        return invitation.getId();
    }

    @Transactional
    public void acceptInvitation(UUID studentId, UUID invitationId) {
        var student = activeStudent(studentId);
        var invitation = invitations.findById(invitationId).filter(i -> i.getStudent().getId().equals(studentId))
                .orElseThrow(() -> notFound("INVITATION_NOT_FOUND", "Invitation not found"));
        lock(invitation.getClassroom(), LockModeType.PESSIMISTIC_WRITE);
        pairLock.lock(invitation.getClassroom().getId(), studentId);
        entityManager.flush();
        entityManager.refresh(invitation);
        if (invitation.effectiveStatus(clock.instant()) != InvitationStatus.PENDING) {
            throw bad("INVITATION_UNAVAILABLE", "Invitation has expired or was revoked");
        }
        requireActiveClass(invitation.getClassroom());
        requireOwnerActiveForWrite(invitation.getClassroom());
        if (!invitation.getClassroom().isJoiningOpen()) throw bad("JOINING_CLOSED", "Classroom is closed for joining");
        activateMembership(invitation.getClassroom(), student, memberships.findByClassroomIdAndStudentId(invitation.getClassroom().getId(), studentId).orElse(null));
        invitation.setStatus(InvitationStatus.ACCEPTED);
        markInvitationNotificationRead(invitationId);
    }

    @Transactional
    public void declineInvitation(UUID studentId, UUID invitationId) {
        activeStudent(studentId);
        var invitation = invitations.findById(invitationId).filter(i -> i.getStudent().getId().equals(studentId))
                .orElseThrow(() -> notFound("INVITATION_NOT_FOUND", "Invitation not found"));
        lock(invitation.getClassroom(), LockModeType.PESSIMISTIC_WRITE);
        pairLock.lock(invitation.getClassroom().getId(), studentId);
        entityManager.flush();
        entityManager.refresh(invitation);
        if (invitation.effectiveStatus(clock.instant()) != InvitationStatus.PENDING)
            throw bad("INVITATION_UNAVAILABLE", "Invitation has expired or was revoked");
        requireOwnerActiveForWrite(invitation.getClassroom());
        invitation.setStatus(InvitationStatus.DECLINED);
        markInvitationNotificationRead(invitationId);
    }

    @Transactional
    public void revokeInvitation(UUID actorId, UUID classroomId, UUID invitationId) {
        var classroom = ownedWritable(actorId, classroomId); requireActiveClass(classroom);
        var invitation = invitations.findById(invitationId).filter(i -> i.getClassroom().getId().equals(classroomId))
                .orElseThrow(() -> notFound("INVITATION_NOT_FOUND", "Invitation not found"));
        pairLock.lock(classroomId, invitation.getStudent().getId());
        entityManager.flush();
        entityManager.refresh(invitation);
        if (invitation.effectiveStatus(clock.instant()) != InvitationStatus.PENDING)
            throw bad("INVITATION_UNAVAILABLE", "Invitation has expired or was revoked");
        invitation.setStatus(InvitationStatus.REVOKED);
    }

    @Transactional
    public void transferOwner(UUID adminId, UUID classroomId, UUID targetTeacherId) {
        if (!isAdmin(activeAccount(adminId))) throw forbidden("Only Admin may transfer ownership");
        var classroom = lockWritable(classroomId);
        var target = activeTeacher(targetTeacherId);
        UUID previousOwner = classroom.getOwner().getId();
        classroom.setOwner(target);
        audit.record(adminId, AccountRole.ADMIN.name(), "CLASSROOM", classroomId.toString(),
                "CLASSROOM_OWNER_TRANSFERRED", "{\"ownerId\":\"" + previousOwner + "\"}",
                "{\"ownerId\":\"" + targetTeacherId + "\"}", null, null);
    }

    private void activateMembership(ClassroomEntity classroom, AccountEntity student, ClassroomMembershipEntity membership) {
        boolean isNew = membership == null;
        if (membership == null) {
            membership = new ClassroomMembershipEntity(); membership.setId(UUID.randomUUID());
            membership.setClassroom(classroom); membership.setStudent(student);
        }
        membership.setStatus(MembershipStatus.ACTIVE); membership.setJoinedAt(clock.instant()); membership.setEndedAt(null);
        if (isNew) memberships.save(membership);
    }
    private InvitationView invitationView(ClassroomInvitationEntity invitation) {
        return new InvitationView(invitation.getId(), invitation.getClassroom().getId(), invitation.getClassroom().getName(),
                invitation.getStudent().getId(), invitation.getStudent().getEmail(),
                invitation.effectiveStatus(clock.instant()).name(), invitation.getExpiresAt(), invitation.getCreatedAt());
    }
    private void markInvitationNotificationRead(UUID invitationId) {
        notifications.findByInvitationId(invitationId).ifPresent(notification -> {
            if (notification.getReadAt() == null) notification.setReadAt(clock.instant());
        });
    }
    private void revokePendingInvitations(UUID classroomId, UUID studentId) {
        var now = clock.instant();
        invitations.findByClassroomIdAndStudentIdAndStatus(classroomId, studentId, InvitationStatus.PENDING)
                .stream().filter(invitation -> invitation.effectiveStatus(now) == InvitationStatus.PENDING)
                .forEach(invitation -> invitation.setStatus(InvitationStatus.REVOKED));
    }
    /** Acquire before child resource locks; caller must hold a transaction. Refresh defeats stale persistence-context snapshots. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ClassroomEntity lockReadable(UUID classroomId) {
        return lock(classroom(classroomId), LockModeType.PESSIMISTIC_READ);
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ClassroomEntity lockWritable(UUID classroomId) {
        return lock(classroom(classroomId), LockModeType.PESSIMISTIC_WRITE);
    }
    private ClassroomEntity lock(ClassroomEntity classroom, LockModeType mode) {
        entityManager.flush();
        entityManager.refresh(classroom, mode);
        return classroom;
    }
    private ClassroomEntity ownedWritable(UUID actorId, UUID classroomId) {
        activeAccount(actorId);
        var classroom = lockWritable(classroomId);
        if (!classroom.getOwner().getId().equals(actorId)) throw forbidden("Only the classroom owner may perform this operation");
        return classroom;
    }
    private ClassroomEntity ownedOrAdminWritable(UUID actorId, UUID classroomId) {
        var actor = activeAccount(actorId);
        var classroom = lockWritable(classroomId);
        if (!isAdmin(actor) && !classroom.getOwner().getId().equals(actorId))
            throw forbidden("Only the classroom owner may perform this operation");
        return classroom;
    }
    private ClassroomEntity owned(UUID actorId, UUID classroomId) { activeAccount(actorId); var c = classroom(classroomId); if (!c.getOwner().getId().equals(actorId)) throw forbidden("Only the classroom owner may perform this operation"); return c; }
    private ClassroomEntity ownedOrAdmin(UUID actorId, UUID classroomId) { var a = activeAccount(actorId); return isAdmin(a) ? classroom(classroomId) : owned(actorId, classroomId); }
    private ClassroomEntity classroom(UUID id) { return classrooms.findById(id).orElseThrow(() -> new ResourceNotFoundException("Classroom", id)); }
    private AccountEntity account(UUID id) { return accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account", id)); }
    private AccountEntity activeAccount(UUID id) { var a = account(id); if (a.getStatus() != AccountStatus.ACTIVE) throw forbidden("Inactive accounts cannot perform this operation"); return a; }
    private AccountEntity activeStudent(UUID id) { var a=activeAccount(id); if(a.getRole()!=AccountRole.STUDENT) throw forbidden("Only Student may join a classroom"); return a; }
    private AccountEntity activeTeacher(UUID id) { var a=activeAccount(id); if(a.getRole()!=AccountRole.TEACHER) throw bad("INVALID_TEACHER", "Target must be an active Teacher"); return a; }
    private boolean isAdmin(AccountEntity account) { return account.getRole()==AccountRole.ADMIN; }
    private void requireActiveClass(ClassroomEntity classroom) { if(classroom.getStatus()!=ClassroomStatus.ACTIVE) throw bad("CLASS_ARCHIVED", "Archived classroom is read-only"); }
    private void requireJoinable(ClassroomEntity c) { requireActiveClass(c); requireOwnerActiveForWrite(c); if(!c.isJoiningOpen()) throw bad("JOINING_CLOSED", "Classroom is closed for joining"); if(c.isCodeLocked()) throw bad("CLASS_CODE_LOCKED", "Class code is locked"); if(c.getCodeExpiresAt()!=null && !c.getCodeExpiresAt().isAfter(clock.instant())) throw bad("CLASS_CODE_EXPIRED", "Class code has expired"); }
    private void requireOwnerActiveForWrite(ClassroomEntity classroom) { if (classroom.getOwner().getStatus() != AccountStatus.ACTIVE) throw bad("CLASS_READ_ONLY", "Classroom is read-only while its owner is inactive"); }
    private void validateExpiry(Instant expiry) { if(expiry != null && !expiry.isAfter(clock.instant())) throw bad("INVALID_CODE_EXPIRY", "Class-code expiry must be in the future"); }
    private String nextCode() { for(int i=0;i<20;i++){ StringBuilder b=new StringBuilder(8); for(int n=0;n<8;n++) b.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]); String code=b.toString(); if(!classrooms.existsByClassCode(code)) return code; } throw conflict("CLASS_CODE_GENERATION_FAILED", "Could not allocate a unique class code"); }
    private static String clean(String value) { return value == null ? null : value.strip(); }
    private static AppException forbidden(String message) { return new DomainException(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", message); }
    private static AppException bad(String code,String message) { return new DomainException(HttpStatus.BAD_REQUEST, code, message); }
    private static AppException conflict(String code,String message) { return new DomainException(HttpStatus.CONFLICT, code, message); }
    private static AppException notFound(String code,String message) { return new DomainException(HttpStatus.NOT_FOUND, code, message); }
    private static final class DomainException extends AppException { private DomainException(HttpStatus s,String c,String m){super(m,s,c);} }
    private static View view(ClassroomEntity c, boolean includeCodeConfiguration) {
        return new View(c.getId(), c.getName(), c.getDescription(), c.getStatus().name(),
                includeCodeConfiguration ? c.getClassCode() : null,
                includeCodeConfiguration ? c.getCodeExpiresAt() : null,
                includeCodeConfiguration && c.isCodeLocked(), c.isJoiningOpen(), c.getJoinMode(),
                c.getOwner().getId(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
