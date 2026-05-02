package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.dto.AuthResponse;
import com.vn.jet.mosco.spinserver.dto.DisplayNameRequest;
import com.vn.jet.mosco.spinserver.dto.UpdateProfileRequest;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Controller quản lý thông tin User.
 * Bao gồm: Xem profile, đặt Display Name, cập nhật profile.
 * 
 * "Galactic Name Shield" — Bộ rule chống gian lận cho ingameName:
 * 1. Độ dài: 2-16 ký tự
 * 2. Cấm tên hệ thống (admin, gm, system, moderator, mosco, [dev], [admin])
 * 3. Trim + gộp khoảng trắng liên tiếp
 * 4. Cấm ký tự điều khiển (U+0000–U+001F, U+007F)
 * 5. Unique constraint (DB level)
 * 6. Server-side validate — KHÔNG tin Client
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserRepository userRepository;
    private final com.vn.jet.mosco.spinserver.service.AuthService authService;

    // Danh sách tên hệ thống bị cấm — chống giả mạo quyền hạn
    private static final Set<String> RESERVED_NAMES = Set.of(
            "admin", "gm", "system", "moderator", "mosco",
            "[dev]", "[admin]", "[gm]", "[mod]", "[system]",
            "developer", "support", "official", "staff"
    );

    // Pattern phát hiện ký tự điều khiển Unicode — chống invisible characters
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u001F\\u007F]");

    // Pattern cho username: chỉ chữ/số/underscore, 3-20 ký tự
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    public UserController(UserRepository userRepository, com.vn.jet.mosco.spinserver.service.AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    /**
     * GET /api/user/{userId} — Xem thông tin user.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserInfo(@PathVariable Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            authService.updateStreak(user);
            userRepository.save(user);
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * POST /api/user/set-display-name — Đặt tên hiển thị lần đầu (hoặc đổi tên).
     * Validate theo "Galactic Name Shield" trước khi lưu.
     */
    @PostMapping("/set-display-name")
    public ResponseEntity<ApiResponse<User>> setDisplayName(
            HttpServletRequest request,
            @RequestBody DisplayNameRequest body) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        // Validate tên theo Galactic Name Shield
        String validationError = validateIngameName(body.getIngameName(), userId);
        if (validationError != null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, validationError));
        }

        String sanitized = sanitizeName(body.getIngameName());

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(404, "User not found"));
        }

        user.setIngameName(sanitized);
        userRepository.save(user);

        logger.info("Display Name set: userId={}, ingameName=\"{}\"", userId, sanitized);
        return ResponseEntity.ok(ApiResponse.success("Display name set successfully!", user));
    }

    /**
     * PUT /api/user/update-profile — Cập nhật username + ingameName.
     * Email KHÔNG cho sửa (Server Truth — chống cheat).
     */
    @PutMapping("/update-profile")
    public ResponseEntity<ApiResponse<User>> updateProfile(
            HttpServletRequest request,
            @RequestBody UpdateProfileRequest body) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(404, "User not found"));
        }

        // Validate + Update username nếu có thay đổi
        if (body.getUsername() != null && !body.getUsername().trim().isEmpty()) {
            String newUsername = body.getUsername().trim();
            if (!newUsername.equals(user.getUsername())) {
                if (!USERNAME_PATTERN.matcher(newUsername).matches()) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(400, "Username chỉ cho phép chữ, số và dấu gạch dưới (3-20 ký tự)"));
                }
                if (userRepository.existsByUsername(newUsername)) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(400, "Username đã được sử dụng"));
                }
                user.setUsername(newUsername);
            }
        }

        // Validate + Update ingameName nếu có thay đổi
        if (body.getIngameName() != null) {
            String sanitized = sanitizeName(body.getIngameName());
            if (!sanitized.equals(user.getIngameName())) {
                String validationError = validateIngameName(body.getIngameName(), userId);
                if (validationError != null) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(400, validationError));
                }
                user.setIngameName(sanitized);
            }
        }

        // --- 🎭 AVATAR UPDATE LOGIC ---
        // Cập nhật avatarId từ kho Objet nếu có gửi lên
        if (body.getAvatarId() != null && !body.getAvatarId().isEmpty()) {
            user.setAvatarId(body.getAvatarId());
        }

        userRepository.save(user);
        logger.info("Profile updated: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully!", user));
    }

    /**
     * POST /api/user/streak/restore — Khôi phục chuỗi đăng nhập.
     */
    @PostMapping("/streak/restore")
    public ResponseEntity<ApiResponse<User>> restoreStreak(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "Authentication required"));
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(404, "User not found"));
        }

        AuthResponse response = authService.restoreStreak(user);
        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response.getMessage(), user));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, response.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GALACTIC NAME SHIELD — Bộ validate chống gian lận
    // ════════════════════════════════════════════════════════════════

    /**
     * Validate tên hiển thị theo 6 quy tắc chống cheat.
     * @return null nếu hợp lệ, message lỗi nếu vi phạm
     */
    private String validateIngameName(String name, Long currentUserId) {
        if (name == null || name.trim().isEmpty()) {
            return "Display name không được để trống";
        }

        String sanitized = sanitizeName(name);

        // Rule 1: Độ dài 2-16 ký tự
        if (sanitized.length() < 2 || sanitized.length() > 16) {
            return "Display name phải từ 2 đến 16 ký tự";
        }

        // Rule 2: Cấm tên hệ thống
        String lowerName = sanitized.toLowerCase();
        for (String reserved : RESERVED_NAMES) {
            if (lowerName.contains(reserved)) {
                return "Tên này không được phép sử dụng";
            }
        }

        // Rule 4: Cấm ký tự điều khiển
        if (CONTROL_CHARS.matcher(sanitized).find()) {
            return "Tên chứa ký tự không hợp lệ";
        }

        // Rule 5: Unique — kiểm tra trùng (trừ chính user hiện tại)
        Optional<User> existingUser = userRepository.findAll().stream()
                .filter(u -> sanitized.equalsIgnoreCase(u.getIngameName()) && !u.getId().equals(currentUserId))
                .findFirst();
        if (existingUser.isPresent()) {
            return "Tên này đã được sử dụng bởi người chơi khác";
        }

        return null; // Hợp lệ
    }

    /**
     * Rule 3: Chuẩn hóa tên — trim + gộp khoảng trắng liên tiếp thành 1.
     */
    private String sanitizeName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", " ");
    }
}
