package com.vn.jet.mosco.spinserver.dto;

/**
 * DTO cho endpoint đặt tên hiển thị trong game.
 */
public class DisplayNameRequest {
    private String ingameName;

    public DisplayNameRequest() {}

    public String getIngameName() { return ingameName; }
    public void setIngameName(String ingameName) { this.ingameName = ingameName; }
}
