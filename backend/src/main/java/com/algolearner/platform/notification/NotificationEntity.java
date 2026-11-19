package com.algolearner.platform.notification;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.classroom.ClassroomInvitationEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tbl_notification")
@Getter @Setter @NoArgsConstructor
public class NotificationEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipient_id", nullable = false)
    private AccountEntity recipient;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "classroom_invitation_id", nullable = true)
    private ClassroomInvitationEntity invitation;
    @Column(name = "announcement_id")
    private UUID announcementId;
    @Column(name = "assignment_id")
    private UUID assignmentId;
    @jakarta.validation.constraints.Size(max = 255)
    @Column(name = "title", length = 255)
    private String title;
    @Column(name = "message")
    private String message;
    @Column(name = "link_url")
    private String linkUrl;
    @Column(nullable = false, length = 40) private String type;
    @Column(name = "read_at") private Instant readAt;
}
