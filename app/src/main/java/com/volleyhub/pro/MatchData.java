package com.volleyhub.pro;

import com.google.firebase.database.IgnoreExtraProperties;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@IgnoreExtraProperties
public class MatchData {
    private static final int POINTS_TO_WIN_REGULAR_SET = 25;
    private static final int POINTS_TO_WIN_TIEBREAK_SET = 15;
    private static final int SETS_TO_WIN_MATCH = 3;
    private static final int FINAL_SET_NUMBER = 5;
    private static final int MIN_WIN_MARGIN = 2;

    private int scoreA;
    private int scoreB;
    private int setsWonA;
    private int setsWonB;
    private int currentSet;
    private long timerAccumulatedSeconds;
    private boolean timerIsPaused;
    private long timerLastStartedAt;
    private String teamAName;
    private String teamBName;
    private String teamAColor;
    private String teamBColor;
    private String servingTeam;
    private String receivingTeam;
    private List<PlayerData> teamAPlayers;
    private List<PlayerData> teamBPlayers;
    private List<PointHistoryItem> history;

    public MatchData() {
        // Default constructor required for Firebase
        this.scoreA = 0;
        this.scoreB = 0;
        this.setsWonA = 0;
        this.setsWonB = 0;
        this.currentSet = 1;
        this.timerAccumulatedSeconds = 0;
        this.timerIsPaused = true;
        this.timerLastStartedAt = 0;
        this.teamAName = "Team A";
        this.teamBName = "Team B";
        this.teamAColor = "#00fbff";
        this.teamBColor = "#ff0055";
        this.servingTeam = "A";
        this.receivingTeam = "B";
        this.teamAPlayers = new ArrayList<>();
        this.teamBPlayers = new ArrayList<>();
        this.history = new ArrayList<>();
    }

    public MatchData(String teamAName, String teamBName, String teamAColor, String teamBColor) {
        this();
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
    public int getCurrentSet() { return currentSet; }
    public long getTimerAccumulatedSeconds() { return timerAccumulatedSeconds; }
    public boolean isTimerIsPaused() { return timerIsPaused; }
    public long getTimerLastStartedAt() { return timerLastStartedAt; }
    public String getTeamAName() { return teamAName != null ? teamAName : "Team A"; }
    public String getTeamBName() { return teamBName != null ? teamBName : "Team B"; }
    public String getTeamAColor() { return teamAColor != null ? teamAColor : "#00fbff"; }
    public String getTeamBColor() { return teamBColor != null ? teamBColor : "#ff0055"; }
    public String getServingTeam() { return servingTeam; }
    public String getReceivingTeam() { return receivingTeam; }
    public List<PlayerData> getTeamAPlayers() { return teamAPlayers != null ? teamAPlayers : new ArrayList<>(); }
    public List<PlayerData> getTeamBPlayers() { return teamBPlayers != null ? teamBPlayers : new ArrayList<>(); }
    public List<PointHistoryItem> getHistory() { return history != null ? history : new ArrayList<>(); }

    // Setters
    public void setScoreA(int scoreA) { this.scoreA = scoreA; }
    public void setScoreB(int scoreB) { this.scoreB = scoreB; }
    public void setSetsWonA(int setsWonA) { this.setsWonA = setsWonA; }
    public void setSetsWonB(int setsWonB) { this.setsWonB = setsWonB; }
    public void setCurrentSet(int currentSet) { this.currentSet = currentSet; }
    public void setTimerAccumulatedSeconds(long timerAccumulatedSeconds) { this.timerAccumulatedSeconds = timerAccumulatedSeconds; }
    public void setTimerIsPaused(boolean timerIsPaused) { this.timerIsPaused = timerIsPaused; }
    public void setTimerLastStartedAt(long timerLastStartedAt) { this.timerLastStartedAt = timerLastStartedAt; }
    public void setTeamAName(String teamAName) { this.teamAName = teamAName; }
    public void setTeamBName(String teamBName) { this.teamBName = teamBName; }
    public void setTeamAColor(String teamAColor) { this.teamAColor = teamAColor; }
    public void setTeamBColor(String teamBColor) { this.teamBColor = teamBColor; }
    public void setServingTeam(String servingTeam) { this.servingTeam = servingTeam; }
    public void setReceivingTeam(String receivingTeam) { this.receivingTeam = receivingTeam; }
    public void setTeamAPlayers(List<PlayerData> teamAPlayers) { this.teamAPlayers = teamAPlayers; }
    public void setTeamBPlayers(List<PlayerData> teamBPlayers) { this.teamBPlayers = teamBPlayers; }
    public void setHistory(List<PointHistoryItem> history) { this.history = history; }

    // Helper methods
    public void addPointA() {
        if (isMatchComplete()) {
            return;
        }
        scoreA++;
        addHistoryPoint("A", getCurrentHistoryTime());
        applySetRules();
    }

    public void addPointB() {
        if (isMatchComplete()) {
            return;
        }
        scoreB++;
        addHistoryPoint("B", getCurrentHistoryTime());
        applySetRules();
    }

    public void removePointA() {
        if (scoreA > 0) {
            scoreA--;
            removeLastHistoryPointForTeam("A");
        }
    }

    public void removePointB() {
        if (scoreB > 0) {
            scoreB--;
            removeLastHistoryPointForTeam("B");
        }
    }

    public void addHistoryPoint(String team, String time) {
        List<PointHistoryItem> updatedHistory = new ArrayList<>(getHistory());
        String teamName = "A".equals(team) ? getTeamAName() : getTeamBName();
        updatedHistory.add(0, new PointHistoryItem(time, team, teamName, scoreA, scoreB));
        if (updatedHistory.size() > 20) {
            updatedHistory = new ArrayList<>(updatedHistory.subList(0, 20));
        }
        history = updatedHistory;
    }

    private String getCurrentHistoryTime() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    private void removeLastHistoryPointForTeam(String team) {
        List<PointHistoryItem> updatedHistory = new ArrayList<>(getHistory());
        if (!updatedHistory.isEmpty() && team.equals(updatedHistory.get(0).getTeam())) {
            updatedHistory.remove(0);
            history = updatedHistory;
        }
    }

    public int getPointsToWinCurrentSet() {
        return isFinalSet() ? POINTS_TO_WIN_TIEBREAK_SET : POINTS_TO_WIN_REGULAR_SET;
    }

    public boolean isMatchComplete() {
        return setsWonA >= SETS_TO_WIN_MATCH || setsWonB >= SETS_TO_WIN_MATCH;
    }

    private boolean isFinalSet() {
        return currentSet >= FINAL_SET_NUMBER;
    }

    private void applySetRules() {
        int targetScore = getPointsToWinCurrentSet();
        int scoreDiff = Math.abs(scoreA - scoreB);

        if (scoreA < targetScore && scoreB < targetScore) {
            return;
        }

        if (scoreDiff < MIN_WIN_MARGIN) {
            return;
        }

        if (scoreA > scoreB) {
            setsWonA++;
        } else {
            setsWonB++;
        }

        scoreA = 0;
        scoreB = 0;

        if (isMatchComplete()) {
            history = new ArrayList<>();
        } else {
            currentSet = Math.min(currentSet + 1, FINAL_SET_NUMBER);
        }
    }
}
