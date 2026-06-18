package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.service.RagEtlService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/etl")
public class TestEtlController {
    
    private final RagEtlService ragEtlService;

    public TestEtlController(RagEtlService ragEtlService) {
        this.ragEtlService = ragEtlService;
    }

    @GetMapping("/run")
    public Map<String, String> runEtlManual() {
        new Thread(() -> ragEtlService.runEtl()).start();
        return Map.of("status", "success", "message", "ETL Pipeline started in background. Check console logs.");
    }
}
