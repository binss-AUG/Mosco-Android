package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.service.AssetManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    @Autowired
    private AssetManagementService assetService;

    /**
     * Kích hoạt đồng bộ thủ công (Cào data → Tải ảnh → Tạo Patch)
     */
    @PostMapping("/sync")
    public ResponseEntity<String> triggerSync() {
        new Thread(() -> assetService.fullSyncProcess()).start();
        return ResponseEntity.ok("Đã bắt đầu quá trình đồng bộ ngầm. Vui lòng kiểm tra /api/assets/status để theo dõi.");
    }

    /**
     * Rebuild toàn bộ Sealed Bundles (dùng khi cần reset)
     */
    @PostMapping("/rebuild")
    public ResponseEntity<String> triggerRebuild() {
        new Thread(() -> assetService.rebuildAllBundles()).start();
        return ResponseEntity.ok("Đã bắt đầu rebuild Sealed Bundles. Quá trình có thể mất vài phút.");
    }

    /**
     * Trạng thái đồng bộ hiện tại (cho Admin Dashboard poll)
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "status", assetService.getSyncStatus(),
                "detail", assetService.getSyncDetail()
        ));
    }

    /**
     * Tải file manifest.json (Client dùng để check update)
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
     * Tải file database.json mới nhất
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

    /**
     * Tải 1 gói cụ thể (bundle_0000.zip, patch_0001.zip, v.v.)
     */
    @GetMapping("/bundle/{filename}")
    public ResponseEntity<Resource> downloadBundle(@PathVariable String filename) {
        // Bảo mật: chỉ cho phép tải file .zip, không cho path traversal
        if (!filename.endsWith(".zip") || filename.contains("..") || filename.contains("/")) {
            return ResponseEntity.badRequest().build();
        }

        File file = new File(assetService.getBundlesDir() + filename);
        if (!file.exists()) return ResponseEntity.notFound().build();

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .body(resource);
    }
}
