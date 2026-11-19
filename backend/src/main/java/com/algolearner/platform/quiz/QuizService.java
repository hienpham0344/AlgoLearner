package com.algolearner.platform.quiz;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.common.exception.AppException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.algolearner.platform.quiz.QuizDtos.*;

@Service
@Profile("!mock")
@Transactional(readOnly=true)
public class QuizService {
    private final QuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final QuizOverrideRepository overrides;
    private final QuizGradeOverrideRepository grades;
    private final ClassroomRepository classrooms;
    private final ClassroomMembershipRepository memberships;
    private final AccountRepository accounts;
    private final AuthAuditPort audit;
    private final ObjectMapper json;
    private final Clock clock;

    public QuizService(QuizRepository quizzes, QuizAttemptRepository attempts, QuizOverrideRepository overrides,
                       QuizGradeOverrideRepository grades, ClassroomRepository classrooms,
                       ClassroomMembershipRepository memberships, AccountRepository accounts,
                       AuthAuditPort audit, ObjectMapper json, Clock clock) {
        this.quizzes=quizzes; this.attempts=attempts; this.overrides=overrides; this.grades=grades;
        this.classrooms=classrooms; this.memberships=memberships; this.accounts=accounts;
        this.audit=audit; this.json=json; this.clock=clock;
    }

    @Transactional
    public QuizView create(UUID actor, UUID classId, Write input) {
        var c=classroom(classId); owner(actor,c,true); validateWrite(input);
        var q=new QuizEntity(); q.setId(UUID.randomUUID()); q.setClassroom(c); q.setTitle(input.title().strip());
        q.setConfiguration(write(input.config())); q.setStatus("DRAFT");
        return view(quizzes.save(q),true);
    }

    public PageResponse<QuizView> list(UUID actor, UUID classId, Pageable page) {
        var c=classroom(classId); boolean teacher=access(actor,c);
        var found=teacher ? quizzes.findByClassroomId(classId,page)
                : quizzes.findByClassroomIdAndStatusNot(classId,"DRAFT",page);
        return PageResponse.fromPage(found.map(q -> view(q,teacher)));
    }

    public QuizView get(UUID actor, UUID classId, UUID quizId) {
        var q=quiz(classId,quizId,false); boolean teacher=access(actor,q.getClassroom());
        if (!teacher && q.getStatus().equals("DRAFT")) throw error(HttpStatus.NOT_FOUND,"QUIZ_NOT_FOUND");
        return view(q,teacher);
    }

    @Transactional
    public QuizView update(UUID actor, UUID classId, UUID quizId, Write input) {
        var q=quiz(classId,quizId,true); owner(actor,q.getClassroom(),true); validateWrite(input);
        if (attempts.findByQuizId(quizId,PageRequest.of(0,1)).hasContent()
                && !sameAssessment(config(q),input.config())) throw error(HttpStatus.CONFLICT,"QUIZ_CONFIGURATION_FROZEN");
        q.setTitle(input.title().strip()); q.setConfiguration(write(input.config())); return view(q,true);
    }

    @Transactional
    public QuizView status(UUID actor, UUID classId, UUID quizId, Status status) {
        var q=quiz(classId,quizId,true); owner(actor,q.getClassroom(),true);
        if(status==Status.PUBLISHED) validate(config(q));
        q.setStatus(status.name()); return view(q,true);
    }

    @Transactional
    public QuizView duplicate(UUID actor, UUID classId, UUID quizId) {
        var q=quiz(classId,quizId,true); owner(actor,q.getClassroom(),true);
        return create(actor,classId,new Write(q.getTitle(),config(q)));
    }

    @Transactional
    public void publishGrades(UUID actor, UUID classId, UUID quizId, boolean published) {
        var q=quiz(classId,quizId,true); owner(actor,q.getClassroom(),true); q.setGradesPublished(published);
    }

    @Transactional
    public AttemptView start(UUID actor, UUID classId, UUID quizId) {
        var q=quiz(classId,quizId,true); student(actor,q.getClassroom()); writable(q.getClassroom());
        var history=history(quizId,actor);
        for(var a:history) if(a.getStatus().equals("ACTIVE")) {
            expire(a);
            if(a.getStatus().equals("ACTIVE")) return attemptView(q,a,false);
        }
        var cfg=config(q); var now=clock.instant(); var override=overrides.findByQuizIdAndStudentId(quizId,actor).orElse(null);
        Instant due=override!=null && override.getDeadline()!=null ? override.getDeadline():cfg.deadline();
        if(!q.getStatus().equals("PUBLISHED") || cfg.startTime()!=null && now.isBefore(cfg.startTime())
                || due!=null && !now.isBefore(due) && !cfg.allowLateAttempts()) throw error(HttpStatus.CONFLICT,"QUIZ_UNAVAILABLE");
        if(history.size()>=allowed(cfg,override)) throw error(HttpStatus.CONFLICT,"ATTEMPT_LIMIT_REACHED");
        Integer seconds=override!=null && override.getTimeLimitSeconds()!=null ? override.getTimeLimitSeconds():cfg.timeLimitSeconds();
        var a=new QuizAttemptEntity(); a.setId(UUID.randomUUID()); a.setQuiz(q); a.setStudent(account(actor));
        var effective=new Config(cfg.kind(),cfg.maxAttempts(),cfg.startTime(),due,cfg.allowLateAttempts(),seconds,
                cfg.timeout(),cfg.shuffleQuestions(),cfg.shuffleAnswers(),cfg.scoring(),cfg.completion(),cfg.passingScore(),
                cfg.publication(),cfg.showCorrectAnswers(),cfg.showExplanations(),cfg.questions());
        a.setNumber(history.size()+1); a.setSnapshot(write(QuizGrading.shuffled(effective))); a.setAnswers("{}");
        a.setStatus("ACTIVE"); a.setStartedAt(now); a.setDeadline(seconds==null?null:now.plusSeconds(seconds));
        a.setLate(due!=null && !now.isBefore(due));
        return attemptView(q,attempts.save(a),false);
    }

    @Transactional
    public AttemptView resume(UUID actor, UUID classId, UUID quizId, UUID attemptId) {
        var q=quiz(classId,quizId,true); boolean teacher=access(actor,q.getClassroom());
        var a=attempt(q,attemptId); if(!teacher && !a.getStudent().getId().equals(actor)) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION");
        expire(a); return attemptView(q,a,teacher);
    }

    @Transactional(noRollbackFor=AttemptClosed.class)
    public AttemptView autosave(UUID actor, UUID classId, UUID quizId, UUID attemptId, UUID questionId, Answer answer) {
        var q=quiz(classId,quizId,true); student(actor,q.getClassroom()); writable(q.getClassroom());
        var a=ownAttempt(q,attemptId,actor); expire(a);
        if(!a.getStatus().equals("ACTIVE")) throw new AttemptClosed();
        var question=snapshot(a).questions().stream().filter(x->x.id().equals(questionId)).findFirst()
                .orElseThrow(()->error(HttpStatus.BAD_REQUEST,"INVALID_QUESTION"));
        var selected=answer.optionIds();
        if(selected==null || new HashSet<>(selected).size()!=selected.size()
                || question.type()!=QuestionType.MULTIPLE_CHOICE && selected.size()>1
                || selected.stream().anyMatch(id->question.options().stream().noneMatch(o->o.id().equals(id))))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_ANSWER");
        var saved=answers(a); saved.put(questionId,List.copyOf(selected)); a.setAnswers(write(saved));
        return attemptView(q,a,false);
    }

    @Transactional(noRollbackFor=AttemptClosed.class)
    public AttemptView submit(UUID actor, UUID classId, UUID quizId, UUID attemptId) {
        var q=quiz(classId,quizId,true); student(actor,q.getClassroom()); writable(q.getClassroom());
        var a=ownAttempt(q,attemptId,actor); expire(a);
        if(a.getStatus().equals("EXPIRED")) throw new AttemptClosed();
        if(a.getStatus().equals("ACTIVE")) finish(a,clock.instant());
        return attemptView(q,a,false);
    }

    public PageResponse<AttemptView> attempts(UUID actor, UUID classId, UUID quizId, Pageable page) {
        var q=quiz(classId,quizId,false); boolean teacher=access(actor,q.getClassroom());
        if(teacher) return PageResponse.fromPage(attempts.findByQuizId(quizId,page).map(a->attemptView(q,a,true)));
        var all=history(quizId,actor); int from=(int)Math.min(page.getOffset(),all.size());
        var slice=all.subList(from,Math.min(from+page.getPageSize(),all.size())).stream().map(a->attemptView(q,a,false)).toList();
        return PageResponse.fromPage(new PageImpl<>(slice,page,all.size()));
    }

    public Result result(UUID actor, UUID classId, UUID quizId, UUID studentId) {
        var q=quiz(classId,quizId,false); boolean teacher=access(actor,q.getClassroom());
        if(!teacher && !actor.equals(studentId)) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION");
        var all=history(quizId,studentId);
        if(!teacher || all.isEmpty()) member(studentId,q.getClassroom());
        var auto=autoScore(all); var cfg=config(q);
        var grade=grades.findFirstByQuizIdAndStudentIdOrderByRevisionDesc(quizId,studentId);
        boolean visible=teacher || visible(q); var score=grade.map(QuizGradeOverrideEntity::getFinalScore).orElse(auto);
        boolean complete=cfg.completion()==Completion.PASSING_SCORE
                ? score!=null && score.compareTo(cfg.passingScore())>=0 : all.stream().anyMatch(QuizAttemptEntity::isCompleted);
        return new Result(studentId,visible && cfg.kind()==Kind.GRADED?auto:null,
                visible && cfg.kind()==Kind.GRADED?score:null,visible?complete:null,all.size(),
                allowed(cfg,overrides.findByQuizIdAndStudentId(quizId,studentId).orElse(null)));
    }

    @Transactional
    public void overrideAccess(UUID actor, UUID classId, UUID quizId, UUID studentId, AccessOverride input) {
        var q=quiz(classId,quizId,true); owner(actor,q.getClassroom(),true); member(studentId,q.getClassroom());
        if(input==null || input.extraAttempts()<0 || input.extraAttempts()>10000
                || input.timeLimitSeconds()!=null && (input.timeLimitSeconds()<1 || input.timeLimitSeconds()>604800))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_ACCESS_OVERRIDE");
        if(input.deadline()!=null && (!input.deadline().isAfter(clock.instant())
                || config(q).startTime()!=null && !input.deadline().isAfter(config(q).startTime())))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_OVERRIDE_DEADLINE");
        var o=overrides.findByQuizIdAndStudentId(quizId,studentId).orElseGet(()->{
            var n=new QuizOverrideEntity(); n.setId(UUID.randomUUID()); n.setQuiz(q); n.setStudent(account(studentId)); return n;
        });
        if(input.resetAttempts()) o.setResetAtCount(history(quizId,studentId).size());
        o.setExtraAttempts(input.extraAttempts()); o.setDeadline(input.deadline()); o.setTimeLimitSeconds(input.timeLimitSeconds()); overrides.save(o);
        audit.record(actor,studentId,AuditActorType.USER,"QUIZ_ACCESS_OVERRIDE",AuditOutcome.SUCCESS,clock.instant());
    }

    @Transactional
    public GradeChange overrideGrade(UUID actor, UUID classId, UUID quizId, UUID studentId, GradeOverride input) {
        var q=quiz(classId,quizId,true); owner(actor,q.getClassroom(),true);
        if(input==null || input.score()==null || input.score().signum()<0 || input.score().compareTo(new BigDecimal("100"))>0
                || input.score().stripTrailingZeros().scale()>4
                || input.reason()==null || input.reason().isBlank() || input.reason().length()>2000)
            throw error(HttpStatus.BAD_REQUEST,"INVALID_GRADE_OVERRIDE");
        var all=history(quizId,studentId); var original=autoScore(all);
        if(config(q).kind()!=Kind.GRADED || original==null) throw error(HttpStatus.CONFLICT,"NO_GRADED_RESULT");
        var g=new QuizGradeOverrideEntity(); g.setId(UUID.randomUUID()); g.setQuiz(q); g.setStudent(all.getFirst().getStudent());
        g.setActor(account(actor)); g.setOriginalScore(original); g.setFinalScore(input.score()); g.setReason(input.reason().strip());
        g.setRevision(grades.findFirstByQuizIdAndStudentIdOrderByRevisionDesc(quizId,studentId)
                .map(previous->previous.getRevision()+1).orElse(1));
        grades.save(g); audit.record(actor,studentId,AuditActorType.USER,"QUIZ_GRADE_OVERRIDE",AuditOutcome.SUCCESS,clock.instant());
        return new GradeChange(g.getId(),actor,original,g.getFinalScore(),g.getReason(),clock.instant());
    }

    public PageResponse<GradeChange> gradeHistory(UUID actor, UUID classId, UUID quizId, UUID studentId, Pageable page) {
        var q=quiz(classId,quizId,false); owner(actor,q.getClassroom(),false);
        return PageResponse.fromPage(grades.findByQuizIdAndStudentId(quizId,studentId,page).map(g ->
                new GradeChange(g.getId(),g.getActor().getId(),g.getOriginalScore(),g.getFinalScore(),g.getReason(),g.getCreatedAt())));
    }

    @Transactional
    public void delete(UUID actor, UUID classId, UUID quizId) {
        var q=quiz(classId,quizId,true); owner(actor,q.getClassroom(),true);
        if(!q.getStatus().equals("DRAFT") || attempts.findByQuizId(quizId,PageRequest.of(0,1)).hasContent())
            throw error(HttpStatus.CONFLICT,"QUIZ_HAS_RETAINED_DATA");
        overrides.deleteAll(overrides.findByQuizId(quizId));
        quizzes.delete(q);
    }

    @Transactional
    public void expireAttempt(UUID id) {
        var quizId=attempts.findQuizId(id).orElse(null); if(quizId==null) return;
        quizzes.lockById(quizId).orElseThrow();
        attempts.lockById(id).ifPresent(this::expire);
    }

    private void expire(QuizAttemptEntity a) {
        if(a.getStatus().equals("ACTIVE") && a.getDeadline()!=null && !clock.instant().isBefore(a.getDeadline())) {
            if(snapshot(a).timeout()==Timeout.AUTO_SUBMIT) finish(a,a.getDeadline());
            else a.setStatus("EXPIRED");
        }
    }
    private void finish(QuizAttemptEntity a, Instant time) {
        var cfg=snapshot(a); var score=QuizGrading.score(cfg,answers(a)); a.setScore(score);
        a.setCompleted(cfg.completion()==Completion.SUBMIT_IS_ENOUGH || score.compareTo(cfg.passingScore())>=0);
        a.setSubmittedAt(time); a.setStatus("SUBMITTED");
        Instant due=cfg.deadline();
        a.setLate(a.isLate() || due!=null && !time.isBefore(due));
    }
    private QuizAttemptEntity ownAttempt(QuizEntity q,UUID id,UUID actor) {
        var a=attempt(q,id); if(!a.getStudent().getId().equals(actor)) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION"); return a;
    }
    private QuizAttemptEntity attempt(QuizEntity q,UUID id) {
        return attempts.lockById(id).filter(a->a.getQuiz().getId().equals(q.getId())).orElseThrow(()->error(HttpStatus.NOT_FOUND,"ATTEMPT_NOT_FOUND"));
    }
    private List<QuizAttemptEntity> history(UUID q,UUID s) { return attempts.findByQuizIdAndStudentIdOrderByNumberAsc(q,s); }
    private BigDecimal autoScore(List<QuizAttemptEntity> all) { return all.stream().filter(a->a.getStatus().equals("SUBMITTED")).map(QuizAttemptEntity::getScore).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null); }
    private int allowed(Config c,QuizOverrideEntity o) { return c.maxAttempts()+(o==null?0:o.getExtraAttempts()+o.getResetAtCount()); }
    private boolean visible(QuizEntity q) { return config(q).publication()==Publication.AUTO || q.isGradesPublished(); }
    private boolean sameAssessment(Config old,Config next) {
        return normalized(old).equals(normalized(new Config(next.kind(),next.maxAttempts(),next.startTime(),next.deadline(),next.allowLateAttempts(),
                next.timeLimitSeconds(),next.timeout(),next.shuffleQuestions(),next.shuffleAnswers(),next.scoring(),
                next.completion(),next.passingScore(),old.publication(),old.showCorrectAnswers(),old.showExplanations(),next.questions())));
    }
    private Config normalized(Config c) {
        var questions=c.questions().stream().map(q->new Question(q.id(),q.text(),q.type(),q.points().stripTrailingZeros(),q.explanation(),
                q.options().stream().map(o->new Option(o.id(),o.text(),o.correct(),o.weight()==null?null:o.weight().stripTrailingZeros())).toList())).toList();
        return new Config(c.kind(),c.maxAttempts(),c.startTime(),c.deadline(),c.allowLateAttempts(),c.timeLimitSeconds(),c.timeout(),
                c.shuffleQuestions(),c.shuffleAnswers(),c.scoring(),c.completion(),c.passingScore()==null?null:c.passingScore().stripTrailingZeros(),
                c.publication(),c.showCorrectAnswers(),c.showExplanations(),questions);
    }
    private AttemptView attemptView(QuizEntity q,QuizAttemptEntity a,boolean teacher) {
        var cfg=snapshot(a); boolean released=teacher || a.getStatus().equals("SUBMITTED") && visible(q);
        return new AttemptView(a.getId(),a.getStudent().getId(),a.getNumber(),a.getStatus(),clock.instant(),a.getStartedAt(),a.getDeadline(),a.getSubmittedAt(),a.isLate(),
                released && cfg.kind()==Kind.GRADED?a.getScore():null,released?a.isCompleted():null,
                QuizGrading.questionViews(cfg,teacher || released && config(q).showCorrectAnswers(),teacher || released && config(q).showExplanations()),answers(a));
    }
    private QuizView view(QuizEntity q,boolean teacher) {
        var c=config(q); return new QuizView(q.getId(),q.getClassroom().getId(),q.getTitle(),q.getStatus(),teacher?c:null,
                c.kind(),c.maxAttempts(),c.startTime(),c.deadline(),c.timeLimitSeconds(),teacher?QuizGrading.questionViews(c,true,true):List.of());
    }
    private QuizEntity quiz(UUID classId,UUID id,boolean lock) {
        return (lock?quizzes.lockById(id):quizzes.findById(id)).filter(q->q.getClassroom().getId().equals(classId))
                .orElseThrow(()->error(HttpStatus.NOT_FOUND,"QUIZ_NOT_FOUND"));
    }
    private ClassroomEntity classroom(UUID id) { return classrooms.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"CLASSROOM_NOT_FOUND")); }
    private AccountEntity account(UUID id) { return accounts.findById(id).filter(a->a.getStatus()==AccountStatus.ACTIVE).orElseThrow(()->error(HttpStatus.FORBIDDEN,"ACCOUNT_INACTIVE")); }
    private boolean access(UUID actor,ClassroomEntity c) {
        var a=account(actor); if(a.getRole()==AccountRole.TEACHER && c.getOwner().getId().equals(actor)) return true;
        student(actor,c); return false;
    }
    private void owner(UUID actor,ClassroomEntity c,boolean write) {
        if(account(actor).getRole()!=AccountRole.TEACHER || !c.getOwner().getId().equals(actor)) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION");
        if(write) writable(c);
    }
    private void member(UUID actor,ClassroomEntity c) {
        if(account(actor).getRole()!=AccountRole.STUDENT || !memberships.findByClassroomIdAndStudentId(c.getId(),actor)
                .map(m->m.getStatus()==MembershipStatus.ACTIVE).orElse(false)) throw error(HttpStatus.FORBIDDEN,"MEMBERSHIP_REQUIRED");
    }
    private void student(UUID actor,ClassroomEntity c) { member(actor,c); }
    private void writable(ClassroomEntity c) {
        if(c.getStatus()!=ClassroomStatus.ACTIVE || c.getOwner().getStatus()!=AccountStatus.ACTIVE) throw error(HttpStatus.CONFLICT,"CLASSROOM_READ_ONLY");
    }
    private void validateWrite(Write input) {
        if(input==null || input.title()==null || input.title().isBlank() || input.title().length()>200)
            throw error(HttpStatus.BAD_REQUEST,"INVALID_QUIZ_TITLE");
        validate(input.config());
    }
    private void validate(Config c) {
        try { QuizGrading.validate(c); }
        catch(IllegalArgumentException e) { throw new QuizException(HttpStatus.BAD_REQUEST,"INVALID_QUIZ_CONFIGURATION",e.getMessage()); }
    }
    private Config config(QuizEntity q) { return read(q.getConfiguration(),Config.class); }
    private Config snapshot(QuizAttemptEntity a) { return read(a.getSnapshot(),Config.class); }
    private Map<UUID,List<UUID>> answers(QuizAttemptEntity a) {
        try { return json.readValue(a.getAnswers(),new TypeReference<LinkedHashMap<UUID,List<UUID>>>(){}); }
        catch(Exception e) { throw new IllegalStateException("Invalid persisted quiz answers",e); }
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private <T> T read(String value,Class<T> type) { try { return json.readValue(value,type); } catch(Exception e) { throw new IllegalStateException("Invalid persisted quiz configuration",e); } }
    private static AppException error(HttpStatus status,String code) { return new QuizException(status,code); }
    static class QuizException extends AppException {
        QuizException(HttpStatus s,String c) { super(c,s,c); }
        QuizException(HttpStatus s,String c,String message) { super(message,s,c); }
    }
    static final class AttemptClosed extends QuizException { AttemptClosed() { super(HttpStatus.CONFLICT,"ATTEMPT_CLOSED"); } }
}
