package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.service.AssetManagementService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;

/**
 * Controller phục vụ các file Metadata động (database.json, manifest.json)
 * từ thư mục dữ liệu bên ngoài thay vì classpath.
 */
@RestController
@RequestMapping("/api")
public class AssetController {

    private final String dataDir;
    private final AssetManagementService assetService;

    public AssetController(@Value("${ASSET_DATA_DIR:data/assets/}") String dataDir,
                           AssetManagementService assetService) {
        this.dataDir = dataDir;
        this.assetService = assetService;
    }

    @GetMapping("/assets/manifest")
    public ResponseEntity<Resource> getManifest() {
        File file = new File(dataDir, "manifest.json");
        if (!file.exists()) return ResponseEntity.notFound().build();
        
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource);
    }

    @GetMapping("/v1/assets/database")
    public ResponseEntity<Resource> getDatabase() {
        File file = new File(dataDir, "database.json");
        if (!file.exists()) return ResponseEntity.notFound().build();

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"database.json\"")
                .body(resource);
    }

    /**
     * POST /api/assets/sync — Kích hoạt tiến trình đồng bộ metadata bất đồng bộ.
     * Tại sao (WHY): Đồng bộ chạy ngầm (asynchronously) để tránh gây nghẽn luồng HTTP chính
     * và timeout kết nối từ phía client/browser.
     */
    @PostMapping("/assets/sync")
    public ResponseEntity<String> sync() {
        java.util.concurrent.CompletableFuture.runAsync(() -> assetService.fullSyncProcess());
        return ResponseEntity.ok("Tiến trình đồng bộ metadata đã được kích hoạt ngầm thành công.");
    }

    /**
     * POST /api/assets/rebuild — API rebuild gói tài nguyên.
     * Tại sao (WHY): Cơ chế Bundling đã bị lược bỏ trong phiên bản Lean để tiết kiệm 10GB dung lượng ổ đĩa,
     * nên API này chỉ trả về thông báo để tránh lỗi 404 trên giao diện quản trị.
     */
    @PostMapping("/assets/rebuild")
    public ResponseEntity<String> rebuild() {
        return ResponseEntity.ok("Cơ chế nén Sealed Bundles đã được lược bỏ trong phiên bản rút gọn (Lean Version) để tối ưu hóa lưu trữ đĩa.");
    }

    /**
     * GET /api/assets/status — Lấy trạng thái đồng bộ hiện tại.
     * Tại sao (WHY): Dashboard Admin cần gọi định kỳ (polling) để hiển thị tiến trình cào dữ liệu thời gian thực.
     */
    @GetMapping("/assets/status")
    public ResponseEntity<java.util.Map<String, String>> getStatus() {
        return ResponseEntity.ok(java.util.Map.of(
            "status", assetService.getSyncStatus(),
            "detail", assetService.getSyncDetail()
        ));
    }
}
