package com.algolearner.platform.classroom.mock;

import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.ClassroomTypes.*;
import com.algolearner.platform.profile.mock.MockData;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("mock")
public class InMemoryClassroomRepository implements ClassroomRepository {
    private final MockData source;
    private final Map<UUID,Classroom> classrooms = new ConcurrentHashMap<>();
    private final Map<String,UUID> codes = new ConcurrentHashMap<>();
    private final Map<String,Membership> memberships = new ConcurrentHashMap<>();
    private final Map<UUID,JoinRequest> requests = new ConcurrentHashMap<>();
    private final Map<UUID,Invitation> invitations = new ConcurrentHashMap<>();

    public InMemoryClassroomRepository(MockData source) {
        this.source=source;
        reset();
    }

    public synchronized void reset() {
        classrooms.clear(); codes.clear(); memberships.clear(); requests.clear(); invitations.clear();
        int number=1;
        for (var item:source.classes) {
            var view=item.value();
            String code="DEMO"+String.format("%04d",number++);
            Classroom classroom=new Classroom(view.classroomId(),view.name(),"Mock classroom",item.owner(),
                    ClassroomStatus.valueOf(item.status()),JoinMode.AUTO_JOIN,true,code,false,null,
                    item.createdAt(),item.createdAt());
            classrooms.put(classroom.id(),classroom); codes.put(code,classroom.id());
        }
        Instant now=source.accounts.stream().map(a -> a.createdAt()).max(Comparator.naturalOrder()).orElse(Instant.EPOCH);
        for (var item:source.memberships) {
            Membership membership=new Membership(MockData.id("membership:"+item.classroom()+":"+item.student()),
                    item.classroom(),item.student(),MembershipStatus.valueOf(item.status()),now,null,now,now);
            memberships.put(key(item.classroom(),item.student()),membership);
        }
    }

    @Override public Optional<Classroom> findById(UUID id) { return Optional.ofNullable(classrooms.get(id)); }
    @Override public Optional<Classroom> findByCode(String code) {
        UUID id=codes.get(code); return id==null?Optional.empty():findById(id);
    }
    @Override public List<Classroom> findAll() { return classrooms.values().stream().toList(); }
    @Override public synchronized Classroom save(Classroom classroom) {
        Classroom old=classrooms.put(classroom.id(),classroom);
        if(old!=null && !old.classCode().equals(classroom.classCode())) codes.remove(old.classCode());
        UUID conflict=codes.putIfAbsent(classroom.classCode(),classroom.id());
        if(conflict!=null && !conflict.equals(classroom.id())) {
            if(old==null) classrooms.remove(classroom.id()); else classrooms.put(old.id(),old);
            throw new IllegalStateException("Class code already exists");
        }
        codes.put(classroom.classCode(),classroom.id());
        return classroom;
    }
    @Override public synchronized void delete(UUID id) {
        Classroom removed=classrooms.remove(id); if(removed!=null) codes.remove(removed.classCode());
    }
    @Override public Optional<Membership> findMembership(UUID classroomId,UUID studentAccountId) {
        return Optional.ofNullable(memberships.get(key(classroomId,studentAccountId)));
    }
    @Override public List<Membership> findMemberships(UUID classroomId) {
        return memberships.values().stream().filter(x -> x.classroomId().equals(classroomId)).toList();
    }
    @Override public Membership saveMembership(Membership membership) {
        memberships.put(key(membership.classroomId(),membership.studentAccountId()),membership); return membership;
    }
    @Override public Optional<JoinRequest> findJoinRequest(UUID id) { return Optional.ofNullable(requests.get(id)); }
    @Override public Optional<JoinRequest> findPendingJoinRequest(UUID classroomId,UUID studentAccountId) {
        return requests.values().stream().filter(x -> x.classroomId().equals(classroomId)
                && x.studentAccountId().equals(studentAccountId) && x.status()==JoinRequestStatus.PENDING).findFirst();
    }
    @Override public List<JoinRequest> findJoinRequests(UUID classroomId) {
        return requests.values().stream().filter(x -> x.classroomId().equals(classroomId)).toList();
    }
    @Override public JoinRequest saveJoinRequest(JoinRequest request) { requests.put(request.id(),request); return request; }
    @Override public Optional<Invitation> findInvitation(UUID id) { return Optional.ofNullable(invitations.get(id)); }
    @Override public Optional<Invitation> findPendingInvitation(UUID classroomId,UUID studentAccountId) {
        return invitations.values().stream().filter(x -> x.classroomId().equals(classroomId)
                && x.inviteeAccountId().equals(studentAccountId) && x.status()==InvitationStatus.PENDING).findFirst();
    }
    @Override public List<Invitation> findInvitations(UUID classroomId) {
        return invitations.values().stream().filter(x -> x.classroomId().equals(classroomId)).toList();
    }
    @Override public Invitation saveInvitation(Invitation invitation) { invitations.put(invitation.id(),invitation); return invitation; }
    @Override public boolean hasPreservableData(UUID classroomId) {
        return memberships.values().stream().anyMatch(x -> x.classroomId().equals(classroomId))
                || requests.values().stream().anyMatch(x -> x.classroomId().equals(classroomId))
                || invitations.values().stream().anyMatch(x -> x.classroomId().equals(classroomId))
                || source.assignments.stream().anyMatch(x -> x.value().classroomId().equals(classroomId))
                || source.quizzes.stream().anyMatch(x -> x.value().classroomId().equals(classroomId))
                || source.works.stream().anyMatch(x -> x.value().classroomId().equals(classroomId))
                || source.judge.stream().anyMatch(x -> classroomId.equals(x.classroom()));
    }
    private String key(UUID classroomId,UUID studentId) { return classroomId+":"+studentId; }
}
