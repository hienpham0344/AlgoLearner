package com.algolearner.platform.quiz;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"logging.level.root=WARN","debug=false","app.security.jwt-secret=quiz-integration-test-only-secret-at-least-32-bytes","app.mail.from=sender@example.test"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles({"internal-auth","test"})
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:postgresql:.*")
class QuizIntegrationTest extends PostgresIntegrationTest {
    static class TestClock extends Clock {
        Instant value=Instant.parse("2026-11-18T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return value;}
    }
    @org.springframework.boot.test.context.TestConfiguration static class Time {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary TestClock testClock(){return new TestClock();}
    }
    @Autowired QuizService quizzes;
    @Autowired QuizLessonProgressProvider progress;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired com.algolearner.platform.lesson.LessonService lessons;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired ClassroomService classrooms;
    @Autowired AccountRepository accounts;
    @Autowired TestClock clock;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void frozenAutosaveManualReleaseAndOfficialHighestScore() throws Exception {
        clock.value=Instant.parse("2026-11-18T00:00:00Z");
        var teacher=account(AccountRole.TEACHER); var student=account(AccountRole.STUDENT);
        var classroom=classrooms.create(teacher.getId(),new ClassroomDtos.Create("Quiz",null,JoinMode.AUTO_JOIN,null,null));
        classrooms.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(student.getUsername()));
        UUID question=UUID.randomUUID(),correct=UUID.randomUUID(),wrong=UUID.randomUUID();
        var quiz=quizzes.create(teacher.getId(),classroom.id(),configure(question,correct,wrong,QuizDtos.ReleasePolicy.MANUAL));
        assertThat(quiz).isNotNull();
        quizzes.publish(teacher.getId(),classroom.id(),quiz.id());
        var lesson=lessons.create(teacher.getId(),classroom.id(),new com.algolearner.platform.lesson.LessonDtos.CreateLessonRequest("Quiz dependency",null,null,null,com.algolearner.platform.lesson.CompletionMode.AUTO_ITEMS));
        lessons.linkItem(teacher.getId(),classroom.id(),lesson.id(),new com.algolearner.platform.lesson.LessonDtos.LinkItemRequest(com.algolearner.platform.lesson.LinkedItemType.QUIZ,quiz.id()));
        lessons.configureCompletionRule(teacher.getId(),classroom.id(),lesson.id(),new com.algolearner.platform.lesson.LessonCompletionRule(
                com.algolearner.platform.lesson.LessonCompletionRule.Operator.ALL,List.of(new com.algolearner.platform.lesson.LessonCompletionRule.Condition(
                        com.algolearner.platform.lesson.LinkedItemType.QUIZ,quiz.id(),com.algolearner.platform.lesson.LessonCompletionRule.Requirement.SCORE_AT_LEAST,BigDecimal.valueOf(80)))));
        lessons.publish(teacher.getId(),classroom.id(),lesson.id());
        var attempt=quizzes.start(student.getId(),classroom.id(),quiz.id());
        assertThat(attempt).isNotNull();
        var authentication=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(student.getId().toString(),"unused",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STUDENT")));
        var body=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/classrooms/{c}/quizzes/{q}/attempts/{a}",classroom.id(),quiz.id(),attempt.id())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(authentication)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"correct\"").doesNotContain("questions_json").doesNotContain("snapshot_json");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/classrooms/{c}/quizzes/{q}/authoring",classroom.id(),quiz.id())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(authentication)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        var originalQuestions=jdbc.queryForObject("select questions_json::text from tbl_quiz where id=?",String.class,quiz.id());
        var changed=new QuizDtos.Question(question,"Changed after start",QuizDtos.QuestionType.SINGLE_CHOICE,QuizDtos.Scoring.ALL_OR_NOTHING,BigDecimal.ONE,
                List.of(new QuizDtos.Option(correct,"4",false),new QuizDtos.Option(wrong,"5",true)));
        jdbc.update("update tbl_quiz set questions_json=?::jsonb where id=?",mapper.writeValueAsString(List.of(changed)),quiz.id());
        assertThat(quizzes.resume(student.getId(),classroom.id(),quiz.id(),attempt.id()).questions()).isEqualTo(attempt.questions());
        var key=UUID.randomUUID(); var answer=new QuizDtos.SaveAnswer(question,List.of(correct),0,key);
        assertThat(quizzes.save(student.getId(),classroom.id(),quiz.id(),attempt.id(),answer).version()).isEqualTo(1);
        assertThat(quizzes.save(student.getId(),classroom.id(),quiz.id(),attempt.id(),answer).version()).isEqualTo(1);
        assertThatThrownBy(()->quizzes.save(student.getId(),classroom.id(),quiz.id(),attempt.id(),new QuizDtos.SaveAnswer(question,List.of(wrong),0,UUID.randomUUID())))
                .isInstanceOf(AppException.class);
        assertThat(quizzes.resume(student.getId(),classroom.id(),quiz.id(),attempt.id()).answers()).hasSize(1);
        var submitted=quizzes.submit(student.getId(),classroom.id(),quiz.id(),attempt.id());
        assertThat(submitted.status()).isEqualTo("SUBMITTED"); assertThat(submitted.score()).isNull();
        assertThat(quizzes.submit(student.getId(),classroom.id(),quiz.id(),attempt.id()).submittedAt()).isEqualTo(submitted.submittedAt());
        assertThat(jdbc.queryForObject("select is_completed from tbl_lesson_completion where lesson_id=? and student_id=?",Boolean.class,lesson.id(),student.getId())).isFalse();
        quizzes.publishGrades(teacher.getId(),classroom.id(),quiz.id());
        assertThat(jdbc.queryForObject("select is_completed from tbl_lesson_completion where lesson_id=? and student_id=?",Boolean.class,lesson.id(),student.getId())).isTrue();
        assertThat(quizzes.resume(student.getId(),classroom.id(),quiz.id(),attempt.id()).score()).isEqualByComparingTo("100");
        jdbc.update("update tbl_quiz set questions_json=?::jsonb where id=?",originalQuestions,quiz.id());
        var second=quizzes.start(student.getId(),classroom.id(),quiz.id());
        quizzes.save(student.getId(),classroom.id(),quiz.id(),second.id(),new QuizDtos.SaveAnswer(question,List.of(wrong),0,UUID.randomUUID()));
        quizzes.submit(student.getId(),classroom.id(),quiz.id(),second.id());
        quizzes.publishGrades(teacher.getId(),classroom.id(),quiz.id());
        assertThat(jdbc.queryForObject("select max(score) from tbl_quiz_attempt where quiz_id=? and student_id=? and status='SUBMITTED'",BigDecimal.class,quiz.id(),student.getId())).isEqualByComparingTo("100");
        assertThat(progress.progress(quiz.id(),classroom.id(),student.getId()).scorePercent()).isEqualByComparingTo("100");
        var source=progress.progress(quiz.id(),classroom.id(),student.getId()).sourceVersion();
        quizzes.publishGrades(teacher.getId(),classroom.id(),quiz.id());
        assertThat(progress.progress(quiz.id(),classroom.id(),student.getId()).sourceVersion()).isEqualTo(source);
        assertThatThrownBy(()->quizzes.start(student.getId(),classroom.id(),quiz.id())).isInstanceOf(AppException.class);
    }

    @Test
    void timeoutUsesOnlySavedAnswersAndRefusesLateAutosave() {
        clock.value=Instant.parse("2026-11-18T00:00:00Z");
        var teacher=account(AccountRole.TEACHER); var student=account(AccountRole.STUDENT);
        var classroom=classrooms.create(teacher.getId(),new ClassroomDtos.Create("Timer",null,JoinMode.AUTO_JOIN,null,null));
        classrooms.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(student.getUsername()));
        UUID question=UUID.randomUUID(),correct=UUID.randomUUID(),wrong=UUID.randomUUID();
        var quiz=quizzes.create(teacher.getId(),classroom.id(),configure(question,correct,wrong,QuizDtos.ReleasePolicy.IMMEDIATE));
        assertThat(quiz).isNotNull(); quizzes.publish(teacher.getId(),classroom.id(),quiz.id());
        var attempt=quizzes.start(student.getId(),classroom.id(),quiz.id());
        assertThatThrownBy(()->quizzes.save(student.getId(),classroom.id(),quiz.id(),attempt.id(),new QuizDtos.SaveAnswer(question,List.of(UUID.randomUUID()),0,UUID.randomUUID())))
                .isInstanceOf(AppException.class);
        quizzes.save(student.getId(),classroom.id(),quiz.id(),attempt.id(),new QuizDtos.SaveAnswer(question,List.of(correct),0,UUID.randomUUID()));
        clock.value=attempt.cutoffAt();
        var late=quizzes.save(student.getId(),classroom.id(),quiz.id(),attempt.id(),new QuizDtos.SaveAnswer(question,List.of(wrong),1,UUID.randomUUID()));
        assertThat(late.accepted()).isFalse(); assertThat(late.attemptStatus()).isEqualTo("SUBMITTED");
        var result=quizzes.resume(student.getId(),classroom.id(),quiz.id(),attempt.id());
        assertThat(result.score()).isEqualByComparingTo("100"); assertThat(result.cutoffAt()).isEqualTo(attempt.cutoffAt());
    }
    @Test
    void concurrentStartsAndIndependentAnswerVersionsRemainConsistent() throws Exception {
        clock.value=Instant.parse("2026-11-18T00:00:00Z");
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);var other=account(AccountRole.STUDENT);
        var classroom=classrooms.create(teacher.getId(),new ClassroomDtos.Create("Concurrency",null,JoinMode.AUTO_JOIN,null,null));
        classrooms.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(student.getUsername()));
        classrooms.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(other.getUsername()));
        UUID q1=UUID.randomUUID(),q2=UUID.randomUUID(),correct=UUID.randomUUID(),wrong=UUID.randomUUID();
        var base=configure(q1,correct,wrong,QuizDtos.ReleasePolicy.IMMEDIATE);
        var questions=new ArrayList<>(base.questions());questions.add(new QuizDtos.Question(q2,"Second",QuizDtos.QuestionType.SINGLE_CHOICE,QuizDtos.Scoring.ALL_OR_NOTHING,BigDecimal.ONE,base.questions().getFirst().options()));
        var quiz=quizzes.create(teacher.getId(),classroom.id(),new QuizDtos.Configure("Concurrent",true,null,null,60,1,false,false,false,base.releasePolicy(),base.timeoutMode(),questions));
        quizzes.publish(teacher.getId(),classroom.id(),quiz.id());
        var gate=new java.util.concurrent.CyclicBarrier(2);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<QuizDtos.AttemptView> start=()->{gate.await(10,java.util.concurrent.TimeUnit.SECONDS);return quizzes.start(student.getId(),classroom.id(),quiz.id());};
            var left=executor.submit(start);var right=executor.submit(start);
            var attempt=left.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(right.get(20,java.util.concurrent.TimeUnit.SECONDS).id()).isEqualTo(attempt.id());
            assertThat(jdbc.queryForObject("select count(*) from tbl_quiz_attempt where quiz_id=?",Integer.class,quiz.id())).isEqualTo(1);
            assertThatThrownBy(()->quizzes.resume(other.getId(),classroom.id(),quiz.id(),attempt.id())).isInstanceOf(AppException.class);
            assertThatThrownBy(()->quizzes.save(student.getId(),classroom.id(),quiz.id(),attempt.id(),new QuizDtos.SaveAnswer(q1,List.of(correct,correct),0,UUID.randomUUID())))
                    .isInstanceOf(AppException.class);
            var one=executor.submit(()->quizzes.save(student.getId(),classroom.id(),quiz.id(),attempt.id(),new QuizDtos.SaveAnswer(q1,List.of(correct),0,UUID.randomUUID())));
            var two=executor.submit(()->quizzes.save(student.getId(),classroom.id(),quiz.id(),attempt.id(),new QuizDtos.SaveAnswer(q2,List.of(correct),0,UUID.randomUUID())));
            assertThat(one.get(20,java.util.concurrent.TimeUnit.SECONDS).version()).isEqualTo(1);
            assertThat(two.get(20,java.util.concurrent.TimeUnit.SECONDS).version()).isEqualTo(1);
            assertThat(quizzes.submit(student.getId(),classroom.id(),quiz.id(),attempt.id()).score()).isEqualByComparingTo("100");
        }
    }
    @Test
    void lockTimeoutNeverCreatesOfficialResult() {
        clock.value=Instant.parse("2026-11-18T00:00:00Z");
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);
        var classroom=classrooms.create(teacher.getId(),new ClassroomDtos.Create("Lock",null,JoinMode.AUTO_JOIN,null,null));
        classrooms.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(student.getUsername()));
        var base=configure(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),QuizDtos.ReleasePolicy.IMMEDIATE);
        var quiz=quizzes.create(teacher.getId(),classroom.id(),new QuizDtos.Configure(base.title(),base.graded(),null,null,1,1,false,false,false,base.releasePolicy(),QuizDtos.TimeoutMode.LOCK,base.questions()));
        quizzes.publish(teacher.getId(),classroom.id(),quiz.id());var attempt=quizzes.start(student.getId(),classroom.id(),quiz.id());
        clock.value=attempt.cutoffAt();assertThat(quizzes.expireOne(attempt.id())).isTrue();assertThat(quizzes.expireOne(attempt.id())).isFalse();
        var locked=quizzes.resume(student.getId(),classroom.id(),quiz.id(),attempt.id());
        assertThat(locked.status()).isEqualTo("LOCKED");assertThat(locked.submittedAt()).isNull();assertThat(locked.score()).isNull();
        assertThat(progress.progress(quiz.id(),classroom.id(),student.getId()).submitted()).isFalse();
    }

    @Test
    void partialCreditRetainsCorrectSelectionsWithoutInventingWrongAnswerPenalty() {
        clock.value=Instant.parse("2026-11-18T00:00:00Z");
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);
        var classroom=classrooms.create(teacher.getId(),new ClassroomDtos.Create("Partial",null,JoinMode.AUTO_JOIN,null,null));
        classrooms.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(student.getUsername()));
        UUID question=UUID.randomUUID(),one=UUID.randomUUID(),two=UUID.randomUUID(),wrong=UUID.randomUUID();
        var quiz=quizzes.create(teacher.getId(),classroom.id(),new QuizDtos.Configure("Partial",true,null,null,null,1,false,false,false,
                QuizDtos.ReleasePolicy.IMMEDIATE,QuizDtos.TimeoutMode.AUTO_SUBMIT,List.of(new QuizDtos.Question(question,"Select correct options",
                QuizDtos.QuestionType.MULTIPLE_CHOICE,QuizDtos.Scoring.PARTIAL,BigDecimal.ONE,
                List.of(new QuizDtos.Option(one,"One",true),new QuizDtos.Option(two,"Two",true),new QuizDtos.Option(wrong,"Wrong",false))))));
        quizzes.publish(teacher.getId(),classroom.id(),quiz.id());var attempt=quizzes.start(student.getId(),classroom.id(),quiz.id());
        quizzes.save(student.getId(),classroom.id(),quiz.id(),attempt.id(),new QuizDtos.SaveAnswer(question,List.of(one,wrong),0,UUID.randomUUID()));
        assertThat(quizzes.submit(student.getId(),classroom.id(),quiz.id(),attempt.id()).score()).isEqualByComparingTo("50");
    }

    @Test
    void lockedDraftRequiresAuditedTeacherGrantAndResetPreservesOfficialHistory() {
        clock.value=Instant.parse("2026-11-18T00:00:00Z");
        var teacher=account(AccountRole.TEACHER);var outsider=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);
        var classroom=classrooms.create(teacher.getId(),new ClassroomDtos.Create("Rights",null,JoinMode.AUTO_JOIN,null,null));
        classrooms.directAdd(teacher.getId(),classroom.id(),new ClassroomDtos.DirectAdd(student.getUsername()));
        var base=configure(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),QuizDtos.ReleasePolicy.IMMEDIATE);
        var quiz=quizzes.create(teacher.getId(),classroom.id(),new QuizDtos.Configure(base.title(),true,null,null,1,1,false,false,false,
                base.releasePolicy(),QuizDtos.TimeoutMode.LOCK,base.questions()));
        quizzes.publish(teacher.getId(),classroom.id(),quiz.id());var draft=quizzes.start(student.getId(),classroom.id(),quiz.id());
        clock.value=draft.cutoffAt();quizzes.expireOne(draft.id());
        assertThatThrownBy(()->quizzes.start(student.getId(),classroom.id(),quiz.id())).isInstanceOf(AppException.class);
        assertThatThrownBy(()->adjust(outsider.getId(),classroom.id(),quiz.id(),student.getId(),"GRANT",1,"Teacher permission"))
                .isInstanceOf(AppException.class);
        adjust(teacher.getId(),classroom.id(),quiz.id(),student.getId(),"GRANT",1,"Fresh permission after timeout");
        var official=quizzes.start(student.getId(),classroom.id(),quiz.id());quizzes.submit(student.getId(),classroom.id(),quiz.id(),official.id());
        var extra=quizzes.start(student.getId(),classroom.id(),quiz.id());quizzes.submit(student.getId(),classroom.id(),quiz.id(),extra.id());
        assertThatThrownBy(()->quizzes.start(student.getId(),classroom.id(),quiz.id())).isInstanceOf(AppException.class);
        adjust(teacher.getId(),classroom.id(),quiz.id(),student.getId(),"RESET",0,"Restart allowance without deleting history");
        assertThat(quizzes.start(student.getId(),classroom.id(),quiz.id()).id()).isNotEqualTo(draft.id());
        var preserved=quizzes.resume(student.getId(),classroom.id(),quiz.id(),draft.id());
        assertThat(preserved.status()).isEqualTo("LOCKED");assertThat(preserved.cutoffAt()).isEqualTo(draft.cutoffAt());
        assertThat(jdbc.queryForObject("select count(*) from tbl_quiz_attempt where quiz_id=? and status='SUBMITTED'",Integer.class,quiz.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from tbl_audit_log where target_id=? and action in ('QUIZ_ATTEMPT_GRANT','QUIZ_ATTEMPT_RESET')",Integer.class,quiz.id().toString())).isEqualTo(2);
    }
    private void adjust(UUID actor,UUID classroom,UUID quiz,UUID student,String action,int additional,String reason) {
        quizzes.adjustAttempts(actor,classroom,quiz,student,action,additional,reason);
    }

    private QuizDtos.Configure configure(UUID q,UUID correct,UUID wrong,QuizDtos.ReleasePolicy release) {
        return new QuizDtos.Configure("Objective",true,null,null,60,2,false,true,true,release,QuizDtos.TimeoutMode.AUTO_SUBMIT,
                List.of(new QuizDtos.Question(q,"2+2?",QuizDtos.QuestionType.SINGLE_CHOICE,QuizDtos.Scoring.ALL_OR_NOTHING,BigDecimal.ONE,
                        List.of(new QuizDtos.Option(correct,"4",true),new QuizDtos.Option(wrong,"5",false)))));
    }
    private AccountEntity account(AccountRole role){var a=new AccountEntity();a.setId(UUID.randomUUID());a.setUsername("quiz_"+UUID.randomUUID().toString().replace("-",""));a.setEmail(a.getUsername()+"@example.test");a.setPasswordHash("unused");a.setRole(role);a.setStatus(AccountStatus.ACTIVE);return accounts.saveAndFlush(a);}
}
