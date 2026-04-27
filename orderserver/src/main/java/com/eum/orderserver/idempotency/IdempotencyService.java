package com.eum.orderserver.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final OrderEventLogRepository eventLogRepository;

    @Transactional
    public boolean tryRegister(String eventType, String eventKey) {
        int inserted = eventLogRepository.insertIfAbsent(eventType, eventKey, LocalDateTime.now());
        return inserted > 0;
    }
}