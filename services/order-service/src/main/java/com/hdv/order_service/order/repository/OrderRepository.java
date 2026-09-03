package com.hdv.order_service.order.repository;

import com.hdv.order_service.order.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Lấy các đơn hàng vẫn đang PENDING nhưng đã tạo quá một khoảng thời gian (VD: quá 12 phút)
    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING' AND o.createdAt <= :thresholdTime")
    List<Order> findExpiredPendingOrders(@Param("thresholdTime") LocalDateTime thresholdTime);

    List<Order> findByUserIdOrderByCreatedAtDesc(java.util.UUID userId);

    @Query("SELECT COUNT(o), COALESCE(SUM(o.totalPrice), 0L) FROM Order o WHERE o.eventId = :eventId AND o.status = com.hdv.order_service.order.domain.enums.OrderStatus.CONFIRMED")
    List<Object[]> getSalesReportByEventId(@Param("eventId") UUID eventId);
}