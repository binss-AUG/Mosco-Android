package com.vn.jet.mosco.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response tổng hợp cho API Bộ Sưu Tập (Collection Book).
 * Chứa tiến trình thu thập và danh sách toàn bộ thẻ bài.
 */
public class CollectionBookResponse {

    @SerializedName("totalCards")
    private int totalCards;

    @SerializedName("ownedCount")
    private int ownedCount;

    @SerializedName("entries")
    private List<CollectionEntry> entries;

    public CollectionBookResponse() {}

    public int getTotalCards() { return totalCards; }
    public int getOwnedCount() { return ownedCount; }
    public List<CollectionEntry> getEntries() { return entries; }
}
