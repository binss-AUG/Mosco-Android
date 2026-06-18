package com.vn.jet.mosco.spinserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TranslationService {
    private static final Logger logger = LoggerFactory.getLogger(TranslationService.class);
    private final GeminiApiService geminiApiService;

    public TranslationService(GeminiApiService geminiApiService) {
        this.geminiApiService = geminiApiService;
    }

    public String translateToVietnamese(String englishText) {
        if (englishText == null || englishText.trim().isEmpty()) {
            return "";
        }

        logger.info("Translating response to Vietnamese (length: {})", englishText.length());
        
        String systemPrompt = "You are a professional translator for a K-pop idol chatbot game. " +
                "Translate the following English text to natural, conversational Vietnamese. " +
                "Keep the tone friendly, slightly playful, and use appropriate pronouns like 'mình' and 'bạn' or 'cậu'. " +
                "Do NOT add any extra commentary or notes. Just output the translated text.";

        String translated = geminiApiService.forceGeminiGenerateContent(systemPrompt, englishText);
        
        if (translated == null || translated.trim().isEmpty()) {
            logger.warn("Translation failed, returning original English text.");
            return englishText;
        }

        return translated.trim();
    }

    public String optimizeQueryForRag(String queryVi, String biasName, java.util.List<java.util.Map<String, Object>> contentsList) {
        if (queryVi == null || queryVi.trim().isEmpty()) {
            return "";
        }

        // Detect pronouns that require context
        boolean needsContext = queryVi.matches("(?i).*(này|đó|kia|ấy|vậy|nó|họ|nhóm|album|bài).*");
        
        if (needsContext && contentsList != null && contentsList.size() > 1) {
            logger.info("Context-aware query optimization triggered for: {}", queryVi);
            
            // Extract the last 2 turns (User & AI) to resolve pronouns
            StringBuilder context = new StringBuilder("Conversation history:\n");
            int start = Math.max(0, contentsList.size() - 3); // last 3 items
            for (int i = start; i < contentsList.size() - 1; i++) {
                java.util.Map<String, Object> turn = contentsList.get(i);
                String role = (String) turn.get("role");
                java.util.List<java.util.Map<String, String>> parts = (java.util.List<java.util.Map<String, String>>) turn.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    context.append(role).append(": ").append(parts.get(0).get("text")).append("\n");
                }
            }

            String prompt = "You are a professional search query optimizer for a K-pop Wiki (tripleS). " +
                    "Analyze the conversation history and rewrite the LATEST USER MESSAGE into a detailed standalone English search query. " +
                    "CRITICAL: If the user says 'this album', 'it', or 'the group' and the previous AI message mentioned a specific album like 'LOVE&POP' or 'ASSEMBLE26', you MUST include that specific name in the rewritten query. " +
                    "If the user asks about awards/wins/achievements, include keywords like 'wins', 'awards', 'music show', 'first win'. " +
                    "\nLatest User Message: " + queryVi + "\n" +
                    "Output ONLY the optimized search query string.";
            
            String optimized = geminiApiService.forceGeminiGenerateContentSilent("You are a search query optimizer.", context.toString() + "\n" + prompt);
            if (optimized != null && !optimized.trim().isEmpty()) {
                logger.info("Resolved query: {} -> {}", queryVi, optimized.trim());
                return optimized.trim();
            }
        }

        // Fallback for simple queries or when context resolution fails
        return "tripleS " + biasName + " " + queryVi;
    }
}
