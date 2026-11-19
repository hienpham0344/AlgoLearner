package com.algolearner.platform.course;

import java.util.UUID;
import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.common.security.AccountAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.algolearner.platform.common.dto.PageResponse;

@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicCourseService {
    private final PublicCourseRepository courses;
    private final AccountAccess accounts;
    private final AuditService audit;

    @Transactional
    public CourseDtos.Summary create(UUID actor, CourseDtos.Create request) {
        requireRole(actor,"TEACHER");
        validText(request.title(),255);
        return courses.create(actor,request);
    }

    @Transactional
    public CourseDtos.Unit addUnit(UUID actor, UUID course, CourseDtos.UnitRequest request) {
        var current = ownedDraft(actor,course);
        validText(request.title(),255);validText(request.content(),100_000);
        if (current.status().equals("REJECTED")) courses.changeStatus(course,"DRAFT",null);
        return courses.addUnit(course,request);
    }

    @Transactional
    public CourseDtos.Summary submitReview(UUID actor, UUID course) {
        var current = ownedDraft(actor,course);
        if (courses.unitCount(course) == 0) throw failure(HttpStatus.UNPROCESSABLE_ENTITY,"COURSE_EMPTY","Course must contain a lesson");
        courses.changeStatus(course,"PENDING_REVIEW",null);
        audit.record(actor,"TEACHER","PUBLIC_COURSE",course.toString(),"PUBLIC_COURSE_SUBMITTED",
                current.status(),"PENDING_REVIEW",null,null);
        return required(course,false);
    }

    @Transactional
    public CourseDtos.Summary review(UUID actor, UUID course, CourseDtos.Review request) {
        requireRole(actor,"ADMIN");
        var current = required(course,true);
        if (!current.status().equals("PENDING_REVIEW")) throw failure(HttpStatus.CONFLICT,"COURSE_STATE_CONFLICT","Course is not pending review");
        if (!request.approved()) validText(request.reason(),500);
        String reason = request.reason() == null ? null : request.reason().strip();
        if (reason != null && reason.length() > 500) throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Review reason is too long");
        String state = request.approved() ? "PUBLISHED" : "REJECTED";
        courses.changeStatus(course,state,reason);
        audit.record(actor,"ADMIN","PUBLIC_COURSE",course.toString(),"PUBLIC_COURSE_REVIEWED",current.status(),state,reason,null);
        return required(course,false);
    }

    @Transactional
    public CourseDtos.Progress enroll(UUID actor, UUID course) {
        requireLearner(actor);requirePublished(course);
        courses.enroll(actor,course);
        return ownProgress(actor,course);
    }

    @Transactional
    public CourseDtos.Progress complete(UUID actor, UUID course, UUID unit) {
        requireLearner(actor);requirePublished(course);
        var enrollment = courses.enrollment(actor,course).orElseThrow(() -> failure(HttpStatus.FORBIDDEN,"COURSE_NOT_ENROLLED","Enroll before completing lessons"));
        if (!courses.containsUnit(course,unit)) throw failure(HttpStatus.NOT_FOUND,"COURSE_UNIT_NOT_FOUND","Course lesson not found");
        courses.complete(enrollment,unit);
        return ownProgress(actor,course);
    }

    public CourseDtos.Progress progress(UUID actor, UUID course) {
        requireLearner(actor);requirePublished(course);
        return ownProgress(actor,course);
    }

    public CourseDtos.Detail detail(UUID actor, UUID course) {
        requireLearner(actor);
        String role = accounts.activeRole(actor);
        var current = required(course,false);
        if (!current.status().equals("PUBLISHED") && !(role.equals("TEACHER") && current.authorId().equals(actor))) {
            throw failure(HttpStatus.NOT_FOUND,"COURSE_NOT_FOUND","Course not found");
        }
        return new CourseDtos.Detail(current,courses.units(course));
    }

    public CourseDtos.Detail administrationDetail(UUID actor, UUID course) {
        requireRole(actor,"ADMIN");
        return new CourseDtos.Detail(required(course,false),courses.units(course));
    }

    public PageResponse<CourseDtos.Summary> list(UUID actor, String state, boolean own, boolean administration, Pageable pageable) {
        if (pageable.getPageSize() > 100) throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Page size exceeds limit");
        UUID author = null;
        if (administration) requireRole(actor,"ADMIN");
        else if (own) { requireRole(actor,"TEACHER");author=actor; }
        else { requireLearner(actor);state="PUBLISHED"; }
        if (!java.util.Set.of("DRAFT","PENDING_REVIEW","PUBLISHED","REJECTED").contains(state)) throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Invalid course status");
        return PageResponse.fromPage(new PageImpl<>(courses.list(state,author,pageable.getPageNumber(),pageable.getPageSize()),pageable,courses.count(state,author)));
    }

    private CourseDtos.Progress ownProgress(UUID actor, UUID course) {
        UUID enrollment = courses.enrollment(actor,course).orElseThrow(() -> failure(HttpStatus.NOT_FOUND,"COURSE_ENROLLMENT_NOT_FOUND","Enrollment not found"));
        int total = courses.unitCount(course);int completed = courses.completedUnits(enrollment,course);
        return new CourseDtos.Progress(course,actor,completed,total,total == 0 ? 0 : 100.0*completed/total);
    }

    private CourseDtos.Summary ownedDraft(UUID actor, UUID course) {
        requireRole(actor,"TEACHER");
        var current = required(course,true);
        if (!current.authorId().equals(actor)) throw failure(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION","Only the course author may edit it");
        if (!java.util.Set.of("DRAFT","REJECTED").contains(current.status())) throw failure(HttpStatus.CONFLICT,"COURSE_STATE_CONFLICT","Only draft courses may be edited");
        return current;
    }

    private void requirePublished(UUID course) {
        if (!required(course,false).status().equals("PUBLISHED")) throw failure(HttpStatus.NOT_FOUND,"COURSE_NOT_FOUND","Course not found");
    }

    private CourseDtos.Summary required(UUID course, boolean lock) {
        return courses.find(course,lock).orElseThrow(() -> failure(HttpStatus.NOT_FOUND,"COURSE_NOT_FOUND","Course not found"));
    }

    private void requireLearner(UUID actor) {
        if (!java.util.Set.of("STUDENT","TEACHER").contains(accounts.activeRole(actor))) throw failure(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION","Learning access denied");
    }

    private void requireRole(UUID actor, String role) {
        if (!accounts.activeRole(actor).equals(role)) throw failure(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION","Course access denied");
    }

    private void validText(String text, int max) {
        if (text == null || text.isBlank() || text.length() > max) throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Invalid course content");
    }

    private AppException failure(HttpStatus status, String code, String message) {
        return new AppException(message,status,code) { };
    }
}
