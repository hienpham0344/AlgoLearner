package com.algolearner.platform.lesson;

import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tbl_lesson")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonEntity extends BaseAuditableEntity {

    @Version @Column(nullable = false)
    private long version;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private ClassroomEntity classroom;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "code_example", columnDefinition = "TEXT")
    private String codeExample;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "external_links", columnDefinition = "JSONB")
    @Builder.Default
    private String externalLinks = "[]";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private LessonStatus status = LessonStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "archived_from_status", length = 30)
    private LessonStatus archivedFromStatus;

    @Column(name = "rule_version", nullable = false)
    private long ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_mode", nullable = false, length = 30)
    @Builder.Default
    private CompletionMode completionMode = CompletionMode.MANUAL;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LessonMaterialEntity> materials = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LessonLinkedItemEntity> linkedItems = new ArrayList<>();
}
