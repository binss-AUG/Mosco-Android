package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.model.VectorDocument;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class VectorStoreService {
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreService.class);
    private static final String STORAGE_PATH = "storage/wiki_vectors.json";
    private final ObjectMapper mapper = new ObjectMapper();
    private List<VectorDocument> documents = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        File file = new File(STORAGE_PATH);
        if (file.exists()) {
            try {
                List<VectorDocument> loadedDocs = mapper.readValue(file, new TypeReference<List<VectorDocument>>(){});
                documents = new CopyOnWriteArrayList<>(loadedDocs);
                logger.info("Loaded {} vector documents from disk.", documents.size());
            } catch (Exception e) {
                logger.error("Failed to load vector store", e);
            }
        } else {
            file.getParentFile().mkdirs();
        }
    }

    public void saveDocuments(List<VectorDocument> newDocs) {
        this.documents = new CopyOnWriteArrayList<>(newDocs);
        saveToDisk();
    }

    public List<VectorDocument> getDocumentsByPagePrefix(String prefix) {
        return documents.stream()
                .filter(doc -> doc.getPageName() != null && doc.getPageName().startsWith(prefix))
                .collect(Collectors.toList());
    }

    public void replaceDocumentsByPage(String pageName, List<VectorDocument> newDocs) {
        documents.removeIf(doc -> pageName.equals(doc.getPageName()));
        documents.addAll(newDocs);
        logger.info("Replaced documents for page '{}': {} chunks (total: {})", pageName, newDocs.size(), documents.size());
        saveToDisk();
    }

    private void saveToDisk() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(STORAGE_PATH), documents);
            logger.info("Saved {} vector documents to disk.", documents.size());
        } catch (Exception e) {
            logger.error("Failed to save vector store", e);
        }
    }

    public List<VectorDocument> search(List<Double> queryEmbedding, int topK) {
        return searchWithPreFilter(queryEmbedding, topK, null);
    }

    public List<VectorDocument> searchWithPreFilter(List<Double> queryEmbedding, int topK, Map<String, String> requiredMetadata) {
        if (documents.isEmpty() || queryEmbedding == null || queryEmbedding.isEmpty()) {
            return new ArrayList<>();
        }

        return documents.stream()
                .filter(doc -> doc.getEmbedding() != null && !doc.getEmbedding().isEmpty())
                .filter(doc -> {
                    if (requiredMetadata == null || requiredMetadata.isEmpty()) return true;
                    if (doc.getMetadata() == null) return false;
                    for (Map.Entry<String, String> entry : requiredMetadata.entrySet()) {
                        if (!entry.getValue().equals(doc.getMetadata().get(entry.getKey()))) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparingDouble((VectorDocument doc) -> {
                    double sim = cosineSimilarity(queryEmbedding, doc.getEmbedding());
                    if (doc.getTimestamp() > 0) {
                        long ageMs = System.currentTimeMillis() - doc.getTimestamp();
                        double ageDays = ageMs / (1000.0 * 60 * 60 * 24);
                        // Time Decay Boost: Max +0.15 for brand new, scales down to 0 over 3 years
                        double timeBoost = Math.max(0.0, 0.15 - (ageDays / (365.0 * 3)) * 0.15);
                        sim += timeBoost;
                    }
                    return sim;
                }).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(List<Double> vecA, List<Double> vecB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        int minSize = Math.min(vecA.size(), vecB.size());
        for (int i = 0; i < minSize; i++) {
            dotProduct += vecA.get(i) * vecB.get(i);
            normA += Math.pow(vecA.get(i), 2);
            normB += Math.pow(vecB.get(i), 2);
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
