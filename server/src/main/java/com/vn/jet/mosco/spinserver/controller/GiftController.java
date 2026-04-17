package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.dto.GiftHistoryDTO;
import com.vn.jet.mosco.spinserver.dto.GiftRequest;
import com.vn.jet.mosco.spinserver.service.GiftService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller quản lý tính năng Tặng Objet (Gift).
 * UI riêng biệt — KHÔNG dùng Mailbox.
 * Tất cả API đều JWT protected — userId lấy từ token.
 */
@RestController
@RequestMapping("/api/gift")
public class GiftController {

    private static final Logger logger = LoggerFactory.getLogger(GiftController.class);
    private final GiftService giftService;

    public GiftController(GiftService giftService) {
        this.giftService = giftService;
    }

    /**
     * POST /api/gift/send — Gửi tặng thẻ bài cho bạn bè.
     * Phí: 36,000 Coin + 36 Diamond.
     * Body: { "cardId": 1, "receiverId": 2 }
     */
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<Void>> sendGift(
            HttpServletRequest request,
            @RequestBody GiftRequest giftRequest) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        // Validate input
        if (giftRequest.getCardId() == null || giftRequest.getReceiverId() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Missing cardId or receiverId"));
        }

        // Gọi service xử lý nghiệp vụ
        String error = giftService.sendGift(userId, giftRequest.getCardId(), giftRequest.getReceiverId());
        if (error != null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, error));
        }

        return ResponseEntity.ok(ApiResponse.success("Gift sent successfully! 🎁", null));
    }

    /**
     * GET /api/gift/received — Danh sách quà đã nhận (inbox).
     */
    @GetMapping("/received")
    public ResponseEntity<ApiResponse<List<GiftHistoryDTO>>> getReceivedGifts(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        List<GiftHistoryDTO> received = giftService.getReceivedGifts(userId);
        return ResponseEntity.ok(ApiResponse.success("Received Gifts", received));
    }

    /**
     * GET /api/gift/sent — Danh sách quà đã gửi.
     */
    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<List<GiftHistoryDTO>>> getSentGifts(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        List<GiftHistoryDTO> sent = giftService.getSentGifts(userId);
        return ResponseEntity.ok(ApiResponse.success("Sent Gifts", sent));
    }

    /**
     * POST /api/gift/mark-read — Đánh dấu tất cả quà nhận là đã đọc.
     */
    @PostMapping("/mark-read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        giftService.markReceivedAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    /**
     * GET /api/gift/daily-remaining — Số lượt tặng còn lại trong ngày.
     */
    @GetMapping("/daily-remaining")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getDailyRemaining(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        int remaining = giftService.getDailyRemaining(userId);
        return ResponseEntity.ok(ApiResponse.success(
                "Daily Gift Uses",
                Map.of("remaining", remaining, "limit", 5)
        ));
    }

    /**
     * GET /api/gift/unread-count — Số quà chưa đọc (cho badge thông báo).
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getUnreadCount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        int count = giftService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(
                "Unread Gifts count",
                Map.of("unreadCount", count)
        ));
    }
}
