package com.algolearner.platform.grading;

import com.algolearner.platform.grading.GradebookDtos.VisibilityRequest;
import com.algolearner.platform.grading.GradebookDtos.VisibilityView;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@RequiredArgsConstructor
public class GradebookVisibilityService {
    private final GradebookAccess access;
    private final GradebookSchemeService schemes;
    private final GradebookRepository gradebooks;

    @Transactional
    public VisibilityView update(Authentication principal, UUID classroomId, VisibilityRequest request) {
        access.requireTeacherWrite(principal, classroomId);
        var gradebook = schemes.lock(classroomId);
        GradebookSchemeService.requireVersion(gradebook, request.expectedVersion());
        gradebook.setShowOverall(request.showOverall());
        gradebooks.flush();
        return new VisibilityView(gradebook.isShowOverall(), gradebook.getVersion());
    }
}
