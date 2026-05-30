package com.vn.jet.mosco.spinserver.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Servlet Filter that validates JWT tokens from the Authorization header.
 * On success, sets "userId" as a request attribute for downstream controllers.
 * On failure, returns 401 Unauthorized with a JSON error body.
 */
public class JwtAuthFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);
    private final JwtUtil jwtUtil;
    private final com.vn.jet.mosco.spinserver.repository.UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, com.vn.jet.mosco.spinserver.repository.UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Allow CORS preflight
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Missing or invalid Authorization header from IP: {}", httpRequest.getRemoteAddr());
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            logger.warn("Invalid/expired JWT from IP: {}", httpRequest.getRemoteAddr());
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired JWT token");
            return;
        }

        try {
            Long userId = jwtUtil.extractUserId(token);
            String username = jwtUtil.extractUsername(token);

            // 1. Kiểm tra Active Token trong cache trước (Local-First RAM)
            String cachedToken = com.vn.jet.mosco.spinserver.security.TokenCache.get(userId);
            if (cachedToken == null) {
                // Cache miss, truy vấn DB và đẩy vào Cache
                com.vn.jet.mosco.spinserver.model.User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    cachedToken = user.getActiveToken();
                    if (cachedToken != null) {
                        com.vn.jet.mosco.spinserver.security.TokenCache.put(userId, cachedToken);
                    }
                }
            }

            if (cachedToken == null || !cachedToken.equals(token)) {
                logger.warn("Token mismatch for User ID {}. Account logged in on another device.", userId);
                sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Tài khoản của bạn đã đăng nhập ở nơi khác. Vui lòng đăng nhập lại.");
                return;
            }

            httpRequest.setAttribute("userId", userId);
            httpRequest.setAttribute("username", username);
            com.vn.jet.mosco.spinserver.utils.UserSessionTracker.updateActivity(userId);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            // Lỗi JWT thực sự (hết hạn, sai chữ ký, hỏng định dạng) -> Trả về 401
            logger.warn("Invalid JWT token: {}", e.getMessage());
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
            return;
        } catch (Exception e) {
            // Lỗi kết nối Database / Lỗi server nội bộ -> Trả về 500
            logger.error("Internal server error or Database connection failure during JWT verification", e);
            sendError(httpResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Hệ thống đang bận, vui lòng thử lại sau.");
            return;
        }

        chain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
