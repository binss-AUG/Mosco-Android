package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.model.UpgradeRequest;
import com.vn.jet.mosco.spinserver.model.UpgradeResponse;
import com.vn.jet.mosco.spinserver.service.UpgradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/upgrade")
@RequiredArgsConstructor
public class UpgradeController {

    private final UpgradeService upgradeService;

    /**
     * Endpoint thực hiện nâng cấp thẻ bài.
     */
    @PostMapping
    public ApiResponse<UpgradeResponse> upgradeCard(@RequestBody UpgradeRequest request) {
        try {
            UpgradeResponse response = upgradeService.upgrade(request);
            return new ApiResponse<>(200, "Success", response);
        } catch (Exception e) {
            return new ApiResponse<>(500, e.getMessage(), null);
        }
    }
}
