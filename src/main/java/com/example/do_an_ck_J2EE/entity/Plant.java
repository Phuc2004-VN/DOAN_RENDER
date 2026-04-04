package com.example.do_an_ck_J2EE.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "plants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String category;

    private Double price;

    @Column(length = 2000)
    private String description;

    private String imageUrl;

    private Integer stock;
}