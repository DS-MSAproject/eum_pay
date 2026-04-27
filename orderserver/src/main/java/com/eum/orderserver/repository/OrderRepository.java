package com.eum.orderserver.repository;

import com.eum.orderserver.domain.Orders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Orders, Long>, JpaSpecificationExecutor<Orders> {

    Optional<Orders> findByOrderId(Long orderId);
}
