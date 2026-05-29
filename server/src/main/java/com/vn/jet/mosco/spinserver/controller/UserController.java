package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.dto.AuthResponse;
import com.vn.jet.mosco.spinserver.dto.DisplayNameRequest;
import com.vn.jet.mosco.spinserver.dto.UpdateProfileRequest;
import com.vn.jet.mosco.spinserver.model.Friendship;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserLike;
import com.vn.jet.mosco.spinserver.repository.FriendshipRepository;
import com.vn.jet.mosco.spinserver.repository.UserLikeRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import com.vn.jet.mosco.spinserver.repository.CardRepository;
import com.vn.jet.mosco.spinserver.repository.UserCardRepository;
import com.vn.jet.mosco.spinserver.repository.GachaHistoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
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
    private final UserLikeRepository userLikeRepository;
    private final FriendshipRepository friendshipRepository;
    private final com.vn.jet.mosco.spinserver.service.AuthService authService;
    private final CardRepository cardRepository;
    private final UserCardRepository userCardRepository;
    private final GachaHistoryRepository gachaHistoryRepository;

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

    public UserController(UserRepository userRepository, 
                          UserLikeRepository userLikeRepository,
                          FriendshipRepository friendshipRepository,
                          com.vn.jet.mosco.spinserver.service.AuthService authService,
                          CardRepository cardRepository,
                          UserCardRepository userCardRepository,
                          GachaHistoryRepository gachaHistoryRepository) {
        this.userRepository = userRepository;
        this.userLikeRepository = userLikeRepository;
        this.friendshipRepository = friendshipRepository;
        this.authService = authService;
        this.cardRepository = cardRepository;
        this.userCardRepository = userCardRepository;
        this.gachaHistoryRepository = gachaHistoryRepository;
    }

    /**
     * GET /api/user/{userId} — Xem thông tin user.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserInfo(HttpServletRequest request, @PathVariable Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            authService.updateStreak(user);
            userRepository.save(user);

            user.setOnline(com.vn.jet.mosco.spinserver.utils.UserSessionTracker.isOnline(userId));

            // Bổ sung tính toán trạng thái mạng xã hội động nếu request được xác thực
            // Tại sao: Client dựa vào các trường này để cập nhật nút Like và Add Friend chính xác
            Long currentUserId = (Long) request.getAttribute("userId");
            if (currentUserId != null) {
                user.setLiked(userLikeRepository.existsByLikerIdAndTargetUserId(currentUserId, userId));

                Optional<Friendship> friendshipOpt = friendshipRepository.findExistingFriendship(currentUserId, userId);
                if (friendshipOpt.isPresent()) {
                    Friendship f = friendshipOpt.get();
                    if (f.getStatus() == 1) {
                        user.setFriendshipStatus(2); // Đã là bạn bè
                    } else {
                        // Trạng thái chờ xác nhận
                        if (currentUserId.equals(f.getAddresseeId())) {
                            // Tại sao: Nếu người dùng hiện tại là người nhận lời mời, trả về trạng thái 3 để Client hiển thị nút Accept / Decline
                            user.setFriendshipStatus(3);
                        } else {
                            user.setFriendshipStatus(1); // Đã gửi lời mời -> Hiển thị Pending
                        }
                    }
                } else {
                    user.setFriendshipStatus(0);
                }
            }

            populateUserStats(user);
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
        populateUserStats(user);
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

        // --- 🖋️ BIO UPDATE LOGIC ---
        if (body.getBio() != null) {
            user.setBio(body.getBio());
        }

        // --- 🖼️ SHOWCASE UPDATE LOGIC ---
        if (body.getShowcaseCardIds() != null) {
            user.getShowcaseCardIds().clear();
            user.getShowcaseCardIds().addAll(body.getShowcaseCardIds());
        }

        // --- 📊 STATS UPDATE LOGIC ---
        if (body.getLikesCount() != null) {
            user.setLikesCount(body.getLikesCount());
        }
        if (body.getFriendsCount() != null) {
            user.setFriendsCount(body.getFriendsCount());
        }
        if (body.getAvatarCropParams() != null) {
            user.setAvatarCropParams(body.getAvatarCropParams());
        }

        userRepository.save(user);
        populateUserStats(user);
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
            populateUserStats(user);
            return ResponseEntity.ok(ApiResponse.success(response.getMessage(), user));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, response.getMessage()));
        }
    }

    /**
     * Tính toán động và điền các chỉ số Gacha Stats cùng Honor Badges cho User.
     * Tại sao (WHY): Tránh lưu trữ thừa thãi trong Database, tính toán theo thời gian thực đảm bảo tính chính xác.
     */
    private void populateUserStats(User user) {
        if (user == null) return;
        Long userId = user.getId();
        
        long totalRolls = gachaHistoryRepository.countByUserId(userId);
        user.setTotalRolls((int) totalRolls);

        long totalCards = cardRepository.count();
        if (totalCards > 0) {
            long uniqueUnlocked = userCardRepository.countUniqueUnlockedCardsByUserId(userId);
            int progress = (int) ((uniqueUnlocked * 100) / totalCards);
            user.setCollectionProgress(Math.min(100, progress));
        } else {
            user.setCollectionProgress(0);
        }

        // Gán danh hiệu danh dự dựa trên các cột mốc thành tích của người chơi
        java.util.List<String> badgesList = new java.util.ArrayList<>();
        if (totalRolls >= 1) {
            badgesList.add("Rookie Roller");
        }
        if (totalRolls >= 100) {
            badgesList.add("Elite Collector");
        }
        if (totalRolls >= 1000) {
            badgesList.add("Gacha Legend");
        }
        if (user.getCollectionProgress() >= 80) {
            badgesList.add("Mosco Master");
        }
        if (user.getStreak() >= 7) {
            badgesList.add("Loyal Explorer");
        }
        user.setBadges(badgesList);
    }

    /**
     * POST /api/user/{targetUserId}/like — Thích hoặc bỏ thích hồ sơ người chơi khác.
     * Tại sao: Đảm bảo tính nguyên tử (Atomicity) khi tăng/giảm like, ngăn chặn race condition 
     * và kiểm tra toàn vẹn dữ liệu tránh một người bấm thích nhiều lần.
     */
    @PostMapping("/{targetUserId}/like")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> likeProfile(
            HttpServletRequest request,
            @PathVariable Long targetUserId) {

        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        // Không cho phép tự thích hồ sơ của chính mình
        if (currentUserId.equals(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Không thể tự thích hồ sơ của chính mình"));
        }

        User targetUser = userRepository.findById(targetUserId).orElse(null);
        if (targetUser == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(404, "Không tìm thấy hồ sơ người chơi"));
        }

        Optional<UserLike> existingLike = userLikeRepository.findByLikerIdAndTargetUserId(currentUserId, targetUserId);
        boolean liked;
        if (existingLike.isPresent()) {
            // Đã thích -> Bỏ thích
            userLikeRepository.delete(existingLike.get());
            targetUser.setLikesCount(Math.max(0, targetUser.getLikesCount() - 1));
            liked = false;
            logger.info("User {} unliked user {}", currentUserId, targetUserId);
        } else {
            // Chưa thích -> Thích
            UserLike newLike = new UserLike(currentUserId, targetUserId);
            userLikeRepository.save(newLike);
            targetUser.setLikesCount(targetUser.getLikesCount() + 1);
            liked = true;
            logger.info("User {} liked user {}", currentUserId, targetUserId);
        }

        userRepository.save(targetUser);

        Map<String, Object> responseData = Map.of(
                "liked", liked,
                "likesCount", targetUser.getLikesCount()
        );

        return ResponseEntity.ok(ApiResponse.success(
                liked ? "Đã thích hồ sơ thành công" : "Đã bỏ thích hồ sơ",
                responseData
        ));
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
