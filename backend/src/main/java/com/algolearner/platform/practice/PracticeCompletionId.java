package com.algolearner.platform.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class PracticeCompletionId implements Serializable {
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "practice_id", nullable = false) private UUID practiceId;

    public PracticeCompletionId(UUID studentId, UUID practiceId) {
        this.studentId = studentId;
        this.practiceId = practiceId;
    }

    @Override public boolean equals(Object o) {
        return o instanceof PracticeCompletionId other && Objects.equals(studentId, other.studentId) && Objects.equals(practiceId, other.practiceId);
    }
    @Override public int hashCode() { return Objects.hash(studentId, practiceId); }
}
