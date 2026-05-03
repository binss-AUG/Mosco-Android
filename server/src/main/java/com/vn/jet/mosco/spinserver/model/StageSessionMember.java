package com.vn.jet.mosco.spinserver.model;

import jakarta.persistence.*;

@Entity
@Table(name = "stage_session_members")
public class StageSessionMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private StageSession stageSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_card_id", nullable = false)
    private UserCard userCard;

    public StageSessionMember() {}

    public StageSessionMember(StageSession stageSession, UserCard userCard) {
        this.stageSession = stageSession;
        this.userCard = userCard;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public StageSession getStageSession() { return stageSession; }
    public void setStageSession(StageSession stageSession) { this.stageSession = stageSession; }

    public UserCard getUserCard() { return userCard; }
    public void setUserCard(UserCard userCard) { this.userCard = userCard; }
}
