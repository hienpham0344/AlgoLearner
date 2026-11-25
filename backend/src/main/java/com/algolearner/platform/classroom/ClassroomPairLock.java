package com.algolearner.platform.classroom;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@org.springframework.context.annotation.Profile("!mock")
class ClassroomPairLock {
    private final JdbcTemplate jdbcTemplate;

    ClassroomPairLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void lock(UUID classroomId, UUID studentId) {
        long key = classroomId.getMostSignificantBits()
                ^ Long.rotateLeft(classroomId.getLeastSignificantBits(), 13)
                ^ Long.rotateLeft(studentId.getMostSignificantBits(), 29)
                ^ Long.rotateLeft(studentId.getLeastSignificantBits(), 43);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, key);
                try (var result = statement.executeQuery()) {
                    result.next();
                }
            }
            return null;
        });
    }
}
