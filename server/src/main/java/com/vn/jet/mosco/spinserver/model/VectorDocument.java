package com.vn.jet.mosco.spinserver.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorDocument {
    private String id;
    private String pageName;
    private String title;
    private String content;
    private List<Double> embedding;
    private java.util.Map<String, String> metadata;
    private long timestamp;

    public VectorDocument(String id, String title, String content, List<Double> embedding) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.embedding = embedding;
        this.timestamp = System.currentTimeMillis();
    }
}
