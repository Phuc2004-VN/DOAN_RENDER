package com.example.do_an_ck_J2EE.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    // PERCENT hoặc FIXED
    @Column(nullable = false, length = 20)
    private String discountType;

    // nếu PERCENT thì ví dụ 10 = giảm 10%
    // nếu FIXED thì ví dụ 30000 = giảm 30k
    @Column(nullable = false)
    private Double discountValue;

    // đơn tối thiểu để được áp mã
    private Double minOrderValue;

    // giới hạn giảm tối đa, hữu ích cho mã %
    private Double maxDiscount;

    // số lượng mã còn lại
    private Integer quantity;

    // đang bật / tắt
    private Boolean active;

    private LocalDateTime expiryDate;
}