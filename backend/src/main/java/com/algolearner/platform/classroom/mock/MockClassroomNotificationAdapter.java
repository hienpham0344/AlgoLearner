package com.algolearner.platform.classroom.mock;

import com.algolearner.platform.classroom.ClassroomNotificationPort;
import com.algolearner.platform.profile.dashboard.DashboardResponse.Notification;
import com.algolearner.platform.profile.mock.MockData;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
@Profile("mock")
public class MockClassroomNotificationAdapter implements ClassroomNotificationPort {
    private final MockData data;
    public MockClassroomNotificationAdapter(MockData data) { this.data=data; }
    @Override public void invitationCreated(UUID invitationId,UUID classroomId,UUID studentAccountId,Instant occurredAt) {
        data.notices.add(new MockData.Notice(studentAccountId,
                new Notification(MockData.id("invitation-notice:"+invitationId),"Classroom invitation",occurredAt,null),false));
    }
}
