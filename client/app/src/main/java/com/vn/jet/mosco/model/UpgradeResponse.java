package com.vn.jet.mosco.model;

/**
 * DTO nhận kết quả upgrade từ Server.
 * Chứa OVR mới — Client không cần tự tính.
 */
public class UpgradeResponse {
    private boolean success;
    private String message;
    private int newLevel;
    private int newOvr;  // OVR mới do Server tính sẵn (Server Truth)

    public UpgradeResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public int getNewLevel() { return newLevel; }
    public void setNewLevel(int newLevel) { this.newLevel = newLevel; }
    public int getNewOvr() { return newOvr; }
    public void setNewOvr(int newOvr) { this.newOvr = newOvr; }
}
