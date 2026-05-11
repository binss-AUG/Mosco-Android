package com.vn.jet.mosco.spinserver.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

/**
 * Controller phục vụ các file Metadata động (database.json, manifest.json)
 * từ thư mục dữ liệu bên ngoài thay vì classpath.
 */
@RestController
@RequestMapping("/api")
public class AssetController {

    private final String dataDir;

    public AssetController(@Value("${ASSET_DATA_DIR:data/assets/}") String dataDir) {
        this.dataDir = dataDir;
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
}
