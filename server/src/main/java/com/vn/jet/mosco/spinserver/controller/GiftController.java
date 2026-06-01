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
import com.vn.jet.mosco.spinserver.utils.MessageConstants;

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
            @RequestAttribute("userId") Long userId,
            @RequestBody GiftRequest giftRequest) {

        // Validate input
        if (giftRequest.getCardId() == null || giftRequest.getReceiverId() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, MessageConstants.GIFT_MISSING_FIELDS));
        }

        // Gọi service xử lý nghiệp vụ
        String error = giftService.sendGift(userId, giftRequest.getCardId(), giftRequest.getReceiverId());
        if (error != null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, error));
        }

        return ResponseEntity.ok(ApiResponse.success(MessageConstants.GIFT_SENT_SUCCESS, null));
    }

    /**
     * GET /api/gift/received — Danh sách quà đã nhận (inbox).
     */
    @GetMapping("/received")
    public ResponseEntity<ApiResponse<List<GiftHistoryDTO>>> getReceivedGifts(@RequestAttribute("userId") Long userId) {
        List<GiftHistoryDTO> received = giftService.getReceivedGifts(userId);
        return ResponseEntity.ok(ApiResponse.success("Received Gifts", received));
    }

    /**
     * GET /api/gift/sent — Danh sách quà đã gửi.
     */
    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<List<GiftHistoryDTO>>> getSentGifts(@RequestAttribute("userId") Long userId) {
        List<GiftHistoryDTO> sent = giftService.getSentGifts(userId);
        return ResponseEntity.ok(ApiResponse.success("Sent Gifts", sent));
    }

    /**
     * POST /api/gift/mark-read — Đánh dấu tất cả quà nhận là đã đọc.
     */
    @PostMapping("/mark-read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@RequestAttribute("userId") Long userId) {
        giftService.markReceivedAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    /**
     * GET /api/gift/daily-remaining — Số lượt tặng còn lại trong ngày.
     */
    @GetMapping("/daily-remaining")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getDailyRemaining(@RequestAttribute("userId") Long userId) {
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
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getUnreadCount(@RequestAttribute("userId") Long userId) {
        int count = giftService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(
                "Unread Gifts count",
                Map.of("unreadCount", count)
        ));
    }
}
