package com.algolearner.platform.quiz;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.algolearner.platform.quiz.QuizDtos.*;
import static com.algolearner.platform.quiz.QuizFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:quiz-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false","logging.level.root=WARN","logging.level.org.hibernate.SQL=WARN","debug=false",
        "app.security.jwt.issuer=https://issuer.example.test","app.security.jwt.audience=quiz-tests",
        "app.security.jwt.jwk-set-uri=https://issuer.example.test/.well-known/jwks.json"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class QuizApiTest {
    @TestConfiguration static class Config {
        @Bean @Primary MutableClock quizClock(){return new MutableClock();}
    }
    static class MutableClock extends Clock {
        volatile Instant now=NOW;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired com.algolearner.platform.auth.AccountRepository accounts;
    @Autowired ClassroomRepository classrooms;
    @Autowired ClassroomMembershipRepository memberships;
    @Autowired QuizAttemptRepository attempts;
    @Autowired QuizRepository quizzes;
    @Autowired QuizGradeOverrideRepository grades;
    @Autowired MutableClock clock;
    @Autowired EntityManager em;
    AccountEntity teacher,student,otherTeacher,otherStudent,admin;
    ClassroomEntity classroom;
    String base;

    @BeforeEach void setup() {
        clock.now=NOW; teacher=account(AccountRole.TEACHER); student=account(AccountRole.STUDENT);
        otherTeacher=account(AccountRole.TEACHER); otherStudent=account(AccountRole.STUDENT); admin=account(AccountRole.ADMIN);
        classroom=new ClassroomEntity();classroom.setId(UUID.randomUUID());classroom.setOwner(teacher);
        classroom.setName("Algorithms");classroom.setStatus(ClassroomStatus.ACTIVE);classroom.setClassCode(UUID.randomUUID().toString().substring(0,12));
        classroom.setJoinMode(JoinMode.AUTO_JOIN);classroom.setJoiningOpen(true);classrooms.save(classroom);
        for(var s:List.of(student,otherStudent)) {
            var m=new ClassroomMembershipEntity();m.setId(UUID.randomUUID());m.setClassroom(classroom);m.setStudent(s);
            m.setStatus(MembershipStatus.ACTIVE);m.setJoinedAt(NOW);memberships.save(m);
        }
        base="/api/v1/classrooms/"+classroom.getId()+"/quizzes";
    }
    @Test void createPublishAutosaveResumeSubmitAndReadResultPersisted() throws Exception {
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT); publish(q); String attempt=start(q);
        mvc.perform(put(path(q,"/attempts/"+attempt+"/answers/"+QUESTION)).with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Answer(List.of(CORRECT)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.answers."+QUESTION+"[0]").value(CORRECT.toString()));
        em.flush();em.clear();
        mvc.perform(get(path(q,"/attempts/"+attempt)).with(actor(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.deadline").value(NOW.plusSeconds(60).toString()))
                .andExpect(jsonPath("$.data.questions[0].options[0].correct").isEmpty());
        mvc.perform(post(path(q,"/attempts/"+attempt+"/submit")).with(actor(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(100)).andExpect(jsonPath("$.data.completed").value(true));
        em.flush();em.clear();
        assertThat(attempts.findById(UUID.fromString(attempt)).orElseThrow().getStatus()).isEqualTo("SUBMITTED");
        mvc.perform(get(path(q,"/students/"+student.getId()+"/result")).with(actor(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.finalScore").value(100));
    }
    @Test void manualPublicationAndStudentPrivacyEnforcedAtApi() throws Exception {
        String q=create(Publication.MANUAL,Timeout.AUTO_SUBMIT);publish(q);String a=start(q);
        mvc.perform(post(path(q,"/attempts/"+a+"/submit")).with(actor(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.score").isEmpty())
                .andExpect(jsonPath("$.data.questions[0].options[0].correct").isEmpty());
        mvc.perform(get(path(q,"/students/"+student.getId()+"/result")).with(actor(otherStudent))).andExpect(status().isForbidden());
        mvc.perform(get(path(q,"/attempts/"+a)).with(actor(otherStudent))).andExpect(status().isForbidden());
        mvc.perform(put(path(q,"/grade-publication")).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content("{\"published\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(get(path(q,"/attempts/"+a)).with(actor(student))).andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(0));
    }
    @Test void roleAndClassroomOwnershipAreChecked() throws Exception {
        var body=json.writeValueAsString(new Write("Q",config(Timeout.AUTO_SUBMIT,Publication.AUTO)));
        for(var forbidden:List.of(student,otherTeacher,admin)) mvc.perform(post(base).with(actor(forbidden))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT);
        mvc.perform(put(path(q,"/status")).with(actor(otherTeacher)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/classrooms/"+UUID.randomUUID()+"/quizzes/"+q).with(actor(teacher))).andExpect(status().isNotFound());
    }
    @Test void validationErrorsAndInvalidAnswerDoNotPersistChanges() throws Exception {
        mvc.perform(post(base).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"config\":null}"))
                .andExpect(status().isBadRequest());
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT);publish(q);String a=start(q);
        mvc.perform(put(path(q,"/attempts/"+a+"/answers/"+QUESTION)).with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Answer(List.of(UUID.randomUUID()))))).andExpect(status().isBadRequest());
        assertThat(attempts.findById(UUID.fromString(a)).orElseThrow().getAnswers()).isEqualTo("{}");
        mvc.perform(put(path(q,"/status")).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SCHEDULED\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void draftIsHiddenAndStudentListUsesPagination() throws Exception {
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT);
        mvc.perform(get(path(q,"")).with(actor(student))).andExpect(status().isNotFound());
        mvc.perform(get(base).with(actor(student))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        publish(q);
        mvc.perform(get(base).with(actor(student)).queryParam("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pagination.size").value(1))
                .andExpect(jsonPath("$.data.items[0].teacherConfiguration").isEmpty());
    }
    @Test void repeatedStartAndSubmitDoNotDuplicateRows() throws Exception {
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT);publish(q);String a=start(q);assertThat(start(q)).isEqualTo(a);
        for(int i=0;i<2;i++)mvc.perform(post(path(q,"/attempts/"+a+"/submit")).with(actor(student))).andExpect(status().isOk());
        em.flush();em.clear(); assertThat(attempts.findByQuizIdAndStudentIdOrderByNumberAsc(UUID.fromString(q),student.getId())).hasSize(1);
    }
    @Test void exactExpiryRejectsLateAnswerButCommitsAutoSubmission() throws Exception {
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT);publish(q);String a=start(q);clock.now=NOW.plusSeconds(60);
        mvc.perform(put(path(q,"/attempts/"+a+"/answers/"+QUESTION)).with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Answer(List.of(CORRECT))))).andExpect(status().isConflict());
        em.flush();em.clear();var persisted=attempts.findById(UUID.fromString(a)).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("SUBMITTED");assertThat(persisted.getScore()).isEqualByComparingTo("0");
        assertThat(persisted.getAnswers()).isEqualTo("{}");
    }
    @Test void lockExpiryDoesNotProduceOfficialScore() throws Exception {
        String q=create(Publication.AUTO,Timeout.LOCK_WITHOUT_SUBMIT);publish(q);String a=start(q);clock.now=NOW.plusSeconds(60);
        mvc.perform(post(path(q,"/attempts/"+a+"/submit")).with(actor(student))).andExpect(status().isConflict());
        em.flush();em.clear();var persisted=attempts.findById(UUID.fromString(a)).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("EXPIRED");assertThat(persisted.getScore()).isNull();assertThat(persisted.getSubmittedAt()).isNull();
    }
    @Test void duplicateAndGradeOverridePreserveOriginalAttempt() throws Exception {
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT);publish(q);String a=start(q);
        mvc.perform(post(path(q,"/attempts/"+a+"/submit")).with(actor(student))).andExpect(status().isOk());
        mvc.perform(post(path(q,"/students/"+student.getId()+"/grade-overrides")).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":75,\"reason\":\"Reviewed\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.originalScore").value(0)).andExpect(jsonPath("$.data.finalScore").value(75));
        String copy=data(mvc.perform(post(path(q,"/duplicate")).with(actor(teacher))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();
        em.flush();em.clear();
        assertThat(attempts.findByQuizIdAndStudentIdOrderByNumberAsc(UUID.fromString(copy),student.getId())).isEmpty();
        assertThat(attempts.findById(UUID.fromString(a)).orElseThrow().getScore()).isEqualByComparingTo("0");
        assertThat(grades.findFirstByQuizIdAndStudentIdOrderByRevisionDesc(UUID.fromString(q),student.getId()).orElseThrow().getFinalScore()).isEqualByComparingTo("75");
    }
    @Test void failedOverrideLeavesOriginalScoreAndHistoryUntouched() throws Exception {
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT);publish(q);String a=start(q);
        mvc.perform(post(path(q,"/attempts/"+a+"/submit")).with(actor(student))).andExpect(status().isOk());
        mvc.perform(post(path(q,"/students/"+student.getId()+"/grade-overrides")).with(actor(otherTeacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":75,\"reason\":\"Reviewed\"}")).andExpect(status().isForbidden());
        assertThat(grades.findFirstByQuizIdAndStudentIdOrderByRevisionDesc(UUID.fromString(q),student.getId())).isEmpty();
        assertThat(attempts.findById(UUID.fromString(a)).orElseThrow().getScore()).isEqualByComparingTo("0");
    }
    @Test void classroomContainingQuizCannotBeDeletedEvenWithoutMemberships() throws Exception {
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT);
        memberships.deleteAll(memberships.findByClassroomIdAndStatus(classroom.getId(),MembershipStatus.ACTIVE));em.flush();
        mvc.perform(delete("/api/v1/classrooms/"+classroom.getId()).with(actor(teacher)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLASS_HAS_RETAINED_DATA"));
        em.flush();em.clear();assertThat(quizzes.existsById(UUID.fromString(q))).isTrue();assertThat(classrooms.existsById(classroom.getId())).isTrue();
    }
    @Test void successiveGradeOverridesUseLatestRevisionAndRetainEveryChange() throws Exception {
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT);publish(q);String a=start(q);
        mvc.perform(post(path(q,"/attempts/"+a+"/submit")).with(actor(student))).andExpect(status().isOk());
        for(int value:List.of(75,85)) mvc.perform(post(path(q,"/students/"+student.getId()+"/grade-overrides")).with(actor(teacher))
                .contentType(MediaType.APPLICATION_JSON).content("{\"score\":"+value+",\"reason\":\"Review "+value+"\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.originalScore").value(0));
        em.flush();em.clear();
        mvc.perform(get(path(q,"/students/"+student.getId()+"/result")).with(actor(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.originalAutoScore").value(0)).andExpect(jsonPath("$.data.finalScore").value(85));
        mvc.perform(get(path(q,"/students/"+student.getId()+"/grade-overrides")).with(actor(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
        assertThat(attempts.findById(UUID.fromString(a)).orElseThrow().getScore()).isEqualByComparingTo("0");
    }
    @Test void everyTeacherMutationRejectsNonOwnerWithoutChangingQuiz() throws Exception {
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT);
        var requests=List.of(
                put(path(q,"")).content(json.writeValueAsString(new Write("Unauthorized change",config(Timeout.AUTO_SUBMIT,Publication.AUTO)))),
                put(path(q,"/status")).content("{\"status\":\"PUBLISHED\"}"),
                put(path(q,"/grade-publication")).content("{\"published\":true}"),
                put(path(q,"/students/"+student.getId()+"/access-override")).content("{\"resetAttempts\":true,\"extraAttempts\":2}"),
                post(path(q,"/duplicate")),
                post(path(q,"/students/"+student.getId()+"/grade-overrides")).content("{\"score\":90,\"reason\":\"Review\"}"),
                delete(path(q,"")));
        for(var request:requests)mvc.perform(request.with(actor(otherTeacher)).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
        em.flush();em.clear();var retained=quizzes.findById(UUID.fromString(q)).orElseThrow();
        assertThat(retained.getTitle()).isEqualTo("Quiz");assertThat(retained.getStatus()).isEqualTo("DRAFT");assertThat(retained.isGradesPublished()).isFalse();
        assertThat(quizzes.findByClassroomId(classroom.getId(),org.springframework.data.domain.PageRequest.of(0,100)).getTotalElements()).isEqualTo(1);
    }
    @Test void nonMembersAndOtherTeachersCannotReadPrivateQuizCollectionsOrAttempts() throws Exception {
        String q=create(Publication.AUTO,Timeout.AUTO_SUBMIT);var nonmember=account(AccountRole.STUDENT);
        for(var outsider:List.of(nonmember,otherTeacher)) {
            mvc.perform(get(base).with(actor(outsider))).andExpect(status().isForbidden());
            mvc.perform(get(path(q,"")).with(actor(outsider))).andExpect(status().isForbidden());
            mvc.perform(get(path(q,"/attempts")).with(actor(outsider))).andExpect(status().isForbidden());
        }
    }
    private String create(Publication pub,Timeout timeout)throws Exception {
        return data(mvc.perform(post(base).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Write("Quiz",config(timeout,pub))))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("id").asText();
    }
    private void publish(String q)throws Exception{mvc.perform(put(path(q,"/status")).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}")).andExpect(status().isOk());}
    private String start(String q)throws Exception{return data(mvc.perform(post(path(q,"/attempts")).with(actor(student))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();}
    private String path(String q,String suffix){return base+"/"+q+suffix;}
    private JsonNode data(String body)throws Exception{return json.readTree(body).path("data");}
    private AccountEntity account(AccountRole role){var a=new AccountEntity();a.setId(UUID.randomUUID());a.setRole(role);a.setStatus(AccountStatus.ACTIVE);a.setUsername("q"+UUID.randomUUID());a.setEmail(UUID.randomUUID()+"@test.example");return accounts.save(a);}
    private static RequestPostProcessor actor(AccountEntity a){return authentication(new UsernamePasswordAuthenticationToken(a.getId().toString(),"N/A",List.of(new SimpleGrantedAuthority("ROLE_"+a.getRole().name()))));}
}
