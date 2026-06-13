package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.service.RankService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller Bảng xếp hạng.
 * 3 loại: Level, OVR (thẻ to nhất), Collection (số thẻ không trùng).
 */
@RestController
@RequestMapping("/api/rank")
public class RankController {

    private static final Logger logger = LoggerFactory.getLogger(RankController.class);
    private final RankService rankService;

    public RankController(RankService rankService) {
        this.rankService = rankService;
    }

    /**
     * GET /api/rank/level — Top 10 theo Level.
     */
    @GetMapping("/level")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopLevel() {
        List<Map<String, Object>> rankings = rankService.getTopByLevel();
        return ResponseEntity.ok(ApiResponse.success("Top Level", rankings));
    }

    /**
     * GET /api/rank/ovr — Top 10 theo OVR (Objet to nhất).
     */
    @GetMapping("/ovr")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopOvr() {
        List<Map<String, Object>> rankings = rankService.getTopByOvr();
        return ResponseEntity.ok(ApiResponse.success("Top OVR", rankings));
    }

    /**
     * GET /api/rank/collection — Top 10 theo số thẻ không trùng.
     */
    @GetMapping("/collection")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopCollection() {
        List<Map<String, Object>> rankings = rankService.getTopByCollection();
        return ResponseEntity.ok(ApiResponse.success("Top Collection", rankings));
    }

    /**
     * GET /api/rank/social — Top 10 theo Nhiều bạn bè nhất.
     */
    @GetMapping("/social")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopSocial() {
        List<Map<String, Object>> rankings = rankService.getTopBySocial();
        return ResponseEntity.ok(ApiResponse.success("Top Social", rankings));
    }

    /**
     * GET /api/rank/streak — Top 10 theo Chuỗi đăng nhập (Streak).
     */
    @GetMapping("/streak")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopStreak() {
        List<Map<String, Object>> rankings = rankService.getTopByStreak();
        return ResponseEntity.ok(ApiResponse.success("Top Streak", rankings));
    }

    /**
     * GET /api/rank/fame — Top 10 theo Danh vọng (Likes).
     */
    @GetMapping("/fame")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopFame() {
        List<Map<String, Object>> rankings = rankService.getTopByFame();
        return ResponseEntity.ok(ApiResponse.success("Top Fame", rankings));
    }

    /**
     * GET /api/rank/duo-streak — Top 10 Couple Streak.
     */
    @GetMapping("/duo-streak")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopDuoStreak() {
        List<Map<String, Object>> rankings = rankService.getTopByDuoStreak();
        return ResponseEntity.ok(ApiResponse.success("Top Duo Streak", rankings));
    }
}
