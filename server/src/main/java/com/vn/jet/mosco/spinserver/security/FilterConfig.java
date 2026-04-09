package com.vn.jet.mosco.spinserver.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers security filters for the Gacha API endpoints.
 * Filter order: RateLimitFilter (1) → JwtAuthFilter (2)
 * Applied only to /api/gacha/* paths.
 */
@Configuration
public class FilterConfig {

    private final JwtUtil jwtUtil;
    private final com.vn.jet.mosco.spinserver.repository.UserRepository userRepository;

    public FilterConfig(JwtUtil jwtUtil, com.vn.jet.mosco.spinserver.repository.UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter());
        registration.addUrlPatterns("/api/gacha/*");
        registration.setOrder(1); // Execute first
        registration.setName("rateLimitFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration() {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthFilter(jwtUtil, userRepository));
        // Đóng sập cửa các API lấy thông tin người dùng và rương đồ nếu Token sai/hết hạn
        // Rank API không cần auth vì là bảng xếp hạng công khai
        registration.addUrlPatterns("/api/gacha/*", "/api/user/*", "/api/inventory/*", "/api/daily/*", "/api/friends/*");
        registration.setOrder(2); // Execute after rate limiter
        registration.setName("jwtAuthFilter");
        return registration;
    }
}
