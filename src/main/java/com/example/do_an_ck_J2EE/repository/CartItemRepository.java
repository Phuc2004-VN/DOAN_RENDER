package com.example.do_an_ck_J2EE.repository;

import com.example.do_an_ck_J2EE.entity.CartItem;
import com.example.do_an_ck_J2EE.entity.Plant;
import com.example.do_an_ck_J2EE.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUser(User user);
    Optional<CartItem> findByUserAndPlant(User user, Plant plant);
    void deleteByUser(User user);
}