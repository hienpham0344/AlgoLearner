package com.algolearner.platform.assignment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Append-only grading generations. Mutable answer scores are the working projection only. */
@Service
@Profile("!mock")
@Transactional(readOnly = true)
public class AssignmentEvaluationHistory {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final com.algolearner.platform.audit.AuditService audit;
    private final com.algolearner.platform.notification.NotificationService notifications;
    public AssignmentEvaluationHistory(JdbcTemplate jdbc, ObjectMapper json, Clock clock, com.algolearner.platform.audit.AuditService audit,
            com.algolearner.platform.notification.NotificationService notifications) {
        this.jdbc = jdbc; this.json = json; this.clock = clock; this.audit=audit; this.notifications=notifications;
    }
    record Part(UUID questionId, BigDecimal score, String feedback, UUID judgeJobId, Long judgeGeneration, String judgeVerdict, UUID judgeRevisionId) { }
    private record CodeResult(UUID job, Long generation, String verdict, UUID revision) { }
    public record Evaluation(UUID id, String kind, BigDecimal autoScore, BigDecimal manualScore,
            BigDecimal overriddenScore, BigDecimal finalScore, boolean complete, String reason, String parts) { }
    @Transactional
    UUID append(AssignmentAttemptEntity attempt, List<AssignmentAnswerEntity> answers, UUID actor, String kind, String reason) {
        var id = UUID.randomUUID();
        var generation=jdbc.queryForObject("SELECT count(*)+1 FROM tbl_assignment_evaluation WHERE attempt_id=?",Long.class,attempt.getId());
        var codeResults=new java.util.HashMap<UUID,CodeResult>();
        jdbc.query("""
                SELECT DISTINCT ON(c.question_id) c.question_id,c.job_id,c.generation,j.status,c.revision_id FROM tbl_assignment_code_job c
                JOIN tbl_judge_job j ON j.id=c.job_id
                WHERE c.attempt_id=? AND c.applied_at IS NOT NULL ORDER BY c.question_id,c.generation DESC
                """,r->{codeResults.put(r.getObject(1,UUID.class),new CodeResult(r.getObject(2,UUID.class),r.getLong(3),r.getString(4),r.getObject(5,UUID.class)));},attempt.getId());
        var parts = answers.stream().map(a -> {
            var code=a.getScore()==null?null:codeResults.get(a.getQuestion().getId());
            return new Part(a.getQuestion().getId(), a.getScore(), a.getFeedback(), code==null?null:code.job(),
                    code==null?null:code.generation(),code==null?null:code.verdict(),code==null?null:code.revision());
        }).toList();
        final String encoded;
        try { encoded = json.writeValueAsString(parts); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Cannot serialize evaluation", failure); }
        jdbc.update("""
                INSERT INTO tbl_assignment_evaluation(id,attempt_id,actor_id,kind,generation,auto_score,manual_score,overridden_score,
                final_score,complete,reason,parts,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)
                """, id, attempt.getId(), actor, kind, generation, attempt.getAutoScore(), attempt.getManualScore(),
                attempt.getOverriddenScore(), attempt.getFinalScore(), attempt.getStatus() == AttemptStatus.GRADED,
                reason, encoded, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        audit.record(actor,actorRole(actor),"ASSIGNMENT_ATTEMPT",attempt.getId().toString(),
                "EVALUATION_"+kind,String.valueOf(attempt.getCurrentEvaluationId()),
                "evaluation="+id+",complete="+(attempt.getStatus()==AttemptStatus.GRADED),reason,null);
        attempt.setCurrentEvaluationId(id);
        return id;
    }
    List<Evaluation> history(UUID attemptId) {
        return jdbc.query("""
                SELECT id,kind,auto_score,manual_score,overridden_score,final_score,complete,reason,parts::text
                FROM tbl_assignment_evaluation WHERE attempt_id=? ORDER BY generation
                """, (r,n) -> new Evaluation(r.getObject(1, UUID.class), r.getString(2), r.getBigDecimal(3),
                r.getBigDecimal(4), r.getBigDecimal(5), r.getBigDecimal(6), r.getBoolean(7), r.getString(8), r.getString(9)), attemptId);
    }
    Evaluation published(UUID attemptId, UUID evaluationId) {
        return history(attemptId).stream().filter(e -> e.id().equals(evaluationId)).findFirst().orElse(null);
    }
    List<Part> parts(Evaluation evaluation) {
        try { return json.readValue(evaluation.parts(), json.getTypeFactory().constructCollectionType(List.class, Part.class)); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Cannot read evaluation parts", failure); }
    }
    private String actorRole(UUID actor) {
        return actor==null?"SYSTEM":jdbc.queryForObject("SELECT role FROM tbl_account WHERE id=?",String.class,actor);
    }
    private boolean officialResultChanged(Evaluation previous,Evaluation current) {
        if(previous.finalScore()!=null && current.finalScore()!=null
                && previous.finalScore().compareTo(current.finalScore())!=0)return true;
        var oldVerdicts=new java.util.HashMap<UUID,String>();
        for(var part:parts(previous))if(part.judgeVerdict()!=null)oldVerdicts.put(part.questionId(),part.judgeVerdict());
        return parts(current).stream().anyMatch(part->part.judgeVerdict()!=null && oldVerdicts.containsKey(part.questionId())
                && !part.judgeVerdict().equals(oldVerdicts.get(part.questionId())));
    }
    @Transactional
    void publish(AssignmentAttemptEntity attempt) { publish(attempt,null); }
    @Transactional
    void publish(AssignmentAttemptEntity attempt,UUID actor) {
        if (attempt.getStatus() == AttemptStatus.GRADED && attempt.getCurrentEvaluationId() != null
                && !attempt.getCurrentEvaluationId().equals(attempt.getPublishedEvaluationId())) {
            audit.record(actor,actorRole(actor),"ASSIGNMENT_ATTEMPT",attempt.getId().toString(),
                    "PUBLISH_EVALUATION",String.valueOf(attempt.getPublishedEvaluationId()),attempt.getCurrentEvaluationId().toString(),null,null);
            var history=history(attempt.getId());
            int previous=-1,current=-1;
            for(int index=0;index<history.size();index++) {
                if(history.get(index).id().equals(attempt.getPublishedEvaluationId()))previous=index;
                if(history.get(index).id().equals(attempt.getCurrentEvaluationId()))current=index;
            }
            if(previous>=0 && current>previous && officialResultChanged(history.get(previous),history.get(current))
                    && history.subList(previous+1,current+1).stream().anyMatch(value->"REJUDGE".equals(value.kind()))) {
                var assignment=attempt.getAssignment();
                notifications.notifyStudent(attempt.getStudent(),"ASSIGNMENT_REJUDGE","Assignment grade updated",
                        "Your assignment '"+assignment.getTitle()+"' score was updated to "+history.get(current).finalScore(),
                        "/classrooms/"+assignment.getClassroom().getId()+"/assignments/"+assignment.getId(),null,assignment.getId());
            }
            attempt.setPublishedEvaluationId(attempt.getCurrentEvaluationId());
        }
    }
}
