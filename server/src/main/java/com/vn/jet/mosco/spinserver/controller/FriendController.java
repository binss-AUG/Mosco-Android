package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.service.FriendService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller quản lý Bạn bè.
 * Tất cả API đều JWT protected — userId lấy từ token.
 */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private static final Logger logger = LoggerFactory.getLogger(FriendController.class);
    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    /**
     * GET /api/friends/list — Danh sách bạn bè đã chấp nhận.
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFriendList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "Authentication required"));
        }
        List<Map<String, Object>> friends = friendService.getFriendList(userId);
        return ResponseEntity.ok(ApiResponse.success("Danh sách bạn bè", friends));
    }

    /**
     * GET /api/friends/requests — Lời mời kết bạn đang chờ.
     */
    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPendingRequests(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "Authentication required"));
        }
        List<Map<String, Object>> pending = friendService.getPendingRequests(userId);
        return ResponseEntity.ok(ApiResponse.success("Lời mời kết bạn", pending));
    }

    /**
     * POST /api/friends/add — Gửi lời mời kết bạn.
     * Body: { "addresseeId": 123 }
     */
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<Void>> addFriend(HttpServletRequest request, @RequestBody Map<String, Long> body) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "Authentication required"));
        }

        Long addresseeId = body.get("addresseeId");
        if (addresseeId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Thiếu addresseeId"));
        }

        String error = friendService.sendRequest(userId, addresseeId);
        if (error != null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, error));
        }
        return ResponseEntity.ok(ApiResponse.success("Đã gửi lời mời kết bạn!", null));
    }

    /**
     * POST /api/friends/accept/{friendshipId} — Chấp nhận lời mời.
     */
    @PostMapping("/accept/{friendshipId}")
    public ResponseEntity<ApiResponse<Void>> acceptFriend(HttpServletRequest request, @PathVariable Long friendshipId) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "Authentication required"));
        }

        String error = friendService.acceptRequest(friendshipId, userId);
        if (error != null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, error));
        }
        return ResponseEntity.ok(ApiResponse.success("Đã chấp nhận lời mời kết bạn!", null));
    }

    /**
     * DELETE /api/friends/remove/{friendshipId} — Xóa bạn / Từ chối.
     */
    @DeleteMapping("/remove/{friendshipId}")
    public ResponseEntity<ApiResponse<Void>> removeFriend(HttpServletRequest request, @PathVariable Long friendshipId) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "Authentication required"));
        }

        String error = friendService.removeFriendship(friendshipId, userId);
        if (error != null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, error));
        }
        return ResponseEntity.ok(ApiResponse.success("Đã xóa bạn bè", null));
    }

    /**
     * GET /api/friends/search?query=xxx — Tìm kiếm user theo tên hoặc ID.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchUsers(
            HttpServletRequest request,
            @RequestParam String query) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "Authentication required"));
        }

        List<Map<String, Object>> results = friendService.searchUsers(query, userId);
        return ResponseEntity.ok(ApiResponse.success("Kết quả tìm kiếm", results));
    }
}
