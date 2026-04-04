package com.example.do_an_ck_J2EE.repository;

import com.example.do_an_ck_J2EE.entity.Plant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantRepository extends JpaRepository<Plant, Long> {

    List<Plant> findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String nameKeyword,
            String categoryKeyword,
            String descriptionKeyword
    );
}