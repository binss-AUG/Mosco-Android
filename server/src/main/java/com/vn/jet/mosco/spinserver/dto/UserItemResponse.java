package com.vn.jet.mosco.spinserver.dto;

public class UserItemResponse {
    private Long id;
    private String itemCode;
    private int quantity;
    private String name;
    private String description;
    private String type;
    private String imageUri;

    public UserItemResponse(Long id, String itemCode, int quantity, String name, String description, String type, String imageUri) {
        this.id = id;
        this.itemCode = itemCode;
        this.quantity = quantity;
        this.name = name;
        this.description = description;
        this.type = type;
        this.imageUri = imageUri;
    }

    public Long getId() { return id; }
    public String getItemCode() { return itemCode; }
    public int getQuantity() { return quantity; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public String getImageUri() { return imageUri; }
}
