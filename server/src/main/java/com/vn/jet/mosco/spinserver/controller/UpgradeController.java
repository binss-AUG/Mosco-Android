package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.model.UpgradeRequest;
import com.vn.jet.mosco.spinserver.model.UpgradeResponse;
import com.vn.jet.mosco.spinserver.service.UpgradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gacha")
public class UpgradeController {

    private final UpgradeService upgradeService;

    public UpgradeController(UpgradeService upgradeService) {
        this.upgradeService = upgradeService;
    }

    @PostMapping("/upgrade")
    public ResponseEntity<ApiResponse<UpgradeResponse>> upgradeCard(
            @RequestAttribute("userId") Long userId,
            @RequestBody UpgradeRequest request) {
        try {
            // Đảm bảo request gán đúng userId từ token
            request.setUserId(userId);
            
            UpgradeResponse response = upgradeService.upgradeCard(request);
            
            // Trả về 200 OK cho cả trường hợp nâng cấp thất bại (trong game) 
            // vì thực tế database vẫn được cập nhật (mất nguyên liệu, có thể tụt level).
            return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(
                ApiResponse.error(400, e.getMessage())
            );
        }
    }
}
