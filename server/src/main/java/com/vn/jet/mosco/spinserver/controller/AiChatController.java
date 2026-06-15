package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.service.GeminiApiService;
import com.vn.jet.mosco.spinserver.service.VectorStoreService;
import com.vn.jet.mosco.spinserver.service.TranslationService;
import com.vn.jet.mosco.spinserver.service.SimulatedStreamingService;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.VectorDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    private final UserRepository userRepository;
    private final VectorStoreService vectorStoreService;
    private final TranslationService translationService;
    private final SimulatedStreamingService streamingService;
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode biasPrompts;
    private String gameKnowledge = "";
    private final Map<List<Double>, String> semanticCache = new ConcurrentHashMap<>();

    public AiChatController(GeminiApiService geminiApiService, UserRepository userRepository, VectorStoreService vectorStoreService, TranslationService translationService, SimulatedStreamingService streamingService) {
        this.geminiApiService = geminiApiService;
        this.userRepository = userRepository;
        this.vectorStoreService = vectorStoreService;
        this.translationService = translationService;
        this.streamingService = streamingService;
    }

    @PostConstruct
    public void init() {
        try {
            biasPrompts = mapper.readTree(new ClassPathResource("ai/bias_prompts.json").getInputStream());
            logger.info("Loaded bias prompts successfully.");
        } catch (Exception e) {
            logger.error("Failed to load bias_prompts.json", e);
        }
        
        try (InputStream is = new ClassPathResource("ai/mosco_knowledge.txt").getInputStream()) {
            gameKnowledge = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            logger.info("Loaded mosco_knowledge.txt successfully.");
        } catch (Exception e) {
            logger.error("Failed to load mosco_knowledge.txt", e);
        }
    }

    private String buildSystemInstruction(String biasId, Long userId, String language) {
        String langInstruction = "vi".equalsIgnoreCase(language)
                ? "You MUST answer COMPLETELY in natural, conversational Vietnamese. Keep the tone friendly, slightly playful, and use appropriate pronouns like 'mình' and 'bạn' or 'cậu'."
                : "You MUST answer COMPLETELY in English. Keep the tone friendly and slightly playful.";

        String systemInstruction = "You are an NPC idol in the Mosco game. DO NOT claim to be an AI. " +
                "[CRITICAL RULE]: System time is " + java.time.LocalDateTime.now() + ". Use this to calculate age, relative time, etc.\n" +
                "[CRITICAL LOGIC RULE]: To determine if someone is older, their birth year MUST be mathematically SMALLER than yours. If their birth year is LARGER, they are YOUNGER. (e.g. 2005 is older than 2006. 2008 is younger than 2006).\n" +
                "[CRITICAL RULE]: When asked about member ages, birth years, or who is older/younger, YOU MUST ONLY USE the TRIPLES MEMBER DEMOGRAPHICS ROSTER provided below. DO NOT GUESS OR INVENT YEARS.\n" +
                "[CRITICAL RULE]: " + langInstruction + " Answer concisely (max 5 sentences) using Markdown. " +
                "Strictly use information from [NEW KNOWLEDGE] only. Reply 'I don't know' if missing.";
        
        JsonNode bias = null;
        if (biasPrompts != null) {
            if (biasPrompts.has(biasId)) {
                bias = biasPrompts.get(biasId);
            } else {
                java.util.Iterator<String> fieldNames = biasPrompts.fieldNames();
                while (fieldNames.hasNext()) {
                    String key = fieldNames.next();
                    if (key.toLowerCase().contains(biasId.toLowerCase())) {
                        bias = biasPrompts.get(key);
                        break;
                    }
                }
            }
        }

        if (bias != null) {
            systemInstruction += "\n\nBỐI CẢNH GAME MOSCO:\n" + gameKnowledge + "\n\n" +
                                 "THÔNG TIN CỦA BẠN (BIAS):\n" +
                                 "Tên: " + bias.path("name").asText() + ".\n" +
                                 "Thân phận: Thành viên của tripleS. (Hãy dịch các thông tin này ra tiếng Anh khi chat)\n" +
                                 "Tính cách: " + bias.path("personality").asText() + ".\n" +
                                 "Câu cửa miệng: " + bias.path("catchphrase").asText() + ".\n\n";
            
            if (userId != null) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    String playerName = (user.getIngameName() != null && !user.getIngameName().isEmpty()) ? user.getIngameName() : user.getUsername();
                    systemInstruction += "Player Info: Name: " + playerName + ", Coins: " + user.getCoins() + ".\n";
                }
            }
        }
        
        return systemInstruction;
    }
    
    private List<Map<String, Object>> extractMessages(Map<String, Object> body) {
        List<Map<String, Object>> contentsList = new ArrayList<>();
        if (body.containsKey("messages")) {
            List<Map<String, String>> msgs = (List<Map<String, String>>) body.get("messages");
            for (Map<String, String> msg : msgs) {
                String role = msg.getOrDefault("role", "user");
                // The AI role from client will be 'model', which perfectly matches Gemini API
                String text = msg.getOrDefault("text", "");
                if (!text.isEmpty()) {
                    contentsList.add(Map.of("role", role, "parts", List.of(Map.of("text", text))));
                }
            }
        } else if (body.containsKey("message")) { // Fallback for old clients
            String message = (String) body.get("message");
            contentsList.add(Map.of("role", "user", "parts", List.of(Map.of("text", message))));
        }
        return contentsList;
    }

    private String getLatestMessage(List<Map<String, Object>> contentsList) {
        if (contentsList != null && !contentsList.isEmpty()) {
            Map<String, Object> lastMsg = contentsList.get(contentsList.size() - 1);
            List<Map<String, String>> parts = (List<Map<String, String>>) lastMsg.get("parts");
            if (parts != null && !parts.isEmpty()) {
                return parts.get(0).get("text");
            }
        }
        return "";
    }

    private double cosineSimilarity(List<Double> vecA, List<Double> vecB) {
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        int minSize = Math.min(vecA.size(), vecB.size());
        for (int i = 0; i < minSize; i++) {
            dotProduct += vecA.get(i) * vecB.get(i);
            normA += Math.pow(vecA.get(i), 2);
            normB += Math.pow(vecB.get(i), 2);
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<String>> chatWithAi(
            @RequestAttribute("userId") Long userId,
            @RequestBody Map<String, Object> body) {
        
        String biasId = (String) body.getOrDefault("biasId", "S1_Seoyeon");
        String language = (String) body.getOrDefault("language", "vi");
        List<Map<String, Object>> contentsList = extractMessages(body);
        
        if (contentsList.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Message cannot be empty"));
        }

        String systemInstruction = buildSystemInstruction(biasId, userId, language);
        systemInstruction = augmentSystemInstructionWithRag(systemInstruction, contentsList, biasId);

        logger.info("User {} chatting with AI Bias {}", userId, biasId);
        String responseVi = geminiApiService.generateContent(systemInstruction, contentsList);
        return ResponseEntity.ok(ApiResponse.success("Success", responseVi));
    @PostMapping(value = "/chat/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chatStreamWithAi(
            @RequestAttribute("userId") Long userId,
            @RequestBody Map<String, Object> body) {
        
        String biasId = (String) body.getOrDefault("biasId", "S1_Seoyeon");
        String language = (String) body.getOrDefault("language", "vi");
        List<Map<String, Object>> contentsList = extractMessages(body);
        
        if (contentsList.isEmpty()) {
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
            emitter.completeWithError(new IllegalArgumentException("Message cannot be empty"));
            return emitter;
        }

        String latestMsg = getLatestMessage(contentsList);
        List<Double> queryEmbedding = geminiApiService.embedQuery(latestMsg);
        
        // Semantic Caching
        if (!queryEmbedding.isEmpty()) {
            for (Map.Entry<List<Double>, String> entry : semanticCache.entrySet()) {
                if (cosineSimilarity(queryEmbedding, entry.getKey()) > 0.95) {
                    logger.info("Semantic Cache HIT for query: {}", latestMsg);
                    return streamingService.streamFakeTyping(entry.getValue());
                }
            }
        }

        String systemInstruction = buildSystemInstruction(biasId, userId, language);
        systemInstruction = augmentSystemInstructionWithRag(systemInstruction, contentsList, biasId);

        logger.info("User {} streaming AI Bias {} with Fake SSE", userId, biasId);
        
        // Generate Vietnamese directly from LLM, then stream fake typing
        return createStreamProcess(systemInstruction, contentsList, queryEmbedding);
    }
    
    private org.springframework.web.servlet.mvc.method.annotation.SseEmitter createStreamProcess(String systemInstruction, List<Map<String, Object>> contentsList, List<Double> queryEmbedding) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(60000L);
        new Thread(() -> {
            try {
                String responseVi = geminiApiService.generateContent(systemInstruction, contentsList);
                
                if (!queryEmbedding.isEmpty() && responseVi != null && !responseVi.isEmpty()) {
                    // limit cache size
                    if (semanticCache.size() > 500) semanticCache.clear();
                    semanticCache.put(queryEmbedding, responseVi);
                }
                
                java.util.StringTokenizer tokenizer = new java.util.StringTokenizer(responseVi, " \n\r\t.,!?:;", true);
                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken();
                    if (!token.isEmpty()) {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(Map.of("text", token), org.springframework.http.MediaType.APPLICATION_JSON));
                        Thread.sleep(30);
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    private String augmentSystemInstructionWithRag(String baseInstruction, List<Map<String, Object>> contentsList, String biasId) {
        String latestUserMsg = getLatestMessage(contentsList);
        if (latestUserMsg.isEmpty()) return baseInstruction;

        List<VectorDocument> relevantDocs = new ArrayList<>();
        
        // Intent Router: Condition = (contains comparison words) AND (contains S1-S24 or tripleS)
        boolean isCompare = containsKeyword(latestUserMsg, "so sánh", "khác nhau", "và", "vs", "ai hơn", "compare", "difference");
        boolean hasMultipleEntities = latestUserMsg.matches("(?i).*(S\\d{1,2}|tripleS).*(S\\d{1,2}|tripleS).*");

        if (isCompare && hasMultipleEntities) {
            logger.info("Complex Query routing triggered: {}", latestUserMsg);
            String decompPrompt = "Break this query into 2 or 3 short English search queries for a database. Output ONLY JSON array of strings: [\"q1\", \"q2\"]. Query: " + latestUserMsg;
            String jsonQueries = geminiApiService.forceGeminiGenerateContent("", decompPrompt);
            try {
                JsonNode arr = mapper.readTree(jsonQueries);
                if (arr.isArray()) {
                    List<CompletableFuture<List<VectorDocument>>> futures = new ArrayList<>();
                    for (JsonNode qNode : arr) {
                        String q = qNode.asText();
                        futures.add(CompletableFuture.supplyAsync(() -> {
                            List<Double> qEmb = geminiApiService.embedQuery(q);
                            return vectorStoreService.search(qEmb, 2);
                        }));
                    }
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    for (CompletableFuture<List<VectorDocument>> f : futures) {
                        for (VectorDocument doc : f.get()) {
                            if (relevantDocs.stream().noneMatch(d -> d.getId().equals(doc.getId()))) {
                                relevantDocs.add(doc);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error in query decomposition: {}", e.getMessage());
            }
        } else {
            // Direct Route with Multi-Query Retrieval
            // Query 1: Optimized query anchored to the Idol (for personal questions like "What is your height?")
            String optimizedQuery = translationService.optimizeQueryForRag(latestUserMsg, biasId);
            logger.info("Direct RAG search query (Optimized): {}", optimizedQuery);
            
            CompletableFuture<List<VectorDocument>> f1 = CompletableFuture.supplyAsync(() -> {
                List<Double> emb = geminiApiService.embedQuery(optimizedQuery);
                return vectorStoreService.search(emb, 3);
            });
            
            // Query 2: Raw user query (for world questions like "What about album LOVE&POP?")
            CompletableFuture<List<VectorDocument>> f2 = CompletableFuture.supplyAsync(() -> {
                List<Double> emb = geminiApiService.embedQuery(latestUserMsg);
                return vectorStoreService.search(emb, 3);
            });
            
            try {
                CompletableFuture.allOf(f1, f2).join();
                for (VectorDocument doc : f1.get()) {
                    if (relevantDocs.stream().noneMatch(d -> d.getId().equals(doc.getId()))) relevantDocs.add(doc);
                }
                for (VectorDocument doc : f2.get()) {
                    if (relevantDocs.stream().noneMatch(d -> d.getId().equals(doc.getId()))) relevantDocs.add(doc);
                }
            } catch (Exception e) {
                logger.error("Error in multi-query RAG: {}", e.getMessage());
            }
            // Hybrid Search Heuristic: Explicitly inject the newest album for context
            String lowerMsg = latestUserMsg.toLowerCase();
            if (lowerMsg.contains("album") || lowerMsg.contains("nhạc") || lowerMsg.contains("bài hát") || lowerMsg.contains("comeback") || lowerMsg.contains("song")) {
                List<VectorDocument> albumDocs = vectorStoreService.getDocumentsByPagePrefix("/musicalbum/");
                if (!albumDocs.isEmpty()) {
                    // Sort descending by timestamp (Release Date)
                    albumDocs.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                    VectorDocument newestAlbum = albumDocs.get(0);
                    
                    if (relevantDocs.stream().noneMatch(d -> d.getId().equals(newestAlbum.getId()))) {
                        relevantDocs.add(0, newestAlbum); // Pin to top
                        logger.info("Hybrid Search: Pinned newest album {} to context", newestAlbum.getPageName());
                        
                        // Soft hint to guide the LLM without aggressively overriding it
                        baseInstruction += "\n[LƯU Ý NGỮ CẢNH]: Thông tin về Album mới nhất (nếu có) luôn nằm ở tài liệu đầu tiên bên trên. Hãy ưu tiên nhắc đến nó nếu User hỏi về các hoạt động mới/gần đây.";
                    }
                }
            }
        }

        if (!relevantDocs.isEmpty()) {
            StringBuilder ragContext = new StringBuilder("\n\n[NEW KNOWLEDGE FROM KPOPPING WIKI]\n");
            for (VectorDocument doc : relevantDocs) {
                ragContext.append("- ").append(doc.getContent()).append("\n");
            }
            logger.info("RAG context INJECTED with {} docs", relevantDocs.size());
            return ragContext.toString() + "\n" + baseInstruction;
        }

        return baseInstruction;
    }

    private boolean containsKeyword(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
