package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.service.PackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pack")
public class PackController {

    private static final Logger logger = LoggerFactory.getLogger(PackController.class);
    private final PackService packService;

    public PackController(PackService packService) {
        this.packService = packService;
    }

    /**
     * Open a pack.
     * POST /api/pack/open?userId=1&packCode=PACK_STARTER
     */
    @PostMapping("/open")
    public ResponseEntity<Map<String, Object>> openPack(
            @RequestParam Long userId, 
            @RequestParam String packCode,
            @RequestParam(defaultValue = "1") int quantity) {
        try {
            logger.info("Received request to open {}x pack. UserID: {}, PackCode: {}", quantity, userId, packCode);
            Map<String, Object> result = packService.openPack(userId, packCode, quantity);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to open pack for UserID: {}, PackCode: {}. Error: {}", userId, packCode, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to open pack",
                "message", e.getMessage(),
                "packCode", packCode,
                "userId", userId
            ));
        }
    }

    /**
     * Reload game configuration and database from disk without restarting server.
     * POST /api/pack/reload
     */
    @PostMapping("/reload")
    public ResponseEntity<String> reloadConfig() {
        logger.info("Admin request: Reloading game configuration...");
        packService.loadData();
        return ResponseEntity.ok("Configuration and database reloaded successfully");
    }

    /**
     * Admin command to give packs to a user.
     * POST /api/pack/give?userId=1&packCode=PACK_STARTER&quantity=5
     */
    @PostMapping("/give")
    public ResponseEntity<String> givePack(@RequestParam Long userId, @RequestParam String packCode, @RequestParam int quantity) {
        try {
            logger.info("Admin request: Give {}x {} to user {}", quantity, packCode, userId);
            packService.givePack(userId, packCode, quantity);
            return ResponseEntity.ok("Successfully gave " + quantity + "x " + packCode + " to user " + userId);
        } catch (Exception e) {
            logger.error("Failed to give pack to user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Failed: " + e.getMessage());
        }
    }
}
