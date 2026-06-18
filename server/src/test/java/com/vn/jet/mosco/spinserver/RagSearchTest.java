package com.vn.jet.mosco.spinserver;

import com.vn.jet.mosco.spinserver.service.GeminiApiService;
import com.vn.jet.mosco.spinserver.service.VectorStoreService;
import com.vn.jet.mosco.spinserver.model.VectorDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RagSearchTest {

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private GeminiApiService geminiApiService;

    private void assertSearchContains(String query, String expectedContent) {
        String searchText = "tripleS " + query;
        System.out.println("\n=== Search: '" + searchText + "' expecting: '" + expectedContent + "' ===");
        List<Double> embedding = geminiApiService.embedQuery(searchText);
        assertNotNull(embedding, "Embedding should not be null for: " + searchText);
        assertFalse(embedding.isEmpty(), "Embedding should not be empty for: " + searchText);

        List<VectorDocument> results = vectorStoreService.search(embedding, 10);
        assertFalse(results.isEmpty(), "Search should return results for: " + searchText);
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (VectorDocument doc : results) {
            String snippet = doc.getContent().length() > 150 ? doc.getContent().substring(0, 150) : doc.getContent();
            System.out.println("  [" + doc.getPageName() + "] " + snippet + "...");
            sb.append("[").append(doc.getPageName()).append("] ");
            if (doc.getContent().toLowerCase().contains(expectedContent.toLowerCase())) {
                found = true;
            }
        }
        assertTrue(found, "Expected '" + expectedContent + "' in search results (top10), got pages: " + sb.toString());
    }

    @Test
    public void testSearchLatestAlbum_vi() {
        assertSearchContains("album mới nhất là gì", "LOVE&POP");
    }

    @Test
    public void testSearchLatestAlbum_en() {
        assertSearchContains("what is the latest album 2026", "ASSEMBLE26");
    }

    @Test
    public void testSearchBabyFlower() {
        assertSearchContains("Baby Flower bài hát", "Baby Flower");
    }

    @Test
    public void testSearchMemberCount() {
        assertSearchContains("có bao nhiêu thành viên", "24");
    }

    @Test
    public void testSearchGroupNameMeaning() {
        assertSearchContains("viết tắt của từ gì", "Social Sonyo Seoul");
    }

    @Test
    public void testSearchDebutDate() {
        assertSearchContains("debut ngày nào", "2023");
    }

    @Test
    public void testSearchDebutAlbum() {
        assertSearchContains("album debut", "ASSEMBLE");
    }

    @Test
    public void testSearchLatestSubUnit() {
        assertSearchContains("sub-unit mới nhất", "Alphie");
    }

    @Test
    public void testSearchMemberS16() {
        assertSearchContains("S16 là ai", "Mayu");
    }

    @Test
    public void testSearchAssemble25() {
        assertSearchContains("ASSEMBLE25 có bao nhiêu bài", "10");
    }

    @Test
    public void testSearchOT24() {
        assertSearchContains("OT24 là gì", "24");
    }

    @Test
    public void testSearchFallbackQuery() {
        assertSearchContains("LOVE&POP pt.1 ASSEMBLE26 2026 latest album", "LOVE&POP");
    }

}
