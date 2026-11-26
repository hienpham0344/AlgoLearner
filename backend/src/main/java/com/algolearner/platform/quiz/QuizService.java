package com.algolearner.platform.quiz;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.*;
import com.algolearner.platform.lesson.LessonService;
import com.algolearner.platform.lesson.LinkedItemType;
import com.algolearner.platform.lesson.LinkedLessonAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.algolearner.platform.quiz.QuizDtos.*;

@Service @Profile("!mock") @RequiredArgsConstructor @Transactional
public class QuizService {
    private final JdbcTemplate jdbc;
    private final ClassroomService classrooms;
    private final ClassroomMembershipRepository memberships;
    private final AccountRepository accounts;
    private final LessonService lessons;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final com.algolearner.platform.audit.AuditService audit;

    public QuizView create(UUID actor,UUID classroom,Configure input) {
        owner(actor,classroom); QuizPolicy.validate(input);
        UUID id=UUID.randomUUID(); Instant now=now();
        jdbc.update("""
                insert into tbl_quiz(id,classroom_id,title,graded,opens_at,closes_at,duration_seconds,max_attempts,
                  allow_late,shuffle_questions,shuffle_answers,release_policy,timeout_mode,questions_json,created_at,updated_at,show_correct_answers,show_explanations,passing_score)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?)
                """,id,classroom,input.title().strip(),input.graded(),ts(input.opensAt()),ts(input.closesAt()),input.durationSeconds(),input.maxAttempts(),
                input.allowLate(),input.shuffleQuestions(),input.shuffleAnswers(),release(input).name(),timeout(input).name(),json(input.questions()),ts(now),ts(now),input.showCorrectAnswers(),input.showExplanations(),input.passingScore());
        return view(quiz(classroom,id,false));
    }
    public QuizView update(UUID actor,UUID classroom,UUID quizId,Configure input) {
        owner(actor,classroom); QuizPolicy.validate(input); var quiz=quiz(classroom,quizId,true);
        if(!quiz.status.equals("DRAFT")) throw conflict("QUIZ_NOT_DRAFT");
        jdbc.update("""
                update tbl_quiz set title=?,graded=?,opens_at=?,closes_at=?,duration_seconds=?,max_attempts=?,allow_late=?,
                shuffle_questions=?,shuffle_answers=?,release_policy=?,timeout_mode=?,questions_json=?::jsonb,updated_at=?,show_correct_answers=?,show_explanations=?,passing_score=? where id=?
                """,input.title().strip(),input.graded(),ts(input.opensAt()),ts(input.closesAt()),input.durationSeconds(),input.maxAttempts(),input.allowLate(),
                input.shuffleQuestions(),input.shuffleAnswers(),release(input).name(),timeout(input).name(),json(input.questions()),ts(now()),input.showCorrectAnswers(),input.showExplanations(),input.passingScore(),quizId);
        return view(quiz(classroom,quizId,false));
    }
    public QuizView publish(UUID actor,UUID classroom,UUID quizId) {
        owner(actor,classroom); var quiz=quiz(classroom,quizId,true);
        if(!quiz.status.equals("DRAFT")) throw conflict("INVALID_QUIZ_TRANSITION");
        jdbc.update("update tbl_quiz set status='PUBLISHED',updated_at=? where id=?",ts(now()),quizId);
        return view(quiz(classroom,quizId,false));
    }
    public QuizView close(UUID actor,UUID classroom,UUID quizId) {
        owner(actor,classroom); var quiz=quiz(classroom,quizId,true);
        if(!quiz.status.equals("PUBLISHED")) throw conflict("INVALID_QUIZ_TRANSITION");
        jdbc.update("update tbl_quiz set status='CLOSED',updated_at=? where id=?",ts(now()),quizId);
        return view(quiz(classroom,quizId,false));
    }
    public List<QuizView> list(UUID actor,UUID classroom) {
        boolean teacher=reader(actor,classroom);
        var hidden=teacher?Set.<UUID>of():new HashSet<>(LinkedLessonAccess.hiddenItems(jdbc,classroom,LinkedItemType.QUIZ));
        return jdbc.query("select * from tbl_quiz where classroom_id=?"+(teacher?"":" and status in ('PUBLISHED','CLOSED')")+" order by created_at,id",
                (rs,n)->view(row(rs)),classroom).stream().filter(q->!hidden.contains(q.id())).toList();
    }
    public QuizView get(UUID actor,UUID classroom,UUID quizId) {
        boolean teacher=reader(actor,classroom); var quiz=quiz(classroom,quizId,false);
        if(!teacher) { requireLinkedAccess(quizId);if(quiz.status.equals("DRAFT"))throw denied(); } return view(quiz);
    }
    public Configure authoring(UUID actor,UUID classroom,UUID quizId) {
        owner(actor,classroom); var q=quiz(classroom,quizId,false);
        return new Configure(q.title,q.graded,q.opensAt,q.closesAt,q.duration,q.maxAttempts,q.allowLate,q.shuffleQuestions,q.shuffleAnswers,q.release,q.timeout,questions(q.questions),q.showCorrectAnswers,q.showExplanations,q.passingScore);
    }
    public QuizView duplicate(UUID actor,UUID classroom,UUID quizId) {
        return create(actor,classroom,authoring(actor,classroom,quizId));
    }
    public void delete(UUID actor,UUID classroom,UUID quizId) {
        owner(actor,classroom); var quiz=quiz(classroom,quizId,true);
        if(!quiz.status.equals("DRAFT")) throw conflict("QUIZ_NOT_DRAFT");
        Integer attempts=jdbc.queryForObject("select count(*) from tbl_quiz_attempt where quiz_id=?",Integer.class,quizId);
        if(attempts!=null && attempts>0) throw conflict("QUIZ_HAS_ATTEMPTS");
        jdbc.update("delete from tbl_lesson_linked_item where item_type='QUIZ' and item_id=?",quizId);
        jdbc.update("delete from tbl_quiz where id=? and classroom_id=?",quizId,classroom);
    }
    public List<ReviewQuestion> review(UUID actor,UUID classroom,UUID quizId,UUID attemptId) {
        boolean teacher=reader(actor,classroom);
        var quiz=quiz(classroom,quizId,false);
        var attempt=attempt(quizId,attemptId,false);
        if(!teacher) {
            requireLinkedAccess(quizId);
            own(actor,attempt);
        }
        if(!attempt.status.equals("SUBMITTED") || !teacher && quiz.graded && !attempt.published) return List.of();
        if(!teacher && !quiz.showCorrectAnswers && !quiz.showExplanations) return List.of();
        return snapshot(attempt).questions().stream().map(q->new ReviewQuestion(q.id(),
                teacher || quiz.showCorrectAnswers ? q.options().stream().filter(Option::correct).map(Option::id).toList() : null,
                teacher || quiz.showExplanations ? q.explanation() : null)).toList();
    }
    public Result result(UUID actor,UUID classroom,UUID quizId,UUID studentId) {
        boolean teacher=reader(actor,classroom);
        if(!teacher) {
            requireLinkedAccess(quizId);
            if(!actor.equals(studentId)) throw denied();
        }
        var quiz=quiz(classroom,quizId,false);
        if(!teacher && quiz.status.equals("DRAFT")) throw denied();
        String visible=teacher?"":" and grade_published";
        boolean submitted=attemptCount(quizId,studentId,"SUBMITTED")>0;
        BigDecimal original=jdbc.queryForObject("select max(score) from tbl_quiz_attempt where quiz_id=? and student_id=? and status='SUBMITTED'"+visible,
                BigDecimal.class,quizId,studentId);
        BigDecimal score=original==null?null:latestGrade(quizId,studentId,teacher).map(GradeChange::finalScore).orElse(original);
        boolean completed=teacher
                ? Boolean.TRUE.equals(jdbc.queryForObject("""
                        select exists(select 1 from tbl_quiz_completion where quiz_id=? and student_id=? and completed)
                        """,Boolean.class,quizId,studentId))
                : quiz.passingScore==null?submitted:score!=null&&score.compareTo(quiz.passingScore)>=0;
        return new Result(submitted,completed,original,score);
    }
    public GradeChange overrideGrade(UUID actor,UUID classroom,UUID quizId,UUID studentId,GradeOverride request) {
        owner(actor,classroom);
        quiz(classroom,quizId,true);
        if(request==null || request.score()==null || request.score().signum()<0 || request.score().compareTo(BigDecimal.valueOf(100))>0
                || request.score().scale()>4 || request.reason()==null || request.reason().isBlank() || request.reason().length()>1000) invalid();
        var best=jdbc.query("""
                select id,score from tbl_quiz_attempt where quiz_id=? and student_id=? and status='SUBMITTED' and score is not null
                order by score desc,grade_published desc,id limit 1
                """,(rs,n)->Map.entry(rs.getObject(1,UUID.class),rs.getBigDecimal(2)),quizId,studentId)
                .stream().findFirst().orElseThrow(()->conflict("QUIZ_NO_GRADED_RESULT"));
        var original=best.getValue();
        var before=latestGrade(quizId,studentId,true).map(GradeChange::finalScore).orElse(original);
        Instant time=now();
        var change=jdbc.queryForObject("""
                insert into tbl_quiz_grade_override(quiz_id,student_id,attempt_id,actor_id,original_score,final_score,reason,created_at,updated_at)
                values(?,?,?,?,?,?,?,?,?) returning id,actor_id,original_score,final_score,reason,created_at
                """,(rs,n)->gradeChange(rs),quizId,studentId,best.getKey(),actor,original,request.score(),request.reason().strip(),ts(time),ts(time));
        audit.record(actor,"TEACHER","QUIZ",quizId.toString(),"QUIZ_GRADE_OVERRIDE",
                json(Map.of("studentId",studentId,"score",before)),
                json(Map.of("studentId",studentId,"score",change.finalScore())),change.reason(),null);
        refreshCompletion(quizId,studentId);
        lessons.recomputeForItem(classroom,LinkedItemType.QUIZ,quizId,studentId);
        return change;
    }
    public List<GradeChange> gradeHistory(UUID actor,UUID classroom,UUID quizId,UUID studentId) {
        if(!reader(actor,classroom)) throw denied();
        quiz(classroom,quizId,false);
        return jdbc.query(GRADE_HISTORY_SQL+" order by id desc",(rs,n)->gradeChange(rs),quizId,studentId);
    }
    private void refreshCompletion(UUID quizId,UUID studentId) {
        jdbc.update("""
                insert into tbl_quiz_completion(quiz_id,student_id,completed)
                select q.id,?,q.passing_score is null or coalesce(
                    (select final_score from tbl_quiz_grade_override where quiz_id=q.id and student_id=? order by id desc limit 1),
                    (select max(score) from tbl_quiz_attempt where quiz_id=q.id and student_id=? and status='SUBMITTED')
                ) >= q.passing_score
                from tbl_quiz q where q.id=?
                on conflict(quiz_id,student_id) do update
                set completed=excluded.completed,updated_at=current_timestamp
                """,studentId,studentId,studentId,quizId);
    }

    private static final String GRADE_HISTORY_SQL =
            "select attempt_id,id,actor_id,original_score,final_score,reason,created_at from tbl_quiz_grade_override g where g.quiz_id=? and g.student_id=?";
    private Optional<GradeChange> latestGrade(UUID quizId,UUID studentId,boolean teacher) {
        String sql="select * from ("+GRADE_HISTORY_SQL+" order by id desc limit 1) latest";
        if(!teacher) sql+=" where exists(select 1 from tbl_quiz_attempt a where a.id=latest.attempt_id and a.grade_published)";
        return jdbc.query(sql,(rs,n)->gradeChange(rs),quizId,studentId).stream().findFirst();
    }
    private GradeChange gradeChange(ResultSet rs) throws SQLException {
        return new GradeChange(rs.getLong("id"),rs.getObject("actor_id",UUID.class),rs.getBigDecimal("original_score"),
                rs.getBigDecimal("final_score"),rs.getString("reason"),instant(rs,"created_at"));
    }
    public AttemptView start(UUID actor,UUID classroom,UUID quizId) {
        student(actor,classroom,true);requireLinkedAccess(quizId); var quiz=quiz(classroom,quizId,true); Instant now=now();
        var active=jdbc.query("select id from tbl_quiz_attempt where quiz_id=? and student_id=? and status='IN_PROGRESS'",(rs,n)->rs.getObject(1,UUID.class),quizId,actor);
        if(!active.isEmpty()) {
            var attempt=attempt(quizId,active.getFirst(),true); expire(quiz,attempt);
            return attemptView(quiz,attempt,false);
        }
        if(!quiz.status.equals("PUBLISHED") || quiz.opensAt!=null && now.isBefore(quiz.opensAt)
                || !quiz.allowLate && QuizPolicy.expired(now,quiz.closesAt)) throw conflict("QUIZ_UNAVAILABLE");
        var allowance=allowance(quizId,actor);
        if(attemptCount(quizId,actor,"LOCKED")>allowance.acknowledgedLocks()) throw conflict("QUIZ_TEACHER_PERMISSION_REQUIRED");
        int used=attemptCount(quizId,actor,"SUBMITTED")-allowance.baseline();
        if(used>=quiz.maxAttempts+allowance.bonus()) throw conflict("QUIZ_ATTEMPT_LIMIT");
        var frozen=new ArrayList<Question>();
        for(var q:questions(quiz.questions)) {
            var options=new ArrayList<>(q.options()); if(quiz.shuffleAnswers) Collections.shuffle(options);
            frozen.add(new Question(q.id(),q.prompt(),q.type(),q.scoring(),q.points(),List.copyOf(options),q.explanation()));
        }
        if(quiz.shuffleQuestions) Collections.shuffle(frozen);
        UUID id=UUID.randomUUID(); Instant cutoff=QuizPolicy.cutoff(now,quiz.duration);
        jdbc.update("""
                insert into tbl_quiz_attempt(id,quiz_id,student_id,started_at,cutoff_at,late,snapshot_json,created_at,updated_at)
                values(?,?,?,?,?,?,?::jsonb,?,?)
                """,id,quizId,actor,ts(now),ts(cutoff),QuizPolicy.expired(now,quiz.closesAt),
                json(new Snapshot(List.copyOf(frozen),quiz.graded,quiz.release,quiz.timeout)),ts(now),ts(now));
        return attemptView(quiz,attempt(quizId,id,false),false);
    }
    public void adjustAttempts(UUID actor,UUID classroom,UUID quizId,UUID studentId,String action,int additional,String reason) {
        owner(actor,classroom);quiz(classroom,quizId,true);student(studentId,classroom,true);
        if(reason==null||reason.isBlank()||reason.strip().length()>1000
                || !("GRANT".equals(action)&&additional>=1&&additional<=1000 || "RESET".equals(action)&&additional==0)) invalid();
        var before=allowance(quizId,studentId);var timestamp=ts(now());
        jdbc.update("""
                insert into tbl_quiz_attempt_adjustment(quiz_id,student_id,actor_id,action,additional_attempts,
                  official_count,locked_count,reason,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?)
                """,quizId,studentId,actor,action,additional,attemptCount(quizId,studentId,"SUBMITTED"),
                attemptCount(quizId,studentId,"LOCKED"),reason.strip(),timestamp,timestamp);
        audit.record(actor,"TEACHER","QUIZ",quizId.toString(),"QUIZ_ATTEMPT_"+action,
                json(Map.of("studentId",studentId,"allowance",before)),
                json(Map.of("studentId",studentId,"allowance",allowance(quizId,studentId))),reason.strip(),null);
    }
    private int attemptCount(UUID quizId,UUID studentId,String status) {
        return jdbc.queryForObject("select count(*) from tbl_quiz_attempt where quiz_id=? and student_id=? and status=?",Integer.class,quizId,studentId,status);
    }
    private Allowance allowance(UUID quizId,UUID studentId) {
        var rows=jdbc.query("select action,additional_attempts,official_count,locked_count from tbl_quiz_attempt_adjustment where quiz_id=? and student_id=? order by id",
                (r,n)->new Adjustment(r.getString(1),r.getInt(2),r.getInt(3),r.getInt(4)),quizId,studentId);
        int baseline=0,bonus=0,locks=0;
        for(var row:rows) {
            if(row.action().equals("RESET")){baseline=row.official();bonus=0;}else bonus=Math.addExact(bonus,row.additional());
            locks=Math.max(locks,row.locks());
        }
        return new Allowance(baseline,bonus,locks);
    }
    private record Adjustment(String action,int additional,int official,int locks){}
    private record Allowance(int baseline,int bonus,int acknowledgedLocks){}
    public SaveReceipt save(UUID actor,UUID classroom,UUID quizId,UUID attemptId,SaveAnswer input) {
        student(actor,classroom,true);requireLinkedAccess(quizId); var quiz=quiz(classroom,quizId,true); var attempt=attempt(quizId,attemptId,true); own(actor,attempt);
        if(input==null || input.questionId()==null || input.selectedOptionIds()==null || input.idempotencyKey()==null || input.expectedVersion()<0) invalid();
        var selected=new HashSet<>(input.selectedOptionIds()); if(selected.contains(null)||selected.size()!=input.selectedOptionIds().size()) invalid();
        String payload=QuizHashes.hash(input.questionId()+":"+selected.stream().sorted().toList()+":"+input.expectedVersion());
        var receipts=jdbc.query("select payload_hash,answer_version from tbl_quiz_save_receipt where attempt_id=? and idempotency_key=?",(rs,n)->Map.entry(rs.getString(1),rs.getLong(2)),attemptId,input.idempotencyKey());
        if(!receipts.isEmpty()) {
            if(!receipts.getFirst().getKey().equals(payload)) throw conflict("IDEMPOTENCY_KEY_REUSED");
            return new SaveReceipt(true,attempt.status,input.questionId(),receipts.getFirst().getValue());
        }
        expire(quiz,attempt);
        if(!attempt.status.equals("IN_PROGRESS")) return new SaveReceipt(false,attempt.status,input.questionId(),answerVersion(attemptId,input.questionId()));
        var question=snapshot(attempt).questions().stream().filter(q->q.id().equals(input.questionId())).findFirst().orElseThrow(()->new ApiException(ErrorCode.VALIDATION_FAILED));
        var optionIds=question.options().stream().map(Option::id).collect(java.util.stream.Collectors.toSet());
        if(!optionIds.containsAll(selected) || question.type()!=QuestionType.MULTIPLE_CHOICE && selected.size()>1) invalid();
        long version=answerVersion(attemptId,input.questionId()); if(version!=input.expectedVersion()) throw conflict("ANSWER_VERSION_CONFLICT");
        long next=version+1; var now=ts(now());
        jdbc.update("""
                insert into tbl_quiz_answer(id,attempt_id,question_id,selected_options,version,created_at,updated_at)
                values(?,?,?,?::jsonb,?,?,?) on conflict(attempt_id,question_id)
                do update set selected_options=excluded.selected_options,version=excluded.version,updated_at=excluded.updated_at
                """,UUID.randomUUID(),attemptId,input.questionId(),json(selected.stream().sorted().toList()),next,now,now);
        jdbc.update("insert into tbl_quiz_save_receipt(attempt_id,idempotency_key,question_id,payload_hash,answer_version,created_at,updated_at) values(?,?,?,?,?,?,?)",
                attemptId,input.idempotencyKey(),input.questionId(),payload,next,now,now);
        return new SaveReceipt(true,attempt.status,input.questionId(),next);
    }
    public AttemptView resume(UUID actor,UUID classroom,UUID quizId,UUID attemptId) {
        boolean teacher=reader(actor,classroom);if(!teacher)requireLinkedAccess(quizId); var quiz=quiz(classroom,quizId,true); var attempt=attempt(quizId,attemptId,true);
        if(!teacher) own(actor,attempt); expire(quiz,attempt); return attemptView(quiz,attempt,teacher);
    }
    public AttemptView submit(UUID actor,UUID classroom,UUID quizId,UUID attemptId) {
        student(actor,classroom,true);requireLinkedAccess(quizId); var quiz=quiz(classroom,quizId,true); var attempt=attempt(quizId,attemptId,true); own(actor,attempt);
        expire(quiz,attempt); if(attempt.status.equals("IN_PROGRESS")) finish(quiz,attempt,false);
        return attemptView(quiz,attempt,false);
    }
    public void publishGrades(UUID actor,UUID classroom,UUID quizId) {
        owner(actor,classroom); quiz(classroom,quizId,true);
        var students=jdbc.query("select distinct student_id from tbl_quiz_attempt where quiz_id=? and status='SUBMITTED' and score is not null and not grade_published",(rs,n)->rs.getObject(1,UUID.class),quizId);
        jdbc.update("update tbl_quiz_attempt set grade_published=true,version=version+1,updated_at=? where quiz_id=? and status='SUBMITTED' and score is not null and not grade_published",ts(now()),quizId);
        students.forEach(student->lessons.recomputeForItem(classroom,LinkedItemType.QUIZ,quizId,student));
    }
    public com.algolearner.platform.common.dto.PageResponse<AttemptView> history(UUID actor,UUID classroom,UUID quizId,UUID studentId,org.springframework.data.domain.Pageable page) {
        boolean teacher=reader(actor,classroom);if(!teacher)requireLinkedAccess(quizId); var quiz=quiz(classroom,quizId,false);
        UUID target=teacher?studentId:actor; if(!teacher && studentId!=null && !studentId.equals(actor)) throw denied();
        String filter=" where quiz_id=?"+(target==null?"":" and student_id=?");
        var args=new ArrayList<Object>();args.add(quizId);if(target!=null)args.add(target);
        long count=jdbc.queryForObject("select count(*) from tbl_quiz_attempt"+filter,Long.class,args.toArray());
        var selected=new ArrayList<>(args);selected.add(Math.min(page.getPageSize(),100));selected.add(page.getOffset());
        var attempts=jdbc.query("select * from tbl_quiz_attempt"+filter+" order by started_at desc,id limit ? offset ?",(rs,n)->attemptView(quiz,attemptRow(rs),teacher),selected.toArray());
        return com.algolearner.platform.common.dto.PageResponse.fromPage(new org.springframework.data.domain.PageImpl<>(attempts,page,count));
    }
    public List<UUID> dueAttempts(int limit) {
        return jdbc.query("select id from tbl_quiz_attempt where status='IN_PROGRESS' and cutoff_at<=? order by cutoff_at,id limit ?",(rs,n)->rs.getObject(1,UUID.class),ts(now()),Math.min(100,Math.max(1,limit)));
    }
    public boolean expireOne(UUID id) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_xact_lock(hashtextextended(?,0))",Boolean.class,"quiz-timeout:"+id))) return false;
        var ids=jdbc.query("select q.classroom_id,a.quiz_id from tbl_quiz_attempt a join tbl_quiz q on q.id=a.quiz_id where a.id=?",(rs,n)->Map.entry(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)),id);
        if(ids.isEmpty())return false;
        var pair=ids.getFirst();classrooms.lockReadable(pair.getKey());
        var quiz=quiz(pair.getKey(),pair.getValue(),true);var attempt=attempt(quiz.id,id,true);
        boolean before=attempt.status.equals("IN_PROGRESS");expire(quiz,attempt);return before&&!attempt.status.equals("IN_PROGRESS");
    }
    private void expire(QuizRow quiz,AttemptRow attempt) {
        if(!attempt.status.equals("IN_PROGRESS") || !QuizPolicy.expired(now(),attempt.cutoff))return;
        var classroom=classrooms.lockReadable(quiz.classroom);
        if(classroom.getStatus()!=ClassroomStatus.ACTIVE || classroom.getOwner().getStatus()!=AccountStatus.ACTIVE
                || !LinkedLessonAccess.available(jdbc,LinkedItemType.QUIZ,quiz.id)) {
            jdbc.update("UPDATE tbl_quiz_attempt SET status='LOCKED',version=version+1,updated_at=? WHERE id=?",ts(now()),attempt.id);
            attempt.status="LOCKED";
            return;
        }
        finish(quiz,attempt,true);
    }
    private void finish(QuizRow quiz,AttemptRow attempt,boolean expired) {
        var snapshot=snapshot(attempt);String status=expired&&snapshot.timeoutMode()==TimeoutMode.LOCK?"LOCKED":"SUBMITTED";
        BigDecimal score=status.equals("SUBMITTED")&&snapshot.graded()?grade(snapshot,answers(attempt.id)):null;
        Instant submitted=status.equals("SUBMITTED")?(expired?attempt.cutoff:now()):null;
        boolean published=score!=null&&snapshot.releasePolicy()==ReleasePolicy.IMMEDIATE;
        jdbc.update("update tbl_quiz_attempt set status=?,submitted_at=?,score=?,grade_published=?,late=?,version=version+1,updated_at=? where id=? and status='IN_PROGRESS'",
                status,ts(submitted),score,published,submitted!=null&&QuizPolicy.expired(submitted,quiz.closesAt),ts(now()),attempt.id);
        attempt.status=status;attempt.submitted=submitted;attempt.score=score;attempt.published=published;
        if(status.equals("SUBMITTED")) {
            refreshCompletion(quiz.id,attempt.student);
            lessons.recomputeForItem(quiz.classroom,LinkedItemType.QUIZ,quiz.id,attempt.student);
        }
    }
    private BigDecimal grade(Snapshot snapshot,List<AnswerView> saved) {
        var byQuestion=new HashMap<UUID,Set<UUID>>();saved.forEach(a->byQuestion.put(a.questionId(),new HashSet<>(a.selectedOptionIds())));
        BigDecimal total=BigDecimal.ZERO,earned=BigDecimal.ZERO;
        for(var question:snapshot.questions()) {
            total=total.add(question.points());var correct=question.options().stream().filter(Option::correct).map(Option::id).collect(java.util.stream.Collectors.toSet());
            var selected=byQuestion.getOrDefault(question.id(),Set.of());
            BigDecimal fraction=selected.equals(correct)?BigDecimal.ONE:BigDecimal.ZERO;
            if(question.scoring()==Scoring.PARTIAL && question.options().stream().allMatch(o->o.weight()!=null)) {
                BigDecimal weighted=BigDecimal.ZERO;
                for(var option:question.options()) {
                    if(selected.contains(option.id())) weighted=weighted.add(option.correct()?option.weight():option.weight().negate());
                }
                earned=earned.add(weighted.max(BigDecimal.ZERO).min(question.points()));
                continue;
            }
            earned=earned.add(question.scoring()==Scoring.PARTIAL
                    ? question.points().multiply(BigDecimal.valueOf(selected.stream().filter(correct::contains).count()))
                        .divide(BigDecimal.valueOf(correct.size()),2,RoundingMode.HALF_UP).min(question.points())
                    : question.points().multiply(fraction));
        }
        return earned.multiply(BigDecimal.valueOf(100)).divide(total,4,RoundingMode.HALF_UP);
    }
    private AttemptView attemptView(QuizRow quiz,AttemptRow a,boolean teacher) {
        var questions=snapshot(a).questions().stream().map(q->new StudentQuestion(q.id(),q.prompt(),q.type(),q.points(),q.options().stream().map(o->new StudentOption(o.id(),o.text())).toList())).toList();
        return new AttemptView(a.id,quiz.id,a.student,a.status,a.started,a.cutoff,a.submitted,teacher||a.published?a.score:null,a.published,questions,answers(a.id),now(),QuizPolicy.expired(a.submitted==null?a.started:a.submitted,quiz.closesAt));
    }
    private List<AnswerView> answers(UUID attempt) {
        return jdbc.query("select question_id,selected_options::text,version from tbl_quiz_answer where attempt_id=? order by question_id",(rs,n)->new AnswerView(rs.getObject(1,UUID.class),uuidList(rs.getString(2)),rs.getLong(3)),attempt);
    }
    private long answerVersion(UUID attempt,UUID question){return jdbc.query("select version from tbl_quiz_answer where attempt_id=? and question_id=?",(rs,n)->rs.getLong(1),attempt,question).stream().findFirst().orElse(0L);}
    private void requireLinkedAccess(UUID quizId) {
        if(!LinkedLessonAccess.available(jdbc,LinkedItemType.QUIZ,quizId))throw denied();
    }
    private boolean reader(UUID actor,UUID classroom) {
        var account=active(actor);var c=classrooms.lockReadable(classroom);
        if(account.getRole()==AccountRole.TEACHER&&c.getOwner().getId().equals(actor))return true;
        if(account.getRole()!=AccountRole.STUDENT||!member(actor,classroom))throw denied();return false;
    }
    private void owner(UUID actor,UUID classroom) {
        var account=active(actor);var c=classrooms.lockReadable(classroom);
        if(account.getRole()!=AccountRole.TEACHER||!c.getOwner().getId().equals(actor)||c.getStatus()!=ClassroomStatus.ACTIVE||c.getOwner().getStatus()!=AccountStatus.ACTIVE)throw denied();
    }
    private void student(UUID actor,UUID classroom,boolean write) {
        var account=active(actor);var c=classrooms.lockReadable(classroom);
        if(account.getRole()!=AccountRole.STUDENT||!member(actor,classroom)||write&&(c.getStatus()!=ClassroomStatus.ACTIVE||c.getOwner().getStatus()!=AccountStatus.ACTIVE))throw denied();
    }
    private boolean member(UUID actor,UUID classroom){return memberships.findByClassroomIdAndStudentId(classroom,actor).filter(m->m.getStatus()==MembershipStatus.ACTIVE).isPresent();}
    private AccountEntity active(UUID actor){var a=accounts.findById(actor).orElseThrow(QuizService::denied);if(a.getStatus()!=AccountStatus.ACTIVE)throw denied();return a;}
    private void own(UUID actor,AttemptRow attempt){if(!actor.equals(attempt.student))throw denied();}
    private QuizRow quiz(UUID classroom,UUID id,boolean lock){return jdbc.query("select * from tbl_quiz where id=? and classroom_id=?"+(lock?" for update":""),(rs,n)->row(rs),id,classroom).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Quiz",id));}
    private AttemptRow attempt(UUID quiz,UUID id,boolean lock){return jdbc.query("select * from tbl_quiz_attempt where id=? and quiz_id=?"+(lock?" for update":""),(rs,n)->attemptRow(rs),id,quiz).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Quiz attempt",id));}
    private QuizRow row(ResultSet r)throws SQLException{return new QuizRow(r.getObject("id",UUID.class),r.getObject("classroom_id",UUID.class),r.getString("title"),r.getString("status"),r.getBoolean("graded"),instant(r,"opens_at"),instant(r,"closes_at"),(Integer)r.getObject("duration_seconds"),r.getInt("max_attempts"),r.getBoolean("allow_late"),r.getBoolean("shuffle_questions"),r.getBoolean("shuffle_answers"),ReleasePolicy.valueOf(r.getString("release_policy")),TimeoutMode.valueOf(r.getString("timeout_mode")),r.getString("questions_json"),r.getBoolean("show_correct_answers"),r.getBoolean("show_explanations"),r.getBigDecimal("passing_score"));}
    private AttemptRow attemptRow(ResultSet r)throws SQLException{return new AttemptRow(r.getObject("id",UUID.class),r.getObject("student_id",UUID.class),r.getString("status"),instant(r,"started_at"),instant(r,"cutoff_at"),instant(r,"submitted_at"),r.getBigDecimal("score"),r.getBoolean("grade_published"),r.getString("snapshot_json"));}
    private QuizView view(QuizRow q){return new QuizView(q.id,q.classroom,q.title,q.status,q.graded,q.opensAt,q.closesAt,q.duration,q.maxAttempts,q.allowLate,q.release);}
    private Snapshot snapshot(AttemptRow a){try{return mapper.readValue(a.snapshot,Snapshot.class);}catch(JsonProcessingException e){throw new IllegalStateException("Stored quiz snapshot invalid",e);}}
    private List<Question> questions(String json){try{return mapper.readValue(json,new TypeReference<List<Question>>(){});}catch(JsonProcessingException e){throw new IllegalStateException("Stored quiz questions invalid",e);}}
    private List<UUID> uuidList(String json){try{return mapper.readValue(json,new TypeReference<List<UUID>>(){});}catch(JsonProcessingException e){throw new IllegalStateException("Stored quiz answer invalid",e);}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException("Invalid quiz data",e);}}
    private Instant now(){return clock.instant().truncatedTo(ChronoUnit.MICROS);}private static Timestamp ts(Instant i){return i==null?null:Timestamp.from(i);}private static Instant instant(ResultSet r,String field)throws SQLException{var t=r.getTimestamp(field);return t==null?null:t.toInstant();}
    private static ReleasePolicy release(Configure c){return c.releasePolicy()==null?ReleasePolicy.MANUAL:c.releasePolicy();}private static TimeoutMode timeout(Configure c){return c.timeoutMode()==null?TimeoutMode.AUTO_SUBMIT:c.timeoutMode();}
    private static ApiException denied(){return new ApiException(ErrorCode.FORBIDDEN_OPERATION);}private static void invalid(){throw new ApiException(ErrorCode.VALIDATION_FAILED);}
    private static AppException conflict(String code){return new QuizConflict(code);}private static final class QuizConflict extends AppException{QuizConflict(String code){super("Quiz operation conflicts with current state",HttpStatus.CONFLICT,code);}}
    private record QuizRow(UUID id,UUID classroom,String title,String status,boolean graded,Instant opensAt,Instant closesAt,Integer duration,int maxAttempts,boolean allowLate,boolean shuffleQuestions,boolean shuffleAnswers,ReleasePolicy release,TimeoutMode timeout,String questions,boolean showCorrectAnswers,boolean showExplanations,BigDecimal passingScore){}
    private static final class AttemptRow {final UUID id,student;String status;final Instant started,cutoff;Instant submitted;BigDecimal score;boolean published;final String snapshot;AttemptRow(UUID id,UUID student,String status,Instant started,Instant cutoff,Instant submitted,BigDecimal score,boolean published,String snapshot){this.id=id;this.student=student;this.status=status;this.started=started;this.cutoff=cutoff;this.submitted=submitted;this.score=score;this.published=published;this.snapshot=snapshot;}}
}
