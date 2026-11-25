package com.algolearner.platform.storage;

import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StoredFileRepository extends JpaRepository<StoredFileEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from StoredFileEntity f where f.id = :id")
    Optional<StoredFileEntity> findLockedById(UUID id);

    @Query("""
            select f.id from StoredFileEntity f
            where f.state = :state and f.updatedAt < :before
                and f.objectUploaded = true and f.scanAttempts < 3
                and (f.processingUntil is null or f.processingUntil <= :now)
            order by f.updatedAt, f.id
            """)
    List<UUID> recoverable(FileState state, Instant before, Instant now, Pageable pageable);
}
