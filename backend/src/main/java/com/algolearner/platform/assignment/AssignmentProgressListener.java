package com.algolearner.platform.assignment;

import com.algolearner.platform.lesson.LessonService;
import com.algolearner.platform.lesson.LinkedItemType;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!mock")
public class AssignmentProgressListener {
    private final LessonService lessons;
    public AssignmentProgressListener(LessonService lessons) { this.lessons = lessons; }
    @EventListener
    public void changed(AssignmentProgressChanged event) {
        lessons.recomputeForItem(event.classroomId(), LinkedItemType.ASSIGNMENT, event.assignmentId(), event.studentId());
    }
}
