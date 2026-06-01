package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.service.DailyCheckinService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.vn.jet.mosco.spinserver.utils.MessageConstants;

import java.util.Map;

/**
 * Controller điểm danh hằng ngày.
 * API được bảo vệ bởi JWT — userId lấy từ token.
 */
@RestController
@RequestMapping("/api/daily")
public class DailyCheckinController {

    private static final Logger logger = LoggerFactory.getLogger(DailyCheckinController.class);
    private final DailyCheckinService dailyCheckinService;

    public DailyCheckinController(DailyCheckinService dailyCheckinService) {
        this.dailyCheckinService = dailyCheckinService;
    }

    /**
     * GET /api/daily/status — Trạng thái 3 slot trong ngày.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(@RequestAttribute("userId") Long userId) {
        Map<String, Object> status = dailyCheckinService.getStatus(userId);
        return ResponseEntity.ok(ApiResponse.success("Trạng thái điểm danh", status));
    }

    /**
     * POST /api/daily/claim — Nhận thưởng slot hiện tại.
     */
    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<Map<String, Object>>> claim(@RequestAttribute("userId") Long userId) {
        Map<String, Object> result = dailyCheckinService.claim(userId);
        if (result == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, MessageConstants.CHECKIN_ERROR));
        }

        return ResponseEntity.ok(ApiResponse.success("Điểm danh thành công!", result));
    }
}
