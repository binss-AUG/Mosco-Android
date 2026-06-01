package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.vn.jet.mosco.spinserver.utils.MessageConstants;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final FileStorageService storageService;

    public BackupController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadBackup(
            @RequestAttribute("userId") Long userId,
            @RequestParam("file") MultipartFile file) {
        
        try {
            String filename = storageService.store(file, userId);
            log.info("User {} uploaded backup: {}", userId, filename);
            return ResponseEntity.ok(ApiResponse.success("Backup uploaded successfully", filename));
        } catch (Exception e) {
            log.error("Failed to upload backup for user {}", userId, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "Failed to store backup: " + e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<String>>> listBackups(@RequestAttribute("userId") Long userId) {
        try {
            Path userPath = java.nio.file.Paths.get("storage/backups").resolve(String.valueOf(userId));
            if (!Files.exists(userPath)) {
                return ResponseEntity.ok(ApiResponse.success("No backups found", List.of()));
            }

            List<String> files = Files.list(userPath)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".db"))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success("Backup list retrieved", files));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "Failed to list backups"));
        }
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadBackup(
            @RequestAttribute("userId") Long userId,
            @PathVariable String filename) {
        
        try {
            Path file = storageService.load(filename, userId);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
