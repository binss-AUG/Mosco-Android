package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.dto.PackOpenResponse;
import com.vn.jet.mosco.spinserver.service.PackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý các yêu cầu liên quan đến Pack (Gói thẻ).
 * Định dạng API: { "status": 200, "message": "...", "data": { ... } }
 */
@RestController
@RequestMapping("/api/pack")
public class PackController {

    private static final Logger logger = LoggerFactory.getLogger(PackController.class);
    private final PackService packService;

    public PackController(PackService packService) {
        this.packService = packService;
    }

    /**
     * Mở Pack. Trả về danh sách thẻ kèm màu sắc độ hiếm.
     */
    @PostMapping("/open")
    public ResponseEntity<ApiResponse<PackOpenResponse>> openPack(
            @RequestParam Long userId, 
            @RequestParam String packCode,
            @RequestParam(defaultValue = "1") int quantity) {
        try {
            logger.info("Yêu cầu mở Pack: User={}, Code={}, Qty={}", userId, packCode, quantity);
            PackOpenResponse result = packService.openPack(userId, packCode, quantity);
            return ResponseEntity.ok(ApiResponse.success("Mở gói thẻ thành công!", result));
        } catch (Exception e) {
            logger.error("Lỗi khi mở Pack cho User {}: {}", userId, e.getMessage());
            return ResponseEntity.status(400).body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * Nạp lại cấu hình Game (Admin).
     */
    @PostMapping("/reload")
    public ResponseEntity<ApiResponse<String>> reloadConfig() {
        logger.info("Yêu cầu Admin: Nạp lại cấu hình Game...");
        packService.loadData();
        return ResponseEntity.ok(ApiResponse.success("Đã nạp lại cấu hình thành công!", null));
    }

    /**
     * Tặng Pack cho người dùng (Admin).
     */
    @PostMapping("/give")
    public ResponseEntity<ApiResponse<String>> givePack(
            @RequestParam Long userId, 
            @RequestParam String packCode, 
            @RequestParam int quantity) {
        try {
            packService.givePack(userId, packCode, quantity);
            return ResponseEntity.ok(ApiResponse.success("Đã tặng " + quantity + "x " + packCode + " cho User " + userId, null));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(ApiResponse.error(400, e.getMessage()));
        }
    }
}