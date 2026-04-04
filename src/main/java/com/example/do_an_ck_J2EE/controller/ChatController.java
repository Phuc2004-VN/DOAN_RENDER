package com.example.do_an_ck_J2EE.controller;

import com.example.do_an_ck_J2EE.entity.ChatMessage;
import com.example.do_an_ck_J2EE.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private static final String ADMIN = "ad";

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/chat/send")
    @ResponseBody
    public ChatMessage sendHttp(@RequestBody ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Message null");
        }

        String sender = message.getSender();
        String receiver = message.getReceiver();
        String content = message.getContent();

        if (sender == null || sender.isBlank()) {
            throw new IllegalArgumentException("Sender trống");
        }
        if (receiver == null || receiver.isBlank()) {
            throw new IllegalArgumentException("Receiver trống");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content trống");
        }

        ChatMessage saved = chatService.save(
                sender.trim(),
                receiver.trim(),
                content.trim()
        );

        messagingTemplate.convertAndSend("/topic/admin", saved);
        messagingTemplate.convertAndSend("/topic/user/" + receiver.trim(), saved);
        messagingTemplate.convertAndSend("/topic/user/" + sender.trim(), saved);

        return saved;
    }

    @GetMapping("/admin/chat/users")
    @ResponseBody
    public List<String> getUsers() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String adminUsername = auth.getName(); // Lấy username của người đang đăng nhập
        return chatService.getAllChatUsers(adminUsername);
    }

    @GetMapping("/admin/current-user")
    @ResponseBody
    public String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    @GetMapping("/admin/chat/messages")
    @ResponseBody
    public List<ChatMessage> getMessages(@RequestParam String username) {
        return chatService.getConversation(ADMIN, username);
    }

    @GetMapping("/admin/chat/unread-count")
    @ResponseBody
    public long getUnreadAdmin() {
        return chatService.countUnread(ADMIN);
    }

    @GetMapping("/admin/chat/unread-detail")
    @ResponseBody
    public Map<String, Object> getUnreadAdminDetail() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Long> unreadBySender = chatService.getUnreadCountBySender(ADMIN);

        result.put("totalUnread", chatService.countUnread(ADMIN));
        result.put("senders", unreadBySender);

        return result;
    }

    @PostMapping("/admin/chat/read")
    @ResponseBody
    public void markRead(@RequestParam String username) {
        chatService.markConversationAsRead(username, ADMIN);
    }

    // ================= USER CHAT =================

    @GetMapping("/chat/history")
    @ResponseBody
    public List<ChatMessage> getUserHistory(@RequestParam String username) {
        return chatService.getConversation(username, ADMIN);
    }

    @GetMapping("/chat/unread-count")
    @ResponseBody
    public long getUnreadUser(@RequestParam String username) {
        return chatService.countUnread(username);
    }

    @PostMapping("/chat/read")
    @ResponseBody
    public void markUserRead(@RequestParam String username) {
        chatService.markConversationAsRead(ADMIN, username);
    }
}
