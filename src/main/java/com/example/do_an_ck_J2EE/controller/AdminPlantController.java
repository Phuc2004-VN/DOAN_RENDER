package com.example.do_an_ck_J2EE.controller;

import com.example.do_an_ck_J2EE.entity.Plant;
import com.example.do_an_ck_J2EE.repository.PlantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/plants")
public class AdminPlantController {

    private final PlantRepository plantRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("plants", plantRepository.findAll());
        return "admin/plants";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("plant", new Plant());
        return "admin/plant-form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Plant plant, RedirectAttributes redirectAttributes) {
        plantRepository.save(plant);
        redirectAttributes.addFlashAttribute("successMessage", "Lưu cây thành công!");
        return "redirect:/admin/plants";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        Plant plant = plantRepository.findById(id).orElseThrow();
        model.addAttribute("plant", plant);
        return "admin/plant-form";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes redirectAttributes) {
        plantRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Xóa cây thành công!");
        return "redirect:/admin/plants";
    }
}