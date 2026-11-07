package com.algolearner.platform.classroom.mock;

import com.algolearner.platform.classroom.ClassroomAuditPort;
import com.algolearner.platform.profile.dashboard.DashboardResponse.Audit;
import com.algolearner.platform.profile.mock.MockData;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
@Profile("mock")
public class MockClassroomAuditAdapter implements ClassroomAuditPort {
    private final MockData data;
    public MockClassroomAuditAdapter(MockData data) { this.data=data; }
    @Override public void record(UUID actorAccountId,String action,UUID targetId,String outcome,Instant occurredAt) {
        data.audits.add(new Audit("classroom:"+action+":"+targetId+":"+occurredAt,action,outcome,occurredAt));
    }
}
