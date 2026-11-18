package com.algolearner.platform.lesson;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tbl_lesson_material")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonMaterialEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private LessonEntity lesson;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_url", nullable = false, length = 512)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 50)
    private MaterialFileType fileType;

    @Column(name = "stored_file_id")
    private UUID storedFileId;

    @Column(name = "material_state", nullable = false, length = 30)
    @Builder.Default
    private String materialState = "NEEDS_REUPLOAD";

    @Column(name = "file_size", nullable = false)
    private long fileSize;
}
