package com.algolearner.platform.profile.mock;

import com.algolearner.platform.common.security.AccountRepository;
import com.algolearner.platform.profile.PracticeRepository;
import com.algolearner.platform.profile.ProfileRepository;
import com.algolearner.platform.profile.dashboard.DashboardResponse.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Shared source fixtures only, not another module's persistence schema or management API. */
@Component
@Profile("mock")
public class MockData implements AccountRepository, PracticeRepository {
    public static UUID id(String key) { return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    public record ClassSource(Classroom value, UUID owner, String status, Instant createdAt) { }
    public record Membership(UUID classroom, UUID student, String status) { }
    public record AssignmentSource(Assignment value, String status, Map<UUID,Instant> extensions) { }
    public record QuizSource(Quiz value, String status, Map<UUID,Instant> extensions) { }
    public record Enrollment(UUID account, Course value, String status) { }
    public record Content(UUID id, UUID contributor, String kind, String status, String moderation, Instant createdAt) { }
    public record Notice(UUID recipient, Notification value, boolean deleted) { }
    public record Work(Submission value, String gradingStatus) { }
    public record Judge(UUID account, UUID publicProblem, UUID classroom, String verdict, boolean official, Instant submittedAt) { }
    public final Map<UUID,ProfileRepository.Profile> profiles = new ConcurrentHashMap<>();
    public final List<Account> accounts = new ArrayList<>();
    public final List<Restriction> restrictions = new ArrayList<>();
    public final Set<UUID> violations = new HashSet<>();
    public final List<ClassSource> classes = new ArrayList<>();
    public final List<Membership> memberships = new ArrayList<>();
    public final List<AssignmentSource> assignments = new ArrayList<>();
    public final List<QuizSource> quizzes = new ArrayList<>();
    public final List<Enrollment> enrollments = new ArrayList<>();
    public final List<Content> contents = new ArrayList<>();
    public final List<Notice> notices = new ArrayList<>();
    public final List<Work> works = new ArrayList<>();
    public final List<Judge> judge = new ArrayList<>();
    public final List<Activity> activities = new ArrayList<>();
    public final List<Audit> audits = new ArrayList<>();
    public MockData(Clock clock) {
        Instant now=clock.instant();
        for (String name:List.of("student","student2","empty","teacher","teacher2","admin","suspended","deactivated","locked")) {
            String role=name.startsWith("teacher")?"TEACHER":name.equals("admin")?"ADMIN":"STUDENT";
            accounts.add(new Account(id(name),name,role,name.equals("deactivated")?"DEACTIVATED":"ACTIVE",now.minus(Duration.ofDays(20))));
            profiles.put(id(name),new ProfileRepository.Profile(id(name),"Demo "+name,name,name+"@example.test",null,"Learning algorithms",role,now.minus(Duration.ofDays(20)),!Set.of("student2","empty").contains(name)));
        }
        restrictions.add(new Restriction(id("suspended"),"SUSPENSION",now.minusSeconds(60),null,null));
        restrictions.add(new Restriction(id("locked"),"ADMIN_LOCK",now.minusSeconds(60),null,null));
        violations.add(id("suspended"));
        for (int n=1;n<=4;n++) {
            UUID cid=id("class"+n), owner=id(n==3?"teacher2":"teacher");
            classes.add(new ClassSource(new Classroom(cid,"Algorithms "+n),owner,n==4?"ARCHIVED":"ACTIVE",now.minus(Duration.ofDays(n))));
            memberships.add(new Membership(cid,id(n==3?"student2":"student"),"ACTIVE"));
            memberships.add(new Membership(cid,id("empty"),"LEFT"));
            for (int k=0;k<4;k++) {
                UUID aid=id("assignment"+n+"-"+k);
                Instant opens=now.plus(Duration.ofDays(k==2?2:-2));
                Instant due=now.plus(Duration.ofDays(k==1?-1:3+k));
                assignments.add(new AssignmentSource(new Assignment(aid,cid,"Algorithms "+n,"Assignment "+k,opens,due),k==3?"DRAFT":"PUBLISHED",k==1?Map.of(id("student"),now.plus(Duration.ofDays(1))):Map.of()));
                quizzes.add(new QuizSource(new Quiz(id("quiz"+n+"-"+k),cid,"Algorithms "+n,"Quiz "+k,opens,due),k==3?"DRAFT":"PUBLISHED",Map.of()));
                works.add(new Work(new Submission(id("work"+n+"-"+k),"ASSIGNMENT_WORK",cid,aid,null,id(n==3?"student2":"student"),n==3?"student2":"student",now.minusSeconds(k*60+20)),k==1?"GRADED":"PENDING"));
            }
        }
        // A newer graded attempt supersedes an older pending attempt.
        works.add(new Work(new Submission(id("new-work"),"ASSIGNMENT_WORK",id("class1"),id("assignment1-0"),null,id("student"),"student",now.minusSeconds(1)),"GRADED"));
        for (int n=0;n<4;n++) {
            String kind=n%2==0?"COURSE":"PROBLEM";
            contents.add(new Content(id("content"+n),id(n<2?"teacher":"teacher2"),kind,n<2?"PUBLISHED":"DRAFT",n==0?"APPROVED":"PENDING",now.minus(Duration.ofDays(n+1))));
        }
        enrollments.add(new Enrollment(id("student"),new Course(id("content0"),"Algorithm foundations",2,6,0),"ACTIVE"));
        enrollments.add(new Enrollment(id("student2"),new Course(id("content0"),"Algorithm foundations",6,6,0),"ACTIVE"));
        for (Account a:accounts) {
            for (int n=0;n<7;n++) notices.add(new Notice(a.id(),new Notification(id(a.username()+"notice"+n),"Update "+n,now.minusSeconds(n*60),n%2==0?null:now.minusSeconds(1)),false));
            notices.add(new Notice(a.id(),new Notification(id(a.username()+"old"),"Expired",now.minus(Duration.ofDays(91)),null),false));
            notices.add(new Notice(a.id(),new Notification(id(a.username()+"deleted"),"Deleted",now,null),true));
        }
        ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");
        for (int day:new int[]{0,1,2,7,8,9,10}) {
            Instant time=LocalDate.now(clock.withZone(zone)).minusDays(day).atStartOfDay(zone).toInstant();
            judge.add(new Judge(id("student"),id("content1"),null,"ACCEPTED",true,time));
        }
        judge.add(new Judge(id("student"),id("content3"),null,"ACCEPTED",true,now.minusSeconds(5)));
        judge.add(new Judge(id("student"),id("wrong"),null,"WRONG_ANSWER",true,now.minusSeconds(5)));
        judge.add(new Judge(id("student"),id("run"),null,"ACCEPTED",false,now.minusSeconds(5)));
        judge.add(new Judge(id("student"),null,id("class1"),"ACCEPTED",true,now.minusSeconds(5)));
        judge.add(new Judge(id("student2"),id("content1"),null,"ACCEPTED",true,now.minus(Duration.ofDays(4))));
        activities.add(new Activity("activity1","PROFILE","PROFILE_UPDATED","Demo profile updated",now.minusSeconds(30)));
        audits.add(new Audit("audit1","ADMIN_LOCK","SUCCESS",now.minusSeconds(60)));
        audits.add(new Audit("audit2","LOGIN","SUCCESS",now.minusSeconds(20)));
    }
    @Override public List<Account> accounts() { return List.copyOf(accounts); }
    @Override public List<Restriction> restrictions() { return List.copyOf(restrictions); }
    @Override public List<Accepted> acceptedPublicSubmissions(UUID account) {
        return judge.stream().filter(s -> s.account().equals(account) && s.official() && s.publicProblem()!=null && s.verdict().equals("ACCEPTED"))
            .map(s -> new Accepted(s.publicProblem(),s.submittedAt())).toList();
    }
}
