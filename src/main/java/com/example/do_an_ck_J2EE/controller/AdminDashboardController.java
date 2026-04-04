package com.example.do_an_ck_J2EE.controller;

import com.example.do_an_ck_J2EE.entity.Order;
import com.example.do_an_ck_J2EE.repository.OrderRepository;
import com.example.do_an_ck_J2EE.repository.PlantRepository;
import com.example.do_an_ck_J2EE.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

//import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AdminDashboardController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PlantRepository plantRepository;

    @GetMapping("/admin")
    public String dashboard(@RequestParam(defaultValue = "0") int page, Model model) {
        long totalOrders = orderRepository.count();
        long totalUsers = userRepository.count();
        long totalPlants = plantRepository.count();

        double revenue = orderRepository.findAll().stream()
                .filter(o -> o.getTotalAmount() != null)
                .mapToDouble(Order::getTotalAmount)
                .sum();

        List<Object[]> revenueData = orderRepository.getRevenueByDate();
        List<String> chartLabels = new ArrayList<>();
        List<Double> chartValues = new ArrayList<>();

        for (Object[] row : revenueData) {
            Object dateObj = row[0];
            Object totalObj = row[1];

            chartLabels.add(dateObj != null ? dateObj.toString() : "");
            chartValues.add(totalObj != null ? ((Number) totalObj).doubleValue() : 0.0);
        }

        int pageSize = 10;
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Order> ordersPage = orderRepository.findAllByOrderByIdDesc(pageable);

        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalPlants", totalPlants);
        model.addAttribute("revenue", revenue);

        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartValues", chartValues);

        model.addAttribute("orders", ordersPage.getContent());
        model.addAttribute("ordersPage", ordersPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", ordersPage.getTotalPages());

        return "admin/dashboard";
    }
    
    @GetMapping("/admin/chat")
    public String chatPage() {
        return "admin/chat";
    }
}