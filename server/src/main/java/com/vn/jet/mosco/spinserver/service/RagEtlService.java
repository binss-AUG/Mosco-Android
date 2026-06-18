package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.VectorDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import java.util.UUID;

@Service
public class RagEtlService {
    private static final Logger logger = LoggerFactory.getLogger(RagEtlService.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final VectorStoreService vectorStoreService;
    private final GeminiApiService geminiApiService;

    @Value("${rag.sidecar.url:http://localhost:5001}")
    private String sidecarUrl;

    private static final String[] PAGES = {
        "/profiles/group/tripleS",
        "/profiles/idol/YooYeon", "/profiles/idol/Mayu", "/profiles/idol/Xinyu", "/profiles/idol/NaKyoung", "/profiles/idol/SoHyun3", "/profiles/idol/DaHyun4", 
        "/profiles/idol/Nien", "/profiles/idol/SeoYeon", "/profiles/idol/JiYeon5", "/profiles/idol/Kotone", "/profiles/idol/ChaeYeon", "/profiles/idol/YuBin7", 
        "/profiles/idol/JiWoo11", "/profiles/idol/Kaede", "/profiles/idol/ShiOn2", "/profiles/idol/Lynn2", "/profiles/idol/Sullin", "/profiles/idol/HyeRin2", 
        "/profiles/idol/ChaeWon2", "/profiles/idol/HaYeon6", "/profiles/idol/SooMin4", "/profiles/idol/YeonJi3", "/profiles/idol/JooBin", "/profiles/idol/SeoAh2",
        "/profiles/group/Visionary-Vision", "/profiles/group/EVOLution", "/profiles/group/moon", "/profiles/group/LOVElution", 
        "/profiles/group/Glow", "/profiles/group/neptune", "/profiles/group/ACID-EYES", "/profiles/group/zenith", "/profiles/group/NXT", 
        "/profiles/group/krystal-eyes", "/profiles/group/Hatchi", "/profiles/group/sun", "/profiles/group/Alphie", "/profiles/group/Acid-Angel-from-Asia",
        "/musicalbum/2026-baby-flower-city-remixes",
        "/musicalbum/ASSEMBLE26",
        "/musicalbum/LOVElution-muhan",
        "/musicalbum/EVOLution-mujook",
        "/musicalbum/A-S-S-E-M-B-L-E",
        "/musicalbum/ACCESS",
        "/musicalbum/BINARY-01",
        "/community"
    };

    public RagEtlService(VectorStoreService vectorStoreService, GeminiApiService geminiApiService) {
        this.vectorStoreService = vectorStoreService;
        this.geminiApiService = geminiApiService;
    }

    @Async
    @Scheduled(cron = "0 0 3 * * SUN")
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void runEtl() {
        logger.info("Starting Mosco Project ETL Pipeline...");
        indexProjectContext();
        logger.info("Total pages to fetch: {}", PAGES.length);

        for (String page : PAGES) {
            try {
                logger.info("Fetching page: {}", page);
                String url = sidecarUrl + "/fetch?page=" + page;
                org.springframework.http.ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);

                if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                    logger.error("Sidecar returned {} for page {}", responseEntity.getStatusCode(), page);
                    continue;
                }

                String response = responseEntity.getBody();
                JsonNode root = mapper.readTree(response);
                if (root.has("content")) {
                    String plainText = root.path("content").asText();
                    String pageTitle = root.has("title") ? root.path("title").asText() : page;

                    String prefix = "[Source: " + pageTitle + "] - ";
                    int maxChunkLen = page.contains("/musicalbum/") ? 1500 : 800;
                    List<String> chunks = chunkText(plainText, maxChunkLen, 150, prefix);
                    List<VectorDocument> pageDocs = new ArrayList<>();

                    Map<String, String> metadata = new HashMap<>();
                    if (page.startsWith("/profiles/idol/")) {
                        metadata.put("entity_type", "member");
                    } else if (page.startsWith("/profiles/group/")) {
                        metadata.put("entity_type", "group");
                    } else if (page.startsWith("/musicalbum/")) {
                        metadata.put("entity_type", "album");
                    }

                    for (int i = 0; i < chunks.size(); i++) {
                        String chunk = chunks.get(i);
                        if (chunk.length() < 50) continue;

                        // Enhanced tagging for Awards/Wins
                        if (chunk.contains("Music Show Wins") || chunk.contains("Award") || chunk.contains("Cup") || chunk.contains("First Win")) {
                            chunk = "[ACHIEVEMENT DATA] " + chunk;
                        }

                        logger.info("Embedding chunk {}/{} for page {}", i + 1, chunks.size(), page);
                        List<Double> embedding = geminiApiService.embedPassage(chunk);

                        if (!embedding.isEmpty()) {
                            VectorDocument doc = new VectorDocument(
                                    UUID.randomUUID().toString(),
                                    pageTitle,
                                    chunk,
                                    embedding
                            );
                            doc.setPageName(page);
                            doc.setMetadata(metadata);
                            
                            // Extract Release Date
                            java.util.regex.Pattern p = java.util.regex.Pattern.compile("Release Date\\s+(\\d{4}-\\d{2}-\\d{2})");
                            java.util.regex.Matcher m = p.matcher(plainText);
                            if (m.find()) {
                                try {
                                    java.time.LocalDate date = java.time.LocalDate.parse(m.group(1));
                                    long epoch = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
                                    doc.setTimestamp(epoch);
                                    logger.info("Found Release Date {} for chunk {}", m.group(1), page);
                                } catch (Exception ex) {
                                    logger.warn("Failed to parse release date: {}", m.group(1));
                                }
                            }
                            
                            pageDocs.add(doc);
                        }

                        Thread.sleep(500);
                    }

                    if (!pageDocs.isEmpty()) {
                        vectorStoreService.replaceDocumentsByPage(page, pageDocs);
                        logger.info("Saved {} chunks for page {}", pageDocs.size(), page);
                    }
                } else {
                    logger.warn("Page {} has no content in response", page);
                }
            } catch (Exception e) {
                logger.error("Error processing page {}", page, e);
            }
        }

        logger.info("ETL Pipeline finished.");
    }

    private void indexProjectContext() {
        logger.info("Starting Internal Project Context Indexing...");
        try {
            List<VectorDocument> projectDocs = new ArrayList<>();
            Map<String, String> metadata = new HashMap<>();
            metadata.put("entity_type", "project_context");

            // 1. Index README.md for high-level overview
            indexFileContent("README.md", "Project Overview", projectDocs, metadata);

            // 2. Index Backend Architecture
            indexFileContent("01_backend_architecture.md", "Backend Architecture & Schema", projectDocs, metadata);

            // 3. Index Features & Use Cases
            indexFileContent("02_features_and_usecases.md", "Features & User Journeys", projectDocs, metadata);

            // 4. Index Detailed Design
            indexFileContent("03_detailed_design_algorithms.md", "Detailed Design & Algorithms", projectDocs, metadata);

            // 5. Index directory structure summary
            String dirStructure = "PROJECT DIRECTORY STRUCTURE & ACCESS POINTS:\n" +
                    "- Project Name: Mosco (Gacha Game App).\n" +
                    "- UI NAVIGATION GUIDE (OFFICIAL):\n" +
                    "  + SHOP (CỬA HÀNG): HOME screen -> TOP-RIGHT corner -> Tap THREE DOTS (⋮) icon -> Select 'Shop' (🛒 icon).\n" +
                    "  + SPIN (GACHA): BOTTOM navigation bar -> CENTER (Large Blue Pill button).\n" +
                    "  + UPGRADE (NÂNG CẤP): BOTTOM navigation bar -> 2nd tab from the LEFT.\n" +
                    "  + COLLECTION (KHO ĐỒ): BOTTOM navigation bar -> 4th tab from the LEFT.\n" +
                    "  + PROFILE (HỒ SƠ): BOTTOM navigation bar -> 5th tab (FAR RIGHT).\n" +
                    "  + HOME (TRANG CHỦ): BOTTOM navigation bar -> 1st tab (FAR LEFT).\n" +
                    "  + DAILY CHECK-IN (ĐIỂM DANH): HOME screen dashboard -> LEFT column -> 2nd box.\n" +
                    "  + AFK STAGES (THÁM HIỂM): HOME screen dashboard -> LEFT column -> BOTTOM box.\n" +
                    "  + RANKING (XẾP HẠNG): HOME screen dashboard -> RIGHT side (Large vertical box).\n" +
                    "- /client: Android Native (Java) source code.\n" +
                    "- /server: Spring Boot (Java 21) backend source code.";
            
            projectDocs.addAll(createChunksFromText(dirStructure, "Directory Structure", "/project/structure", metadata));

            if (!projectDocs.isEmpty()) {
                vectorStoreService.replaceDocumentsByPage("/project/context", projectDocs);
                logger.info("Indexed {} project context chunks.", projectDocs.size());
            }
        } catch (Exception e) {
            logger.error("Failed to index project context", e);
        }
    }

    private void indexFileContent(String filePath, String title, List<VectorDocument> targetList, Map<String, String> metadata) {
        try {
            java.io.File file = new java.io.File(filePath);
            if (file.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                targetList.addAll(createChunksFromText(content, title, "/" + filePath, metadata));
            }
        } catch (Exception e) {
            logger.warn("Failed to index file {}: {}", filePath, e.getMessage());
        }
    }

    private List<VectorDocument> createChunksFromText(String text, String title, String pageName, Map<String, String> metadata) {
        List<VectorDocument> docs = new ArrayList<>();
        List<String> chunks = chunkText(text, 1200, 200, "[Project Context: " + title + "] - ");
        for (String chunk : chunks) {
            List<Double> embedding = geminiApiService.embedPassage(chunk);
            if (!embedding.isEmpty()) {
                VectorDocument doc = new VectorDocument(UUID.randomUUID().toString(), title, chunk, embedding);
                doc.setPageName(pageName);
                doc.setMetadata(metadata);
                doc.setTimestamp(System.currentTimeMillis());
                docs.add(doc);
            }
        }
        return docs;
    }

    private List<String> chunkText(String text, int maxLen, int overlap, String prefix) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder currentChunk = new StringBuilder(prefix);

        for (String p : paragraphs) {
            p = p.trim();
            if (p.isEmpty()) continue;
            
            if (currentChunk.length() + p.length() + 2 > maxLen) {
                if (currentChunk.length() > prefix.length()) {
                    chunks.add(currentChunk.toString().trim());
                    String prevText = currentChunk.toString();
                    int overlapStart = Math.max(prefix.length(), prevText.length() - overlap);
                    // attempt to find a natural break near overlapStart
                    int breakPoint = prevText.indexOf('\n', overlapStart);
                    if (breakPoint == -1) breakPoint = overlapStart;
                    currentChunk = new StringBuilder(prefix + prevText.substring(breakPoint).trim() + "\n\n" + p + "\n\n");
                } else {
                    currentChunk.append(p).append("\n\n");
                }
            } else {
                currentChunk.append(p).append("\n\n");
            }
        }
        if (currentChunk.length() > prefix.length()) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks;
    }
}
