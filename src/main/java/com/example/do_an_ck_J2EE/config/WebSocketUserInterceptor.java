package com.example.do_an_ck_J2EE.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.Map;

public class WebSocketUserInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

            if (sessionAttributes != null) {
                String username = (String) sessionAttributes.get("username");

                if (username != null) {
                    accessor.setUser(new StompPrincipal(username));
                }
            }
        }

        return message;
    }
}