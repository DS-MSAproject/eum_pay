package com.eum.orderserver.repository;

import com.eum.orderserver.domain.Orders;
import com.eum.orderserver.domain.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Orders, Long>, JpaSpecificationExecutor<Orders> {

    Optional<Orders> findByOrderId(Long orderId);

    long countByTimeGreaterThanEqual(LocalDateTime from);

    long countByOrderStateIn(Collection<OrderState> states);

    @Query("SELECT o.orderState, COUNT(o) FROM Orders o GROUP BY o.orderState")
    List<Object[]> countGroupByOrderState();
}
