package com.vn.jet.mosco.dto;

import java.util.List;

public class BattleRequest {
    private List<FormationSlot> formation;

    public BattleRequest() {}

    public List<FormationSlot> getFormation() { return formation; }
    public void setFormation(List<FormationSlot> formation) { this.formation = formation; }

    public static class FormationSlot {
        private Long userCardId;

        public FormationSlot() {}

        public Long getUserCardId() { return userCardId; }
        public void setUserCardId(Long userCardId) { this.userCardId = userCardId; }
    }
}
