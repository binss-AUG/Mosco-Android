package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.dto.GachaRollRequest;
import com.vn.jet.mosco.spinserver.dto.GachaRollResponse;
import com.vn.jet.mosco.spinserver.dto.GachaSpinRequest;
import com.vn.jet.mosco.spinserver.dto.GachaSpinResponse;
import com.vn.jet.mosco.spinserver.model.GachaHistory;
import com.vn.jet.mosco.spinserver.repository.GachaHistoryRepository;
import com.vn.jet.mosco.spinserver.service.GachaService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the Gacha system.
 * <p>
 * All endpoints under /api/gacha/* are protected by:
 * - RateLimitFilter (60 req/IP/min)
 * - JwtAuthFilter (Bearer token validation)
 * <p>
 * The authenticated userId is available via request.getAttribute("userId"),
 * set by JwtAuthFilter after successful token validation.
 */
@RestController
@RequestMapping("/api/gacha")
public class GachaController {

    private static final Logger logger = LoggerFactory.getLogger(GachaController.class);

    private final GachaService gachaService;
    private final GachaHistoryRepository gachaHistoryRepository;

    public GachaController(GachaService gachaService, GachaHistoryRepository gachaHistoryRepository) {
        this.gachaService = gachaService;
        this.gachaHistoryRepository = gachaHistoryRepository;
    }

    /**
     * POST /api/gacha/roll
     * <p>
     * Roll a gacha pack. Requires JWT authentication.
     * Request body: { "packCode": "PACK_METAL", "quantity": 1 }
     * <p>
     * Response 200: { "success": true, "itemId": "...", "rarity": "...", "quantity": 1, "cardData": {...} }
     * Response 400: { "success": false, "message": "..." }
     * Response 401: Handled by JwtAuthFilter (missing/invalid token)
     * Response 429: Handled by RateLimitFilter (rate exceeded)
     */
    @PostMapping("/roll")
    public ResponseEntity<ApiResponse<GachaRollResponse>> roll(
            HttpServletRequest request,
            @RequestBody GachaRollRequest rollRequest) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        if (rollRequest == null || rollRequest.getPackCode() == null || rollRequest.getPackCode().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "packCode is required"));
        }

        logger.info("POST /api/gacha/roll — userId={}, packCode={}", userId, rollRequest.getPackCode());

        GachaRollResponse response = gachaService.roll(userId, rollRequest.getPackCode());

        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, response.getMessage()));
        }
    }

    /**
     * POST /api/gacha/spin
     * <p>
     * Trade a card for a chance to get a new one. (Card Sacrifice)
     * Request body: { "cardId": "card_001" }
     */
    @PostMapping("/spin")
    public ResponseEntity<ApiResponse<GachaSpinResponse>> spin(
            HttpServletRequest request,
            @RequestBody GachaSpinRequest spinRequest) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        if (spinRequest == null || spinRequest.getCardId() == null || spinRequest.getCardId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "cardId is required"));
        }

        logger.info("POST /api/gacha/spin — userId={}, sacrificedCardId={}", userId, spinRequest.getCardId());

        GachaSpinResponse response = gachaService.spin(userId, spinRequest.getCardId());

        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, response.getMessage()));
        }
    }

    /**
     * GET /api/gacha/history
     * <p>
     * Retrieve gacha roll history for the authenticated user.
     * Requires JWT authentication.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<GachaHistory>>> getHistory(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "Authentication required"));
        }

        List<GachaHistory> history = gachaHistoryRepository.findByUserIdOrderByRolledAtDesc(userId);
        return ResponseEntity.ok(ApiResponse.success("History retrieved", history));
    }
}
