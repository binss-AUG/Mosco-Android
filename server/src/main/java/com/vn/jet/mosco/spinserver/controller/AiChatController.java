package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.service.GeminiApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ClassPathResource;
import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private static final Logger logger = LoggerFactory.getLogger(AiChatController.class);
    private final GeminiApiService geminiApiService;
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode biasPrompts;

    public AiChatController(GeminiApiService geminiApiService) {
        this.geminiApiService = geminiApiService;
    }

    @PostConstruct
    public void init() {
        try {
            biasPrompts = mapper.readTree(new ClassPathResource("ai/bias_prompts.json").getInputStream());
            logger.info("Loaded bias prompts successfully.");
        } catch (Exception e) {
            logger.error("Failed to load bias_prompts.json", e);
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<String>> chatWithAi(
            @RequestAttribute("userId") Long userId,
            @RequestBody Map<String, String> body) {
        
        String biasId = body.getOrDefault("biasId", "S1_Seoyeon");
        String message = body.get("message");
        
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Message cannot be empty"));
        }

        String lang = body.getOrDefault("language", "vi");
        String langInstruction = lang.equalsIgnoreCase("en") ? "Only answer in English" : "Chỉ trả lời bằng tiếng Việt";
        String systemInstruction = "Bạn là AI Assistant hỗ trợ người chơi game Mosco.";
        
        if (biasPrompts != null && biasPrompts.has(biasId)) {
            JsonNode bias = biasPrompts.get(biasId);
            systemInstruction = "Đóng vai: " + bias.path("name").asText() + ".\n" +
                                "Tính cách: " + bias.path("personality").asText() + ".\n" +
                                "Giọng điệu: " + bias.path("tone").asText() + ".\n" +
                                "Câu cửa miệng: " + bias.path("catchphrase").asText() + ".\n" +
                                "LUẬT: " + langInstruction + ", rất ngắn gọn, thể hiện đúng tính cách và thỉnh thoảng dùng câu cửa miệng.";
        }

        logger.info("User {} chatting with AI Bias {}: {}", userId, biasId, message);
        String response = geminiApiService.generateContent(systemInstruction, message);
        
        return ResponseEntity.ok(ApiResponse.success("Success", response));
    }
}
