package com.example.do_an_ck_J2EE.repository;

import com.example.do_an_ck_J2EE.entity.Order;
import com.example.do_an_ck_J2EE.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser(User user);

    List<Order> findByUserOrderByCreatedAtDesc(User user);

    Page<Order> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Page<Order> findAllByOrderByIdDesc(Pageable pageable);

    Page<Order> findByStatus(String status, Pageable pageable);

    Page<Order> findByPaymentMethod(String paymentMethod, Pageable pageable);

    Page<Order> findByCreatedAtBetweenOrderByIdDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Query("""
        SELECT o FROM Order o
        WHERE (:start IS NULL OR o.createdAt >= :start)
          AND (:end IS NULL OR o.createdAt <= :end)
          AND (:status IS NULL OR :status = '' OR o.status = :status)
          AND (:payment IS NULL OR :payment = '' OR o.paymentMethod = :payment)
          AND (
                :keyword IS NULL OR :keyword = ''
                OR CAST(o.id AS string) LIKE CONCAT('%', :keyword, '%')
                OR o.phone LIKE CONCAT('%', :keyword, '%')
                OR LOWER(o.receiverName) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
        ORDER BY o.id DESC
    """)
    Page<Order> searchOrders(LocalDateTime start,
                             LocalDateTime end,
                             String keyword,
                             String status,
                             String payment,
                             Pageable pageable);

    @Query("SELECT FUNCTION('DATE', o.createdAt), SUM(o.totalAmount) " +
           "FROM Order o GROUP BY FUNCTION('DATE', o.createdAt) ORDER BY FUNCTION('DATE', o.createdAt)")
    List<Object[]> getRevenueByDate();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN ('PENDING', 'PENDING_PAYMENT')")
    long countNewOrders();
}