package com.algolearner.platform.quiz;

import com.algolearner.platform.lesson.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile("!mock") @RequiredArgsConstructor @Transactional(readOnly=true)
public class QuizLessonProgressProvider implements LessonProgressProvider {
    private final JdbcTemplate jdbc;
    public LinkedItemType itemType(){return LinkedItemType.QUIZ;}
    public boolean existsInClassroom(UUID item,UUID classroom){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from tbl_quiz where id=? and classroom_id=?)",Boolean.class,item,classroom));}
    public boolean availableToStudent(UUID item,UUID classroom,UUID student){return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from tbl_quiz q join tbl_classroom_membership m on m.classroom_id=q.classroom_id
              join tbl_account a on a.id=m.student_id where q.id=? and q.classroom_id=? and q.status in('PUBLISHED','CLOSED')
              and m.student_id=? and m.status='ACTIVE' and a.role='STUDENT' and a.status='ACTIVE')
            """,Boolean.class,item,classroom,student)) && LinkedLessonAccess.available(jdbc,LinkedItemType.QUIZ,item);}
    @Override public List<ClassroomItemSummary> classroomItems(UUID classroomId) {
        return jdbc.query(
                "SELECT id, title, (status IN ('PUBLISHED', 'CLOSED')) AS available FROM tbl_quiz WHERE classroom_id = ? ORDER BY created_at DESC, id",
                (rs, rowNum) -> new ClassroomItemSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3)),
                classroomId);
    }
    public LessonProgressSource progress(UUID item,UUID classroom,UUID student){
        if(!existsInClassroom(item,classroom))return new LessonProgressSource(false,false,null,null,"missing");
        var rows=jdbc.query("select id,score,grade_published,version from tbl_quiz_attempt where quiz_id=? and student_id=? and status='SUBMITTED' order by id",
                (rs,n)->new Result(rs.getObject(1,UUID.class),rs.getBigDecimal(2),rs.getBoolean(3),rs.getLong(4)),item,student);
        Result highest=null;var source=new StringBuilder();
        for(var row:rows){source.append(row.id).append(':').append(row.version).append(':').append(row.score).append(':').append(row.published).append(';');
            if(row.published&&row.score!=null&&(highest==null||row.score.compareTo(highest.score)>0))highest=row;}
        return new LessonProgressSource(!rows.isEmpty(),highest!=null,highest==null?null:highest.score,highest==null?null:highest.id,QuizHashes.hash(source.toString()));
    }
    private record Result(UUID id,BigDecimal score,boolean published,long version){}
}
