package com.algolearner.platform.practice;

import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tbl_classroom_practice")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class ClassroomPracticeEntity extends BaseAuditableEntity {
    @Id @ToString.Include @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "classroom_id", nullable = false) private ClassroomEntity classroom;
    /** Plain column until module 4.5 adds tbl_lesson; availability is resolved through {@link LessonStatusPort}. */
    @Column(name = "lesson_id") private UUID lessonId;
    @ToString.Include @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(name = "problem_type", nullable = false, length = 20)
    private PracticeProblemType problemType = PracticeProblemType.STANDARD_IO;
    @Convert(converter = LanguageSetConverter.class)
    @Column(name = "allowed_languages", nullable = false, length = 40)
    private Set<PracticeLanguage> allowedLanguages = EnumSet.noneOf(PracticeLanguage.class);
    @Convert(converter = StarterCodeConverter.class)
    @Column(name = "starter_code", columnDefinition = "TEXT")
    private Map<PracticeLanguage, String> starterCode = new EnumMap<>(PracticeLanguage.class);
    @Column(name = "time_limit_ms", nullable = false) private int timeLimitMs = 2000;
    @Column(name = "memory_limit_mb", nullable = false) private int memoryLimitMb = 256;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PracticeStatus status;
    @Column(name = "testcase_version", nullable = false) private int testcaseVersion = 1;

    @Override public boolean equals(Object o) { return o instanceof ClassroomPracticeEntity other && id != null && id.equals(other.id); }
    @Override public int hashCode() { return getClass().hashCode(); }
}
