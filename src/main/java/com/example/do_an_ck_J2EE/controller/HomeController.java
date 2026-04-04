package com.example.do_an_ck_J2EE.controller;

import com.example.do_an_ck_J2EE.entity.Plant;
import com.example.do_an_ck_J2EE.repository.PlantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PlantRepository plantRepository;

    @GetMapping("/")
    public String home(@RequestParam(required = false) String keyword, Model model) {
        String normalizedKeyword = keyword != null ? keyword.trim() : "";
        List<Plant> plants;

        if (normalizedKeyword.isBlank()) {
            plants = plantRepository.findAll();
        } else {
            plants = plantRepository
                    .findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                            normalizedKeyword,
                            normalizedKeyword,
                            normalizedKeyword
                    );
        }

        model.addAttribute("plants", plants);
        model.addAttribute("keyword", normalizedKeyword);

        if (!normalizedKeyword.isBlank() && plants.isEmpty()) {
            model.addAttribute("infoMessage",
                    "Không tìm thấy sản phẩm nào phù hợp với từ khóa: " + normalizedKeyword);
        }

        return "index";
    }
}