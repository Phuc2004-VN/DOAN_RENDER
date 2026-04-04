package com.example.do_an_ck_J2EE.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne
    @JoinColumn(name = "plant_id")
    private Plant plant;

    private Integer quantity;

    // đơn giá tại thời điểm đặt hàng
    private Double price;

    // thành tiền của dòng
    private Double subtotal;
}