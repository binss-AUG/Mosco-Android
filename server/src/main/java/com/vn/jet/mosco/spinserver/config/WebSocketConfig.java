package com.vn.jet.mosco.spinserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketRateLimitInterceptor rateLimitInterceptor;

    public WebSocketConfig(WebSocketRateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

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

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Đăng ký interceptor lọc tin nhắn inbound từ client gửi lên để thực hiện giới hạn tần suất (Rate Limiting)
        registration.interceptors(rateLimitInterceptor);
    }
}
