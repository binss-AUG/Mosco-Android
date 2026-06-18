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
import com.vn.jet.mosco.spinserver.repository.CoupleStreakRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.vn.jet.mosco.spinserver.utils.MessageConstants;
import com.vn.jet.mosco.spinserver.exception.ResourceNotFoundException;
import com.vn.jet.mosco.spinserver.exception.BadRequestException;

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
    private final CoupleStreakRepository coupleStreakRepository;
    private final com.vn.jet.mosco.spinserver.service.AiModeratorService aiModeratorService;

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
                          GachaHistoryRepository gachaHistoryRepository,
                          CoupleStreakRepository coupleStreakRepository,
                          com.vn.jet.mosco.spinserver.service.AiModeratorService aiModeratorService) {
        this.userRepository = userRepository;
        this.userLikeRepository = userLikeRepository;
        this.friendshipRepository = friendshipRepository;
        this.authService = authService;
        this.cardRepository = cardRepository;
        this.userCardRepository = userCardRepository;
        this.gachaHistoryRepository = gachaHistoryRepository;
        this.coupleStreakRepository = coupleStreakRepository;
        this.aiModeratorService = aiModeratorService;
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
            @RequestAttribute("userId") Long userId,
            @RequestBody DisplayNameRequest body) {

        if (aiModeratorService.isBanned(userId)) {
            long remaining = aiModeratorService.getBanRemainingSeconds(userId);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(403, "Tài khoản đang bị khóa thao tác! Còn lại: " + remaining + " giây."));
        }

        // Validate tên theo Galactic Name Shield
        String validationError = validateIngameName(body.getIngameName(), userId);
        if (validationError != null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, validationError));
        }

        // AI Moderation Layer
        if (aiModeratorService.containsBadWords(body.getIngameName()) || aiModeratorService.checkContextWithAi(body.getIngameName())) {
            aiModeratorService.applyPenalty(userId);
            long remaining = aiModeratorService.getBanRemainingSeconds(userId);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Tên hiển thị vi phạm chuẩn mực! Bạn bị khóa thao tác " + remaining + " giây."));
        }

        String sanitized = sanitizeName(body.getIngameName());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageConstants.USER_NOT_FOUND));

        user.setIngameName(sanitized);
        userRepository.save(user);

        logger.info("Display Name set: userId={}, ingameName=\"{}\"", userId, sanitized);
        populateUserStats(user);
        return ResponseEntity.ok(ApiResponse.success(MessageConstants.DISPLAY_NAME_SET_SUCCESS, user));
    }

    /**
     * PUT /api/user/update-profile — Cập nhật username + ingameName.
     * Email KHÔNG cho sửa (Server Truth — chống cheat).
     */
    @PutMapping("/update-profile")
    public ResponseEntity<ApiResponse<User>> updateProfile(
            @RequestAttribute("userId") Long userId,
            @RequestBody UpdateProfileRequest body) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageConstants.USER_NOT_FOUND));

        // Validate + Update username nếu có thay đổi
        if (body.getUsername() != null && !body.getUsername().trim().isEmpty()) {
            String newUsername = body.getUsername().trim();
            if (!newUsername.equals(user.getUsername())) {
                if (!USERNAME_PATTERN.matcher(newUsername).matches()) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(400, MessageConstants.USERNAME_FORMAT_ERROR));
                }
                if (userRepository.existsByUsername(newUsername)) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(400, MessageConstants.USERNAME_IN_USE));
                }
                user.setUsername(newUsername);
            }
        }

        // Validate + Update ingameName nếu có thay đổi
        if (body.getIngameName() != null) {
            String sanitized = sanitizeName(body.getIngameName());
            if (!sanitized.equals(user.getIngameName())) {
                if (aiModeratorService.isBanned(userId)) {
                    long remaining = aiModeratorService.getBanRemainingSeconds(userId);
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(403, "Tài khoản đang bị khóa thao tác! Còn lại: " + remaining + " giây."));
                }
                
                String validationError = validateIngameName(body.getIngameName(), userId);
                if (validationError != null) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(400, validationError));
                }
                
                // AI Moderation Layer
                if (aiModeratorService.containsBadWords(body.getIngameName()) || aiModeratorService.checkContextWithAi(body.getIngameName())) {
                    aiModeratorService.applyPenalty(userId);
                    long remaining = aiModeratorService.getBanRemainingSeconds(userId);
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(400, "Tên hiển thị vi phạm chuẩn mực! Bạn bị khóa thao tác " + remaining + " giây."));
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
        return ResponseEntity.ok(ApiResponse.success(MessageConstants.PROFILE_UPDATED_SUCCESS, user));
    }

    /**
     * POST /api/user/streak/restore — Khôi phục chuỗi đăng nhập.
     */
    @PostMapping("/streak/restore")
    public ResponseEntity<ApiResponse<User>> restoreStreak(@RequestAttribute("userId") Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageConstants.USER_NOT_FOUND));

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
     * Tại sao (WHY): Tránh lưu trữ thừa thãi trong Database, tính toán theo thời gian thực đảm bảo tính chính xác
     * và giảm tải ghi dữ liệu khi hệ thống phục vụ lượng lớn người chơi hoạt động song song.
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
        // Tại sao (WHY): So khớp chỉ số thời gian thực và tự động tạo danh sách huy hiệu theo cấu trúc Tier Type
        java.util.List<String> badgesList = new java.util.ArrayList<>();

        // 1. Spin Master (Bậc thầy quay thẻ)
        long spins = gachaHistoryRepository.countByUserIdAndSource(userId, "GACHA_ROLL");
        user.setSpinsCount((int) spins);
        if (spins >= 6700) {
            badgesList.add("EX Spin Master");
        } else if (spins >= 1000) {
            badgesList.add("Diamond Spin Master");
        } else if (spins >= 500) {
            badgesList.add("Gold Spin Master");
        } else if (spins >= 100) {
            badgesList.add("Silver Spin Master");
        } else if (spins >= 36) {
            badgesList.add("Bronze Spin Master");
        } else if (spins >= 1) {
            badgesList.add("Iron Spin Master");
        }

        // 2. Pack Master (Bậc thầy mở gói)
        long packs = gachaHistoryRepository.countByUserIdAndSource(userId, "PACK_OPEN");
        user.setPacksCount((int) packs);
        if (packs >= 6700) {
            badgesList.add("EX Pack Master");
        } else if (packs >= 1000) {
            badgesList.add("Diamond Pack Master");
        } else if (packs >= 500) {
            badgesList.add("Gold Pack Master");
        } else if (packs >= 100) {
            badgesList.add("Silver Pack Master");
        } else if (packs >= 36) {
            badgesList.add("Bronze Pack Master");
        } else if (packs >= 1) {
            badgesList.add("Iron Pack Master");
        }

        // 3. Collection Master (Bậc thầy sưu tập)
        int collProgress = user.getCollectionProgress();
        if (collProgress >= 95) {
            badgesList.add("EX Collection Master");
        } else if (collProgress >= 80) {
            badgesList.add("Diamond Collection Master");
        } else if (collProgress >= 60) {
            badgesList.add("Gold Collection Master");
        } else if (collProgress >= 35) {
            badgesList.add("Silver Collection Master");
        } else if (collProgress >= 15) {
            badgesList.add("Bronze Collection Master");
        } else if (collProgress >= 5) {
            badgesList.add("Iron Collection Master");
        }

        // 4. Immortal (Login Streak)
        int loginStreak = user.getStreak();
        if (loginStreak >= 365) {
            badgesList.add("EX Immortal");
        } else if (loginStreak >= 200) {
            badgesList.add("Diamond Immortal");
        } else if (loginStreak >= 100) {
            badgesList.add("Gold Immortal");
        } else if (loginStreak >= 30) {
            badgesList.add("Silver Immortal");
        } else if (loginStreak >= 10) {
            badgesList.add("Bronze Immortal");
        } else if (loginStreak >= 3) {
            badgesList.add("Iron Immortal");
        }

        // 5. Duo Flame (Couple Streak)
        Integer coupleStreakVal = coupleStreakRepository.findMaxStreakCountByUserId(userId);
        int coupleStreak = (coupleStreakVal != null) ? coupleStreakVal : 0;
        user.setCoupleStreakCount(coupleStreak);
        if (coupleStreak >= 365) {
            badgesList.add("EX Duo Flame");
        } else if (coupleStreak >= 200) {
            badgesList.add("Diamond Duo Flame");
        } else if (coupleStreak >= 100) {
            badgesList.add("Gold Duo Flame");
        } else if (coupleStreak >= 30) {
            badgesList.add("Silver Duo Flame");
        } else if (coupleStreak >= 10) {
            badgesList.add("Bronze Duo Flame");
        } else if (coupleStreak >= 3) {
            badgesList.add("Iron Duo Flame");
        }

        // 6. Celebrity (Likes Count)
        int likes = user.getLikesCount();
        if (likes >= 600) {
            badgesList.add("EX Celebrity");
        } else if (likes >= 300) {
            badgesList.add("Diamond Celebrity");
        } else if (likes >= 150) {
            badgesList.add("Gold Celebrity");
        } else if (likes >= 50) {
            badgesList.add("Silver Celebrity");
        } else if (likes >= 15) {
            badgesList.add("Bronze Celebrity");
        } else if (likes >= 5) {
            badgesList.add("Iron Celebrity");
        }

        // 7. Golden Hammer (Card Upgrade Level)
        int maxNormalUpgrade = getMaxUpgradeForClasses(userId, java.util.List.of("First", "Welcome", "Zero"));
        int maxDoubleUpgrade = getMaxUpgradeForClasses(userId, java.util.List.of("Double"));
        int maxSpecialUpgrade = getMaxUpgradeForClasses(userId, java.util.List.of("Special", "Motion"));
        int maxPremierUpgrade = getMaxUpgradeForClasses(userId, java.util.List.of("Unit", "Premier"));

        if (maxPremierUpgrade >= 8) {
            badgesList.add("EX Golden Hammer");
        } else if (maxPremierUpgrade >= 5 || maxSpecialUpgrade >= 8) {
            badgesList.add("Diamond Golden Hammer");
        } else if (maxSpecialUpgrade >= 5 || maxDoubleUpgrade >= 8) {
            badgesList.add("Gold Golden Hammer");
        } else if (maxDoubleUpgrade >= 5) {
            badgesList.add("Silver Golden Hammer");
        } else if (maxNormalUpgrade >= 8) {
            badgesList.add("Bronze Golden Hammer");
        } else if (maxNormalUpgrade >= 5) {
            badgesList.add("Iron Golden Hammer");
        }

        // Gán giá trị nâng cấp cao nhất để Client hiển thị progress bar thật cho Golden Hammer
        int overallMax = Math.max(Math.max(maxNormalUpgrade, maxDoubleUpgrade), Math.max(maxSpecialUpgrade, maxPremierUpgrade));
        user.setMaxUpgradeLevel(overallMax);

        user.setBadges(badgesList);
    }

    /**
     * Lấy giá trị nâng cấp (upgradeLevel) lớn nhất của user đối với danh sách class cụ thể.
     * Tại sao (WHY): Tránh viết nhiều query lặp, tối ưu hóa tái sử dụng code theo chuẩn DRY.
     */
    private int getMaxUpgradeForClasses(Long userId, java.util.List<String> classNames) {
        int max = 0;
        for (String className : classNames) {
            Integer val = userCardRepository.findMaxUpgradeLevelByUserIdAndClassName(userId, className);
            if (val != null && val > max) {
                max = val;
            }
        }
        return max;
    }

    /**
     * POST /api/user/{targetUserId}/like — Thích hoặc bỏ thích hồ sơ người chơi khác.
     * Tại sao: Đảm bảo tính nguyên tử (Atomicity) khi tăng/giảm like, ngăn chặn race condition 
     * và kiểm tra toàn vẹn dữ liệu tránh một người bấm thích nhiều lần.
     */
    @PostMapping("/{targetUserId}/like")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> likeProfile(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long targetUserId) {

        // Không cho phép tự thích hồ sơ của chính mình
        if (currentUserId.equals(targetUserId)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, MessageConstants.CANNOT_LIKE_SELF));
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageConstants.USER_PROFILE_NOT_FOUND));

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
                liked ? MessageConstants.LIKE_SUCCESS : MessageConstants.UNLIKE_SUCCESS,
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
            return MessageConstants.DISPLAY_NAME_EMPTY;
        }

        String sanitized = sanitizeName(name);

        // Rule 1: Độ dài 2-16 ký tự
        if (sanitized.length() < 2 || sanitized.length() > 16) {
            return MessageConstants.DISPLAY_NAME_LENGTH_ERROR;
        }

        // Rule 2: Cấm tên hệ thống
        String lowerName = sanitized.toLowerCase();
        for (String reserved : RESERVED_NAMES) {
            if (lowerName.contains(reserved)) {
                return MessageConstants.DISPLAY_NAME_RESERVED;
            }
        }

        // Rule 4: Cấm ký tự điều khiển
        if (CONTROL_CHARS.matcher(sanitized).find()) {
            return MessageConstants.DISPLAY_NAME_INVALID_CHARS;
        }

        // Rule 5: Unique — kiểm tra trùng (trừ chính user hiện tại)
        Optional<User> existingUser = userRepository.findAll().stream()
                .filter(u -> sanitized.equalsIgnoreCase(u.getIngameName()) && !u.getId().equals(currentUserId))
                .findFirst();
        if (existingUser.isPresent()) {
            return MessageConstants.DISPLAY_NAME_IN_USE;
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

    /**
     * POST /api/user/delete-account — Yêu cầu xóa tài khoản (Soft Delete 14 ngày).
     * Yêu cầu xác thực OTP gửi về email trước đó.
     * Tại sao: Bảo vệ tài khoản người chơi khỏi việc bị xóa trộm.
     */
    @PostMapping("/delete-account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @RequestAttribute("userId") Long userId,
            @RequestParam String code) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageConstants.USER_NOT_FOUND));

        // Xác thực mã OTP thông qua AuthService
        boolean otpValid = authService.verifyCode(user.getEmail(), code);
        if (!otpValid) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, MessageConstants.OTP_INVALID_OR_EXPIRED_SHORT));
        }

        user.setDeletionRequestedAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        logger.info("User requested account deletion: userId={}, email={}", userId, user.getEmail());
        return ResponseEntity.ok(ApiResponse.success(MessageConstants.ACCOUNT_DELETION_REQUESTED, null));
    }
}
