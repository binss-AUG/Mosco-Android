package com.vn.jet.mosco.spinserver.dto;

/**
 * Request body for POST /api/gacha/roll
 */
public class GachaRollRequest {

    private String packCode;
    private int quantity = 1;

    public GachaRollRequest() {}

    public GachaRollRequest(String packCode, int quantity) {
        this.packCode = packCode;
        this.quantity = quantity;
    }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
