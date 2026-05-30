package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.StartStageRequest;
import com.vn.jet.mosco.spinserver.dto.StageSessionResponse;
import com.vn.jet.mosco.spinserver.dto.StageRewardResponse;
import com.vn.jet.mosco.spinserver.service.StageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/stage")
public class StageController {

    private final StageService stageService;

    public StageController(StageService stageService) {
        this.stageService = stageService;
    }

    @PostMapping("/start/{userId}")
    public ResponseEntity<?> startStage(@PathVariable Long userId, @RequestBody StartStageRequest request) {
        try {
            StageSessionResponse response = stageService.startStage(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to start stage for user {}", userId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/claim/{userId}/{sessionId}")
    public ResponseEntity<?> claimReward(@PathVariable Long userId, @PathVariable Long sessionId) {
        try {
            StageRewardResponse response = stageService.claimReward(userId, sessionId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/abort/{userId}/{sessionId}")
    public ResponseEntity<String> abortStage(@PathVariable Long userId, @PathVariable Long sessionId) {
        try {
            stageService.abortStage(userId, sessionId);
            return ResponseEntity.ok("Aborted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/speed-up/{userId}/{sessionId}")
    public ResponseEntity<String> speedUpStage(@PathVariable Long userId, @PathVariable Long sessionId) {
        try {
            stageService.speedUpStage(userId, sessionId);
            return ResponseEntity.ok("Speed-up successful");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/my-sessions/{userId}")
    public ResponseEntity<List<StageSessionResponse>> getMySessions(@PathVariable Long userId) {
        return ResponseEntity.ok(stageService.getMySessions(userId));
    }
}
