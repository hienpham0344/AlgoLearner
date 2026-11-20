package com.algolearner.platform.assignment;

import java.util.UUID;

record AssignmentProgressChanged(UUID classroomId, UUID assignmentId, UUID studentId) { }
