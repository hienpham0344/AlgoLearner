package com.algolearner.platform.storage;

import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.common.security.AccountAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FileRecordService {
    private final StoredFileRepository files;
    private final ClassroomRepository classrooms;
    private final ClassroomMembershipRepository memberships;
    private final ClassroomService classroomService;
    private final AccountAccess accounts;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final EntityManager entityManager;

    public record ProcessingTicket(UUID id, UUID uploaderId, String objectKey, long size, String sha256, UUID token, boolean objectUploaded) {
        public ProcessingTicket(UUID id, UUID uploaderId, String objectKey, long size, String sha256, UUID token) {
            this(id, uploaderId, objectKey, size, sha256, token, true);
        }
    }

    public void authorizeUpload(UUID actor, UUID classroom, FilePurpose purpose) {
        authorizeWrite(actor, classroom, purpose, false);
    }

    @Transactional(readOnly = true)
    public FilePolicyService.Policy uploadPolicy() {
        var rows = jdbc.query(
                "SELECT max_bytes,allowed_kinds FROM tbl_file_policy WHERE id='00000000-0000-0000-0000-000000000001'",
                (row, index) -> new FilePolicyService.Policy(
                        row.getLong("max_bytes"),
                        parseAllowedKinds(row.getString("allowed_kinds"))));
        return rows.isEmpty() ? new FilePolicyService.Policy(0L, java.util.Set.of()) : rows.getFirst();
    }

    private static java.util.Set<FileKind> parseAllowedKinds(String raw) {
        if (raw == null || raw.isBlank()) return java.util.Set.of();
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(FileKind::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Transactional
    public StoredFileEntity begin(UUID actor, UUID classroom, FilePurpose purpose, ValidatedFile file, String sha256) {
        authorizeWrite(actor, classroom, purpose, true);
        var policy = uploadPolicy();
        if (file.bytes().length > policy.maxBytes() || !policy.allowedKinds().contains(file.kind())) {
            throw new StorageException("FILE_POLICY_REJECTED", HttpStatus.UNPROCESSABLE_ENTITY, "File does not satisfy upload policy");
        }
        var record = new StoredFileEntity();
        record.setId(UUID.randomUUID());
        record.setUploaderId(actor);
        record.setClassroomId(classroom);
        record.setObjectKey(actor + "/" + record.getId());
        record.setName(file.name());
        record.setMediaType(file.mediaType());
        record.setKind(file.kind());
        record.setSize(file.bytes().length);
        record.setSha256(sha256);
        record.setPurpose(purpose);
        record.setState(FileState.PENDING);
        return files.saveAndFlush(record);
    }

    @Transactional
    public void uploaded(UUID id) {
        var record = locked(id);
        if (record.getState() != FileState.PENDING) throw conflict();
        record.setObjectUploaded(true);
    }

    @Transactional
    public FileMetadata ready(UUID actor, UUID id) {
        // Lock classroom first, then file, consistently with attachment binding and cleanup.
        var snapshot = require(id);
        authorizeWrite(actor, snapshot.getClassroomId(), snapshot.getPurpose(), true);
        var record = locked(id);
        if (!record.getUploaderId().equals(actor) || !record.isObjectUploaded()) throw conflict();
        if (record.getState() == FileState.READY) return record.metadata();
        if (record.getState() != FileState.PENDING) throw conflict();
        record.setState(FileState.READY);
        record.setProcessingToken(null);
        record.setProcessingUntil(null);
        return record.metadata();
    }

    @Transactional
    public void rejected(UUID id) {
        var record = locked(id);
        if (record.getState() != FileState.PENDING) throw conflict();
        record.setState(FileState.REJECTED);
    }

    public FileMetadata read(UUID actor, UUID id) {
        var file = require(id);
        authorizeRead(actor, file);
        return file.metadata();
    }

    public StoredFileEntity downloadable(UUID actor, UUID id) {
        var file = require(id);
        authorizeRead(actor, file);
        if (file.getState() != FileState.READY) throw conflict();
        return file;
    }

    @Transactional
    public FileMetadata requireReady(UUID actor, UUID classroom, UUID id, FilePurpose purpose) {
        authorizeWrite(actor, classroom, purpose, true);
        var file = locked(id);
        if (!file.getClassroomId().equals(classroom) || file.getPurpose() != purpose) throw denied();
        if (!file.getUploaderId().equals(actor) && purpose == FilePurpose.ESSAY_SUBMISSION) throw denied();
        if (file.getState() != FileState.READY) throw conflict();
        return file.metadata();
    }

    public List<UUID> recoveryCandidates() {
        Instant now = clock.instant();
        return files.recoverable(FileState.PENDING, now.minusSeconds(60), now, PageRequest.of(0, 25));
    }

    @Transactional
    public ProcessingTicket claimRecovery(UUID id) {
        var file = locked(id);
        Instant now = clock.instant();
        if (file.getState() != FileState.PENDING || !file.isObjectUploaded() || file.getScanAttempts() >= 3
                || file.getUpdatedAt().isAfter(now.minusSeconds(60))
                || file.getProcessingUntil() != null && file.getProcessingUntil().isAfter(now)) return null;
        file.setProcessingToken(UUID.randomUUID());
        file.setProcessingUntil(now.plusSeconds(120));
        file.setScanAttempts(file.getScanAttempts() + 1);
        return ticket(file);
    }

    @Transactional
    public void finishRecovery(ProcessingTicket ticket, boolean clean, boolean infected) {
        var initial = require(ticket.id());
        // An account losing write access cannot finalize an upload on a later retry.
        if (clean) authorizeWrite(initial.getUploaderId(), initial.getClassroomId(), initial.getPurpose(), true);
        var file = locked(ticket.id());
        if (!ticket.token().equals(file.getProcessingToken()) || file.getState() != FileState.PENDING) return;
        if (clean) file.setState(FileState.READY);
        else if (infected) file.setState(FileState.REJECTED);
        file.setProcessingToken(null);
        file.setProcessingUntil(null);
    }

    public List<UUID> cleanupCandidates() {
        return jdbc.queryForList("""
                SELECT file.id FROM tbl_stored_file file
                WHERE file.updated_at < ?
                    AND (file.processing_until IS NULL OR file.processing_until <= ?)
                    AND NOT EXISTS(SELECT 1 FROM tbl_lesson_material WHERE stored_file_id=file.id)
                    AND NOT EXISTS(SELECT 1 FROM tbl_assignment_answer WHERE stored_file_id=file.id)
                ORDER BY file.updated_at,file.id LIMIT 25
                """, UUID.class, java.sql.Timestamp.from(clock.instant().minusSeconds(86400)), java.sql.Timestamp.from(clock.instant()));
    }

    @Transactional
    public ProcessingTicket claimDeletion(UUID id) {
        var initial = require(id);
        classroomService.lockReadable(initial.getClassroomId());
        var file = locked(id);
        Instant now = clock.instant();
        if (file.getUpdatedAt().isAfter(now.minusSeconds(86400))
                || file.getProcessingUntil() != null && file.getProcessingUntil().isAfter(now)) return null;
        boolean referenced = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM tbl_lesson_material WHERE stored_file_id=?)
                    OR EXISTS(SELECT 1 FROM tbl_assignment_answer WHERE stored_file_id=?)
                """, Boolean.class, id, id));
        if (referenced) return null;
        file.setState(FileState.DELETING);
        file.setProcessingToken(UUID.randomUUID());
        file.setProcessingUntil(now.plusSeconds(120));
        return ticket(file);
    }

    @Transactional
    public ProcessingTicket claimImmediateDeletion(UUID actor, UUID fileId) {
        accounts.activeRole(actor);
        var initial = require(fileId);
        classroomService.lockReadable(initial.getClassroomId());
        var file = locked(fileId);
        if (!file.getUploaderId().equals(actor)) throw denied();
        Instant now = clock.instant();
        if (file.getState() == FileState.DELETING
                || (file.getProcessingUntil() != null && file.getProcessingUntil().isAfter(now))) {
            throw conflict();
        }
        boolean referenced = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM tbl_lesson_material WHERE stored_file_id=?)
                    OR EXISTS(SELECT 1 FROM tbl_assignment_answer WHERE stored_file_id=?)
                """, Boolean.class, fileId, fileId));
        if (referenced) throw conflict();
        file.setState(FileState.DELETING);
        file.setProcessingToken(UUID.randomUUID());
        file.setProcessingUntil(now.plusSeconds(120));
        return ticket(file);
    }

    @Transactional
    public void finishDeletion(ProcessingTicket ticket) {
        var file = locked(ticket.id());
        if (file.getState() == FileState.DELETING && ticket.token().equals(file.getProcessingToken())) files.delete(file);
    }

    private ProcessingTicket ticket(StoredFileEntity file) {
        return new ProcessingTicket(file.getId(), file.getUploaderId(), file.getObjectKey(), file.getSize(),
                file.getSha256(), file.getProcessingToken(), file.isObjectUploaded());
    }

    private void authorizeWrite(UUID actor, UUID classroomId, FilePurpose purpose, boolean lock) {
        String role = accounts.activeRole(actor);
        var classroom = lock ? classroomService.lockReadable(classroomId) : classroom(classroomId);
        if (classroom.getStatus() != ClassroomStatus.ACTIVE || classroom.getOwner().getStatus() != AccountStatus.ACTIVE) throw denied();
        boolean allowed = purpose == FilePurpose.LESSON_MATERIAL
                ? role.equals("TEACHER") && classroom.getOwner().getId().equals(actor)
                : purpose == FilePurpose.ESSAY_SUBMISSION && role.equals("STUDENT") && activeMember(actor, classroomId);
        if (!allowed) throw denied();
    }

    private void authorizeRead(UUID actor, StoredFileEntity file) {
        String role = accounts.activeRole(actor);
        var classroom = classroom(file.getClassroomId());
        if (role.equals("TEACHER") && classroom.getOwner().getId().equals(actor)) return;
        if (!role.equals("STUDENT") || !activeMember(actor, file.getClassroomId())) throw denied();
        if (file.getPurpose() == FilePurpose.ESSAY_SUBMISSION && file.getUploaderId().equals(actor)) return;
        if (file.getPurpose() == FilePurpose.LESSON_MATERIAL && file.getState() == FileState.READY
                && Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM tbl_lesson_material m JOIN tbl_lesson l ON l.id=m.lesson_id
                        WHERE m.stored_file_id=? AND m.material_state='READY' AND l.status='PUBLISHED' AND l.classroom_id=?)
                        """, Boolean.class, file.getId(), file.getClassroomId()))) return;
        throw denied();
    }

    private boolean activeMember(UUID actor, UUID classroom) {
        return memberships.findByClassroomIdAndStudentId(classroom, actor)
                .map(member -> member.getStatus() == MembershipStatus.ACTIVE).orElse(false);
    }

    private ClassroomEntity classroom(UUID id) {
        return classrooms.findById(id).orElseThrow(this::denied);
    }

    private StoredFileEntity require(UUID id) {
        return files.findById(id).orElseThrow(() -> new StorageException("FILE_NOT_FOUND", HttpStatus.NOT_FOUND, "File not found"));
    }

    private StoredFileEntity locked(UUID id) {
        var file = require(id);
        entityManager.refresh(file, LockModeType.PESSIMISTIC_WRITE);
        return file;
    }

    private StorageException denied() {
        return new StorageException("FILE_ACCESS_DENIED", HttpStatus.FORBIDDEN, "File access denied");
    }

    private StorageException conflict() {
        return new StorageException("FILE_NOT_READY", HttpStatus.CONFLICT, "File is not ready");
    }
}
