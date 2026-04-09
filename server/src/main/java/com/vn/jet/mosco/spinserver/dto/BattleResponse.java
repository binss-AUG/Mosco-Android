package com.vn.jet.mosco.spinserver.dto;

import java.util.List;
import java.util.Map;

public class BattleResponse {
    private int totalOvr;
    private List<String> activeSynergies;
    private Map<String, String> buffSummary;
    private Map<Long, Integer> cardOvrMap;

    public BattleResponse() {}

    public int getTotalOvr() { return totalOvr; }
    public void setTotalOvr(int totalOvr) { this.totalOvr = totalOvr; }

    public List<String> getActiveSynergies() { return activeSynergies; }
    public void setActiveSynergies(List<String> activeSynergies) { this.activeSynergies = activeSynergies; }

    public Map<String, String> getBuffSummary() { return buffSummary; }
    public void setBuffSummary(Map<String, String> buffSummary) { this.buffSummary = buffSummary; }
    
    public Map<Long, Integer> getCardOvrMap() { return cardOvrMap; }
    public void setCardOvrMap(Map<Long, Integer> cardOvrMap) { this.cardOvrMap = cardOvrMap; }
}
