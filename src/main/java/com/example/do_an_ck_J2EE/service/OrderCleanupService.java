package com.example.do_an_ck_J2EE.service;

import com.example.do_an_ck_J2EE.entity.Order;
import com.example.do_an_ck_J2EE.entity.OrderItem;
import com.example.do_an_ck_J2EE.entity.Plant;
import com.example.do_an_ck_J2EE.repository.OrderItemRepository;
import com.example.do_an_ck_J2EE.repository.OrderRepository;
import com.example.do_an_ck_J2EE.repository.PlantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCleanupService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PlantRepository plantRepository;

    private static final int EXPIRE_MINUTES = 15;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void autoCancelExpiredBankQrOrders() {
        List<Order> orders = orderRepository.findAll();

        LocalDateTime now = LocalDateTime.now();

        for (Order order : orders) {
            boolean isBankQr = "BANK_QR".equalsIgnoreCase(order.getPaymentMethod());
            boolean waitingTransfer = "WAITING_TRANSFER".equalsIgnoreCase(order.getPaymentStatus());
            boolean pendingPayment = "PENDING_PAYMENT".equalsIgnoreCase(order.getStatus());

            if (!isBankQr || !waitingTransfer || !pendingPayment || order.getCreatedAt() == null) {
                continue;
            }

            LocalDateTime expiredAt = order.getCreatedAt().plusMinutes(EXPIRE_MINUTES);

            if (now.isAfter(expiredAt)) {
                cancelAndRestoreStock(order);
            }
        }
    }

    @Transactional
    public void cancelAndRestoreStock(Order order) {
        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            return;
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrder(order);

        for (OrderItem item : orderItems) {
            Plant plant = item.getPlant();
            if (plant != null) {
                int currentStock = plant.getStock() == null ? 0 : plant.getStock();
                int restoreQty = item.getQuantity() == null ? 0 : item.getQuantity();
                plant.setStock(currentStock + restoreQty);
                plantRepository.save(plant);
            }
        }

        order.setStatus("CANCELLED");
        order.setPaymentStatus("UNPAID");
        orderRepository.save(order);
    }
}