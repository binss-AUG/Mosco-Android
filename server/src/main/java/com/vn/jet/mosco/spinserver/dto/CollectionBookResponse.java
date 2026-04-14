package com.vn.jet.mosco.spinserver.dto;

import java.util.List;

/**
 * Response tổng hợp cho API Bộ Sưu Tập (Collection Book).
 * Chứa tiến trình thu thập và danh sách toàn bộ thẻ bài.
 */
public class CollectionBookResponse {

    private int totalCards;    // Tổng số thẻ trong hệ thống
    private int ownedCount;    // Số thẻ user đã sở hữu (không trùng collectionId)
    private List<CollectionEntry> entries; // Danh sách toàn bộ thẻ

    public CollectionBookResponse() {}

    public CollectionBookResponse(int totalCards, int ownedCount, List<CollectionEntry> entries) {
        this.totalCards = totalCards;
        this.ownedCount = ownedCount;
        this.entries = entries;
    }

    public int getTotalCards() { return totalCards; }
    public void setTotalCards(int totalCards) { this.totalCards = totalCards; }

    public int getOwnedCount() { return ownedCount; }
    public void setOwnedCount(int ownedCount) { this.ownedCount = ownedCount; }

    public List<CollectionEntry> getEntries() { return entries; }
    public void setEntries(List<CollectionEntry> entries) { this.entries = entries; }
}
