package com.algolearner.platform.classroom;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static com.algolearner.platform.classroom.ClassroomTypes.*;

/** Persistence port. Implementations must make each save atomic. */
public interface ClassroomRepository {
    Optional<Classroom> findById(UUID id);
    Optional<Classroom> findByCode(String code);
    List<Classroom> findAll();
    Classroom save(Classroom classroom);
    void delete(UUID id);

    Optional<Membership> findMembership(UUID classroomId, UUID studentAccountId);
    List<Membership> findMemberships(UUID classroomId);
    Membership saveMembership(Membership membership);

    Optional<JoinRequest> findJoinRequest(UUID id);
    Optional<JoinRequest> findPendingJoinRequest(UUID classroomId, UUID studentAccountId);
    List<JoinRequest> findJoinRequests(UUID classroomId);
    JoinRequest saveJoinRequest(JoinRequest request);

    Optional<Invitation> findInvitation(UUID id);
    Optional<Invitation> findPendingInvitation(UUID classroomId, UUID studentAccountId);
    List<Invitation> findInvitations(UUID classroomId);
    Invitation saveInvitation(Invitation invitation);

    boolean hasPreservableData(UUID classroomId);
}
