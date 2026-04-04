package com.example.do_an_ck_J2EE.repository;

import com.example.do_an_ck_J2EE.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCodeIgnoreCase(String code);
}