package com.algolearner.platform.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.course.CourseDtos.AnswerRequest;
import com.algolearner.platform.course.CourseDtos.AnswerVisibility;
import com.algolearner.platform.course.CourseDtos.Block;
import com.algolearner.platform.course.CourseDtos.CompletionRule;
import com.algolearner.platform.course.CourseDtos.CourseContent;
import com.algolearner.platform.course.CourseDtos.LessonContent;
import com.algolearner.platform.course.CourseDtos.Level;
import com.algolearner.platform.course.CourseDtos.OptionContent;
import com.algolearner.platform.course.CourseDtos.ProgressStatus;
import com.algolearner.platform.course.CourseDtos.Publication;
import com.algolearner.platform.course.CourseDtos.PublicationAction;
import com.algolearner.platform.course.CourseDtos.PublicationChange;
import com.algolearner.platform.course.CourseDtos.QuestionContent;
import com.algolearner.platform.course.CourseDtos.QuestionType;
import com.algolearner.platform.course.CourseDtos.QuizContent;
import com.algolearner.platform.course.CourseDtos.Review;
import com.algolearner.platform.course.CourseDtos.ReviewDecision;
import com.algolearner.platform.course.CourseDtos.ReviewStatus;
import com.algolearner.platform.course.CourseDtos.SaveAnswers;
import com.algolearner.platform.course.CourseDtos.UnpublishPolicy;
import com.algolearner.platform.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=course-flow-test-only-secret-at-least-32-bytes"})
@ActiveProfiles({"internal-auth", "test"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class PublicCourseFlowTest extends PostgresIntegrationTest {
    @Autowired AccountRepository accounts;
    @Autowired PublicCourseService learning;
    @Autowired PublicCourseQuizService quizzes;
    @Autowired CourseContributionService contributions;
    @Autowired CourseAdministrationService admin;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc http;
    @Autowired ObjectMapper mapper;

    // ------------------------------------------------------------------ HTTP / RBAC
    @Test
    void httpCourseFlowKeepsAdminReviewAndOwnLearningInSeparateRoutes() throws Exception {
        var teacher = account(AccountRole.TEACHER); var adminAccount = account(AccountRole.ADMIN); var student = account(AccountRole.STUDENT);
        var response = http.perform(post("/api/v1/courses").with(identity(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"HTTP course\",\"description\":\"Learning\",\"level\":\"BEGINNER\",\"topic\":\"Arrays\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT")).andReturn();
        UUID course = UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).at("/data/id").asText());
        var lessonResponse = http.perform(post("/api/v1/courses/{id}/lessons", course).with(identity(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lesson\",\"content\":\"Real content\"}"))
                .andExpect(status().isCreated()).andReturn();
        String unit = mapper.readTree(lessonResponse.getResponse().getContentAsString()).at("/data/id").asText();
        http.perform(get("/api/v1/courses/{id}", course).with(identity(student))).andExpect(status().isNotFound());
        http.perform(post("/api/v1/courses/{id}/review-requests", course).with(identity(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
        http.perform(get("/api/v1/courses/contributions").with(identity(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1));
        http.perform(get("/api/v1/admin/courses/reviews").with(identity(adminAccount)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray()).andExpect(jsonPath("$.data.pagination").exists());
        http.perform(get("/api/v1/admin/courses/reviews").with(identity(teacher))).andExpect(status().isForbidden());
        http.perform(post("/api/v1/admin/courses/{id}/reviews", course).with(identity(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true}")).andExpect(status().isForbidden());
        http.perform(post("/api/v1/admin/courses/{id}/reviews", course).with(identity(adminAccount)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}")).andExpect(status().isBadRequest());
        http.perform(post("/api/v1/admin/courses/{id}/reviews", course).with(identity(adminAccount)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.publicationStatus").value("PUBLISHED"));
        http.perform(get("/api/v1/courses").param("q", "HTTP cour").param("level", "BEGINNER").with(identity(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].course.id").value(course.toString()))
                .andExpect(jsonPath("$.data.items[0].course.createdAt").value(org.hamcrest.Matchers.endsWith("Z")));
        http.perform(get("/api/v1/courses/{id}", course).with(identity(adminAccount))).andExpect(status().isForbidden());
        http.perform(get("/api/v1/courses/{id}/lessons/{unit}", course, unit).with(identity(student)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("COURSE_NOT_ENROLLED"));
        http.perform(post("/api/v1/courses/{id}/enrollments", course).with(identity(student))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"));
        http.perform(post("/api/v1/courses/{id}/lessons/{unit}/completion", course, unit).with(identity(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.progressPercent").value(100.0))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andExpect(jsonPath("$.data.accountId").value(student.getId().toString()));
        http.perform(delete("/api/v1/courses/{id}/enrollments/me", course).with(identity(student)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("COURSE_ALREADY_STARTED"));
        http.perform(post("/api/v1/admin/courses/{id}/publication", course).with(identity(adminAccount)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"UNPUBLISH\"}")).andExpect(status().isBadRequest());
        http.perform(delete("/api/v1/admin/courses/{id}", course).with(identity(adminAccount)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("COURSE_HAS_LEARNING_DATA"));
        http.perform(get("/api/v1/courses/enrollments/me").with(identity(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].progress.status").value("COMPLETED"));
    }

    // ------------------------------------------------------------------ enrollment & progress
    @Test
    void reviewedCourseEnrollmentProducesRealIdempotentOwnProgress() {
        var teacher = account(AccountRole.TEACHER); var adminAccount = account(AccountRole.ADMIN);
        var student = account(AccountRole.STUDENT); var other = account(AccountRole.STUDENT);
        var course = contributions.create(teacher.getId(), new CourseDtos.Create("Algorithms", "Public course"));
        var first = contributions.addLesson(teacher.getId(), course.id(), new CourseDtos.UnitRequest("Arrays", "Content"));
        contributions.addLesson(teacher.getId(), course.id(), new CourseDtos.UnitRequest("Lists", "Content"));
        assertThatThrownBy(() -> learning.enroll(student.getId(), course.id())).isInstanceOf(CourseException.class);
        contributions.submit(teacher.getId(), course.id());
        assertThatThrownBy(() -> admin.review(teacher.getId(), course.id(), new Review(true, null))).isInstanceOf(RuntimeException.class);
        admin.review(adminAccount.getId(), course.id(), new Review(true, null));
        assertThat(learning.enroll(student.getId(), course.id()).completedUnits()).isZero();
        learning.enroll(student.getId(), course.id());
        learning.completeLesson(student.getId(), course.id(), first.id());
        learning.completeLesson(student.getId(), course.id(), first.id());
        var own = learning.progress(student.getId(), course.id());
        assertThat(own.progressPercent()).isEqualTo(50.0);
        assertThat(own.status()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThatThrownBy(() -> learning.progress(other.getId(), course.id())).isInstanceOf(CourseException.class);
        assertThat(count("SELECT count(*) FROM tbl_course_enrollment WHERE course_id=?", course.id())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM tbl_audit_log WHERE action='PUBLIC_COURSE_REVIEWED' AND target_id=?", course.id().toString())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM tbl_course_enrollment_event e JOIN tbl_course_enrollment c ON c.id=e.enrollment_id WHERE c.course_id=? AND e.event='LESSON_COMPLETED'", course.id())).isEqualTo(1);
    }

    @Test
    void studentsCannotAuthorAndUnitsCannotCrossCourses() {
        var teacher = account(AccountRole.TEACHER); var otherTeacher = account(AccountRole.TEACHER);
        var adminAccount = account(AccountRole.ADMIN); var student = account(AccountRole.STUDENT);
        assertThatThrownBy(() -> contributions.create(student.getId(), new CourseDtos.Create("Forbidden", null))).isInstanceOf(CourseException.class);
        var first = publishedCourse(teacher, adminAccount, "One");
        var second = publishedCourse(teacher, adminAccount, "Two");
        UUID foreignUnit = learning.detail(student.getId(), first).lessons().get(0).id();
        learning.enroll(student.getId(), second);
        assertThatThrownBy(() -> learning.completeLesson(student.getId(), second, foreignUnit)).isInstanceOf(CourseException.class);
        assertThat(learning.progress(student.getId(), second).completedUnits()).isZero();
        assertThatThrownBy(() -> contributions.saveContent(otherTeacher.getId(), second, content("Hijack", 1, List.of())))
                .hasFieldOrPropertyWithValue("errorCode", "FORBIDDEN_OPERATION"); // FR-PCRS-012 AC-13
    }

    @Test
    void unenrollBeforeStartingKeepsHistoryAndAllowsReEnrollment() {
        var teacher = account(AccountRole.TEACHER); var adminAccount = account(AccountRole.ADMIN); var student = account(AccountRole.STUDENT);
        UUID course = publishedCourse(teacher, adminAccount, "Unenroll");
        learning.enroll(student.getId(), course);
        learning.unenroll(student.getId(), course);
        assertThatThrownBy(() -> learning.progress(student.getId(), course)).hasFieldOrPropertyWithValue("errorCode", "COURSE_ENROLLMENT_NOT_FOUND");
        assertThat(learning.enroll(student.getId(), course).status()).isEqualTo(ProgressStatus.NOT_STARTED);
        assertThat(count("SELECT count(*) FROM tbl_course_enrollment_event e JOIN tbl_course_enrollment c ON c.id=e.enrollment_id WHERE c.course_id=?", course)).isEqualTo(3);
        UUID lesson = learning.detail(student.getId(), course).lessons().get(0).id();
        learning.lesson(student.getId(), course, lesson); // opening a lesson starts the course
        assertThatThrownBy(() -> learning.unenroll(student.getId(), course)).hasFieldOrPropertyWithValue("errorCode", "COURSE_ALREADY_STARTED");
        assertThat(learning.progress(student.getId(), course).status()).isEqualTo(ProgressStatus.IN_PROGRESS);
    }

    @Test
    void catalogSearchFiltersByNameLevelAndTopicWithoutTouchingEnrollment() { // FR-PCRS-003
        var teacher = account(AccountRole.TEACHER); var adminAccount = account(AccountRole.ADMIN); var student = account(AccountRole.STUDENT);
        String token = "zq" + UUID.randomUUID().toString().substring(0, 8);
        UUID adv = UUID.fromString(admin.create(adminAccount.getId(), new CourseContent(token + " Graphs", null, Level.ADVANCED, "Graphs", 90,
                List.of(lesson("G", false)), List.of())).course().id().toString());
        admin.changePublication(adminAccount.getId(), adv, new PublicationChange(PublicationAction.PUBLISH, null, null));
        UUID draft = admin.create(adminAccount.getId(), new CourseContent(token + " Hidden", null, Level.ADVANCED, "Graphs", null,
                List.of(lesson("H", false)), List.of())).course().id();
        publishedCourse(teacher, adminAccount, token + " Basics");
        var page = PageRequest.of(0, 20);
        assertThat(learning.catalog(student.getId(), token, null, null, page).getData().getItems()).hasSize(2);
        assertThat(learning.catalog(student.getId(), token.toUpperCase(), Level.ADVANCED, "graphs", page).getData().getItems())
                .singleElement().satisfies(i -> assertThat(i.course().id()).isEqualTo(adv));
        assertThat(learning.catalog(student.getId(), token, null, null, page).getData().getItems())
                .noneMatch(i -> i.course().id().equals(draft));
        assertThat(learning.catalog(student.getId(), "%", null, null, page).getData().getItems()).isEmpty();
        assertThat(count("SELECT count(*) FROM tbl_course_enrollment WHERE account_id=?", student.getId())).isZero();
    }

    // ------------------------------------------------------------------ contribution review & content change
    @Test
    void publishedChangesNeedReReviewAndPreserveCompletedLearners() { // FR-PCRS-007, 012, 015
        var teacher = account(AccountRole.TEACHER); var adminAccount = account(AccountRole.ADMIN);
        var finished = account(AccountRole.STUDENT); var midway = account(AccountRole.STUDENT);
        var created = contributions.create(teacher.getId(), new CourseDtos.Create("Revisions", null));
        var draft = contributions.saveContent(teacher.getId(), created.id(), content("Revisions", 2, List.of()));
        contributions.submit(teacher.getId(), created.id());
        admin.review(adminAccount.getId(), created.id(), new Review(null, ReviewDecision.APPROVE, null, true));
        UUID course = created.id();
        var lessons = draft.content().lessons();
        learning.enroll(finished.getId(), course);
        lessons.forEach(l -> learning.completeLesson(finished.getId(), course, l.id()));
        assertThat(learning.progress(finished.getId(), course).status()).isEqualTo(ProgressStatus.COMPLETED);
        learning.enroll(midway.getId(), course);
        learning.completeLesson(midway.getId(), course, lessons.get(0).id());
        var completedAt = learning.progress(finished.getId(), course).completedAt();

        // Teacher edits a published course: a new revision; public version untouched (AC-08, AC-09).
        var edited = new java.util.ArrayList<>(lessons);
        edited.add(lesson("Brand new lesson", false));
        var revision = contributions.saveContent(teacher.getId(), course, new CourseContent("Revisions", null, null, null, null, edited, List.of()));
        assertThat(revision.summary().revisionNo()).isEqualTo(2);
        assertThat(learning.detail(finished.getId(), course).lessons()).hasSize(2);
        contributions.submit(teacher.getId(), course);
        assertThatThrownBy(() -> contributions.saveContent(teacher.getId(), course, revision.content()))
                .hasFieldOrPropertyWithValue("errorCode", "COURSE_REVIEW_PENDING");
        assertThatThrownBy(() -> admin.review(adminAccount.getId(), course, new Review(null, ReviewDecision.CHANGES_REQUESTED, " ", null)))
                .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_FAILED"); // AC-05
        admin.review(adminAccount.getId(), course, new Review(null, ReviewDecision.CHANGES_REQUESTED, "Add an example", null));
        var reread = contributions.contribution(teacher.getId(), course);
        assertThat(reread.summary().status()).isEqualTo(ReviewStatus.CHANGES_REQUESTED);
        assertThat(reread.summary().reason()).isEqualTo("Add an example"); // AC-06
        contributions.submit(teacher.getId(), course);
        admin.review(adminAccount.getId(), course, new Review(null, ReviewDecision.APPROVE, null, null));

        assertThat(learning.detail(finished.getId(), course).lessons()).hasSize(3);
        var kept = learning.progress(finished.getId(), course);
        assertThat(kept.status()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(kept.completedAt()).isEqualTo(completedAt);
        assertThat(kept.hasUpdate()).isTrue();
        assertThat(learning.acknowledgeUpdate(finished.getId(), course).hasUpdate()).isFalse();
        var recalculated = learning.progress(midway.getId(), course);
        assertThat(recalculated.status()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(recalculated.progressPercent()).isEqualTo(33.33);
        assertThat(recalculated.hasUpdate()).isFalse();
        assertThat(created.authorId()).isEqualTo(teacher.getId()); // AC-07: author kept
        assertThat(count("SELECT count(*) FROM tbl_notification WHERE recipient_id=? AND type='PUBLIC_COURSE_REVIEW'", teacher.getId())).isEqualTo(3);

        // Removing a lesson soft-deletes it: completion history remains (FR-PCRS-015 AC-02).
        admin.updateContent(adminAccount.getId(), course, new CourseContent("Revisions", null, null, null, null, List.of(lessons.get(1)), List.of()));
        assertThat(count("SELECT count(*) FROM tbl_course_unit_completion c JOIN tbl_course_enrollment e ON e.id=c.enrollment_id WHERE e.account_id=?", finished.getId())).isEqualTo(2);
        assertThat(learning.progress(midway.getId(), course).progressPercent()).isZero();
    }

    // ------------------------------------------------------------------ quiz
    @Test
    void mandatoryQuizUsesBestScoreAttemptLimitAndHiddenAnswers() { // FR-PCRS-006 AC-02/04, FR-PCRS-010
        var adminAccount = account(AccountRole.ADMIN); var student = account(AccountRole.STUDENT); var other = account(AccountRole.STUDENT);
        UUID quizId = UUID.randomUUID(), questionId = UUID.randomUUID(), right = UUID.randomUUID(), wrong = UUID.randomUUID();
        var quiz = new QuizContent(quizId, null, "Check", true, CompletionRule.PASSING_SCORE, BigDecimal.valueOf(80), 2, 600,
                AnswerVisibility.AFTER_PASSED, List.of(new QuestionContent(questionId, "Pick right", QuestionType.SINGLE_CHOICE,
                BigDecimal.ONE, "Right is right", List.of(new OptionContent(right, "right", true), new OptionContent(wrong, "wrong", false)))));
        LessonContent lesson = new LessonContent(UUID.randomUUID(), "Lesson", null, null, false,
                List.of(Block.text("Read"), new Block(CourseDtos.BlockType.QUIZ, null, null, null, null, null, null, quizId)));
        UUID course = admin.create(adminAccount.getId(), new CourseContent("Quiz course", null, null, null, null, List.of(lesson), List.of(quiz))).course().id();
        admin.changePublication(adminAccount.getId(), course, new PublicationChange(PublicationAction.PUBLISH, null, null));
        assertThatThrownBy(() -> quizzes.start(student.getId(), course, quizId)).hasFieldOrPropertyWithValue("errorCode", "COURSE_NOT_ENROLLED");
        learning.enroll(student.getId(), course);
        learning.completeLesson(student.getId(), course, lesson.id());
        assertThat(learning.progress(student.getId(), course).status()).isEqualTo(ProgressStatus.IN_PROGRESS);

        var first = quizzes.start(student.getId(), course, quizId);
        assertThat(first.deadlineAt()).isEqualTo(first.startedAt().plusSeconds(600));
        assertThat(first.questions().get(0).options()).allMatch(o -> o.correct() == null);
        assertThat(quizzes.start(student.getId(), course, quizId).id()).isEqualTo(first.id()); // resume, timer not reset
        var failed = quizzes.submit(student.getId(), course, quizId, first.id(), new SaveAnswers(List.of(new AnswerRequest(questionId, List.of(wrong)))));
        assertThat(failed.passed()).isFalse();
        assertThat(failed.answersRevealed()).isFalse(); // AC-11: hidden until passed
        assertThat(failed.questions().get(0).explanation()).isNull();
        assertThat(learning.progress(student.getId(), course).status()).isEqualTo(ProgressStatus.IN_PROGRESS);

        var second = quizzes.start(student.getId(), course, quizId);
        assertThatThrownBy(() -> quizzes.saveAnswers(other.getId(), course, quizId, second.id(), new SaveAnswers(List.of())))
                .isInstanceOf(CourseException.class); // IDOR: other learner is not enrolled / cannot touch the attempt
        quizzes.saveAnswers(student.getId(), course, quizId, second.id(), new SaveAnswers(List.of(new AnswerRequest(questionId, List.of(right)))));
        var passed = quizzes.submit(student.getId(), course, quizId, second.id(), null);
        assertThat(passed.score()).isEqualByComparingTo("100");
        assertThat(passed.answersRevealed()).isTrue();
        assertThat(passed.questions().get(0).explanation()).isEqualTo("Right is right");
        assertThatThrownBy(() -> quizzes.start(student.getId(), course, quizId)).hasFieldOrPropertyWithValue("errorCode", "QUIZ_ATTEMPTS_EXHAUSTED");

        var summary = quizzes.status(student.getId(), course, quizId);
        assertThat(summary.officialScore()).isEqualByComparingTo("100");
        assertThat(summary.attempts()).hasSize(2);
        assertThat(summary.attemptsRemaining()).isZero();
        var done = learning.progress(student.getId(), course);
        assertThat(done.status()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(done.completedQuizIds()).containsExactly(quizId);
    }

    @Test
    void expiredAttemptIsFinalizedWithSavedAnswersAndRejectsNewAnswers() { // FR-PCRS-010 AC-13
        var adminAccount = account(AccountRole.ADMIN); var student = account(AccountRole.STUDENT);
        UUID quizId = UUID.randomUUID(), questionId = UUID.randomUUID(), right = UUID.randomUUID();
        var quiz = new QuizContent(quizId, null, "Timed", false, CompletionRule.SUBMIT, null, null, 60, AnswerVisibility.AFTER_SUBMIT,
                List.of(new QuestionContent(questionId, "?", QuestionType.TRUE_FALSE, BigDecimal.ONE, null,
                        List.of(new OptionContent(right, "yes", true), new OptionContent(UUID.randomUUID(), "no", false)))));
        UUID course = admin.create(adminAccount.getId(), new CourseContent("Timed course", null, null, null, null,
                List.of(lesson("L", false)), List.of(quiz))).course().id();
        admin.changePublication(adminAccount.getId(), course, new PublicationChange(PublicationAction.PUBLISH, null, null));
        learning.enroll(student.getId(), course);
        var attempt = quizzes.start(student.getId(), course, quizId);
        quizzes.saveAnswers(student.getId(), course, quizId, attempt.id(), new SaveAnswers(List.of(new AnswerRequest(questionId, List.of(right)))));
        jdbc.update("UPDATE tbl_public_course_quiz_attempt SET deadline_at=started_at - interval '1 second' WHERE id=?", attempt.id());
        assertThatThrownBy(() -> quizzes.saveAnswers(student.getId(), course, quizId, attempt.id(), new SaveAnswers(List.of())))
                .hasFieldOrPropertyWithValue("errorCode", "QUIZ_TIME_EXPIRED");
        var status = quizzes.status(student.getId(), course, quizId);
        assertThat(status.attempts()).singleElement().satisfies(a -> {
            assertThat(a.status()).isEqualTo(CourseDtos.AttemptStatus.SUBMITTED);
            assertThat(a.score()).isEqualByComparingTo("100");
        });
    }

    // ------------------------------------------------------------------ publication lifecycle
    @Test
    void unpublishArchiveAndDeletePreserveLearningData() { // FR-PCRS-013, 014
        var teacher = account(AccountRole.TEACHER); var adminAccount = account(AccountRole.ADMIN);
        var enrolled = account(AccountRole.STUDENT); var newcomer = account(AccountRole.STUDENT);
        UUID course = publishedCourse(teacher, adminAccount, "Lifecycle");
        UUID lesson = learning.detail(enrolled.getId(), course).lessons().get(0).id();
        learning.enroll(enrolled.getId(), course);
        learning.completeLesson(enrolled.getId(), course, lesson);

        admin.changePublication(adminAccount.getId(), course, new PublicationChange(PublicationAction.UNPUBLISH, UnpublishPolicy.ENROLLED_CONTINUE, "Outdated"));
        assertThatThrownBy(() -> learning.enroll(newcomer.getId(), course)).hasFieldOrPropertyWithValue("errorCode", "COURSE_NOT_ENROLLABLE");
        assertThatThrownBy(() -> learning.detail(newcomer.getId(), course)).hasFieldOrPropertyWithValue("errorCode", "COURSE_NOT_FOUND");
        assertThat(learning.lesson(enrolled.getId(), course, lesson).id()).isEqualTo(lesson);

        admin.changePublication(adminAccount.getId(), course, new PublicationChange(PublicationAction.UNPUBLISH, UnpublishPolicy.LOCK_ALL, null));
        assertThatThrownBy(() -> learning.lesson(enrolled.getId(), course, lesson)).hasFieldOrPropertyWithValue("errorCode", "COURSE_ACCESS_LOCKED");
        var locked = learning.progress(enrolled.getId(), course);
        assertThat(locked.contentLocked()).isTrue();
        assertThat(locked.status()).isEqualTo(ProgressStatus.COMPLETED);

        admin.changePublication(adminAccount.getId(), course, new PublicationChange(PublicationAction.ARCHIVE, null, null));
        assertThat(admin.detail(adminAccount.getId(), course).course().status()).isEqualTo(Publication.ARCHIVED);
        assertThatThrownBy(() -> learning.enroll(newcomer.getId(), course)).hasFieldOrPropertyWithValue("errorCode", "COURSE_NOT_ENROLLABLE");
        assertThat(learning.progress(enrolled.getId(), course).completedUnits()).isEqualTo(1);
        assertThatThrownBy(() -> admin.delete(adminAccount.getId(), course)).hasFieldOrPropertyWithValue("errorCode", "COURSE_HAS_LEARNING_DATA");
        assertThatThrownBy(() -> admin.changePublication(teacher.getId(), course, new PublicationChange(PublicationAction.PUBLISH, null, null)))
                .isInstanceOf(CourseException.class);

        UUID unused = admin.create(adminAccount.getId(), content("Unused", 1, List.of())).course().id();
        admin.delete(adminAccount.getId(), unused);
        assertThat(count("SELECT count(*) FROM tbl_public_course WHERE id=?", unused)).isZero();
        assertThat(count("SELECT count(*) FROM tbl_audit_log WHERE target_id=? AND action='PUBLIC_COURSE_PUBLICATION_CHANGED'", course.toString())).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM tbl_audit_log WHERE target_id=? AND action='PUBLIC_COURSE_DELETED'", unused.toString())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers
    private UUID publishedCourse(AccountEntity teacher, AccountEntity adminAccount, String title) {
        var course = contributions.create(teacher.getId(), new CourseDtos.Create(title, null));
        contributions.addLesson(teacher.getId(), course.id(), new CourseDtos.UnitRequest("Lesson", "Content"));
        contributions.submit(teacher.getId(), course.id());
        admin.review(adminAccount.getId(), course.id(), new Review(true, null));
        return course.id();
    }

    private static CourseContent content(String title, int lessons, List<QuizContent> quizzes) {
        var list = new java.util.ArrayList<LessonContent>();
        for (int i = 0; i < lessons; i++) list.add(lesson("Lesson " + i, i == 0));
        return new CourseContent(title, null, null, null, null, list, quizzes);
    }

    private static LessonContent lesson(String title, boolean preview) {
        return new LessonContent(UUID.randomUUID(), title, 5, null, preview, List.of(Block.text("Body of " + title)));
    }

    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    private AccountEntity account(AccountRole role) {
        var value = new AccountEntity(); value.setId(UUID.randomUUID()); value.setUsername("course_" + UUID.randomUUID().toString().replace("-", ""));
        value.setEmail(value.getUsername() + "@example.test"); value.setPasswordHash("unused"); value.setRole(role); value.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(value);
    }

    private JwtRequestPostProcessor identity(AccountEntity account) {
        return jwt().jwt(token -> token.subject(account.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()));
    }
}
