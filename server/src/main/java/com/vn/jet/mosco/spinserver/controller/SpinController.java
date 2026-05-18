package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.utils.SpinSystem;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.PostConstruct;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class SpinController {
    private final SpinSystem spinSystem = new SpinSystem(null);

    @PostConstruct
    public void init() {
        try {
            // Đọc dữ liệu từ thư mục resources
            var db = new ClassPathResource("database.json");
            var rates = new ClassPathResource("rates_config.json");
            spinSystem.loadData(
                    new InputStreamReader(db.getInputStream(), StandardCharsets.UTF_8),
                    new InputStreamReader(rates.getInputStream(), StandardCharsets.UTF_8)
            );
            System.out.println(">>> Jet Mien Tay - SpinSystem loaded!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PostMapping("/spin")
    public ResponseEntity<String> spin() {
        return ResponseEntity.ok(spinSystem.spin().toJson());
    }
}