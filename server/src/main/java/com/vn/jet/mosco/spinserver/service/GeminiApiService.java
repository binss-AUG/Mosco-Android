package com.vn.jet.mosco.spinserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class GeminiApiService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiApiService.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.openrouter.api-key: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}")
    private String openRouterApiKey;

    @Value("${ai.gemini.fallback-message:Thôi chết tớ bận quá, mạng mẽo bị gì rồi nè, để lát tớ nhắn lại nha!}")
    private String fallbackMessage;

    @Value("${rag.sidecar.url:http://localhost:5001}")
    private String sidecarUrl;

    private final Map<String, List<Double>> embeddingCache = new ConcurrentHashMap<>();

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";
    private static final String GEMINI_EMBED_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2:embedContent?key=";
    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String OPENROUTER_MODEL = "openrouter/auto"; // Use auto routing for best stability

    private List<Map<String, Object>> convertToOpenRouterMessages(String systemInstruction, List<Map<String, Object>> contentsList) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemInstruction != null && !systemInstruction.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemInstruction));
        }

        for (Map<String, Object> content : contentsList) {
            String role = (String) content.get("role");
            if ("model".equals(role)) role = "assistant";

            List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
            if (parts != null && !parts.isEmpty()) {
                String text = parts.get(0).get("text");
                messages.add(Map.of("role", role, "content", text));
            }
        }
        return messages;
    }

    public List<Double> embedQuery(String text) {
        // For user queries — prepend "query: " prefix (required by multilingual-e5-small)
        return embedText("query: " + text);
    }

    public List<Double> embedPassage(String text) {
        // For document chunks — prepend "passage: " prefix (required by multilingual-e5-small)
        return embedText("passage: " + text);
    }

    public List<Double> embedText(String text) {
        if (text == null || text.trim().length() < 10) {
            return new ArrayList<>();
        }
        String cacheKey = text.trim().toLowerCase();
        List<Double> cached = embeddingCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Double> local = embedTextLocal(text);
        if (local != null) {
            embeddingCache.put(cacheKey, local);
            return local;
        }

        // Fallback: Gemini Embed API is disabled because its vectors (768-dim) 
        // are incompatible with the database's MiniLM vectors (384-dim).
        logger.error("CRITICAL: Local embed failed on port 5001. Please make sure the Python Sidecar is running! RAG will be skipped.");
        return new ArrayList<>();
    }

    private List<Double> embedTextLocal(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("text", text), headers);
            String url = sidecarUrl + "/embed";
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode valuesNode = response.getBody().path("embedding");
                if (valuesNode.isArray()) {
                    List<Double> vector = new ArrayList<>();
                    for (JsonNode val : valuesNode) {
                        vector.add(val.asDouble());
                    }
                    logger.debug("Local embed success for text ({} chars)", text.length());
                    return vector;
                }
            }
        } catch (Exception e) {
            logger.warn("Local embed failed (will fallback to Gemini): {}", e.getMessage());
        }
        return null;
    }

    private List<Double> embedTextGemini(String text, String cacheKey) {
        int maxRetries = 3;
        int retryDelayMs = 60000;
        Exception lastEx = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", "models/gemini-embedding-2");
                requestBody.put("content", Map.of("parts", List.of(Map.of("text", text))));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_EMBED_API_URL + geminiApiKey, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode valuesNode = root.path("embedding").path("values");
                    if (valuesNode.isArray()) {
                        List<Double> vector = new ArrayList<>();
                        for (JsonNode val : valuesNode) {
                            vector.add(val.asDouble());
                        }
                        embeddingCache.put(cacheKey, vector);
                        return vector;
                    }
                }
                return new ArrayList<>();
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                lastEx = e;
                logger.warn("Gemini Embed 429 (attempt {}/{}). Waiting 60s...", attempt, maxRetries);
                if (attempt < maxRetries) {
                    try { Thread.sleep(retryDelayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            } catch (Exception e) {
                logger.error("Gemini Embed API Error: {}", e.getMessage());
                return new ArrayList<>();
            }
        }
        logger.error("Gemini Embed API failed after {} retries: {}", maxRetries, lastEx.getMessage());
        return new ArrayList<>();
    }

    public String generateContent(String systemInstruction, List<Map<String, Object>> contentsList) {
        // LAYER 1: OpenRouter (Gemma-4)
        try {
            List<Map<String, Object>> messages = convertToOpenRouterMessages(systemInstruction, contentsList);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", OPENROUTER_MODEL);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.1);
            requestBody.put("top_p", 0.1);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openRouterApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(OPENROUTER_API_URL, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("choices").get(0).path("message").path("content").asText();
            } else {
                logger.warn("OpenRouter API returned status: {}. Falling back to Gemini...", response.getStatusCode());
            }
        } catch (Exception e) {
            logger.warn("OpenRouter API Error: {}. Falling back to Gemini...", e.getMessage());
        }

        // LAYER 2: Fallback to Google Gemini
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || geminiApiKey.equals("CHƠ_BẠN_NHẬP_VÀO_SÁNG_MAI")) {
            logger.warn("Gemini API Key is missing. Returning fallback.");
            return fallbackMessage;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            if (systemInstruction != null && !systemInstruction.isEmpty()) {
                requestBody.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
            }
            requestBody.put("contents", contentsList);
            requestBody.put("generationConfig", Map.of("temperature", 0.1, "topP", 0.1));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_API_URL + geminiApiKey, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            }
        } catch (Exception e) {
            logger.error("Gemini API Error: {}", e.getMessage());
        }

        return fallbackMessage;
    }

    public String forceGeminiGenerateContent(String systemInstruction, String prompt) {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || geminiApiKey.equals("CHƠ_BẠN_NHẬP_VÀO_SÁNG_MAI")) {
            return fallbackMessage;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            if (systemInstruction != null && !systemInstruction.isEmpty()) {
                requestBody.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
            }
            requestBody.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));
            requestBody.put("generationConfig", Map.of("temperature", 0.1, "topP", 0.1));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_API_URL + geminiApiKey, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            }
        } catch (Exception e) {
            logger.error("Force Gemini API Error: {}", e.getMessage());
        }
        return fallbackMessage;
    }

    public String forceGeminiGenerateContentSilent(String systemInstruction, String prompt) {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || geminiApiKey.equals("CHƠ_BẠN_NHẬP_VÀO_SÁNG_MAI")) {
            return null;
        }
        try {
            Map<String, Object> requestBody = new HashMap<>();
            if (systemInstruction != null && !systemInstruction.isEmpty()) {
                requestBody.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
            }
            requestBody.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));
            requestBody.put("generationConfig", Map.of("temperature", 0.1, "topP", 0.1));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_API_URL + geminiApiKey, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            }
        } catch (Exception e) {
            logger.error("Silent Gemini API Error: {}", e.getMessage());
        }
        return null;
    }

    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamGenerateContent(String systemInstruction, List<Map<String, Object>> contentsList) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(60000L); // 60s timeout
        
        new Thread(() -> {
            boolean success = tryOpenRouterStream(emitter, systemInstruction, contentsList);
            if (!success) {
                logger.warn("OpenRouter stream failed. Falling back to Gemini...");
                boolean geminiSuccess = tryGeminiStream(emitter, systemInstruction, contentsList);
                if (!geminiSuccess) {
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .data(Map.of("text", fallbackMessage), MediaType.APPLICATION_JSON));
                        emitter.complete();
                    } catch (Exception e) {}
                }
            }
        }).start();

        return emitter;
    }

    private boolean tryOpenRouterStream(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter, String systemInstruction, List<Map<String, Object>> contentsList) {
        try {
            List<Map<String, Object>> messages = convertToOpenRouterMessages(systemInstruction, contentsList);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", OPENROUTER_MODEL);
            requestBody.put("messages", messages);
            requestBody.put("stream", true);
            requestBody.put("temperature", 0.1);
            requestBody.put("top_p", 0.1);

            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(OPENROUTER_API_URL)
                    .post(okhttp3.RequestBody.create(
                            objectMapper.writeValueAsString(requestBody),
                            okhttp3.MediaType.parse("application/json")
                    ))
                    .addHeader("Authorization", "Bearer " + openRouterApiKey)
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return false;
                }
                
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.body().byteStream()));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (data.isEmpty() || data.equals("[DONE]")) continue;
                        
                        try {
                            JsonNode root = objectMapper.readTree(data);
                            JsonNode choicesNode = root.path("choices");
                            if (choicesNode.isArray() && choicesNode.size() > 0) {
                                JsonNode deltaNode = choicesNode.get(0).path("delta").path("content");
                                if (!deltaNode.isMissingNode()) {
                                    String textChunk = deltaNode.asText();
                                    if (textChunk != null && !textChunk.isEmpty()) {
                                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                            .data(Map.of("text", textChunk), MediaType.APPLICATION_JSON));
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            // ignore parse error for stream
                        }
                    }
                }
                emitter.complete();
                return true;
            }
        } catch (Exception e) {
            logger.warn("Error in tryOpenRouterStream: {}", e.getMessage());
            return false;
        }
    }

    private boolean tryGeminiStream(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter, String systemInstruction, List<Map<String, Object>> contentsList) {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || geminiApiKey.equals("CHƠ_BẠN_NHẬP_VÀO_SÁNG_MAI")) {
            return false;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            if (systemInstruction != null && !systemInstruction.isEmpty()) {
                requestBody.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
            }
            requestBody.put("contents", contentsList);
            requestBody.put("generationConfig", Map.of("temperature", 0.1, "topP", 0.1));

            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            String streamUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=" + geminiApiKey;
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(streamUrl)
                    .post(okhttp3.RequestBody.create(
                            objectMapper.writeValueAsString(requestBody),
                            okhttp3.MediaType.parse("application/json")
                    ))
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return false;
                }
                
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.body().byteStream()));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (data.isEmpty() || data.equals("[DONE]")) continue;
                        
                        try {
                            JsonNode root = objectMapper.readTree(data);
                            JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
                            if (!textNode.isMissingNode()) {
                                String textChunk = textNode.asText();
                                if (textChunk != null && !textChunk.isEmpty()) {
                                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                        .data(Map.of("text", textChunk), MediaType.APPLICATION_JSON));
                                }
                            }
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
                }
                emitter.complete();
                return true;
            }
        } catch (Exception e) {
            logger.error("Error in tryGeminiStream: {}", e.getMessage());
            return false;
        }
    }
}
