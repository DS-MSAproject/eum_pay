package com.eum.orderserver.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, Long> {

    List<OrderOutbox> findByAggregateIdOrderByCreatedAtAsc(Long aggregateId);
}