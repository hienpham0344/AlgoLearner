package com.algolearner.platform.quiz;

import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "tbl_quiz")
@Getter @Setter @NoArgsConstructor
public class QuizEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "classroom_id", nullable = false)
    private ClassroomEntity classroom;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "text") private String configuration;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "grades_published", nullable = false) private boolean gradesPublished;
}
