package com.example.do_an_ck_J2EE.service;

import com.example.do_an_ck_J2EE.entity.ChatMessage;
import com.example.do_an_ck_J2EE.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatRepo;

    public ChatMessage save(String sender, String receiver, String content) {
        String cleanSender = sender == null ? "" : sender.trim();
        String cleanReceiver = receiver == null ? "" : receiver.trim();
        String cleanContent = content == null ? "" : content.trim();

        if (cleanSender.isBlank() || cleanReceiver.isBlank() || cleanContent.isBlank()) {
            throw new IllegalArgumentException("Sender, receiver, content không được để trống");
        }

        ChatMessage msg = ChatMessage.builder()
                .sender(cleanSender)
                .receiver(cleanReceiver)
                .content(cleanContent)
                .createdAt(LocalDateTime.now())
                .read(Boolean.FALSE)
                .build();

        System.out.println("Saving message: " + cleanSender + " -> " + cleanReceiver + " : " + cleanContent);

        return chatRepo.save(msg);
    }

    public List<ChatMessage> getConversation(String user1, String user2) {
        String aUser = user1 == null ? "" : user1.trim();
        String bUser = user2 == null ? "" : user2.trim();

        List<ChatMessage> a = chatRepo.findBySenderAndReceiverOrderByCreatedAtAsc(aUser, bUser);
        List<ChatMessage> b = chatRepo.findBySenderAndReceiverOrderByCreatedAtAsc(bUser, aUser);

        List<ChatMessage> all = new ArrayList<>();
        all.addAll(a);
        all.addAll(b);
        all.sort(Comparator.comparing(ChatMessage::getCreatedAt));

        return all;
    }

    public List<String> getAllChatUsers(String adminUsername) {
        String cleanAdminUsername = adminUsername == null ? "" : adminUsername.trim();
        return chatRepo.findAllChatUsers(cleanAdminUsername);
    }

    public long countUnread(String username) {
        String cleanUsername = username == null ? "" : username.trim();
        return chatRepo.countByReceiverAndReadFalse(cleanUsername);
    }

    public void markConversationAsRead(String sender, String receiver) {
        String cleanSender = sender == null ? "" : sender.trim();
        String cleanReceiver = receiver == null ? "" : receiver.trim();

        List<ChatMessage> messages = chatRepo.findBySenderAndReceiverOrderByCreatedAtAsc(cleanSender, cleanReceiver);

        boolean changed = false;
        for (ChatMessage msg : messages) {
            if (!Boolean.TRUE.equals(msg.getRead())) {
                msg.setRead(Boolean.TRUE);
                changed = true;
            }
        }

        if (changed) {
            chatRepo.saveAll(messages);
        }
    }

    public List<String> getUnreadSenders(String receiver) {
        String cleanReceiver = receiver == null ? "" : receiver.trim();
        if (cleanReceiver.isBlank()) {
            return List.of();
        }
        return chatRepo.findUnreadSendersByReceiver(cleanReceiver);
    }

    public Map<String, Long> getUnreadCountBySender(String receiver) {
        String cleanReceiver = receiver == null ? "" : receiver.trim();
        Map<String, Long> result = new LinkedHashMap<>();

        if (cleanReceiver.isBlank()) {
            return result;
        }

        List<String> senders = chatRepo.findUnreadSendersByReceiver(cleanReceiver);
        for (String sender : senders) {
            long count = chatRepo.countUnreadBySenderAndReceiver(sender, cleanReceiver);
            result.put(sender, count);
        }

        return result;
    }
}