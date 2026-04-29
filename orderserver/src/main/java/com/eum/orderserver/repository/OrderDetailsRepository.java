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

    @Query(value = """
            SELECT
                d.item_id,
                d.snapshot_json->>'productName',
                SUM(d.amount),
                SUM(d.total_price)
            FROM order_details d
            JOIN orders o ON d.order_id = o.order_id
            WHERE o.order_state = 'ORDER_COMPLETED'
            GROUP BY d.item_id, d.snapshot_json->>'productName'
            ORDER BY SUM(d.amount) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopProductSales(@Param("limit") int limit);
}
