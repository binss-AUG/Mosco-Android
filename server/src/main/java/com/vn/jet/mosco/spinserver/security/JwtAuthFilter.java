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

            com.vn.jet.mosco.spinserver.model.User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getActiveToken() == null || !user.getActiveToken().equals(token)) {
                logger.warn("Token mismatch for User ID {}. Account logged in on another device.", userId);
                sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Tài khoản của bạn đã đăng nhập ở nơi khác. Vui lòng đăng nhập lại.");
                return;
            }

            httpRequest.setAttribute("userId", userId);
            httpRequest.setAttribute("username", username);
            com.vn.jet.mosco.spinserver.utils.UserSessionTracker.updateActivity(userId);
            logger.debug("JWT authenticated: userId={}, username={}", userId, username);
        } catch (org.springframework.dao.DataAccessException | jakarta.persistence.PersistenceException e) {
            logger.error("Database connection error during JWT verification", e);
            sendError(httpResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Kết nối cơ sở dữ liệu tạm thời gián đoạn. Vui lòng thử lại sau.");
            return;
        } catch (Exception e) {
            logger.error("Failed to extract claims from JWT", e);
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Malformed JWT token");
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
