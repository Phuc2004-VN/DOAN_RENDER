package com.example.do_an_ck_J2EE.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private Double subtotal;
    private Double shippingFee;
    private Double discountAmount;
    private String voucherCode;
    private Double totalAmount;

    // PENDING, PROCESSING, DONE, CANCELLED, PENDING_PAYMENT
    private String status;

    // COD, BANK_QR
    private String paymentMethod;

    // UNPAID, WAITING_TRANSFER, PAID
    private String paymentStatus;

    // ví dụ: DH15
    private String paymentReference;

    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    // Thông tin giao hàng
    private String receiverName;
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(columnDefinition = "TEXT")
    private String note;
}