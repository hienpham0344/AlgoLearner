package com.algolearner.platform.classroom;
import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import java.util.UUID;
@Entity @Table(name="tbl_classroom_join_request") @Getter @Setter @NoArgsConstructor
public class ClassroomJoinRequestEntity extends BaseAuditableEntity {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="classroom_id",nullable=false) private ClassroomEntity classroom;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="student_id",nullable=false) private AccountEntity student;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private JoinRequestStatus status;
}
