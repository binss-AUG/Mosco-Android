package com.vn.jet.mosco.spinserver.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller cung cấp thông tin cấu hình và đồng bộ Database cho Client.
 * Hỗ trợ chiến thuật Dynamic Sync (Phương án 2).
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final String DB_FILE = "database.json";

    /**
     * Trả về Version (mã MD5) của file database.json hiện tại trên Server.
     */
    @GetMapping("/db-version")
    public ResponseEntity<Map<String, String>> getDatabaseVersion() {
        Map<String, String> response = new HashMap<>();
        try {
            Resource resource = new ClassPathResource(DB_FILE);
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = resource.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            byte[] hashBytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            response.put("version", sb.toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Could not calculate DB version");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Cho phép Client tải file database.json bản mới nhất.
     */
    @GetMapping("/db-download")
    public ResponseEntity<Resource> downloadDatabase() {
        try {
            Resource resource = new ClassPathResource(DB_FILE);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + DB_FILE + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
