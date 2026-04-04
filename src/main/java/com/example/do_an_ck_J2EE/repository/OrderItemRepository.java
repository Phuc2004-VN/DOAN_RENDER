package com.example.do_an_ck_J2EE.repository;

import com.example.do_an_ck_J2EE.entity.Order;
import com.example.do_an_ck_J2EE.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderIn(List<Order> orders);
    void deleteByOrderIn(List<Order> orders);

    List<OrderItem> findByOrder(Order order);
}