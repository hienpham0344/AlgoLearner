package com.algolearner.platform.quiz;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.algolearner.platform.quiz.QuizDtos.*;
import static com.algolearner.platform.quiz.QuizFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class QuizServiceTest {
    final QuizRepository quizzes=mock(QuizRepository.class);
    final QuizAttemptRepository attempts=mock(QuizAttemptRepository.class);
    final QuizOverrideRepository overrides=mock(QuizOverrideRepository.class);
    final QuizGradeOverrideRepository grades=mock(QuizGradeOverrideRepository.class);
    final ClassroomRepository classrooms=mock(ClassroomRepository.class);
    final ClassroomMembershipRepository memberships=mock(ClassroomMembershipRepository.class);
    final AccountRepository accounts=mock(AccountRepository.class);
    final AuthAuditPort audit=mock(AuthAuditPort.class);
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final Clock clock=mock(Clock.class);
    final QuizService service=new QuizService(quizzes,attempts,overrides,grades,classrooms,memberships,accounts,audit,json,clock);
    final AccountEntity teacher=account(AccountRole.TEACHER),student=account(AccountRole.STUDENT),other=account(AccountRole.STUDENT);
    final ClassroomEntity classroom=new ClassroomEntity();
    final QuizEntity quiz=new QuizEntity();
    final List<QuizAttemptEntity> history=new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        classroom.setId(UUID.randomUUID()); classroom.setOwner(teacher); classroom.setStatus(ClassroomStatus.ACTIVE);
        quiz.setId(UUID.randomUUID()); quiz.setClassroom(classroom); quiz.setTitle("Quiz"); quiz.setStatus("PUBLISHED");
        quiz.setConfiguration(json.writeValueAsString(config(Timeout.AUTO_SUBMIT,Publication.AUTO)));
        for(var a:List.of(teacher,student,other)) when(accounts.findById(a.getId())).thenReturn(Optional.of(a));
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(quizzes.lockById(quiz.getId())).thenReturn(Optional.of(quiz)); when(quizzes.findById(quiz.getId())).thenReturn(Optional.of(quiz));
        when(quizzes.save(any())).thenAnswer(i->i.getArgument(0));
        for(var a:List.of(student,other)) {
            var m=new ClassroomMembershipEntity(); m.setStatus(MembershipStatus.ACTIVE);
            when(memberships.findByClassroomIdAndStudentId(classroom.getId(),a.getId())).thenReturn(Optional.of(m));
        }
        when(attempts.findByQuizIdAndStudentIdOrderByNumberAsc(quiz.getId(),student.getId())).thenReturn(history);
        when(attempts.save(any())).thenAnswer(i->{ QuizAttemptEntity a=i.getArgument(0); history.add(a);
            when(attempts.lockById(a.getId())).thenReturn(Optional.of(a)); return a; });
        when(attempts.findByQuizId(eq(quiz.getId()),any(Pageable.class))).thenAnswer(i->new PageImpl<>(history));
    }
    @Test void ownerCreatesDraftWithConfiguration() {
        var result=service.create(teacher.getId(),classroom.getId(),new Write(" Quiz ",config(Timeout.AUTO_SUBMIT,Publication.AUTO)));
        assertThat(result.status()).isEqualTo("DRAFT"); assertThat(result.title()).isEqualTo("Quiz");
        assertThat(result.teacherConfiguration()).isNotNull();
    }
    @Test void studentCannotCreateQuiz() {
        assertThatThrownBy(()->service.create(student.getId(),classroom.getId(),new Write("Q",config(Timeout.AUTO_SUBMIT,Publication.AUTO)))).isInstanceOf(AppException.class);
        verify(quizzes,never()).save(any());
    }
    @Test void startPersistsSnapshotAndServerDeadlineWithoutAnswerLeak() {
        var a=start(); assertThat(a.deadline()).isEqualTo(NOW.plusSeconds(60)); assertThat(history).hasSize(1);
        assertThat(a.questions().getFirst().options()).allMatch(o->o.correct()==null);
        assertThat(a.questions().getFirst().explanation()).isNull(); assertThat(a.score()).isNull();
        assertThat(history.getFirst().getSnapshot()).contains("correct");
    }
    @Test void startResumesExistingAttemptAndPreservesTimer() {
        var a=start(); when(clock.instant()).thenReturn(NOW.plusSeconds(20)); var b=start();
        assertThat(b.id()).isEqualTo(a.id()); assertThat(b.deadline()).isEqualTo(a.deadline()); assertThat(history).hasSize(1);
    }
    @Test void autosaveAndResumeRestoreOnlySavedAnswers() {
        var a=start(); var saved=service.autosave(student.getId(),classroom.getId(),quiz.getId(),a.id(),QUESTION,new Answer(List.of(CORRECT)));
        assertThat(saved.answers()).containsEntry(QUESTION,List.of(CORRECT));
        assertThat(service.resume(student.getId(),classroom.getId(),quiz.getId(),a.id()).answers()).isEqualTo(saved.answers());
        assertThat(history.getFirst().getScore()).isNull();
    }
    @Test void invalidOptionsAndMultipleSingleChoiceSelectionsDoNotMutateAnswers() {
        var a=start();
        for(var selected:List.of(List.of(UUID.randomUUID()),List.of(CORRECT,CORRECT),List.of(CORRECT,WRONG)))
            assertThatThrownBy(()->service.autosave(student.getId(),classroom.getId(),quiz.getId(),a.id(),QUESTION,new Answer(selected))).isInstanceOf(AppException.class);
        assertThat(history.getFirst().getAnswers()).isEqualTo("{}");
    }
    @Test void submitGradesAndIsIdempotent() {
        var a=start(); save(a.id(),CORRECT); var first=submit(a.id()); when(clock.instant()).thenReturn(NOW.plusSeconds(10)); var again=submit(a.id());
        assertThat(first.score()).isEqualByComparingTo("100"); assertThat(first.completed()).isTrue();
        assertThat(again.submittedAt()).isEqualTo(first.submittedAt()); assertThat(history).hasSize(1);
    }
    @Test void autosaveAfterManualSubmitIsRejectedWithoutChangingScore() {
        var a=start(); save(a.id(),CORRECT); submit(a.id());
        assertThatThrownBy(()->save(a.id(),WRONG)).isInstanceOf(AppException.class);
        assertThat(history.getFirst().getScore()).isEqualByComparingTo("100");
    }
    @Test void boundaryExpiryAutoSubmitsSavedAnswerAndRejectsLateWrite() {
        var a=start(); save(a.id(),CORRECT); when(clock.instant()).thenReturn(a.deadline());
        assertThatThrownBy(()->save(a.id(),WRONG)).isInstanceOf(AppException.class);
        assertThat(history.getFirst().getStatus()).isEqualTo("SUBMITTED");
        assertThat(history.getFirst().getScore()).isEqualByComparingTo("100");
        assertThat(history.getFirst().getSubmittedAt()).isEqualTo(a.deadline());
    }
    @Test void lockWithoutSubmitCreatesNoOfficialResult() throws Exception {
        configure(config(Timeout.LOCK_WITHOUT_SUBMIT,Publication.AUTO)); var a=start(); save(a.id(),CORRECT);
        when(clock.instant()).thenReturn(a.deadline());
        assertThatThrownBy(()->submit(a.id())).isInstanceOf(AppException.class);
        assertThat(history.getFirst().getStatus()).isEqualTo("EXPIRED"); assertThat(history.getFirst().getScore()).isNull();
        assertThat(service.result(student.getId(),classroom.getId(),quiz.getId(),student.getId()).finalScore()).isNull();
    }
    @Test void highestScoreWinsAcrossAttemptsAndHistoryIsRetained() {
        var a=start(); save(a.id(),CORRECT); submit(a.id()); var b=start(); save(b.id(),WRONG); submit(b.id());
        var result=service.result(student.getId(),classroom.getId(),quiz.getId(),student.getId());
        assertThat(result.finalScore()).isEqualByComparingTo("100"); assertThat(result.attemptsUsed()).isEqualTo(2);
        assertThatThrownBy(this::start).isInstanceOf(AppException.class); assertThat(history).hasSize(2);
    }
    @Test void manualPublicationHidesScoreCorrectAnswersAndCompletionUntilPublished() throws Exception {
        configure(config(Timeout.AUTO_SUBMIT,Publication.MANUAL)); var a=start(); save(a.id(),CORRECT); var result=submit(a.id());
        assertThat(result.score()).isNull(); assertThat(result.completed()).isNull();
        assertThat(result.questions().getFirst().options()).allMatch(o->o.correct()==null);
        service.publishGrades(teacher.getId(),classroom.getId(),quiz.getId(),true);
        var released=service.resume(student.getId(),classroom.getId(),quiz.getId(),a.id());
        assertThat(released.score()).isEqualByComparingTo("100"); assertThat(released.questions().getFirst().options().getFirst().correct()).isTrue();
    }
    @Test void studentCannotAccessAnotherAttemptOrResult() {
        var a=start();
        assertThatThrownBy(()->service.resume(other.getId(),classroom.getId(),quiz.getId(),a.id())).isInstanceOf(AppException.class);
        assertThatThrownBy(()->service.result(other.getId(),classroom.getId(),quiz.getId(),student.getId())).isInstanceOf(AppException.class);
        assertThatThrownBy(()->service.submit(other.getId(),classroom.getId(),quiz.getId(),a.id())).isInstanceOf(AppException.class);
    }
    @Test void classroomMismatchReturnsNotFound() {
        assertThatThrownBy(()->service.get(student.getId(),UUID.randomUUID(),quiz.getId())).isInstanceOf(AppException.class)
                .extracting(e->((AppException)e).getHttpStatus().value()).isEqualTo(404);
    }
    @Test void draftClosedFutureStartAndDeadlineBlockNewAttempt() throws Exception {
        quiz.setStatus("DRAFT"); assertThatThrownBy(this::start).isInstanceOf(AppException.class);
        quiz.setStatus("CLOSED"); assertThatThrownBy(this::start).isInstanceOf(AppException.class);
        quiz.setStatus("PUBLISHED"); configure(timed(NOW.plusSeconds(1),NOW.plusSeconds(100),false));
        assertThatThrownBy(this::start).isInstanceOf(AppException.class);
        configure(timed(null,NOW,false)); assertThatThrownBy(this::start).isInstanceOf(AppException.class); assertThat(history).isEmpty();
    }
    @Test void lateAttemptsAreMarkedButClosedStillBlocks() throws Exception {
        configure(timed(null,NOW.minusSeconds(1),true)); assertThat(start().late()).isTrue();
        submit(history.getFirst().getId()); quiz.setStatus("CLOSED"); assertThatThrownBy(this::start).isInstanceOf(AppException.class);
    }
    @Test void overrideUpdatesLimitsWithoutDeletingHistoryAndRecordsAudit() {
        var a=start(); submit(a.id()); service.overrideAccess(teacher.getId(),classroom.getId(),quiz.getId(),student.getId(),new AccessOverride(true,3,NOW.plusSeconds(500),120));
        var capture=org.mockito.ArgumentCaptor.forClass(QuizOverrideEntity.class); verify(overrides).save(capture.capture());
        assertThat(capture.getValue().getResetAtCount()).isEqualTo(1); assertThat(capture.getValue().getExtraAttempts()).isEqualTo(3);
        assertThat(history).hasSize(1); verify(audit).record(eq(teacher.getId()),eq(student.getId()),eq(AuditActorType.USER),eq("QUIZ_ACCESS_OVERRIDE"),eq(AuditOutcome.SUCCESS),eq(NOW));
    }
    @Test void gradeOverrideRetainsOriginalScoreAndAddsAudit() {
        var a=start(); submit(a.id()); var changed=service.overrideGrade(teacher.getId(),classroom.getId(),quiz.getId(),student.getId(),new GradeOverride(new BigDecimal("75"),"Review"));
        assertThat(changed.originalScore()).isEqualByComparingTo("0"); assertThat(changed.finalScore()).isEqualByComparingTo("75");
        assertThat(history.getFirst().getScore()).isEqualByComparingTo("0"); verify(grades).save(any()); verify(audit).record(any(),any(),any(),eq("QUIZ_GRADE_OVERRIDE"),any(),any());
    }
    @Test void duplicateCopiesConfigurationButNoStudentData() {
        var a=start(); var duplicate=service.duplicate(teacher.getId(),classroom.getId(),quiz.getId());
        assertThat(duplicate.id()).isNotEqualTo(quiz.getId()); assertThat(duplicate.status()).isEqualTo("DRAFT");
        assertThat(duplicate.teacherConfiguration()).isEqualTo(config(Timeout.AUTO_SUBMIT,Publication.AUTO)); assertThat(history).hasSize(1);
    }
    @Test void contentChangeAfterAttemptIsRejectedButTitleCanChange() {
        start(); assertThatThrownBy(()->service.update(teacher.getId(),classroom.getId(),quiz.getId(),new Write("Q",config(Timeout.LOCK_WITHOUT_SUBMIT,Publication.AUTO)))).isInstanceOf(AppException.class);
        assertThat(service.update(teacher.getId(),classroom.getId(),quiz.getId(),new Write("Renamed",config(Timeout.AUTO_SUBMIT,Publication.AUTO))).title()).isEqualTo("Renamed");
    }
    @Test void archivedClassAndInactiveOwnerPreventWrites() {
        classroom.setStatus(ClassroomStatus.ARCHIVED); assertThatThrownBy(this::start).isInstanceOf(AppException.class);
        classroom.setStatus(ClassroomStatus.ACTIVE); teacher.setStatus(AccountStatus.DEACTIVATED); assertThatThrownBy(this::start).isInstanceOf(AppException.class);
    }
    @Test void inactiveMembershipCannotStart() {
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(),student.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(this::start).isInstanceOf(AppException.class); verify(attempts,never()).save(any());
    }
    @Test void passingScoreRequiresSubmittedPassingAttempt() throws Exception {
        var c=config(Timeout.AUTO_SUBMIT,Publication.AUTO);
        configure(new Config(c.kind(),c.maxAttempts(),null,null,false,60,c.timeout(),false,false,c.scoring(),
                Completion.PASSING_SCORE,new BigDecimal("60"),c.publication(),true,true,c.questions()));
        var a=start(); assertThat(service.result(student.getId(),classroom.getId(),quiz.getId(),student.getId()).completed()).isFalse();
        assertThat(submit(a.id()).completed()).isFalse();
        var b=start();save(b.id(),CORRECT);assertThat(submit(b.id()).completed()).isTrue();
        assertThat(service.result(student.getId(),classroom.getId(),quiz.getId(),student.getId()).completed()).isTrue();
    }
    @Test void ungradedQuizRecordsCompletionWithoutPublishingScore() throws Exception {
        var c=config(Timeout.AUTO_SUBMIT,Publication.AUTO);
        configure(new Config(Kind.UNGRADED,c.maxAttempts(),null,null,false,60,c.timeout(),false,false,c.scoring(),
                c.completion(),null,c.publication(),true,true,c.questions()));
        var a=start();save(a.id(),CORRECT);var result=submit(a.id());
        assertThat(result.score()).isNull();assertThat(result.completed()).isTrue();
        var official=service.result(student.getId(),classroom.getId(),quiz.getId(),student.getId());
        assertThat(official.originalAutoScore()).isNull();assertThat(official.finalScore()).isNull();assertThat(official.completed()).isTrue();
        assertThatThrownBy(()->service.overrideGrade(teacher.getId(),classroom.getId(),quiz.getId(),student.getId(),new GradeOverride(BigDecimal.TEN,"Review"))).isInstanceOf(AppException.class);
    }
    @Test void visibilityCanBeWithdrawnAfterAttemptsWithoutChangingSnapshot() throws Exception {
        var a=start();save(a.id(),CORRECT);submit(a.id());String original=history.getFirst().getSnapshot();
        var c=config(Timeout.AUTO_SUBMIT,Publication.AUTO);
        var hidden=new Config(c.kind(),c.maxAttempts(),null,null,false,60,c.timeout(),false,false,c.scoring(),c.completion(),null,
                Publication.MANUAL,false,false,c.questions());
        service.update(teacher.getId(),classroom.getId(),quiz.getId(),new Write("Hidden",hidden));
        var result=service.resume(student.getId(),classroom.getId(),quiz.getId(),a.id());assertThat(result.score()).isNull();
        assertThat(result.questions().getFirst().options()).allMatch(o->o.correct()==null);assertThat(history.getFirst().getSnapshot()).isEqualTo(original);
        service.publishGrades(teacher.getId(),classroom.getId(),quiz.getId(),true);
        var released=service.resume(student.getId(),classroom.getId(),quiz.getId(),a.id());assertThat(released.score()).isEqualByComparingTo("100");
        assertThat(released.questions().getFirst().options()).allMatch(o->o.correct()==null);assertThat(released.questions().getFirst().explanation()).isNull();
    }
    @Test void onlyUnusedDraftCanBeDeleted() {
        assertThatThrownBy(()->service.delete(teacher.getId(),classroom.getId(),quiz.getId())).isInstanceOf(AppException.class);
        quiz.setStatus("DRAFT");service.delete(teacher.getId(),classroom.getId(),quiz.getId());verify(quizzes).delete(quiz);
        quiz.setStatus("PUBLISHED");start();quiz.setStatus("DRAFT");
        assertThatThrownBy(()->service.delete(teacher.getId(),classroom.getId(),quiz.getId())).isInstanceOf(AppException.class);
        verify(quizzes,times(1)).delete(any());
    }
    @Test void ownerReadsAppendOnlyGradeHistoryButStudentCannot() {
        var g=new QuizGradeOverrideEntity();g.setId(UUID.randomUUID());g.setActor(teacher);g.setOriginalScore(BigDecimal.ZERO);
        g.setFinalScore(BigDecimal.TEN);g.setReason("Review");g.setCreatedAt(NOW);
        when(grades.findByQuizIdAndStudentId(eq(quiz.getId()),eq(student.getId()),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(g)));
        var result=service.gradeHistory(teacher.getId(),classroom.getId(),quiz.getId(),student.getId(),PageRequest.of(0,20));
        assertThat(result.getData().getItems()).hasSize(1);assertThat(result.getData().getItems().getFirst().reason()).isEqualTo("Review");
        assertThatThrownBy(()->service.gradeHistory(student.getId(),classroom.getId(),quiz.getId(),student.getId(),PageRequest.of(0,20))).isInstanceOf(AppException.class);
    }
    @Test void invalidDirectAccessOverridesDoNotPersist() {
        for(var input:List.of(new AccessOverride(false,-1,null,null),new AccessOverride(false,0,NOW,null),new AccessOverride(false,0,null,0)))
            assertThatThrownBy(()->service.overrideAccess(teacher.getId(),classroom.getId(),quiz.getId(),student.getId(),input)).isInstanceOf(AppException.class);
        verify(overrides,never()).save(any());verifyNoInteractions(audit);
    }
    @Test void invalidDirectGradeOverridesDoNotPersist() {
        var a=start();submit(a.id());
        for(var input:List.of(new GradeOverride(new BigDecimal("101"),"Review"),new GradeOverride(new BigDecimal("75.12345"),"Review"),new GradeOverride(BigDecimal.TEN," "),new GradeOverride(null,"Review")))
            assertThatThrownBy(()->service.overrideGrade(teacher.getId(),classroom.getId(),quiz.getId(),student.getId(),input)).isInstanceOf(AppException.class);
        verify(grades,never()).save(any());verifyNoInteractions(audit);
    }
    @Test void disabledTimerHasNoDeadlineAndResumeNeverAddsOne() throws Exception {
        var c=config(Timeout.AUTO_SUBMIT,Publication.AUTO);
        configure(new Config(c.kind(),c.maxAttempts(),null,null,false,null,c.timeout(),false,false,c.scoring(),
                c.completion(),null,c.publication(),true,true,c.questions()));
        var a=start();assertThat(a.deadline()).isNull();
        when(clock.instant()).thenReturn(NOW.plusSeconds(100000));
        var resumed=service.resume(student.getId(),classroom.getId(),quiz.getId(),a.id());
        assertThat(resumed.deadline()).isNull();assertThat(resumed.status()).isEqualTo("ACTIVE");
        assertThat(resumed.startedAt()).isEqualTo(NOW);
    }
    @Test void effectiveOverridesAreSnapshottedAndLaterChangesDoNotResetAttempt() throws Exception {
        configure(timed(null,NOW.minusSeconds(1),false));
        var override=new QuizOverrideEntity();override.setDeadline(NOW.plusSeconds(500));override.setTimeLimitSeconds(120);override.setExtraAttempts(2);
        when(overrides.findByQuizIdAndStudentId(quiz.getId(),student.getId())).thenReturn(Optional.of(override));
        var a=start();assertThat(a.deadline()).isEqualTo(NOW.plusSeconds(120));assertThat(a.late()).isFalse();
        var effective=json.readValue(history.getFirst().getSnapshot(),Config.class);
        assertThat(effective.deadline()).isEqualTo(NOW.plusSeconds(500));assertThat(effective.timeLimitSeconds()).isEqualTo(120);
        String snapshot=history.getFirst().getSnapshot();override.setDeadline(NOW.plusSeconds(900));override.setTimeLimitSeconds(300);
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        var resumed=service.resume(student.getId(),classroom.getId(),quiz.getId(),a.id());
        assertThat(resumed.deadline()).isEqualTo(a.deadline());assertThat(history.getFirst().getSnapshot()).isEqualTo(snapshot);
        assertThat(service.result(student.getId(),classroom.getId(),quiz.getId(),student.getId()).attemptsAllowed()).isEqualTo(4);
    }
    @Test void teacherCanReadAndOverrideRetainedResultAfterStudentLeaves() {
        var a=start();save(a.id(),CORRECT);submit(a.id());
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(),student.getId())).thenReturn(Optional.empty());
        assertThat(service.result(teacher.getId(),classroom.getId(),quiz.getId(),student.getId()).originalAutoScore()).isEqualByComparingTo("100");
        var changed=service.overrideGrade(teacher.getId(),classroom.getId(),quiz.getId(),student.getId(),new GradeOverride(new BigDecimal("90"),"Review after leave"));
        assertThat(changed.originalScore()).isEqualByComparingTo("100");assertThat(changed.finalScore()).isEqualByComparingTo("90");
        assertThatThrownBy(()->service.result(student.getId(),classroom.getId(),quiz.getId(),student.getId())).isInstanceOf(AppException.class);
    }
    @Test void numericJsonRoundTripDoesNotPreventVisibilityUpdates() throws Exception {
        var c=config(Timeout.AUTO_SUBMIT,Publication.AUTO); var q=c.questions().getFirst();
        configure(new Config(c.kind(),c.maxAttempts(),c.startTime(),c.deadline(),c.allowLateAttempts(),c.timeLimitSeconds(),c.timeout(),
                c.shuffleQuestions(),c.shuffleAnswers(),c.scoring(),c.completion(),c.passingScore(),c.publication(),c.showCorrectAnswers(),c.showExplanations(),
                List.of(new Question(q.id(),q.text(),q.type(),q.points().setScale(2),q.explanation(),q.options()))));
        start();
        assertThat(service.update(teacher.getId(),classroom.getId(),quiz.getId(),new Write("Quiz",c)).title()).isEqualTo("Quiz");
    }
    private AttemptView start(){return service.start(student.getId(),classroom.getId(),quiz.getId());}
    private void save(UUID id,UUID option){service.autosave(student.getId(),classroom.getId(),quiz.getId(),id,QUESTION,new Answer(List.of(option)));}
    private AttemptView submit(UUID id){return service.submit(student.getId(),classroom.getId(),quiz.getId(),id);}
    private void configure(Config c)throws Exception{quiz.setConfiguration(json.writeValueAsString(c));}
    private static AccountEntity account(AccountRole role){var a=new AccountEntity();a.setId(UUID.randomUUID());a.setRole(role);a.setStatus(AccountStatus.ACTIVE);return a;}
}
