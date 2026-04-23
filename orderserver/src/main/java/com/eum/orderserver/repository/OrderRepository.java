package com.eum.orderserver.repository;

import com.eum.orderserver.domain.Orders;
import com.eum.orderserver.domain.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface OrderRepository extends JpaRepository<Orders, Long> {

    @Query("""
            select o from Orders o
             where o.userId = :userId
               and (:startDate is null or o.time >= :startDate)
               and (:endDate is null or o.time <= :endDate)
               and (:status is null or o.orderState = :status)
            """)
    Page<Orders> findOrders(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("status") OrderState status,
            Pageable pageable
    );
}
