package com.vn.jet.mosco.spinserver.model;

/**
 * DTO trả về kết quả nâng cấp cho client.
 */
public class UpgradeResponse {
    private boolean success;
    private int newLevel;
    private int newOvr;        // OVR mới sau khi upgrade — Server Truth
    private double revealRate;
    private String message;

    public UpgradeResponse() {}

    public UpgradeResponse(boolean success, int newLevel, int newOvr, double revealRate, String message) {
        this.success = success;
        this.newLevel = newLevel;
        this.newOvr = newOvr;
        this.revealRate = revealRate;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public int getNewLevel() { return newLevel; }
    public void setNewLevel(int newLevel) { this.newLevel = newLevel; }

    public int getNewOvr() { return newOvr; }
    public void setNewOvr(int newOvr) { this.newOvr = newOvr; }

    public double getRevealRate() { return revealRate; }
    public void setRevealRate(double revealRate) { this.revealRate = revealRate; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
