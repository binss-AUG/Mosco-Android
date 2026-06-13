package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.utils.SpinSystem;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.PostConstruct;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
public class SpinController {
    private final SpinSystem spinSystem = new SpinSystem(null);

    @PostConstruct
    public void init() {
        try {
            // Đọc dữ liệu từ thư mục resources
            java.io.File dbFile = new java.io.File("data/assets/database.json");
            if (!dbFile.exists()) {
                throw new RuntimeException("data/assets/database.json not found!");
            }
            var rates = new ClassPathResource("rates_config.json");
            spinSystem.loadData(
                    new InputStreamReader(new java.io.FileInputStream(dbFile), StandardCharsets.UTF_8),
                    new InputStreamReader(rates.getInputStream(), StandardCharsets.UTF_8)
            );
            log.info("Jet Mien Tay - SpinSystem loaded!");
        } catch (Exception e) {
            log.error("Failed to load SpinSystem data", e);
        }
    }

    @PostMapping("/spin")
    public ResponseEntity<String> spin() {
        return ResponseEntity.ok(spinSystem.spin().toJson());
    }
}