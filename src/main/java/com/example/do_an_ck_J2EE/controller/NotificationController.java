package com.example.do_an_ck_J2EE.controller;

import com.example.do_an_ck_J2EE.repository.ChatMessageRepository;
import com.example.do_an_ck_J2EE.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final ChatMessageRepository chatRepo;
    private final OrderRepository orderRepo;

    @GetMapping
    public Map<String, Object> getNotifications(Principal principal) {
        String username = principal.getName();

        long unreadMessages = chatRepo.countByReceiverAndReadFalse(username);
        long newOrders = 0;

        if ("ad".equalsIgnoreCase(username)) {
            newOrders = orderRepo.countNewOrders();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messages", unreadMessages);
        result.put("orders", newOrders);
        result.put("total", unreadMessages + newOrders);

        return result;
    }
}