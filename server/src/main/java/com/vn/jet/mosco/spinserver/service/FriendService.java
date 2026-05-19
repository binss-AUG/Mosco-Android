package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.Friendship;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.repository.FriendshipRepository;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import com.vn.jet.mosco.spinserver.repository.CoupleStreakRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Service xử lý logic Bạn bè.
 * Hỗ trợ: Tìm kiếm user, gửi/chấp nhận/từ chối/xóa bạn bè.
 */
@Service
public class FriendService {

    private static final Logger logger = LoggerFactory.getLogger(FriendService.class);

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final CoupleStreakRepository streakRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public FriendService(FriendshipRepository friendshipRepository, UserRepository userRepository, 
                         CoupleStreakRepository streakRepository, SimpMessagingTemplate messagingTemplate) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.streakRepository = streakRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Lấy danh sách bạn bè đã chấp nhận.
     * Trả về thông tin cơ bản của bạn bè (id, tên, level).
     */
    public List<Map<String, Object>> getFriendList(Long userId) {
        List<Friendship> accepted = friendshipRepository.findAcceptedFriendships(userId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Friendship f : accepted) {
            // Xác định friendId: nếu user là requester thì friend là addressee, ngược lại
            Long friendId = f.getRequesterId().equals(userId) ? f.getAddresseeId() : f.getRequesterId();
            User friend = userRepository.findById(friendId).orElse(null);
            if (friend == null) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("friendshipId", f.getId());
            entry.put("userId", friend.getId());
            entry.put("ingameName", friend.getIngameName() != null ? friend.getIngameName() : friend.getUsername());
            entry.put("level", friend.getLevel());
            entry.put("avatarId", friend.getAvatarId());
            entry.put("online", com.vn.jet.mosco.spinserver.utils.UserSessionTracker.isOnline(friend.getId()));
            result.add(entry);
        }
        return result;
    }

    /**
     * Lấy danh sách lời mời kết bạn đang chờ (user là người được mời).
     */
    public List<Map<String, Object>> getPendingRequests(Long userId) {
        List<Friendship> pending = friendshipRepository.findPendingRequests(userId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Friendship f : pending) {
            User requester = userRepository.findById(f.getRequesterId()).orElse(null);
            if (requester == null) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("friendshipId", f.getId());
            entry.put("userId", requester.getId());
            entry.put("ingameName", requester.getIngameName() != null ? requester.getIngameName() : requester.getUsername());
            entry.put("level", requester.getLevel());
            entry.put("avatarId", requester.getAvatarId());
            result.add(entry);
        }
        return result;
    }

    /**
     * Gửi lời mời kết bạn.
     * @return Thông báo kết quả
     */
    @Transactional
    public String sendRequest(Long requesterId, Long addresseeId) {
        // Chống tự kết bạn với chính mình
        if (requesterId.equals(addresseeId)) {
            return "Không thể kết bạn với chính mình";
        }

        // Kiểm tra user tồn tại
        if (!userRepository.existsById(addresseeId)) {
            return "Người chơi không tồn tại";
        }

        // Kiểm tra đã có quan hệ chưa
        Optional<Friendship> existing = friendshipRepository.findExistingFriendship(requesterId, addresseeId);
        if (existing.isPresent()) {
            Friendship f = existing.get();
            if (f.getStatus() == 1) return "Hai bạn đã là bạn bè rồi";
            return "Đã gửi lời mời trước đó, đang chờ phản hồi";
        }

        Friendship friendship = new Friendship(requesterId, addresseeId);
        friendshipRepository.save(friendship);
        logger.info("Friend request sent: {} -> {}", requesterId, addresseeId);
        
        notifyFriendStatusUpdate(addresseeId);
        
        return null; // null = thành công
    }

    /**
     * Chấp nhận lời mời kết bạn.
     */
    @Transactional
    public String acceptRequest(Long friendshipId, Long userId) {
        Friendship f = friendshipRepository.findById(friendshipId).orElse(null);
        if (f == null) return "Lời mời không tồn tại";

        // Chỉ người được mời mới có quyền chấp nhận
        if (!f.getAddresseeId().equals(userId)) {
            return "Bạn không có quyền chấp nhận lời mời này";
        }

        if (f.getStatus() == 1) return "Đã là bạn bè rồi";

        f.setStatus(1);
        friendshipRepository.save(f);

        // Tại sao: Tăng số lượng bạn bè (friendsCount) cho cả 2 người chơi để hiển thị chính xác trên Profile
        User requester = userRepository.findById(f.getRequesterId()).orElse(null);
        User addressee = userRepository.findById(f.getAddresseeId()).orElse(null);
        if (requester != null) {
            requester.setFriendsCount(requester.getFriendsCount() + 1);
            userRepository.save(requester);
        }
        if (addressee != null) {
            addressee.setFriendsCount(addressee.getFriendsCount() + 1);
            userRepository.save(addressee);
        }

        logger.info("Friend request accepted: friendshipId={}", friendshipId);
        
        notifyFriendStatusUpdate(f.getRequesterId());
        notifyFriendStatusUpdate(f.getAddresseeId());
        
        return null;
    }

    /**
     * Xóa bạn bè / Từ chối lời mời.
     */
    @Transactional
    public String removeFriendship(Long friendshipId, Long userId) {
        Friendship f = friendshipRepository.findById(friendshipId).orElse(null);
        if (f == null) return "Quan hệ không tồn tại";

        // Kiểm tra quyền: chỉ 1 trong 2 người mới được xóa
        if (!f.getRequesterId().equals(userId) && !f.getAddresseeId().equals(userId)) {
            return "Bạn không có quyền thao tác";
        }

        boolean wasAccepted = f.getStatus() == 1;
        friendshipRepository.delete(f);

        if (wasAccepted) {
            // Tại sao: Giảm số lượng bạn bè nếu hai người đã từng là bạn bè
            User requester = userRepository.findById(f.getRequesterId()).orElse(null);
            User addressee = userRepository.findById(f.getAddresseeId()).orElse(null);
            if (requester != null) {
                requester.setFriendsCount(Math.max(0, requester.getFriendsCount() - 1));
                userRepository.save(requester);
            }
            if (addressee != null) {
                addressee.setFriendsCount(Math.max(0, addressee.getFriendsCount() - 1));
                userRepository.save(addressee);
            }
        }
        
        // Xóa Couple Streak data cho cả 2
        streakRepository.deleteBetweenUsers(f.getRequesterId(), f.getAddresseeId());

        logger.info("Friendship removed: friendshipId={}, by userId={}", friendshipId, userId);
        
        notifyFriendStatusUpdate(f.getRequesterId());
        notifyFriendStatusUpdate(f.getAddresseeId());
        
        return null;
    }

    /**
     * Hủy kết bạn hoặc hủy lời mời kết bạn dựa trên ID người chơi được chọn.
     * Tại sao: Khi ở màn hình Profile của người khác, Client chỉ biết targetUserId thay vì friendshipId,
     * phương thức này giúp tra cứu và xóa đúng bản ghi quan hệ để đồng bộ chính xác.
     */
    @Transactional
    public String removeFriendshipByTargetUser(Long currentUserId, Long targetUserId) {
        Optional<Friendship> fOpt = friendshipRepository.findExistingFriendship(currentUserId, targetUserId);
        if (fOpt.isEmpty()) {
            return "Quan hệ không tồn tại";
        }
        Friendship f = fOpt.get();
        boolean wasAccepted = f.getStatus() == 1;
        friendshipRepository.delete(f);

        if (wasAccepted) {
            // Tại sao: Giảm số lượng bạn bè khi hủy kết bạn thành công
            User requester = userRepository.findById(f.getRequesterId()).orElse(null);
            User addressee = userRepository.findById(f.getAddresseeId()).orElse(null);
            if (requester != null) {
                requester.setFriendsCount(Math.max(0, requester.getFriendsCount() - 1));
                userRepository.save(requester);
            }
            if (addressee != null) {
                addressee.setFriendsCount(Math.max(0, addressee.getFriendsCount() - 1));
                userRepository.save(addressee);
            }
        }
        
        // Xóa Couple Streak data
        streakRepository.deleteBetweenUsers(currentUserId, targetUserId);

        logger.info("Friendship removed between userId={} and targetUserId={}", currentUserId, targetUserId);
        
        notifyFriendStatusUpdate(currentUserId);
        notifyFriendStatusUpdate(targetUserId);
        
        return null;
    }

    /**
     * Chấp nhận lời mời kết bạn dựa trên ID người chơi được chọn.
     * Tại sao: Cho phép Client chấp nhận lời mời ngay khi đang xem hồ sơ của người gửi mà không cần friendshipId.
     */
    @Transactional
    public String acceptRequestByTargetUser(Long currentUserId, Long targetUserId) {
        Optional<Friendship> fOpt = friendshipRepository.findExistingFriendship(currentUserId, targetUserId);
        if (fOpt.isEmpty()) {
            return "Lời mời không tồn tại";
        }
        Friendship f = fOpt.get();
        // Chỉ addressee mới được chấp nhận
        if (!f.getAddresseeId().equals(currentUserId)) {
            return "Bạn không có quyền chấp nhận lời mời này";
        }
        if (f.getStatus() == 1) {
            return "Đã là bạn bè rồi";
        }
        f.setStatus(1);
        friendshipRepository.save(f);

        // Tại sao: Tăng số lượng bạn bè khi chấp nhận kết bạn thành công
        User requester = userRepository.findById(f.getRequesterId()).orElse(null);
        User addressee = userRepository.findById(f.getAddresseeId()).orElse(null);
        if (requester != null) {
            requester.setFriendsCount(requester.getFriendsCount() + 1);
            userRepository.save(requester);
        }
        if (addressee != null) {
            addressee.setFriendsCount(addressee.getFriendsCount() + 1);
            userRepository.save(addressee);
        }

        logger.info("Friend request accepted between userId={} and targetUserId={}", currentUserId, targetUserId);
        
        notifyFriendStatusUpdate(currentUserId);
        notifyFriendStatusUpdate(targetUserId);
        
        return null;
    }

    /**
     * Tìm kiếm user theo tên hoặc ID.
     * Trả về tối đa 20 kết quả.
     */
    public List<Map<String, Object>> searchUsers(String query, Long excludeUserId) {
        if (query == null || query.trim().isEmpty()) return new ArrayList<>();

        String searchTerm = query.trim();
        String searchTermLower = searchTerm.toLowerCase();
        List<User> allUsers = userRepository.findAll();

        // Thử parse query thành Long để tìm theo ID chính xác
        Long searchId = null;
        try {
            searchId = Long.parseLong(searchTerm);
        } catch (NumberFormatException ignored) {}

        Long finalSearchId = searchId;
        return allUsers.stream()
                .filter(u -> !u.getId().equals(excludeUserId)) // Loại trừ chính mình
                .filter(u -> {
                    // Tìm theo ID (so sánh số, không phải chuỗi)
                    if (finalSearchId != null && u.getId().equals(finalSearchId)) return true;
                    // Tìm theo ingameName
                    if (u.getIngameName() != null && u.getIngameName().toLowerCase().contains(searchTermLower)) return true;
                    // Tìm theo username
                    if (u.getUsername() != null && u.getUsername().toLowerCase().contains(searchTermLower)) return true;
                    return false;
                })
                .limit(20)
                .map(u -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("userId", u.getId());
                    entry.put("ingameName", u.getIngameName() != null ? u.getIngameName() : u.getUsername());
                    entry.put("level", u.getLevel());
                    entry.put("avatarId", u.getAvatarId());
                    entry.put("online", com.vn.jet.mosco.spinserver.utils.UserSessionTracker.isOnline(u.getId()));
                    return entry;
                })
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách tối đa 20 tài khoản ngẫu nhiên chưa có bất kỳ quan hệ bạn bè/lời mời nào với người chơi.
     * Tại sao (WHY): Sử dụng giải pháp xáo trộn danh sách trực tiếp trên RAM (In-memory Shuffling) thay vì dùng lệnh SQL tốn kém ORDER BY RAND()
     * nhằm bảo toàn triệt để hiệu năng cơ sở dữ liệu, cho phép người dùng ấn làm mới liên tục mà không gây thắt cổ chai hệ thống.
     */
    public List<Map<String, Object>> getExploreSuggestions(Long currentUserId) {
        List<Friendship> allRels = friendshipRepository.findAllFriendshipsForUser(currentUserId);
        Set<Long> excludedIds = new HashSet<>();
        excludedIds.add(currentUserId); // Loại trừ chính mình

        for (Friendship f : allRels) {
            excludedIds.add(f.getRequesterId());
            excludedIds.add(f.getAddresseeId());
        }

        List<User> candidates = userRepository.findAll().stream()
                .filter(u -> !excludedIds.contains(u.getId()))
                .collect(Collectors.toList());

        Collections.shuffle(candidates);

        return candidates.stream()
                .limit(20)
                .map(u -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("userId", u.getId());
                    entry.put("ingameName", u.getIngameName() != null ? u.getIngameName() : u.getUsername());
                    entry.put("level", u.getLevel());
                    entry.put("avatarId", u.getAvatarId());
                    entry.put("online", com.vn.jet.mosco.spinserver.utils.UserSessionTracker.isOnline(u.getId()));
                    return entry;
                })
                .collect(Collectors.toList());
    }

    private void notifyFriendStatusUpdate(Long targetUserId) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("senderId", "SYSTEM_FRIEND");
            msg.put("content", "FRIEND_UPDATE");
            messagingTemplate.convertAndSend("/topic/private." + targetUserId, msg);
        } catch (Exception e) {
            logger.error("Failed to broadcast friend update to user " + targetUserId, e);
        }
    }
}
