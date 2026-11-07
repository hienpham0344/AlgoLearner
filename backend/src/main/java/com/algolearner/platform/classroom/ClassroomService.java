package com.algolearner.platform.classroom;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.common.security.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import static com.algolearner.platform.classroom.ClassroomDtos.*;
import static com.algolearner.platform.classroom.ClassroomTypes.*;

@Service
@Transactional(readOnly=true)
public class ClassroomService {
    private final ClassroomRepository repository;
    private final AccountRepository accounts;
    private final ClassroomNotificationPort notifications;
    private final ClassroomAuditPort audit;
    private final ClassCodeGenerator codeGenerator;
    private final Clock clock;

    public ClassroomService(ClassroomRepository repository,AccountRepository accounts,
            ClassroomNotificationPort notifications,ClassroomAuditPort audit,
            ClassCodeGenerator codeGenerator,Clock clock) {
        this.repository=repository; this.accounts=accounts; this.notifications=notifications;
        this.audit=audit; this.codeGenerator=codeGenerator; this.clock=clock;
    }

    @Transactional
    public synchronized ClassroomResponse create(UUID actorId,CreateClassroomRequest request) {
        requireActive(actorId,"TEACHER");
        return response(saveNew(actorId,request.name(),request.description(),request.joinMode(),
                request.joiningOpen(),request.classCodeExpiresAt()),actorId);
    }

    @Transactional
    public synchronized ClassroomResponse adminCreate(UUID adminId,AdminCreateClassroomRequest request) {
        requireActive(adminId,"ADMIN"); requireActive(request.ownerAccountId(),"TEACHER");
        return response(saveNew(request.ownerAccountId(),request.name(),request.description(),request.joinMode(),
                request.joiningOpen(),request.classCodeExpiresAt()),adminId);
    }

    public List<ClassroomResponse> list(UUID actorId) {
        var actor=requireActive(actorId,null);
        return repository.findAll().stream().filter(c -> canRead(c,actor))
                .sorted(Comparator.comparing(Classroom::createdAt).reversed())
                .map(c -> response(c,actorId)).toList();
    }

    public ClassroomResponse get(UUID actorId,UUID classroomId) {
        Classroom classroom=requireClassroom(classroomId);
        if(!canRead(classroom,requireActive(actorId,null))) throw forbidden();
        return response(classroom,actorId);
    }

    @Transactional
    public synchronized ClassroomResponse update(UUID actorId,UUID classroomId,UpdateClassroomRequest request) {
        Classroom old=requireOwnerOrAdmin(actorId,classroomId);
        if(request.name()==null && request.description()==null) throw invalid("At least one classroom field is required");
        String name=request.name()==null?old.name():requiredText(request.name(),150,"Classroom name");
        String description=request.description()==null?old.description():optionalText(request.description(),2000,"Description");
        Classroom saved=repository.save(copy(old,name,description,old.ownerAccountId(),old.status(),old.joinMode(),
                old.joiningOpen(),old.classCode(),old.classCodeLocked(),old.classCodeExpiresAt()));
        return response(saved,actorId);
    }

    @Transactional
    public synchronized ClassroomResponse archive(UUID actorId,UUID classroomId) {
        Classroom old=requireOwnerOrAdmin(actorId,classroomId);
        if(old.status()==ClassroomStatus.ARCHIVED) return response(old,actorId);
        Classroom saved=repository.save(copy(old,old.name(),old.description(),old.ownerAccountId(),
                ClassroomStatus.ARCHIVED,old.joinMode(),old.joiningOpen(),old.classCode(),
                old.classCodeLocked(),old.classCodeExpiresAt()));
        audit.record(actorId,"CLASSROOM_ARCHIVED",classroomId,"SUCCESS",clock.instant());
        return response(saved,actorId);
    }

    @Transactional
    public synchronized void delete(UUID actorId,UUID classroomId) {
        requireOwnerOrAdmin(actorId,classroomId);
        if(repository.hasPreservableData(classroomId))
            throw conflict("Classroom has data that must be preserved and cannot be permanently deleted");
        repository.delete(classroomId);
        audit.record(actorId,"CLASSROOM_DELETED",classroomId,"SUCCESS",clock.instant());
    }

    public JoinCodeResponse joinCode(UUID actorId,UUID classroomId) {
        Classroom classroom=requireOwnerOrAdmin(actorId,classroomId);
        return new JoinCodeResponse(classroom.classCode(),classroom.classCodeLocked(),classroom.classCodeExpiresAt());
    }

    @Transactional
    public synchronized JoinCodeResponse regenerateCode(UUID actorId,UUID classroomId) {
        Classroom old=requireOwner(actorId,classroomId);
        String code=uniqueCode();
        Classroom saved=repository.save(copy(old,old.name(),old.description(),old.ownerAccountId(),old.status(),
                old.joinMode(),old.joiningOpen(),code,false,old.classCodeExpiresAt()));
        return new JoinCodeResponse(saved.classCode(),saved.classCodeLocked(),saved.classCodeExpiresAt());
    }

    @Transactional
    public synchronized JoinCodeResponse updateJoinCode(UUID actorId,UUID classroomId,JoinCodeSettingsRequest request) {
        Classroom old=requireOwner(actorId,classroomId);
        if(request.locked()==null && request.neverExpires()==null && request.expiresAt()==null)
            throw invalid("At least one class code setting is required");
        if(Boolean.TRUE.equals(request.neverExpires()) && request.expiresAt()!=null)
            throw invalid("expiresAt cannot be supplied when neverExpires is true");
        Instant expiry=Boolean.TRUE.equals(request.neverExpires())?null:
                request.expiresAt()!=null?request.expiresAt():old.classCodeExpiresAt();
        if(expiry!=null && !expiry.isAfter(clock.instant())) throw invalid("Class code expiry must be in the future");
        boolean locked=request.locked()==null?old.classCodeLocked():request.locked();
        Classroom saved=repository.save(copy(old,old.name(),old.description(),old.ownerAccountId(),old.status(),
                old.joinMode(),old.joiningOpen(),old.classCode(),locked,expiry));
        return new JoinCodeResponse(saved.classCode(),saved.classCodeLocked(),saved.classCodeExpiresAt());
    }

    @Transactional
    public synchronized ClassroomResponse updateJoinSettings(UUID actorId,UUID classroomId,JoinSettingsRequest request) {
        Classroom old=requireOwner(actorId,classroomId);
        if(request.joiningOpen()==null && request.joinMode()==null)
            throw invalid("At least one join setting is required");
        Classroom saved=repository.save(copy(old,old.name(),old.description(),old.ownerAccountId(),old.status(),
                request.joinMode()==null?old.joinMode():request.joinMode(),
                request.joiningOpen()==null?old.joiningOpen():request.joiningOpen(),old.classCode(),
                old.classCodeLocked(),old.classCodeExpiresAt()));
        return response(saved,actorId);
    }

    @Transactional
    public synchronized JoinResult join(UUID studentId,JoinClassRequest request) {
        requireActive(studentId,"STUDENT");
        String code=request.classCode().strip().toUpperCase(Locale.ROOT);
        Classroom classroom=repository.findByCode(code).orElseThrow(() -> joinDenied("Class code is invalid"));
        assertJoinable(classroom);
        var existing=repository.findMembership(classroom.id(),studentId);
        if(existing.isPresent() && existing.get().status()==MembershipStatus.ACTIVE)
            throw conflict("Student is already a classroom member");
        if(classroom.joinMode()==JoinMode.AUTO_JOIN) {
            Membership member=activateMembership(existing.orElse(null),classroom.id(),studentId);
            return new JoinResult(classroom.id(),"JOINED",membership(member),null);
        }
        if(repository.findPendingJoinRequest(classroom.id(),studentId).isPresent())
            throw conflict("A join request is already pending");
        Instant now=clock.instant();
        JoinRequest pending=repository.saveJoinRequest(new JoinRequest(UUID.randomUUID(),classroom.id(),studentId,
                JoinRequestStatus.PENDING,null,null,now,now));
        return new JoinResult(classroom.id(),"PENDING_APPROVAL",null,pending.id());
    }

    public List<MembershipResponse> members(UUID actorId,UUID classroomId) {
        requireOwnerOrAdmin(actorId,classroomId);
        return repository.findMemberships(classroomId).stream().map(this::membership).toList();
    }

    @Transactional
    public synchronized MembershipResponse addMember(UUID actorId,UUID classroomId,AddMemberRequest request) {
        requireOwner(actorId,classroomId); requireActive(request.studentAccountId(),"STUDENT");
        Membership saved=activateMembership(repository.findMembership(classroomId,request.studentAccountId()).orElse(null),
                classroomId,request.studentAccountId());
        return membership(saved);
    }

    @Transactional
    public synchronized MembershipResponse removeMember(UUID actorId,UUID classroomId,UUID studentId) {
        requireOwner(actorId,classroomId);
        Membership old=repository.findMembership(classroomId,studentId)
                .filter(x -> x.status()==MembershipStatus.ACTIVE)
                .orElseThrow(() -> conflict("Student is not an active classroom member"));
        Instant now=clock.instant();
        Membership saved=repository.saveMembership(new Membership(old.id(),old.classroomId(),old.studentAccountId(),
                MembershipStatus.REMOVED,old.joinedAt(),now,old.createdAt(),now));
        audit.record(actorId,"CLASSROOM_MEMBER_REMOVED",studentId,"SUCCESS",now);
        return membership(saved);
    }

    @Transactional
    public synchronized MembershipResponse leave(UUID studentId,UUID classroomId) {
        requireActive(studentId,"STUDENT");
        Membership old=repository.findMembership(classroomId,studentId)
                .filter(x -> x.status()==MembershipStatus.ACTIVE)
                .orElseThrow(() -> conflict("Student is not an active classroom member"));
        Instant now=clock.instant();
        return membership(repository.saveMembership(new Membership(old.id(),old.classroomId(),old.studentAccountId(),
                MembershipStatus.LEFT,old.joinedAt(),now,old.createdAt(),now)));
    }

    public List<JoinRequestResponse> joinRequests(UUID actorId,UUID classroomId) {
        requireOwner(actorId,classroomId);
        return repository.findJoinRequests(classroomId).stream().map(this::joinRequest).toList();
    }

    @Transactional
    public synchronized JoinRequestResponse reviewJoinRequest(UUID actorId,UUID classroomId,UUID requestId,boolean approve) {
        Classroom classroom=requireOwner(actorId,classroomId); assertWritable(classroom);
        JoinRequest old=repository.findJoinRequest(requestId)
                .filter(x -> x.classroomId().equals(classroomId) && x.status()==JoinRequestStatus.PENDING)
                .orElseThrow(() -> conflict("Join request is not pending"));
        if(approve) {
            requireActive(old.studentAccountId(),"STUDENT");
            activateMembership(repository.findMembership(classroomId,old.studentAccountId()).orElse(null),
                    classroomId,old.studentAccountId());
        }
        Instant now=clock.instant();
        return joinRequest(repository.saveJoinRequest(new JoinRequest(old.id(),old.classroomId(),old.studentAccountId(),
                approve?JoinRequestStatus.APPROVED:JoinRequestStatus.REJECTED,actorId,now,old.createdAt(),now)));
    }

    public List<InvitationResponse> invitations(UUID actorId,UUID classroomId) {
        requireOwner(actorId,classroomId);
        return repository.findInvitations(classroomId).stream().map(this::invitation).toList();
    }

    @Transactional
    public synchronized InvitationResponse invite(UUID actorId,UUID classroomId,CreateInvitationRequest request) {
        Classroom classroom=requireOwner(actorId,classroomId); assertWritable(classroom);
        var student=accounts.accounts().stream().filter(a -> a.email().equalsIgnoreCase(request.email().strip()))
                .findFirst().orElseThrow(() -> invalid("Invitation email must belong to a registered Student"));
        requireActive(student.id(),"STUDENT");
        if(repository.findMembership(classroomId,student.id()).filter(x -> x.status()==MembershipStatus.ACTIVE).isPresent())
            throw conflict("Student is already a classroom member");
        if(repository.findPendingInvitation(classroomId,student.id()).isPresent())
            throw conflict("An invitation is already pending for this Student");
        if(request.expiresAt()!=null && !request.expiresAt().isAfter(clock.instant()))
            throw invalid("Invitation expiry must be in the future");
        Instant now=clock.instant();
        Invitation saved=repository.saveInvitation(new Invitation(UUID.randomUUID(),classroomId,student.id(),actorId,
                InvitationStatus.PENDING,request.expiresAt(),null,null,now,now));
        notifications.invitationCreated(saved.id(),classroomId,student.id(),now);
        return invitation(saved);
    }

    @Transactional
    public synchronized InvitationResponse revokeInvitation(UUID actorId,UUID classroomId,UUID invitationId) {
        requireOwner(actorId,classroomId);
        Invitation old=requirePendingInvitation(invitationId,classroomId);
        Instant now=clock.instant();
        return invitation(repository.saveInvitation(new Invitation(old.id(),old.classroomId(),old.inviteeAccountId(),
                old.invitedByAccountId(),InvitationStatus.REVOKED,old.expiresAt(),old.respondedAt(),now,old.createdAt(),now)));
    }

    @Transactional
    public synchronized InvitationResponse respondInvitation(UUID studentId,UUID invitationId,boolean accept) {
        requireActive(studentId,"STUDENT");
        Invitation old=repository.findInvitation(invitationId).orElseThrow(() ->
                new ApiException(ErrorCode.INVITATION_NOT_FOUND));
        if(!old.inviteeAccountId().equals(studentId)) throw forbidden();
        if(old.status()!=InvitationStatus.PENDING) throw expiredInvitation();
        Instant now=clock.instant();
        if(old.expiresAt()!=null && !old.expiresAt().isAfter(now)) {
            repository.saveInvitation(new Invitation(old.id(),old.classroomId(),old.inviteeAccountId(),
                    old.invitedByAccountId(),InvitationStatus.EXPIRED,old.expiresAt(),null,null,old.createdAt(),now));
            throw expiredInvitation();
        }
        Classroom classroom=requireClassroom(old.classroomId()); assertWritable(classroom);
        if(accept) activateMembership(repository.findMembership(classroom.id(),studentId).orElse(null),classroom.id(),studentId);
        Invitation saved=repository.saveInvitation(new Invitation(old.id(),old.classroomId(),old.inviteeAccountId(),
                old.invitedByAccountId(),accept?InvitationStatus.ACCEPTED:InvitationStatus.DECLINED,
                old.expiresAt(),now,null,old.createdAt(),now));
        return invitation(saved);
    }

    @Transactional
    public synchronized ClassroomResponse transferOwnership(UUID adminId,UUID classroomId,TransferOwnershipRequest request) {
        requireActive(adminId,"ADMIN"); requireActive(request.newOwnerAccountId(),"TEACHER");
        Classroom old=requireClassroom(classroomId);
        if(old.ownerAccountId().equals(request.newOwnerAccountId())) throw conflict("Teacher already owns this classroom");
        Classroom saved=repository.save(copy(old,old.name(),old.description(),request.newOwnerAccountId(),old.status(),
                old.joinMode(),old.joiningOpen(),old.classCode(),old.classCodeLocked(),old.classCodeExpiresAt()));
        audit.record(adminId,"TRANSFER_CLASS_OWNERSHIP",classroomId,"SUCCESS",clock.instant());
        return response(saved,adminId);
    }

    private Classroom saveNew(UUID ownerId,String rawName,String rawDescription,JoinMode mode,Boolean joiningOpen,Instant expiry) {
        Instant now=clock.instant();
        if(expiry!=null && !expiry.isAfter(now)) throw invalid("Class code expiry must be in the future");
        Classroom classroom=new Classroom(UUID.randomUUID(),requiredText(rawName,150,"Classroom name"),
                optionalText(rawDescription,2000,"Description"),ownerId,ClassroomStatus.ACTIVE,
                mode==null?JoinMode.AUTO_JOIN:mode,joiningOpen==null||joiningOpen,uniqueCode(),false,expiry,now,now);
        return repository.save(classroom);
    }
    private String uniqueCode() {
        for(int attempt=0;attempt<32;attempt++) {
            String code=codeGenerator.nextCode(); if(repository.findByCode(code).isEmpty()) return code;
        }
        throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR,"Unable to allocate a unique class code");
    }
    private Membership activateMembership(Membership old,UUID classroomId,UUID studentId) {
        Instant now=clock.instant();
        Membership next=old==null?new Membership(UUID.randomUUID(),classroomId,studentId,MembershipStatus.ACTIVE,
                now,null,now,now):new Membership(old.id(),classroomId,studentId,MembershipStatus.ACTIVE,
                now,null,old.createdAt(),now);
        return repository.saveMembership(next);
    }
    private void assertJoinable(Classroom classroom) {
        assertWritable(classroom);
        Instant now=clock.instant();
        if(!classroom.joiningOpen()) throw joinDenied("Classroom is closed for joining");
        if(classroom.classCodeLocked()) throw joinDenied("Class code is locked");
        if(classroom.classCodeExpiresAt()!=null && !classroom.classCodeExpiresAt().isAfter(now))
            throw joinDenied("Class code has expired");
    }
    private void assertWritable(Classroom classroom) {
        if(classroom.status()!=ClassroomStatus.ACTIVE || !isActive(classroom.ownerAccountId(),"TEACHER"))
            throw conflict("Classroom is read-only");
    }
    private Classroom requireOwner(UUID actorId,UUID classroomId) {
        requireActive(actorId,"TEACHER");
        Classroom classroom=requireClassroom(classroomId);
        if(!classroom.ownerAccountId().equals(actorId)) throw forbidden();
        return classroom;
    }
    private Classroom requireOwnerOrAdmin(UUID actorId,UUID classroomId) {
        var actor=requireActive(actorId,null); Classroom classroom=requireClassroom(classroomId);
        if(!actor.role().equals("ADMIN") && !(actor.role().equals("TEACHER") && classroom.ownerAccountId().equals(actorId)))
            throw forbidden();
        return classroom;
    }
    private JoinRequestResponse joinRequest(JoinRequest x) {
        return new JoinRequestResponse(x.id(),x.classroomId(),x.studentAccountId(),x.status(),
                x.reviewedByAccountId(),x.reviewedAt(),x.createdAt());
    }
    private InvitationResponse invitation(Invitation x) {
        InvitationStatus status=x.status()==InvitationStatus.PENDING && x.expiresAt()!=null
                && !x.expiresAt().isAfter(clock.instant())?InvitationStatus.EXPIRED:x.status();
        return new InvitationResponse(x.id(),x.classroomId(),x.inviteeAccountId(),status,
                x.expiresAt(),x.respondedAt(),x.revokedAt());
    }
    private MembershipResponse membership(Membership x) {
        return new MembershipResponse(x.id(),x.classroomId(),x.studentAccountId(),x.status(),x.joinedAt(),x.endedAt());
    }
    private ClassroomResponse response(Classroom x,UUID viewer) {
        return new ClassroomResponse(x.id(),x.name(),x.description(),x.ownerAccountId(),x.status(),x.joinMode(),
                x.joiningOpen(),x.status()==ClassroomStatus.ARCHIVED||!isActive(x.ownerAccountId(),"TEACHER"),
                x.createdAt(),x.updatedAt());
    }
    private Classroom copy(Classroom old,String name,String description,UUID owner,ClassroomStatus status,
            JoinMode mode,boolean joiningOpen,String code,boolean locked,Instant expiry) {
        return new Classroom(old.id(),name,description,owner,status,mode,joiningOpen,code,locked,expiry,
                old.createdAt(),clock.instant());
    }
    private boolean canRead(Classroom classroom,AccountRepository.Account actor) {
        if(actor.role().equals("ADMIN") || classroom.ownerAccountId().equals(actor.id())) return true;
        return actor.role().equals("STUDENT") && repository.findMembership(classroom.id(),actor.id())
                .map(x -> x.status()==MembershipStatus.ACTIVE).orElse(false);
    }
    private AccountRepository.Account requireActive(UUID id,String requiredRole) {
        var account=accounts.accounts().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow(this::forbidden);
        if(!account.status().equals("ACTIVE") || (requiredRole!=null && !account.role().equals(requiredRole))
                || accounts.restrictions().stream().anyMatch(r -> r.accountId().equals(id) && r.active(clock.instant())))
            throw forbidden();
        return account;
    }
    private boolean isActive(UUID id,String role) {
        try { requireActive(id,role); return true; } catch(ApiException ex) { return false; }
    }
    private Classroom requireClassroom(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.CLASSROOM_NOT_FOUND));
    }
    private Invitation requirePendingInvitation(UUID invitationId,UUID classroomId) {
        return repository.findInvitation(invitationId).filter(x -> x.classroomId().equals(classroomId)
                && x.status()==InvitationStatus.PENDING).orElseThrow(this::expiredInvitation);
    }
    private String requiredText(String value,int limit,String field) {
        if(value==null || value.isBlank()) throw invalid(field+" is required");
        return checked(value.strip(),limit,field);
    }
    private String optionalText(String value,int limit,String field) {
        if(value==null || value.isBlank()) return null;
        return checked(value.strip(),limit,field);
    }
    private String checked(String value,int limit,String field) {
        if(value.codePointCount(0,value.length())>limit) throw invalid(field+" exceeds maximum length");
        return value;
    }
    private ApiException invalid(String message) { return new ApiException(ErrorCode.VALIDATION_FAILED,message); }
    private ApiException conflict(String message) { return new ApiException(ErrorCode.CLASSROOM_CONFLICT,message); }
    private ApiException joinDenied(String message) { return new ApiException(ErrorCode.CLASSROOM_JOIN_DENIED,message); }
    private ApiException forbidden() { return new ApiException(ErrorCode.FORBIDDEN_OPERATION,"Classroom access denied"); }
    private ApiException expiredInvitation() { return new ApiException(ErrorCode.INVITATION_EXPIRED,"Classroom invitation is no longer valid"); }
}
