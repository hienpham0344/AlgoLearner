package com.algolearner.platform.quiz;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomService;
import com.algolearner.platform.classroom.ClassroomStatus;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.lesson.LessonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QuizServiceUnitTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ClassroomService classrooms = mock(ClassroomService.class);
    private final ClassroomMembershipRepository memberships = mock(ClassroomMembershipRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final LessonService lessons = mock(LessonService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);
    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final QuizService service = new QuizService(jdbc, classrooms, memberships, accounts, lessons, mapper, clock, audit, events);

    @Test
    void attemptViewExposesStudentId() {
        var attemptId = UUID.randomUUID();
        var quizId = UUID.randomUUID();
        var studentId = UUID.randomUUID();
        var view = new QuizDtos.AttemptView(
                attemptId, quizId, studentId, "IN_PROGRESS",
                clock.instant(), null, null, null, false,
                List.of(), List.of(), clock.instant(), false);
        assertThat(view.studentId()).isEqualTo(studentId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownerCanDeleteUnusedDraftQuiz() throws Exception {
        var owner = teacher();
        var classroom = classroom(owner);
        var quizId = UUID.randomUUID();

        when(accounts.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(classrooms.lockReadable(classroom.getId())).thenReturn(classroom);
        when(jdbc.query(contains("from tbl_quiz where id=?"), any(RowMapper.class), eq(quizId), eq(classroom.getId())))
                .thenAnswer(inv -> {
                    RowMapper<Object> rm = inv.getArgument(1);
                    ResultSet rs = quizResultSet(quizId, classroom.getId(), "DRAFT");
                    return List.of(rm.mapRow(rs, 0));
                });
        when(jdbc.queryForObject(contains("count(*) from tbl_quiz_attempt"), eq(Integer.class), eq(quizId)))
                .thenReturn(0);

        service.delete(owner.getId(), classroom.getId(), quizId);

        verify(jdbc).update(contains("delete from tbl_lesson_linked_item"), eq(quizId));
        verify(jdbc).update(contains("delete from tbl_quiz where id=?"), eq(quizId), eq(classroom.getId()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletingPublishedQuizThrowsConflict() throws Exception {
        var owner = teacher();
        var classroom = classroom(owner);
        var quizId = UUID.randomUUID();

        when(accounts.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(classrooms.lockReadable(classroom.getId())).thenReturn(classroom);
        when(jdbc.query(contains("from tbl_quiz where id=?"), any(RowMapper.class), eq(quizId), eq(classroom.getId())))
                .thenAnswer(inv -> {
                    RowMapper<Object> rm = inv.getArgument(1);
                    ResultSet rs = quizResultSet(quizId, classroom.getId(), "PUBLISHED");
                    return List.of(rm.mapRow(rs, 0));
                });

        assertThatThrownBy(() -> service.delete(owner.getId(), classroom.getId(), quizId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo("QUIZ_NOT_DRAFT");
        verify(jdbc, never()).update(contains("delete from tbl_quiz"), any(), any());
    }

    private AccountEntity teacher() {
        var a = new AccountEntity();
        a.setId(UUID.randomUUID());
        a.setRole(AccountRole.TEACHER);
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    private ClassroomEntity classroom(AccountEntity owner) {
        var c = new ClassroomEntity();
        c.setId(UUID.randomUUID());
        c.setOwner(owner);
        c.setStatus(ClassroomStatus.ACTIVE);
        return c;
    }

    private ResultSet quizResultSet(UUID quizId, UUID classroomId, String status) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(quizId);
        when(rs.getObject("classroom_id", UUID.class)).thenReturn(classroomId);
        when(rs.getString("title")).thenReturn("Draft Quiz");
        when(rs.getString("status")).thenReturn(status);
        when(rs.getBoolean("graded")).thenReturn(true);
        when(rs.getTimestamp(anyString())).thenReturn(null);
        when(rs.getObject("duration_seconds")).thenReturn(600);
        when(rs.getInt("max_attempts")).thenReturn(2);
        when(rs.getBoolean("allow_late")).thenReturn(false);
        when(rs.getBoolean("shuffle_questions")).thenReturn(false);
        when(rs.getBoolean("shuffle_answers")).thenReturn(false);
        when(rs.getString("release_policy")).thenReturn("MANUAL");
        when(rs.getString("timeout_mode")).thenReturn("AUTO_SUBMIT");
        when(rs.getString("questions_json")).thenReturn("[]");
        when(rs.getBoolean("show_correct_answers")).thenReturn(false);
        when(rs.getBoolean("show_explanations")).thenReturn(false);
        when(rs.getBigDecimal("passing_score")).thenReturn(BigDecimal.valueOf(50));
        return rs;
    }
}
