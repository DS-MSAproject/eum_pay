package com.eum.authserver.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LogSearchResponse {

    private long totalHits;
    private List<LogEntry> logs;

    @Getter
    @Builder
    public static class LogEntry {
        private String serviceName;
        private String level;
        private String message;
        private String traceId;
        private String timestamp;
    }
}
