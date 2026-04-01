package com.volleyhub.pro;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class MatchData {
    private int scoreA;
    private int scoreB;
    private int setsWonA;
    private int setsWonB;
    private long elapsedTime;
    private boolean isTimerRunning;
    private long lastUpdateTime;
    private String teamAName;
    private String teamBName;
    private String teamAColor;
    private String teamBColor;

    public MatchData() {
        // Default constructor required for Firebase
        this.scoreA = 0;
        this.scoreB = 0;
        this.setsWonA = 0;
        this.setsWonB = 0;
        this.elapsedTime = 0;
        this.isTimerRunning = false;
        this.lastUpdateTime = System.currentTimeMillis();
        this.teamAName = "Team A";
        this.teamBName = "Team B";
        this.teamAColor = "#00fbff";
        this.teamBColor = "#ff0055";
    }

    public MatchData(String teamAName, String teamBName) {
        this.scoreA = 0;
        this.scoreB = 0;
        this.setsWonA = 0;
        this.setsWonB = 0;
        this.elapsedTime = 0;
        this.isTimerRunning = false;
        this.lastUpdateTime = System.currentTimeMillis();
        this.teamAName = teamAName;
        this.teamBName = teamBName;
        this.teamAColor = "#00fbff";
        this.teamBColor = "#ff0055";
    }

    public MatchData(String teamAName, String teamBName, String teamAColor, String teamBColor) {
        this.scoreA = 0;
        this.scoreB = 0;
        this.setsWonA = 0;
        this.setsWonB = 0;
        this.elapsedTime = 0;
        this.isTimerRunning = false;
        this.lastUpdateTime = System.currentTimeMillis();
        this.teamAName = teamAName;
        this.teamBName = teamBName;
        this.teamAColor = teamAColor;
        this.teamBColor = teamBColor;
    }

    // Getters
    public int getScoreA() { return scoreA; }
    public int getScoreB() { return scoreB; }
    public int getSetsWonA() { return setsWonA; }
    public int getSetsWonB() { return setsWonB; }
    public long getElapsedTime() { return elapsedTime; }
    public boolean isTimerRunning() { return isTimerRunning; }
    public long getLastUpdateTime() { return lastUpdateTime; }
    public String getTeamAName() { return teamAName != null ? teamAName : "Team A"; }
    public String getTeamBName() { return teamBName != null ? teamBName : "Team B"; }
    public String getTeamAColor() { return teamAColor != null ? teamAColor : "#00fbff"; }
    public String getTeamBColor() { return teamBColor != null ? teamBColor : "#ff0055"; }

    // Setters
    public void setScoreA(int scoreA) { this.scoreA = scoreA; }
    public void setScoreB(int scoreB) { this.scoreB = scoreB; }
    public void setSetsWonA(int setsWonA) { this.setsWonA = setsWonA; }
    public void setSetsWonB(int setsWonB) { this.setsWonB = setsWonB; }
    public void setElapsedTime(long elapsedTime) { this.elapsedTime = elapsedTime; }
    public void setTimerRunning(boolean timerRunning) { isTimerRunning = timerRunning; }
    public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    public void setTeamAName(String teamAName) { this.teamAName = teamAName; }
    public void setTeamBName(String teamBName) { this.teamBName = teamBName; }
    public void setTeamAColor(String teamAColor) { this.teamAColor = teamAColor; }
    public void setTeamBColor(String teamBColor) { this.teamBColor = teamBColor; }

    // Helper methods
    public void addPointA() { scoreA++; }
    public void addPointB() { scoreB++; }
    public void removePointA() { if (scoreA > 0) scoreA--; }
    public void removePointB() { if (scoreB > 0) scoreB--; }
}
