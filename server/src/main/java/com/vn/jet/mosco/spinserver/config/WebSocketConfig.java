package com.vn.jet.mosco.spinserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Kích hoạt simple broker để broadcast tin nhắn tới các topic
        config.enableSimpleBroker("/topic");
        // Prefix cho các tin nhắn từ client gửi lên @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint để client Android kết nối
        registry.addEndpoint("/ws-mosco")
                .setAllowedOriginPatterns("*");
        
        registry.addEndpoint("/ws-mosco")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
