package com.dorm.ndidrs_back;

import com.dorm.ndidrs_back.entity.DormLeave;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DormLeaveJsonTests {
    @Test
    void acceptsDateTimeFormatSentByLeaveApplyPage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        DormLeave leave = objectMapper.readValue("""
                {
                  "startTime": "2026-07-22 00:00:00",
                  "endTime": "2026-07-23 23:59:59"
                }
                """, DormLeave.class);

        assertEquals(LocalDateTime.of(2026, 7, 22, 0, 0, 0), leave.getStartTime());
        assertEquals(LocalDateTime.of(2026, 7, 23, 23, 59, 59), leave.getEndTime());
    }
}
