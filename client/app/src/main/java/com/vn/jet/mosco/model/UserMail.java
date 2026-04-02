package com.vn.jet.mosco.model;

public class UserMail {
    private Long id;
    private String title;
    private String content;
    private String itemCode;
    private Integer quantity;
    private boolean received;
    private String createdAt;

    public UserMail() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public boolean isReceived() { return received; }
    public void setReceived(boolean received) { this.received = received; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
