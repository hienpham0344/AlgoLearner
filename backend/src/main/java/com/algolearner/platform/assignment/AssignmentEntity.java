package com.algolearner.platform.assignment;

import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tbl_assignment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentEntity extends BaseAuditableEntity {

    @Column(name = "published_revision_id")
    private UUID publishedRevisionId;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private ClassroomEntity classroom;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    @Builder.Default
    private AssignmentType type = AssignmentType.GRADED;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "deadline")
    private Instant deadline;

    @Column(name = "allow_late_submission", nullable = false)
    @Builder.Default
    private boolean allowLateSubmission = false;

    @Column(name = "late_penalty_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal latePenaltyPercent = BigDecimal.ZERO;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_expired_action", nullable = false, length = 30)
    @Builder.Default
    private TimeExpiredAction timeExpiredAction = TimeExpiredAction.AUTO_SUBMIT;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_policy", nullable = false, length = 30)
    @Builder.Default
    private ScoringPolicy scoringPolicy = ScoringPolicy.HIGHEST;

    @Column(name = "shuffle_questions", nullable = false)
    @Builder.Default
    private boolean shuffleQuestions = false;

    @Column(name = "shuffle_answers", nullable = false)
    @Builder.Default
    private boolean shuffleAnswers = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_publish_mode", nullable = false, length = 30)
    @Builder.Default
    private GradePublishMode gradePublishMode = GradePublishMode.MANUAL;

    @Column(name = "is_grade_published", nullable = false)
    @Builder.Default
    private boolean isGradePublished = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "view_answers_mode", nullable = false, length = 30)
    @Builder.Default
    private ViewAnswersMode viewAnswersMode = ViewAnswersMode.NEVER;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_feedback_level", nullable = false, length = 30)
    @Builder.Default
    private CodeFeedbackLevel codeFeedbackLevel = CodeFeedbackLevel.DETAILED;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AssignmentQuestionEntity> questions = new ArrayList<>();

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AssignmentAttemptEntity> attempts = new ArrayList<>();
}
