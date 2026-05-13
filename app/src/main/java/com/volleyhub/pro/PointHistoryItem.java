package com.volleyhub.pro;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class PointHistoryItem {
    private String time;
    private String team;
    private String teamName;
    private int scoreA;
    private int scoreB;
    private boolean causedRotation;

    public PointHistoryItem() {
        // Default constructor required for Firebase
    }

    public PointHistoryItem(String time, String team, String teamName, int scoreA, int scoreB) {
        this.time = time;
        this.team = team;
        this.teamName = teamName;
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.causedRotation = false;
    }

    public String getTime() { return time; }
    public String getTeam() { return team; }
    public String getTeamName() { return teamName; }
    public int getScoreA() { return scoreA; }
    public int getScoreB() { return scoreB; }
    public boolean isCausedRotation() { return causedRotation; }

    public void setTime(String time) { this.time = time; }
    public void setTeam(String team) { this.team = team; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public void setScoreA(int scoreA) { this.scoreA = scoreA; }
    public void setScoreB(int scoreB) { this.scoreB = scoreB; }
    public void setCausedRotation(boolean causedRotation) { this.causedRotation = causedRotation; }
}
