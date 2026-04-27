package com.eum.orderserver.repository;

import com.eum.orderserver.domain.OrderDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderDetailsRepository extends JpaRepository<OrderDetails, Long> {

    @Query("""
            select d from OrderDetails d
            join fetch d.orders o
            where d.userId = :userId
            order by o.time desc, d.id desc
            """)
    List<OrderDetails> findAllByUserId(@Param("userId") Long userId);

    List<OrderDetails> findAllByOrdersOrderIdOrderByIdAsc(Long orderId);
}