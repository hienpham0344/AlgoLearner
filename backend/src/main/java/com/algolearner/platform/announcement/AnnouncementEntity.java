package com.algolearner.platform.announcement;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tbl_announcement")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private ClassroomEntity classroom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private AccountEntity author;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private boolean isPinned = false;

    @Column(name = "pinned_at")
    private Instant pinnedAt;
}
