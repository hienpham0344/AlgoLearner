package com.algolearner.platform.quiz;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static com.algolearner.platform.quiz.QuizDtos.*;
import static com.algolearner.platform.quiz.QuizFixtures.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:quiz-concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false","logging.level.root=WARN","logging.level.org.hibernate.SQL=WARN","debug=false",
        "app.quiz.expiry-interval-ms=3600000",
        "app.security.jwt.issuer=https://issuer.example.test","app.security.jwt.audience=quiz-tests",
        "app.security.jwt.jwk-set-uri=https://issuer.example.test/.well-known/jwks.json"})
@ActiveProfiles("test") @Import(QuizApiTest.Config.class)
class QuizConcurrencyTest {
    @Autowired QuizService service;
    @Autowired com.algolearner.platform.auth.AccountRepository accounts;
    @Autowired ClassroomRepository classrooms;
    @Autowired ClassroomMembershipRepository memberships;
    @Autowired QuizAttemptRepository attempts;
    @Autowired QuizApiTest.MutableClock clock;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired QuizExpiryScheduler scheduler;
    UUID teacher,student,classId,quizId;
    final UUID secondQuestion=UUID.randomUUID(),secondCorrect=UUID.randomUUID(),secondWrong=UUID.randomUUID();

    @BeforeEach void setup() {
        clock.now=NOW;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx->{
            var t=account(AccountRole.TEACHER);var s=account(AccountRole.STUDENT);teacher=t.getId();student=s.getId();
            var c=new ClassroomEntity();c.setId(UUID.randomUUID());c.setOwner(t);c.setName("Concurrency");c.setStatus(ClassroomStatus.ACTIVE);
            c.setClassCode(UUID.randomUUID().toString().substring(0,12));c.setJoinMode(JoinMode.AUTO_JOIN);c.setJoiningOpen(true);classrooms.save(c);classId=c.getId();
            var m=new ClassroomMembershipEntity();m.setId(UUID.randomUUID());m.setClassroom(c);m.setStudent(s);m.setStatus(MembershipStatus.ACTIVE);m.setJoinedAt(NOW);memberships.save(m);
            var cfg=config(Timeout.AUTO_SUBMIT,Publication.AUTO);var questions=new ArrayList<>(cfg.questions());
            questions.add(new Question(secondQuestion,"3 + 3?",QuestionType.SINGLE_CHOICE,BigDecimal.ONE,"Addition",List.of(new Option(secondCorrect,"6",true,null),new Option(secondWrong,"7",false,null))));
            var both=new Config(cfg.kind(),cfg.maxAttempts(),null,null,false,60,cfg.timeout(),false,false,cfg.scoring(),cfg.completion(),null,cfg.publication(),true,true,questions);
            quizId=service.create(teacher,classId,new Write("Concurrent",both)).id();service.status(teacher,classId,quizId,Status.PUBLISHED);
        });
    }
    @Test void concurrentStartReturnsOneActiveAttempt() throws Exception {
        var results=parallel(()->service.start(student,classId,quizId),()->service.start(student,classId,quizId));
        assertThat(results.getFirst().id()).isEqualTo(results.getLast().id());
        assertThat(attempts.findByQuizIdAndStudentIdOrderByNumberAsc(quizId,student)).hasSize(1);
    }
    @Test void concurrentIndependentAutosavesPreserveBothAnswers() throws Exception {
        var a=service.start(student,classId,quizId);
        parallel(()->service.autosave(student,classId,quizId,a.id(),QUESTION,new Answer(List.of(CORRECT))),
                ()->service.autosave(student,classId,quizId,a.id(),secondQuestion,new Answer(List.of(secondCorrect))));
        var resumed=service.resume(student,classId,quizId,a.id());
        assertThat(resumed.answers()).containsEntry(QUESTION,List.of(CORRECT)).containsEntry(secondQuestion,List.of(secondCorrect));
        assertThat(service.submit(student,classId,quizId,a.id()).score()).isEqualByComparingTo("100");
    }
    @Test void concurrentSubmitCreatesOneStableResult() throws Exception {
        var a=service.start(student,classId,quizId);
        service.autosave(student,classId,quizId,a.id(),QUESTION,new Answer(List.of(CORRECT)));
        var results=parallel(()->service.submit(student,classId,quizId,a.id()),()->service.submit(student,classId,quizId,a.id()));
        assertThat(results).allSatisfy(r->{assertThat(r.score()).isEqualByComparingTo("50");assertThat(r.status()).isEqualTo("SUBMITTED");});
        assertThat(results.getFirst().submittedAt()).isEqualTo(results.getLast().submittedAt());
        assertThat(attempts.findByQuizIdAndStudentIdOrderByNumberAsc(quizId,student)).hasSize(1);
    }
    @Test void schedulerGradesLatestCommittedAutosaves() {
        var a=service.start(student,classId,quizId);
        service.autosave(student,classId,quizId,a.id(),QUESTION,new Answer(List.of(CORRECT)));
        service.autosave(student,classId,quizId,a.id(),secondQuestion,new Answer(List.of(secondCorrect)));
        clock.now=NOW.plusSeconds(60); scheduler.expire();
        var persisted=attempts.findById(a.id()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("SUBMITTED");assertThat(persisted.getScore()).isEqualByComparingTo("100");
    }
    @Test void concurrentAutosaveAndSubmitProduceConsistentSavedAnswerAndScore() throws Exception {
        var a=service.start(student,classId,quizId);
        var results=QuizConcurrencyTest.<Object>parallel(()->{
            try{return service.autosave(student,classId,quizId,a.id(),QUESTION,new Answer(List.of(CORRECT)));}
            catch(com.algolearner.platform.common.exception.AppException e){assertThat(e.getHttpStatus().value()).isEqualTo(409);return "CLOSED";}
        },()->service.submit(student,classId,quizId,a.id()));
        var persisted=attempts.findById(a.id()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("SUBMITTED");
        if(results.getFirst() instanceof String) {
            assertThat(persisted.getAnswers()).isEqualTo("{}");assertThat(persisted.getScore()).isEqualByComparingTo("0");
        } else {
            assertThat(persisted.getAnswers()).contains(CORRECT.toString());assertThat(persisted.getScore()).isEqualByComparingTo("50");
        }
    }
    @Test void lateWriteReturnsConflictAndAutoSubmitCommitsInSeparateTransaction() {
        var a=service.start(student,classId,quizId);
        service.autosave(student,classId,quizId,a.id(),QUESTION,new Answer(List.of(CORRECT)));clock.now=NOW.plusSeconds(60);
        assertThatThrownBy(()->service.autosave(student,classId,quizId,a.id(),secondQuestion,new Answer(List.of(secondCorrect))))
                .isInstanceOf(com.algolearner.platform.common.exception.AppException.class);
        var persisted=attempts.findById(a.id()).orElseThrow();assertThat(persisted.getStatus()).isEqualTo("SUBMITTED");
        assertThat(persisted.getScore()).isEqualByComparingTo("50");assertThat(persisted.getAnswers()).doesNotContain(secondQuestion.toString());
    }
    private static <T> List<T> parallel(Callable<T> first,Callable<T> second)throws Exception {
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Barrier timeout");return first.call();});
            var b=executor.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Barrier timeout");return second.call();});
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
    private AccountEntity account(AccountRole role){var a=new AccountEntity();a.setId(UUID.randomUUID());a.setRole(role);a.setStatus(AccountStatus.ACTIVE);a.setUsername("q"+UUID.randomUUID());a.setEmail(UUID.randomUUID()+"@test.example");return accounts.save(a);}
}
