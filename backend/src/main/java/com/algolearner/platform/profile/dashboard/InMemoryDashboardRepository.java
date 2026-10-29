package com.algolearner.platform.profile.dashboard;

import com.algolearner.platform.profile.mock.MockData;
import com.algolearner.platform.profile.mock.MockData.*;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static com.algolearner.platform.profile.dashboard.DashboardResponse.*;

@Repository
@org.springframework.context.annotation.Profile("mock")
public class InMemoryDashboardRepository implements DashboardRepository {
    private final MockData data;
    public InMemoryDashboardRepository(MockData data) { this.data=data; }
    private <T> Preview<T> preview(Stream<T> source,Context c) {
        List<T> all=source.toList();
        return new Preview<>(all.stream().limit(c.limit()).toList(),all.size(),all.size()>c.limit());
    }
    private boolean active(UUID classroom) { return data.classes.stream().anyMatch(x -> x.value().classroomId().equals(classroom) && x.status().equals("ACTIVE")); }
    private boolean owns(UUID classroom,UUID account) { return data.classes.stream().anyMatch(x -> x.value().classroomId().equals(classroom) && x.owner().equals(account) && x.status().equals("ACTIVE")); }
    private boolean member(UUID classroom,UUID account) { return active(classroom) && data.memberships.stream().anyMatch(x -> x.classroom().equals(classroom) && x.student().equals(account) && x.status().equals("ACTIVE")); }
    private boolean upcoming(Instant deadline,Context c) { return deadline!=null && !deadline.isBefore(c.now()) && deadline.isBefore(c.upcomingEnd()); }
    private Stream<ClassSource> owned(Context c) { return data.classes.stream().filter(x -> owns(x.value().classroomId(),c.accountId())).sorted(Comparator.comparing(x -> x.value().name())); }
    public Preview<Classroom> studentClassrooms(Context c) {
        return preview(data.classes.stream().filter(x -> member(x.value().classroomId(),c.accountId())).map(ClassSource::value).sorted(Comparator.comparing(Classroom::name)),c);
    }
    public Preview<Classroom> teacherClassrooms(Context c) { return preview(owned(c).map(ClassSource::value),c); }
    public Preview<Assignment> studentAssignments(Context c) {
        return preview(data.assignments.stream().filter(x -> x.status().equals("PUBLISHED") && member(x.value().classroomId(),c.accountId()))
            .map(x -> { var a=x.value(); return new Assignment(a.assignmentId(),a.classroomId(),a.classroomName(),a.title(),a.opensAt(),x.extensions().getOrDefault(c.accountId(),a.effectiveDueAt())); })
            .filter(a -> (a.opensAt()==null || !a.opensAt().isAfter(c.now())) && upcoming(a.effectiveDueAt(),c))
            .sorted(Comparator.comparing(Assignment::effectiveDueAt).thenComparing(Assignment::assignmentId)),c);
    }
    public Preview<Assignment> teacherAssignments(Context c) {
        return preview(data.assignments.stream().filter(x -> x.status().equals("PUBLISHED") && owns(x.value().classroomId(),c.accountId())).map(AssignmentSource::value)
            .filter(a -> ((a.opensAt()==null || !a.opensAt().isAfter(c.now())) && (a.effectiveDueAt()==null || !a.effectiveDueAt().isBefore(c.now())))
                || (a.opensAt()!=null && a.opensAt().isAfter(c.now()) && a.opensAt().isBefore(c.upcomingEnd())))
            .sorted(Comparator.comparing((Assignment a) -> a.opensAt()==null?c.now():a.opensAt()).thenComparing(Assignment::assignmentId)),c);
    }
    public Preview<Quiz> studentQuizzes(Context c) {
        return preview(data.quizzes.stream().filter(x -> x.status().equals("PUBLISHED") && member(x.value().classroomId(),c.accountId()))
            .map(x -> { var q=x.value(); return new Quiz(q.quizId(),q.classroomId(),q.classroomName(),q.title(),q.opensAt(),x.extensions().getOrDefault(c.accountId(),q.effectiveClosesAt())); })
            .filter(q -> upcoming(q.effectiveClosesAt(),c)).sorted(Comparator.comparing(Quiz::effectiveClosesAt).thenComparing(Quiz::quizId)),c);
    }
    public Preview<Course> courses(Context c) {
        return preview(data.enrollments.stream().filter(e -> e.account().equals(c.accountId()) && e.status().equals("ACTIVE"))
            .filter(e -> data.contents.stream().anyMatch(x -> x.id().equals(e.value().courseId()) && x.status().equals("PUBLISHED")))
            .map(e -> { var v=e.value(); return new Course(v.courseId(),v.title(),v.completedUnits(),v.totalUnits(),v.totalUnits()==0?0:Math.round(10000.0*v.completedUnits()/v.totalUnits())/100.0); })
            .sorted(Comparator.comparing(Course::title).thenComparing(Course::courseId)),c);
    }
    public Preview<Notification> notifications(Context c) {
        return preview(data.notices.stream().filter(n -> n.recipient().equals(c.accountId()) && !n.deleted() && !n.value().createdAt().isBefore(c.now().minus(Duration.ofDays(90))))
            .map(Notice::value).sorted(Comparator.comparing(Notification::createdAt).thenComparing(Notification::notificationId).reversed()),c);
    }
    private Stream<Work> latestPending(UUID owner) {
        Comparator<Work> order=Comparator.comparing((Work w) -> w.value().submittedAt()).thenComparing(w -> w.value().submissionId());
        return data.works.stream().filter(w -> owns(w.value().classroomId(),owner) && w.value().kind().equals("ASSIGNMENT_WORK") && w.gradingStatus().equals("PENDING"))
            .filter(w -> data.works.stream().noneMatch(n -> n.value().kind().equals("ASSIGNMENT_WORK") && Objects.equals(n.value().assignmentId(),w.value().assignmentId())
                && n.value().studentAccountId().equals(w.value().studentAccountId()) && order.compare(n,w)>0));
    }
    public Preview<Submission> ungraded(Context c) {
        return preview(latestPending(c.accountId()).map(Work::value).sorted(Comparator.comparing(Submission::submittedAt).thenComparing(Submission::submissionId).reversed()),c);
    }
    public Preview<Submission> recentSubmissions(Context c) {
        Stream<Submission> code=data.judge.stream().filter(s -> s.official() && s.publicProblem()==null && s.classroom()!=null && owns(s.classroom(),c.accountId()))
            .map(s -> new Submission(MockData.id(s.account()+s.submittedAt().toString()),"CODE_JUDGE",s.classroom(),null,MockData.id("practice"),s.account(),
                data.accounts.stream().filter(a -> a.id().equals(s.account())).findFirst().orElseThrow().username(),s.submittedAt()));
        return preview(Stream.concat(data.works.stream().filter(w -> owns(w.value().classroomId(),c.accountId())).map(Work::value),code)
            .sorted(Comparator.comparing(Submission::submittedAt).thenComparing(Submission::submissionId).reversed()),c);
    }
    public Preview<ClassStatistics> classStatistics(Context c) {
        return preview(owned(c).map(x -> {
            UUID id=x.value().classroomId();
            return new ClassStatistics(id,x.value().name(),data.memberships.stream().filter(m -> m.classroom().equals(id) && m.status().equals("ACTIVE")).count(),
                data.assignments.stream().filter(a -> a.value().classroomId().equals(id) && a.status().equals("PUBLISHED")).count(),
                data.quizzes.stream().filter(q -> q.value().classroomId().equals(id) && q.status().equals("PUBLISHED")).count(),
                latestPending(c.accountId()).filter(w -> w.value().classroomId().equals(id)).count());
        }),c);
    }
    public Contributions contributions(Context c,boolean allTeachers) {
        var pending=data.contents.stream().filter(x -> x.moderation().equals("PENDING") && (allTeachers || x.contributor().equals(c.accountId())))
            .filter(x -> data.accounts.stream().anyMatch(a -> a.id().equals(x.contributor()) && a.role().equals("TEACHER"))).toList();
        return new Contributions(pending.stream().filter(x -> x.kind().equals("COURSE")).count(),pending.stream().filter(x -> x.kind().equals("PROBLEM")).count());
    }
    public Totals totals() {
        return new Totals(data.accounts.stream().filter(a -> a.role().equals("STUDENT")).count(),data.accounts.stream().filter(a -> a.role().equals("TEACHER")).count(),data.classes.size(),
            data.contents.stream().filter(x -> x.kind().equals("COURSE")).count(),data.contents.stream().filter(x -> x.kind().equals("PROBLEM")).count(),data.judge.stream().filter(Judge::official).count());
    }
    public Moderation moderation(Context c) {
        var restrictions=data.restrictions.stream().filter(r -> r.active(c.now())).toList();
        return new Moderation(restrictions.stream().filter(r -> Set.of("AUTO_LOCK","ADMIN_LOCK").contains(r.kind())).map(r -> r.accountId()).distinct().count(),
            restrictions.stream().filter(r -> r.kind().equals("SUSPENSION")).map(r -> r.accountId()).distinct().count(),data.violations.size());
    }
    public Preview<Activity> activities(Context c) { return preview(data.activities.stream().sorted(Comparator.comparing(Activity::occurredAt).thenComparing(Activity::eventId).reversed()),c); }
    public Preview<Audit> audits(Context c) {
        return preview(data.audits.stream().filter(a -> Set.of("ADMIN_LOCK","ADMIN_UNLOCK","SUSPEND_ACCOUNT","DEACTIVATE_ACCOUNT","GRADE_OVERRIDE","REJUDGE","TRANSFER_CLASS_OWNERSHIP","SYSTEM_CONFIGURATION_UPDATED").contains(a.action()))
            .sorted(Comparator.comparing(Audit::occurredAt).thenComparing(Audit::auditId).reversed()),c);
    }
    public List<ChartEvent> chartEvents(Instant from,Instant to) {
        List<ChartEvent> events=new ArrayList<>();
        data.accounts.stream().filter(a -> Set.of("STUDENT","TEACHER").contains(a.role())).forEach(a -> events.add(new ChartEvent(a.createdAt(),a.role(),1)));
        data.classes.forEach(x -> events.add(new ChartEvent(x.createdAt(),"CLASSROOM",1)));
        data.contents.forEach(x -> events.add(new ChartEvent(x.createdAt(),x.kind(),1)));
        data.judge.stream().filter(Judge::official).forEach(x -> events.add(new ChartEvent(x.submittedAt(),"SUBMISSION",1)));
        return events.stream().filter(e -> !e.occurredAt().isBefore(from) && e.occurredAt().isBefore(to)).toList();
    }
}
