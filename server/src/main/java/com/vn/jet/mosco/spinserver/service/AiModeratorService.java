package com.vn.jet.mosco.spinserver.service;

import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiModeratorService {
    private static final Logger logger = LoggerFactory.getLogger(AiModeratorService.class);
    private final GeminiApiService geminiApiService;
    private final List<String> badWords = new ArrayList<>();
    
    // In-memory penalty cache: userId -> violationCount
    private final ConcurrentHashMap<Long, Integer> violationCache = new ConcurrentHashMap<>();
    // userId -> unbanTime
    private final ConcurrentHashMap<Long, Long> banCache = new ConcurrentHashMap<>();

    public AiModeratorService(GeminiApiService geminiApiService) {
        this.geminiApiService = geminiApiService;
    }

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new ClassPathResource("ai/bad_words_blacklist.json").getInputStream());
            if (root.has("vietnamese")) {
                root.path("vietnamese").forEach(node -> badWords.add(node.asText().toLowerCase()));
            }
            if (root.has("english")) {
                root.path("english").forEach(node -> badWords.add(node.asText().toLowerCase()));
            }
            logger.info("Loaded {} bad words for moderation.", badWords.size());
        } catch (Exception e) {
            logger.error("Failed to load bad words blacklist: {}", e.getMessage());
        }
    }

    public boolean isBanned(Long userId) {
        Long unbanTime = banCache.get(userId);
        if (unbanTime != null) {
            if (System.currentTimeMillis() < unbanTime) {
                return true;
            } else {
                banCache.remove(userId);
            }
        }
        return false;
    }

    public long getBanRemainingSeconds(Long userId) {
        Long unbanTime = banCache.get(userId);
        if (unbanTime != null && unbanTime > System.currentTimeMillis()) {
            return (unbanTime - System.currentTimeMillis()) / 1000;
        }
        return 0;
    }

    public void applyPenalty(Long userId) {
        int violations = violationCache.getOrDefault(userId, 0) + 1;
        violationCache.put(userId, violations);
        
        long banDurationMs;
        if (violations == 1) banDurationMs = 15 * 1000L; // 15s
        else if (violations == 2) banDurationMs = 60 * 1000L; // 1m
        else banDurationMs = 15 * 60 * 1000L; // 15m
        
        banCache.put(userId, System.currentTimeMillis() + banDurationMs);
        logger.warn("Applied penalty to user {}: {} ms ban (violation #{})", userId, banDurationMs, violations);
    }

    // Layer 1: Simple List Matching
    public boolean containsBadWords(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        for (String word : badWords) {
            // Check if the exact word exists as a substring
            if (lower.contains(word)) return true;
        }
        return false;
    }

    // Layer 2: AI Context Moderation
    public boolean checkContextWithAi(String text) {
        String prompt = "Bạn là AI kiểm duyệt. Hãy phân tích câu sau đây. Nếu câu mang ý nghĩa xúc phạm, chửi thề, vi phạm chuẩn mực đạo đức, đe dọa hoặc độc hại, hãy trả lời đúng 1 chữ 'YES'. Nếu câu bình thường, trả lời 'NO'.\nCâu: \"" + text + "\"";
        String response = geminiApiService.generateContent(null, List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));
        return response != null && response.toUpperCase().contains("YES");
    }
}
