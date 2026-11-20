package com.algolearner.platform.lesson;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.judge.JudgeGateway;
import com.algolearner.platform.practice.ClassPracticeService;
import com.algolearner.platform.problem.ProblemService;
import com.algolearner.platform.quiz.*;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties={"logging.level.root=WARN","app.security.jwt-secret=lesson-linked-content-test-only-secret-at-least-32-bytes","app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth","test"})
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:postgresql:.*")
class LessonLinkedContentSafetyTest extends PostgresIntegrationTest {
    @Autowired LessonService lessons;
    @Autowired ClassroomService classrooms;
    @Autowired AccountRepository accounts;
    @Autowired QuizService quizzes;
    @Autowired QuizLessonProgressProvider quizProgress;
    @Autowired ClassPracticeService practice;
    @Autowired ProblemService problems;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test void archivedQuizRejectsStudentContentAndWritesUntilRestored() {
        var f=fixture();var quiz=quiz(f);
        // A standalone published quiz is usable before it is linked.
        var attempt=quizzes.start(f.student(),f.classroom(),quiz.id());
        var lesson=lesson(f,LinkedItemType.QUIZ,quiz.id(),true);
        lessons.archive(f.teacher(),f.classroom(),lesson);
        assertAll(
            ()->assertThat(quizProgress.availableToStudent(quiz.id(),f.classroom(),f.student())).isFalse(),
            ()->assertThat(quizzes.list(f.student(),f.classroom())).isEmpty(),
            ()->assertThatThrownBy(()->quizzes.get(f.student(),f.classroom(),quiz.id())).isInstanceOf(AppException.class),
            ()->assertThatThrownBy(()->quizzes.start(f.student(),f.classroom(),quiz.id())).isInstanceOf(AppException.class),
            ()->assertThatThrownBy(()->quizzes.save(f.student(),f.classroom(),quiz.id(),attempt.id(),new QuizDtos.SaveAnswer(attempt.questions().getFirst().id(),List.of(attempt.questions().getFirst().options().getFirst().id()),0,UUID.randomUUID()))).isInstanceOf(AppException.class),
            ()->assertThatThrownBy(()->quizzes.submit(f.student(),f.classroom(),quiz.id(),attempt.id())).isInstanceOf(AppException.class),
            ()->assertThatThrownBy(()->quizzes.resume(f.student(),f.classroom(),quiz.id(),attempt.id())).isInstanceOf(AppException.class),
            ()->assertThatThrownBy(()->quizzes.history(f.student(),f.classroom(),quiz.id(),null,PageRequest.of(0,10))).isInstanceOf(AppException.class));
        assertThat(jdbc.queryForObject("SELECT status FROM tbl_quiz_attempt WHERE id=?",String.class,attempt.id())).isEqualTo("IN_PROGRESS");
        assertThat(quizzes.history(f.teacher(),f.classroom(),quiz.id(),f.student(),PageRequest.of(0,10)).getData().getItems()).hasSize(1);
        lessons.restore(f.teacher(),f.classroom(),lesson);
        assertThat(quizProgress.availableToStudent(quiz.id(),f.classroom(),f.student())).isTrue();
        assertThat(quizzes.resume(f.student(),f.classroom(),quiz.id(),attempt.id()).id()).isEqualTo(attempt.id());
        assertThat(quizzes.submit(f.student(),f.classroom(),quiz.id(),attempt.id()).submittedAt()).isNotNull();
    }

    @Test void timeoutCannotCreateOfficialQuizSubmissionThroughArchivedLesson() {
        var f=fixture();var quiz=quiz(f);var lesson=lesson(f,LinkedItemType.QUIZ,quiz.id(),true);
        var attempt=quizzes.start(f.student(),f.classroom(),quiz.id());
        lessons.archive(f.teacher(),f.classroom(),lesson);
        jdbc.update("UPDATE tbl_quiz_attempt SET cutoff_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",attempt.id());
        quizzes.expireOne(attempt.id());
        assertThat(jdbc.queryForObject("SELECT submitted_at FROM tbl_quiz_attempt WHERE id=?",java.sql.Timestamp.class,attempt.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM tbl_quiz_attempt WHERE id=?",String.class,attempt.id())).isEqualTo("LOCKED");
    }

    @Test void teacherReadingExpiredQuizCannotSubmitThroughArchivedLesson() {
        var f=fixture();var quiz=quiz(f);var lesson=lesson(f,LinkedItemType.QUIZ,quiz.id(),true);
        var attempt=quizzes.start(f.student(),f.classroom(),quiz.id());
        lessons.archive(f.teacher(),f.classroom(),lesson);
        jdbc.update("UPDATE tbl_quiz_attempt SET cutoff_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",attempt.id());
        var read=quizzes.resume(f.teacher(),f.classroom(),quiz.id(),attempt.id());
        assertThat(read.submittedAt()).isNull();assertThat(read.status()).isEqualTo("LOCKED");
    }

    @Test void practiceRemainsAvailableThroughAnyPublishedLessonAndPreservesHistoryWhenHidden() {
        var f=fixture();var admin=account(AccountRole.ADMIN);
        var content=new ProblemService.Content("Echo","Echo input",Set.of(JudgeGateway.Language.PYTHON),List.of(new JudgeGateway.TestCaseSnapshot("x","x",true,false)),new JudgeGateway.Limits(10,2000,65536),JudgeGateway.Feedback.PASS_FAIL);
        var problem=problems.create(f.teacher(),content);problems.submitReview(f.teacher(),problem);problems.review(admin,problem,true,null);
        UUID item=practice.copy(f.teacher(),f.classroom(),problem);practice.publish(f.teacher(),f.classroom(),item);
        var input=new ProblemService.CodeInput("print(input())",JudgeGateway.Language.PYTHON,"original");
        UUID job=practice.evaluate(f.student(),f.classroom(),item,input,false);
        var one=lesson(f,LinkedItemType.PRACTICE,item,true);var two=lesson(f,LinkedItemType.PRACTICE,item,true);
        lessons.archive(f.teacher(),f.classroom(),one);
        assertThat(practice.availableToStudent(item,f.classroom(),f.student())).isTrue();
        lessons.unpublish(f.teacher(),f.classroom(),two);
        assertAll(
            ()->assertThat(practice.availableToStudent(item,f.classroom(),f.student())).isFalse(),
            ()->assertThat(practice.list(f.student(),f.classroom(),PageRequest.of(0,10)).getData().getItems()).isEmpty(),
            ()->assertThatThrownBy(()->practice.detail(f.student(),f.classroom(),item)).isInstanceOf(AppException.class),
            ()->assertThatThrownBy(()->practice.evaluate(f.student(),f.classroom(),item,new ProblemService.CodeInput("pass",JudgeGateway.Language.PYTHON,"hidden-submit"),false)).isInstanceOf(AppException.class),
            ()->assertThatThrownBy(()->practice.evaluate(f.student(),f.classroom(),item,new ProblemService.CodeInput("pass",JudgeGateway.Language.PYTHON,"hidden-run"),true)).isInstanceOf(AppException.class),
            ()->assertThatThrownBy(()->practice.result(f.student(),f.classroom(),item,job)).isInstanceOf(AppException.class),
            ()->assertThatThrownBy(()->practice.history(f.student(),f.classroom(),item,PageRequest.of(0,10))).isInstanceOf(AppException.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tbl_submission WHERE owner_id=? AND resource_id=?",Long.class,f.student(),item)).isEqualTo(1);
        lessons.restore(f.teacher(),f.classroom(),one);
        assertThat(practice.availableToStudent(item,f.classroom(),f.student())).isTrue();
        assertThat(practice.history(f.student(),f.classroom(),item,PageRequest.of(0,10)).getData().getItems()).hasSize(1);
        assertThat(practice.list(f.student(),f.classroom(),PageRequest.of(0,10)).getData().getPagination().getTotalElements()).isEqualTo(1);
    }

    @Test void committedArchiveWinsOverConcurrentQuizStart() throws Exception {
        var f=fixture();var quiz=quiz(f);var lesson=lesson(f,LinkedItemType.QUIZ,quiz.id(),true);
        var archived=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var pid=new AtomicInteger();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var archive=pool.submit(()->new TransactionTemplate(transactions).executeWithoutResult(s->{lessons.archive(f.teacher(),f.classroom(),lesson);archived.countDown();await(release);}));
            assertThat(archived.await(10,TimeUnit.SECONDS)).isTrue();
            var start=pool.submit(()->new TransactionTemplate(transactions).execute(s->{pid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));started.countDown();return quizzes.start(f.student(),f.classroom(),quiz.id());}));
            try {
                assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();
                long cutoff=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean blocked=false;
                while(System.nanoTime()<cutoff && !(blocked=Boolean.TRUE.equals(jdbc.queryForObject("SELECT cardinality(pg_blocking_pids(?))>0",Boolean.class,pid.get())))) Thread.sleep(10);
                assertThat(blocked).isTrue();
            } finally {release.countDown();}
            archive.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->start.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(AppException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tbl_quiz_attempt WHERE quiz_id=?",Long.class,quiz.id())).isZero();
        } finally {release.countDown();}
    }
    private static void await(CountDownLatch gate){try{if(!gate.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Test barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    private record Fixture(UUID teacher,UUID student,UUID classroom){}
    private Fixture fixture(){UUID teacher=account(AccountRole.TEACHER),student=account(AccountRole.STUDENT);UUID classroom=classrooms.create(teacher,new ClassroomDtos.Create("Linked items",null,JoinMode.AUTO_JOIN,null,null)).id();classrooms.directAdd(teacher,classroom,new ClassroomDtos.DirectAdd(accounts.findById(student).orElseThrow().getUsername()));return new Fixture(teacher,student,classroom);}
    private UUID account(AccountRole role){var a=new AccountEntity();a.setId(UUID.randomUUID());a.setUsername("linked-"+a.getId());a.setEmail(a.getUsername()+"@example.test");a.setFullName("Linked content");a.setPasswordHash("unused");a.setRole(role);a.setStatus(AccountStatus.ACTIVE);return accounts.saveAndFlush(a).getId();}
    private UUID lesson(Fixture f,LinkedItemType type,UUID item,boolean published){var l=lessons.create(f.teacher(),f.classroom(),new LessonDtos.CreateLessonRequest("Lesson",null,null,null,CompletionMode.MANUAL));lessons.linkItem(f.teacher(),f.classroom(),l.id(),new LessonDtos.LinkItemRequest(type,item));if(published)lessons.publish(f.teacher(),f.classroom(),l.id());return l.id();}
    private QuizDtos.QuizView quiz(Fixture f){var question=new QuizDtos.Question(UUID.randomUUID(),"Choose",QuizDtos.QuestionType.SINGLE_CHOICE,QuizDtos.Scoring.ALL_OR_NOTHING,BigDecimal.ONE,List.of(new QuizDtos.Option(UUID.randomUUID(),"Right",true),new QuizDtos.Option(UUID.randomUUID(),"Wrong",false)));var q=quizzes.create(f.teacher(),f.classroom(),new QuizDtos.Configure("Quiz",true,null,null,600,5,false,false,false,QuizDtos.ReleasePolicy.IMMEDIATE,QuizDtos.TimeoutMode.AUTO_SUBMIT,List.of(question)));quizzes.publish(f.teacher(),f.classroom(),q.id());return q;}
}
