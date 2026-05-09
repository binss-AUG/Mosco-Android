package com.vn.jet.mosco.network;

public class UpgradeProbabilityResponse {
    private double successRate;

    public UpgradeProbabilityResponse() {
    }

    public UpgradeProbabilityResponse(double successRate) {
        this.successRate = successRate;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }
}
