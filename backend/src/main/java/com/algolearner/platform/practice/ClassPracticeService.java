package com.algolearner.platform.practice;

import java.util.*;
import com.algolearner.platform.problem.PublicProblemCatalog;
import com.algolearner.platform.problem.PublicProblemCatalog.ContentSnapshot;
import com.algolearner.platform.problem.ProblemService;
import com.algolearner.platform.judge.JudgeGateway;
import static com.algolearner.platform.judge.JudgeGateway.*;
import com.algolearner.platform.lesson.*;
import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.common.dto.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;

@Service @Profile("!mock") @Transactional(readOnly=true)
public class ClassPracticeService implements LessonProgressProvider {
    private final NamedParameterJdbcTemplate jdbc;private final ObjectMapper json;private final JudgeGateway judge;
    private final PublicProblemCatalog catalog;private final ProblemService actors;private final AuditService audit;
    public ClassPracticeService(NamedParameterJdbcTemplate jdbc,ObjectMapper json,JudgeGateway judge,PublicProblemCatalog catalog,ProblemService actors,AuditService audit) {
        this.jdbc=jdbc;this.json=json;this.judge=judge;this.catalog=catalog;this.actors=actors;this.audit=audit;
    }
    @Transactional public UUID copy(UUID teacher,UUID classroom,UUID problem) {
        actors.requireRole(teacher,"TEACHER");owner(teacher,classroom);var snapshot=catalog.contentSnapshot(teacher,problem);UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_class_practice(id,classroom_id,source_problem_id,revision_id,title,payload) VALUES(:id,:class,:problem,:revision,:title,:payload)",
                Map.of("id",id,"class",classroom,"problem",problem,"revision",snapshot.revisionId(),"title",snapshot.title(),"payload",encode(snapshot)));
        audit.record(teacher,"TEACHER","CLASS_PRACTICE",id.toString(),"PRACTICE_COPY",null,"DRAFT",null,null);return id;
    }
    @Transactional public void publish(UUID teacher,UUID classroom,UUID practice) {
        actors.requireRole(teacher,"TEACHER");owner(teacher,classroom);var row=practice(classroom,practice,true);
        if("PUBLISHED".equals(row.get("status")))return;
        jdbc.update("UPDATE tbl_class_practice SET status='PUBLISHED',updated_at=clock_timestamp() WHERE id=:id",Map.of("id",practice));
        audit.record(teacher,"TEACHER","CLASS_PRACTICE",practice.toString(),"PRACTICE_PUBLISH","DRAFT","PUBLISHED",null,null);
    }
    public ProblemService.View detail(UUID actor,UUID classroom,UUID practice) {
        actors.requireRole(actor,"STUDENT","TEACHER");var row=practice(classroom,practice,false);
        if(!isOwner(actor,classroom))requireStudent(actor,classroom,row);return ProblemService.view(practice,snapshot(row),(String)row.get("status"));
    }
    public PageResponse<ProblemService.Summary> list(UUID actor,UUID classroom,Pageable page) {
        actors.requireRole(actor,"STUDENT","TEACHER");boolean owner=isOwner(actor,classroom);
        if(!owner)requireMembership(actor,classroom);
        var params=new HashMap<String,Object>(Map.of("class",classroom,"limit",Math.min(100,page.getPageSize()),"offset",(long)page.getPageNumber()*Math.min(100,page.getPageSize())));
        String from=" FROM tbl_class_practice WHERE classroom_id=:class"+(owner?"":" AND status='PUBLISHED'");
        if(!owner) {
            var hidden=LinkedLessonAccess.hiddenItems(jdbc.getJdbcTemplate(),classroom,LinkedItemType.PRACTICE);
            if(!hidden.isEmpty()){params.put("hidden",hidden);from+=" AND id NOT IN (:hidden)";}
        }
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,params,Long.class);
        var items=jdbc.query("SELECT id,title,status,created_at"+from+" ORDER BY created_at DESC,id LIMIT :limit OFFSET :offset",params,
                (rs,n)->new ProblemService.Summary(rs.getObject("id",UUID.class),rs.getString("title"),rs.getString("status"),rs.getTimestamp("created_at").toInstant()));
        return PageResponse.fromPage(new PageImpl<>(items,PageRequest.of(page.getPageNumber(),(int)params.get("limit")),total));
    }
    @Transactional public UUID evaluate(UUID student,UUID classroom,UUID practice,ProblemService.CodeInput input,boolean run) {
        actors.requireRole(student,"STUDENT");if(!"ACTIVE".equals(lockClass(classroom).get("owner_status")))throw ProblemService.failure(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION");var row=practice(classroom,practice,true);requireStudent(student,classroom,row);var content=snapshot(row);
        if(input.requestKey()==null || input.requestKey().length()>64 || !content.languages().contains(input.language()))throw ProblemService.failure(HttpStatus.BAD_REQUEST,"INVALID_CODE_REQUEST");
        var r=new CodeEvaluationRequest(student,null,null,input.source(),input.language(),content.testCases(),"practice:"+practice+":"+input.requestKey(),0,
                content.feedback(),SubmissionScope.CLASS_PRACTICE,practice,content.revisionId(),content.limits());return run?judge.run(r):judge.submit(r);
    }
    public PageResponse<ProblemService.Submission> history(UUID student,UUID classroom,UUID practice,Pageable page) {
        actors.requireRole(student,"STUDENT");requireStudent(student,classroom,practice(classroom,practice,false));
        return ProblemService.submissionHistory(jdbc,student,practice,"CLASS_PRACTICE",page);
    }
    public EvaluationResult result(UUID student,UUID classroom,UUID practice,UUID job) {
        actors.requireRole(student,"STUDENT");requireStudent(student,classroom,practice(classroom,practice,false));
        ProblemService.requireJobScope(jdbc,student,practice,job,"CLASS_PRACTICE");return judge.result(student,job);
    }
    @Override public LinkedItemType itemType(){return LinkedItemType.PRACTICE;}
    @Override public boolean existsInClassroom(UUID item,UUID classroom){return jdbc.queryForObject("SELECT COUNT(*) FROM tbl_class_practice WHERE id=:id AND classroom_id=:class",Map.of("id",item,"class",classroom),Long.class)==1;}
    @Override public boolean availableToStudent(UUID item,UUID classroom,UUID student) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tbl_class_practice p JOIN tbl_classroom c ON c.id=p.classroom_id JOIN tbl_classroom_membership m ON m.classroom_id=c.id JOIN tbl_account a ON a.id=m.student_id WHERE p.id=:id AND c.id=:class AND p.status='PUBLISHED' AND c.status='ACTIVE' AND m.student_id=:student AND m.status='ACTIVE' AND a.status='ACTIVE' AND a.role='STUDENT'",Map.of("id",item,"class",classroom,"student",student),Long.class)==1 && LinkedLessonAccess.available(jdbc.getJdbcTemplate(),LinkedItemType.PRACTICE,item);
    }
    @Override public LessonProgressSource progress(UUID item,UUID classroom,UUID student) {
        if(!availableToStudent(item,classroom,student))return new LessonProgressSource(false,false,null,null,"unavailable");
        var rows=jdbc.queryForList("""
                SELECT latest.id FROM tbl_submission s
                JOIN LATERAL (SELECT j.id,j.status FROM tbl_judge_job j WHERE j.submission_id=s.id AND j.mode='SUBMIT'
                              AND j.status IN ('ACCEPTED','WRONG_ANSWER','COMPILE_ERROR','RUNTIME_ERROR',
                                               'MEMORY_LIMIT_EXCEEDED','TIME_LIMIT_EXCEEDED','OUTPUT_LIMIT_EXCEEDED')
                              ORDER BY j.generation DESC,j.created_at DESC,j.id LIMIT 1) latest ON true
                WHERE s.scope='CLASS_PRACTICE' AND s.resource_id=:item AND s.owner_id=:student AND latest.status='ACCEPTED'
                ORDER BY s.created_at,latest.id LIMIT 1
                """,Map.of("item",item,"student",student));
        if(rows.isEmpty())return new LessonProgressSource(false,false,null,null,"not-accepted");UUID id=(UUID)rows.getFirst().get("id");
        // Practice contributes completion only, never a score leaf.
        return new LessonProgressSource(true,false,null,id,id.toString());
    }
    private Map<String,Object> practice(UUID classroom,UUID id,boolean lock) {
        var rows=jdbc.queryForList("SELECT * FROM tbl_class_practice WHERE id=:id AND classroom_id=:class"+(lock?" FOR UPDATE":""),Map.of("id",id,"class",classroom));
        if(rows.isEmpty())throw ProblemService.failure(HttpStatus.NOT_FOUND,"PRACTICE_NOT_FOUND");return rows.getFirst();
    }
    private void requireStudent(UUID student,UUID classroom,Map<String,Object> row){if(!"PUBLISHED".equals(row.get("status")) || !LinkedLessonAccess.available(jdbc.getJdbcTemplate(),LinkedItemType.PRACTICE,(UUID)row.get("id")))throw ProblemService.failure(HttpStatus.NOT_FOUND,"PRACTICE_NOT_FOUND");requireMembership(student,classroom);}
    private void requireMembership(UUID student,UUID classroom){
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM tbl_classroom_membership m JOIN tbl_classroom c ON c.id=m.classroom_id JOIN tbl_account a ON a.id=m.student_id WHERE c.id=:class AND c.status='ACTIVE' AND m.student_id=:student AND m.status='ACTIVE' AND a.status='ACTIVE' AND a.role='STUDENT'",Map.of("class",classroom,"student",student),Long.class);
        if(count!=1)throw ProblemService.failure(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION");
    }
    private void owner(UUID teacher,UUID classroom){if(!teacher.equals(lockClass(classroom).get("owner_id")))throw ProblemService.failure(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION");}
    private Map<String,Object> lockClass(UUID classroom){
        var rows=jdbc.queryForList("SELECT c.owner_id,a.status AS owner_status FROM tbl_classroom c JOIN tbl_account a ON a.id=c.owner_id WHERE c.id=:class AND c.status='ACTIVE' FOR SHARE OF c",Map.of("class",classroom));
        if(rows.isEmpty())throw ProblemService.failure(HttpStatus.NOT_FOUND,"CLASSROOM_NOT_FOUND");return rows.getFirst();
    }
    private boolean isOwner(UUID actor,UUID classroom){return jdbc.queryForObject("SELECT COUNT(*) FROM tbl_classroom WHERE id=:class AND owner_id=:owner AND status='ACTIVE'",Map.of("class",classroom,"owner",actor),Long.class)==1;}
    private ContentSnapshot snapshot(Map<String,Object> row){try{return json.readValue((String)row.get("payload"),ContentSnapshot.class);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid practice snapshot",e);}}
    private String encode(Object s){try{return json.writeValueAsString(s);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Practice snapshot serialization failed",e);}}
}
