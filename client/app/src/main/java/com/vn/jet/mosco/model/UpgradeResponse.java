package com.vn.jet.mosco.model;

public class UpgradeResponse {
    private boolean success;
    private String message;
    private int newLevel;

    public UpgradeResponse() {}

    public UpgradeResponse(boolean success, String message, int newLevel) {
        this.success = success;
        this.message = message;
        this.newLevel = newLevel;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public int getNewLevel() { return newLevel; }
    public void setNewLevel(int newLevel) { this.newLevel = newLevel; }
}
