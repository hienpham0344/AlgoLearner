package com.algolearner.platform.practice;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.util.UUID;

@Entity
@Table(name = "tbl_practice_testcase")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class PracticeTestcaseEntity extends BaseAuditableEntity {
    @Id @ToString.Include @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "practice_id", nullable = false) private ClassroomPracticeEntity practice;
    @Column(nullable = false) private int version;
    @Column(nullable = false, columnDefinition = "TEXT") private String input;
    @Column(name = "expected_output", nullable = false, columnDefinition = "TEXT") private String expectedOutput;
    @Column(name = "is_sample", nullable = false) private boolean sample;
    @Column(name = "order_index", nullable = false) private int orderIndex;

    @Override public boolean equals(Object o) { return o instanceof PracticeTestcaseEntity other && id != null && id.equals(other.id); }
    @Override public int hashCode() { return getClass().hashCode(); }
}
