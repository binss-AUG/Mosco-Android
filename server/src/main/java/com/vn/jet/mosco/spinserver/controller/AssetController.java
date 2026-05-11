package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.service.AssetManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;

/**
 * Controller phục vụ Metadata (Lean Version).
 * Đã loại bỏ các endpoint liên quan đến Bundles/Zips.
 */
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    @Autowired
    private AssetManagementService assetService;

    /**
     * Kích hoạt đồng bộ Metadata thủ công
     */
    @PostMapping("/sync")
    public ResponseEntity<String> triggerSync() {
        new Thread(() -> assetService.fullSyncProcess()).start();
        return ResponseEntity.ok("Đã bắt đầu quá trình cập nhật Metadata. Vui lòng kiểm tra /api/assets/status để theo dõi.");
    }

    /**
     * Trạng thái đồng bộ hiện tại
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "status", assetService.getSyncStatus(),
                "detail", assetService.getSyncDetail()
        ));
    }

    /**
     * Tải file manifest.json (Dùng để check version)
     */
    @GetMapping("/manifest")
    public ResponseEntity<Resource> downloadManifest() {
        File file = new File(assetService.getManifestPath());
        if (!file.exists()) return ResponseEntity.notFound().build();

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource);
    }

    /**
     * Tải file database.json mới nhất (Dùng cho Initial Sync - Tự động nén Gzip)
     */
    @GetMapping("/database")
    public ResponseEntity<Resource> downloadDatabase() {
        File file = new File(assetService.getJsonPath());
        if (!file.exists()) return ResponseEntity.notFound().build();

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource);
    }
}
