package com.algolearner.platform.lesson;

import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.*;

@Service @Profile("!mock") @RequiredArgsConstructor
@Transactional
public class LessonProgressService {
    private final LessonRepository lessons;
    private final LessonLinkedItemRepository links;
    private final LessonCompletionRepository completions;
    private final AccountRepository accounts;
    private final List<LessonProgressProvider> providers;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public void configure(UUID classroomId, UUID lessonId, LessonCompletionRule rule) {
        var lesson = locked(classroomId, lessonId);
        if (lesson.getStatus() == LessonStatus.ARCHIVED || rule == null || rule.operator() == null
                || rule.conditions() == null || rule.conditions().isEmpty()) invalid();
        for (var condition : rule.conditions()) {
            if (condition == null || condition.itemType() == null || condition.itemType() == LinkedItemType.PRACTICE
                    || condition.itemId() == null || condition.requirement() == null
                    || !links.existsByLessonIdAndItemTypeAndItemId(lessonId, condition.itemType(), condition.itemId())) invalid();
            if (condition.requirement() == LessonCompletionRule.Requirement.SCORE_AT_LEAST
                    && (condition.scorePercent() == null || condition.scorePercent().signum() < 0
                    || condition.scorePercent().compareTo(java.math.BigDecimal.valueOf(100)) > 0)) invalid();
            var provider = provider(condition.itemType());
            if (provider == null || !provider.existsInClassroom(condition.itemId(), classroomId)) invalid();
        }
        var json = json(rule);
        var previous = currentRule(lesson);
        if (previous != null && json.equals(previous) && lesson.getCompletionMode() == CompletionMode.AUTO_ITEMS) return;
        lesson.setRuleVersion(lesson.getRuleVersion() + 1);
        lesson.setCompletionMode(CompletionMode.AUTO_ITEMS);
        entityManager.flush();
        var now = Timestamp.from(clock.instant());
        jdbc.update("insert into tbl_lesson_rule_history(lesson_id,rule_version,rule_json,created_at,updated_at) values(?,?,?,?,?)",
                lessonId, lesson.getRuleVersion(), json, now, now);
        jdbc.queryForList("select student_id from tbl_classroom_membership where classroom_id=? and status='ACTIVE'", UUID.class, classroomId)
                .forEach(student -> recompute(lesson, student));
    }

    @Transactional(readOnly = true)
    public LessonDtos.CompletionRuleView rule(UUID lessonId) {
        var lesson = lessons.findById(lessonId).orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));
        var stored = currentRule(lesson);
        try { return new LessonDtos.CompletionRuleView(lesson.getRuleVersion(), stored == null ? null : mapper.readValue(stored, LessonCompletionRule.class)); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Stored lesson rule is invalid",error); }
    }

    public void recordManual(LessonEntity lesson, UUID studentId) {
        var now = Timestamp.from(clock.instant());
        jdbc.update("""
                insert into tbl_lesson_completion_history(lesson_id,student_id,rule_version,source_fingerprint,is_completed,created_at,updated_at)
                values(?,?,?,?,true,?,?) on conflict(lesson_id,student_id,rule_version,source_fingerprint) do nothing
                """, lesson.getId(),studentId,lesson.getRuleVersion(),hash("MANUAL_COMPLETED"),now,now);
    }

    public boolean ruleReferences(UUID lessonId, LinkedItemType type, UUID itemId) {
        var lesson = lessons.findById(lessonId).orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));
        var stored = currentRule(lesson);
        if (stored == null) return false;
        try { return mapper.readValue(stored, LessonCompletionRule.class).conditions().stream()
                .anyMatch(condition -> condition.itemType() == type && condition.itemId().equals(itemId)); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Stored lesson rule is invalid",error); }
    }

    public void recomputeForItem(UUID classroomId, LinkedItemType type, UUID itemId, UUID studentId) {
        links.findByItemTypeAndItemId(type, itemId).stream()
                .filter(link -> link.getLesson().getClassroom().getId().equals(classroomId))
                .map(link -> link.getLesson().getId()).distinct().sorted()
                .forEach(id -> recompute(locked(classroomId, id), studentId));
    }

    private void recompute(LessonEntity lesson, UUID studentId) {
        if (lesson.getCompletionMode() != CompletionMode.AUTO_ITEMS) return;
        var ruleJson = currentRule(lesson);
        if (ruleJson == null) return;
        LessonCompletionRule rule;
        try { rule = mapper.readValue(ruleJson, LessonCompletionRule.class); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Stored lesson rule is invalid", error); }
        boolean result = rule.operator() == LessonCompletionRule.Operator.ALL;
        var fingerprint = new StringBuilder();
        for (var condition : rule.conditions()) {
            var provider = provider(condition.itemType());
            var source = provider == null || !provider.existsInClassroom(condition.itemId(), lesson.getClassroom().getId()) ? null
                    : provider.progress(condition.itemId(), lesson.getClassroom().getId(), studentId);
            boolean met = source != null && (condition.requirement() == LessonCompletionRule.Requirement.SUBMITTED ? source.submitted()
                    : source.publishedFinalEvaluation() && source.scorePercent() != null
                    && source.scorePercent().compareTo(condition.scorePercent()) >= 0);
            fingerprint.append(condition.itemType()).append(':').append(condition.itemId()).append(':').append(source).append(';');
            result = rule.operator() == LessonCompletionRule.Operator.ALL ? result && met : result || met;
        }
        var now = Timestamp.from(clock.instant());
        jdbc.update("""
                insert into tbl_lesson_completion_history(lesson_id,student_id,rule_version,source_fingerprint,is_completed,created_at,updated_at)
                values(?,?,?,?,?,?,?) on conflict(lesson_id,student_id,rule_version,source_fingerprint) do nothing
                """, lesson.getId(), studentId, lesson.getRuleVersion(), hash(fingerprint.toString()), result, now, now);
        var completion = completions.findByLessonIdAndStudentId(lesson.getId(), studentId).orElseGet(() -> {
            var value = new LessonCompletionEntity(); value.setId(UUID.randomUUID()); value.setLesson(lesson);
            value.setStudent(accounts.getReferenceById(studentId)); return value;
        });
        if (completion.isCompleted() != result) completion.setCompletedAt(result ? clock.instant() : null);
        completion.setCompleted(result); completions.save(completion);
    }
    private LessonEntity locked(UUID classroomId, UUID lessonId) {
        var lesson = lessons.findByIdAndClassroomId(lessonId,classroomId).orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));
        entityManager.flush(); entityManager.refresh(lesson, LockModeType.PESSIMISTIC_WRITE); return lesson;
    }
    private String currentRule(LessonEntity lesson) {
        return jdbc.query("select rule_json from tbl_lesson_rule_history where lesson_id=? and rule_version=?", (rs,n) -> rs.getString(1), lesson.getId(), lesson.getRuleVersion()).stream().findFirst().orElse(null);
    }
    private LessonProgressProvider provider(LinkedItemType type) { return providers.stream().filter(value -> value.itemType()==type).findFirst().orElse(null); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch(JsonProcessingException error) { throw new IllegalArgumentException("Invalid lesson rule",error); } }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static void invalid() { throw new ApiException(ErrorCode.VALIDATION_FAILED,"Invalid lesson completion rule"); }
}
