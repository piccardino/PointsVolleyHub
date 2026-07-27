package com.volleyhub.pro;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final long UPDATE_INTERVAL = 1000;

    private FirebaseAuth mAuth;
    private DatabaseReference mMatchRef;
    private DatabaseReference mPlayersRef;
    private DatabaseReference mFormationRef;
    private ValueEventListener mMatchListener;
    private ValueEventListener mPlayersListener;
    private ValueEventListener mFormationListener;

    private TextView scoreAView;
    private TextView scoreBView;
    private TextView timerView;
    private TextView setsView;
    private TextView digitalClockView;
    private LinearLayout recentPointsRow;
    private LinearLayout playersPage;
    private LinearLayout playersListContainer;
    private Button btnAddPointA;
    private Button btnRemovePointA;
    private Button btnAddPointB;
    private Button btnRemovePointB;
    private View btnSwapSides;
    private Button btnToggleTimer;
    private Button btnResetMatch;
    private Button btnExit;
    private Button btnGenerateTeams;
    private GestureDetector pageSwipeDetector;

    private Handler timerHandler;
    private Runnable timerRunnable;
    private Runnable clockRunnable;
    private boolean isTimerRunning = false;
    private long elapsedTime = 0;
    private long lastUpdateTime;
    private String lastShownWinner = null;
    private MatchData currentMatch;
    private List<PlayerData> rosterPlayers = new ArrayList<>();
    private List<PlayerData> fallbackTeamAPlayers = new ArrayList<>();
    private List<PlayerData> fallbackTeamBPlayers = new ArrayList<>();
    private List<FormationToken> formationTokens = new ArrayList<>();
    private String fallbackTeamAName = "Team A";
    private String fallbackTeamBName = "Team B";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            Log.e(TAG, "No user logged in, finishing MainActivity");
            finish();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference()
            .child("users").child(userId);
        mMatchRef = userRef.child("matchData").child("liveMatchProgress_index");
        mPlayersRef = userRef.child("players");
        mFormationRef = userRef.child("matchData").child("formation");

        scoreAView = findViewById(R.id.scoreAView);
        scoreBView = findViewById(R.id.scoreBView);
        timerView = findViewById(R.id.timerView);
        setsView = findViewById(R.id.setsView);
        digitalClockView = findViewById(R.id.digitalClockView);
        recentPointsRow = findViewById(R.id.recentPointsRow);
        playersPage = findViewById(R.id.playersPage);
        playersListContainer = findViewById(R.id.playersListContainer);
        btnAddPointA = findViewById(R.id.btnAddPointA);
        btnRemovePointA = findViewById(R.id.btnRemovePointA);
        btnAddPointB = findViewById(R.id.btnAddPointB);
        btnRemovePointB = findViewById(R.id.btnRemovePointB);
        btnSwapSides = findViewById(R.id.btnSwapSides);
        btnToggleTimer = findViewById(R.id.btnToggleTimer);
        btnResetMatch = findViewById(R.id.btnResetMatch);
        btnExit = findViewById(R.id.btnExit);
        btnGenerateTeams = findViewById(R.id.btnGenerateTeams);

        btnAddPointA.setOnClickListener(v -> updateScore(true, true));
        btnRemovePointA.setOnClickListener(v -> updateScore(false, true));
        btnAddPointB.setOnClickListener(v -> updateScore(true, false));
        btnRemovePointB.setOnClickListener(v -> updateScore(false, false));
        if (btnSwapSides != null) {
            btnSwapSides.setOnClickListener(v -> toggleSwapSides());
        }
        btnToggleTimer.setOnClickListener(v -> toggleTimer());
        btnResetMatch.setOnClickListener(v -> resetMatch());
        btnExit.setOnClickListener(v -> logout());
        if (btnGenerateTeams != null) {
            btnGenerateTeams.setOnClickListener(v -> generateTeams());
        }
        setupPageSwipeGestures();

        ColorStateList darkGray = ColorStateList.valueOf(Color.parseColor("#1c1c1c"));
        btnToggleTimer.setBackgroundTintList(darkGray);
        btnResetMatch.setBackgroundTintList(darkGray);
        btnExit.setBackgroundTintList(darkGray);

        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimerDisplay();
                if (isTimerRunning) {
                    timerHandler.postDelayed(this, UPDATE_INTERVAL);
                }
            }
        };
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                updateDigitalClock();
                timerHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };

        initializeMatchDataIfNeeded();
        setupFirebaseListener();
        setupPlayersListener();
        setupFormationListener();
        updateDigitalClock();
        timerHandler.post(clockRunnable);
    }

    private void initializeMatchDataIfNeeded() {
        mMatchRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (!task.getResult().exists()) {
                    MatchData initialData = new MatchData("Team A", "Team B", "#00fbff", "#ff0055");
                    mMatchRef.setValue(initialData);
                }
            } else {
                Log.w(TAG, "Failed to check match data", task.getException());
            }
        });
    }

    private void setupFirebaseListener() {
        mMatchListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    MatchData match = dataSnapshot.getValue(MatchData.class);
                    if (match != null) {
                        updateUI(match);
                    }
                } else {
                    Log.d(TAG, "Match data not yet initialized");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "Failed to read value", databaseError.toException());
                Toast.makeText(MainActivity.this, "Errore: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
        mMatchRef.addValueEventListener(mMatchListener);
    }

    private void setupPlayersListener() {
        mPlayersListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<PlayerData> loadedPlayers = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    PlayerData player = child.getValue(PlayerData.class);
                    if (player != null) {
                        loadedPlayers.add(player);
                    }
                }
                rosterPlayers = loadedPlayers;
                rebuildFallbackPlayers();
                refreshPlayersPage();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read roster players", error.toException());
            }
        };
        mPlayersRef.addValueEventListener(mPlayersListener);
    }

    private void setupFormationListener() {
        mFormationListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                fallbackTeamAName = getSnapshotString(snapshot.child("benchA"), "Team A");
                fallbackTeamBName = getSnapshotString(snapshot.child("benchB"), "Team B");

                List<FormationToken> loadedTokens = new ArrayList<>();
                for (DataSnapshot tokenSnapshot : snapshot.child("tokens").getChildren()) {
                    String name = getSnapshotString(tokenSnapshot.child("name"), "");
                    String team = getSnapshotString(tokenSnapshot.child("team"), "");
                    String role = getSnapshotString(tokenSnapshot.child("role"), "");
                    if (!name.isEmpty() && !team.isEmpty()) {
                        loadedTokens.add(new FormationToken(name, team, role));
                    }
                }

                formationTokens = loadedTokens;
                rebuildFallbackPlayers();
                refreshPlayersPage();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read formation data", error.toException());
            }
        };
        mFormationRef.addValueEventListener(mFormationListener);
    }

    private void updateUI(MatchData match) {
        currentMatch = match;
        boolean isSwapped = match.isSidesSwapped();

        if (isSwapped) {
            scoreAView.setText(String.valueOf(match.getScoreB()));
            scoreBView.setText(String.valueOf(match.getScoreA()));
        } else {
            scoreAView.setText(String.valueOf(match.getScoreA()));
            scoreBView.setText(String.valueOf(match.getScoreB()));
        }

        String setInfo = String.format(
            Locale.getDefault(),
            "Set %d: %d - %d",
            match.getCurrentSet(),
            isSwapped ? match.getSetsWonB() : match.getSetsWonA(),
            isSwapped ? match.getSetsWonA() : match.getSetsWonB()
        );
        setsView.setText(setInfo);

        String teamAColorStr = match.getTeamAColor();
        String teamBColorStr = match.getTeamBColor();

        try {
            int teamAColor = Color.parseColor(teamAColorStr);
            int teamBColor = Color.parseColor(teamBColorStr);
            scoreAView.setTextColor(isSwapped ? teamBColor : teamAColor);
            scoreBView.setTextColor(isSwapped ? teamAColor : teamBColor);
            updateButtonBackgrounds(teamAColor, teamBColor, isSwapped);
            updateRecentPoints(match, teamAColor, teamBColor);
            updatePlayersPage(match, teamAColor, teamBColor);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid color format, using defaults", e);
            int defaultTeamAColor = Color.parseColor("#00fbff");
            int defaultTeamBColor = Color.parseColor("#ff0055");
            scoreAView.setTextColor(isSwapped ? defaultTeamBColor : defaultTeamAColor);
            scoreBView.setTextColor(isSwapped ? defaultTeamAColor : defaultTeamBColor);
            updateButtonBackgrounds(defaultTeamAColor, defaultTeamBColor, isSwapped);
            updateRecentPoints(match, defaultTeamAColor, defaultTeamBColor);
            updatePlayersPage(match, defaultTeamAColor, defaultTeamBColor);
        }

        elapsedTime = match.getTimerAccumulatedSeconds() * 1000;
        isTimerRunning = !match.isTimerIsPaused();
        lastUpdateTime = match.getTimerLastStartedAt();

        updateTimerButton();

        if (isTimerRunning && timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);
        } else if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }

        updateTimerDisplay();
        maybeShowWinnerAlert(match);
    }

    private void setupPageSwipeGestures() {
        pageSwipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_DISTANCE_THRESHOLD = 70;
            private static final int SWIPE_VELOCITY_THRESHOLD = 70;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) {
                    return false;
                }

                float deltaX = e2.getX() - e1.getX();
                float deltaY = e2.getY() - e1.getY();
                boolean isHorizontalSwipe = Math.abs(deltaX) > SWIPE_DISTANCE_THRESHOLD
                    && Math.abs(deltaX) > Math.abs(deltaY)
                    && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD;

                if (!isHorizontalSwipe) {
                    return false;
                }

                if (deltaX < 0 && playersPage.getVisibility() != View.VISIBLE) {
                    showPlayersPage(true);
                    return true;
                }

                if (deltaX < 0 && playersPage.getVisibility() == View.VISIBLE) {
                    showPlayersPage(false);
                    return true;
                }

                return false;
            }
        });
    }

    private void showPlayersPage(boolean show) {
        playersPage.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (pageSwipeDetector != null) {
            pageSwipeDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void updateRecentPoints(MatchData match, int teamAColor, int teamBColor) {
        recentPointsRow.removeAllViews();
        List<PointHistoryItem> history = match.getHistory();

        for (int i = 0; i < 5; i++) {
            TextView dot = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(10), dpToPx(10));
            params.setMargins(dpToPx(2), 0, dpToPx(2), 0);
            dot.setLayoutParams(params);

            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setCornerRadius(dpToPx(2));
            if (i < history.size()) {
                String team = history.get(i).getTeam();
                background.setColor("A".equals(team) ? teamAColor : teamBColor);
            } else {
                background.setColor(Color.parseColor("#333333"));
            }
            dot.setBackground(background);
            recentPointsRow.addView(dot);
        }
    }

    private void updatePlayersPage(MatchData match, int teamAColor, int teamBColor) {
        playersListContainer.removeAllViews();
        boolean useFormationFallback = !formationTokens.isEmpty();
        List<PlayerData> teamAPlayers = useFormationFallback
            ? fallbackTeamAPlayers
            : (!match.getTeamAPlayers().isEmpty() ? match.getTeamAPlayers() : fallbackTeamAPlayers);
        List<PlayerData> teamBPlayers = useFormationFallback
            ? fallbackTeamBPlayers
            : (!match.getTeamBPlayers().isEmpty() ? match.getTeamBPlayers() : fallbackTeamBPlayers);

        String teamAName = useFormationFallback
            ? fallbackTeamAName
            : (!match.getTeamAPlayers().isEmpty() ? match.getTeamAName() : fallbackTeamAName);
        String teamBName = useFormationFallback
            ? fallbackTeamBName
            : (!match.getTeamBPlayers().isEmpty() ? match.getTeamBName() : fallbackTeamBName);

        addTeamPlayersSection(teamAName, teamAPlayers, teamAColor);
        addTeamPlayersSection(teamBName, teamBPlayers, teamBColor);
    }

    private void rebuildFallbackPlayers() {
        if (!formationTokens.isEmpty()) {
            Map<String, PlayerData> rosterByName = new HashMap<>();
            for (PlayerData player : rosterPlayers) {
                rosterByName.put(normalizeName(player.getName()), player);
            }

            List<PlayerData> teamAPlayers = new ArrayList<>();
            List<PlayerData> teamBPlayers = new ArrayList<>();
            for (FormationToken token : formationTokens) {
                PlayerData player = buildPlayerFromSources(rosterByName.get(normalizeName(token.name)), token);
                if ("team-a".equals(token.team)) {
                    teamAPlayers.add(player);
                } else if ("team-b".equals(token.team)) {
                    teamBPlayers.add(player);
                }
            }

            fallbackTeamAPlayers = teamAPlayers;
            fallbackTeamBPlayers = teamBPlayers;
            return;
        }

        List<PlayerData> roster = new ArrayList<>(rosterPlayers);
        int mid = (int) Math.ceil(roster.size() / 2.0);
        fallbackTeamAPlayers = new ArrayList<>(roster.subList(0, Math.min(mid, roster.size())));
        fallbackTeamBPlayers = new ArrayList<>(roster.subList(Math.min(mid, roster.size()), roster.size()));
    }

    private PlayerData buildPlayerFromSources(PlayerData rosterPlayer, FormationToken token) {
        PlayerData player = new PlayerData();
        if (rosterPlayer != null) {
            player.setName(rosterPlayer.getName());
            player.setNum(rosterPlayer.getNum());
            player.setRole(rosterPlayer.getRole());
        }

        if (player.getName().equals("Giocatore")) {
            player.setName(token.name);
        }
        if (player.getRole().isEmpty() && !token.role.isEmpty()) {
            player.setRole(token.role);
        }
        return player;
    }

    private void refreshPlayersPage() {
        if (currentMatch == null) {
            return;
        }
        updatePlayersPage(
            currentMatch,
            parseTeamColor(currentMatch.getTeamAColor(), "#00fbff"),
            parseTeamColor(currentMatch.getTeamBColor(), "#ff0055")
        );
    }

    private int parseTeamColor(String colorValue, String fallbackColor) {
        try {
            return Color.parseColor(colorValue);
        } catch (IllegalArgumentException e) {
            return Color.parseColor(fallbackColor);
        }
    }

    private String getSnapshotString(DataSnapshot snapshot, String fallback) {
        Object value = snapshot.getValue();
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void addTeamPlayersSection(String teamName, List<PlayerData> players, int teamColor) {
        TextView title = new TextView(this);
        title.setText(teamName);
        title.setTextColor(teamColor);
        title.setTextSize(12);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dpToPx(8), 0, dpToPx(3));
        playersListContainer.addView(title);

        if (players.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nessun giocatore salvato");
            empty.setTextColor(Color.parseColor("#8a8a8a"));
            empty.setTextSize(9);
            empty.setPadding(dpToPx(4), dpToPx(2), 0, dpToPx(6));
            playersListContainer.addView(empty);
            return;
        }

        for (PlayerData player : players) {
            TextView row = new TextView(this);
            String number = player.getDisplayNumber();
            String numberText = number.isEmpty() ? "" : "#" + number + "  ";
            String role = player.getRole();
            String roleText = role.isEmpty() ? "" : " · " + role;
            row.setText(numberText + player.getName() + roleText);
            row.setTextColor(Color.WHITE);
            row.setTextSize(10);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(6), dpToPx(3), dpToPx(6), dpToPx(3));
            playersListContainer.addView(row);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void maybeShowWinnerAlert(MatchData match) {
        if (!match.isMatchComplete()) {
            lastShownWinner = null;
            return;
        }

        String winnerName = match.getSetsWonA() >= 3 ? match.getTeamAName() : match.getTeamBName();
        if (winnerName.equals(lastShownWinner)) {
            return;
        }

        lastShownWinner = winnerName;
        new AlertDialog.Builder(this)
            .setTitle("Partita finita")
            .setMessage("Il team " + winnerName + " ha vinto!")
            .setPositiveButton("OK", null)
            .show();
    }

    private void updateButtonBackgrounds(int teamAColor, int teamBColor, boolean isSwapped) {
        ColorStateList cslLeft = ColorStateList.valueOf(isSwapped ? teamBColor : teamAColor);
        ColorStateList cslRight = ColorStateList.valueOf(isSwapped ? teamAColor : teamBColor);

        if (btnAddPointA != null) {
            btnAddPointA.setBackgroundTintList(cslLeft);
            btnAddPointA.setText(isSwapped ? "+ B" : "+ A");
        }
        if (btnRemovePointA != null) {
            btnRemovePointA.setBackgroundTintList(cslLeft);
            btnRemovePointA.setText(isSwapped ? "- B" : "- A");
        }
        if (btnAddPointB != null) {
            btnAddPointB.setBackgroundTintList(cslRight);
            btnAddPointB.setText(isSwapped ? "+ A" : "+ B");
        }
        if (btnRemovePointB != null) {
            btnRemovePointB.setBackgroundTintList(cslRight);
            btnRemovePointB.setText(isSwapped ? "- A" : "- B");
        }
    }

    private void updateScore(boolean add, boolean isLeftSide) {
        mMatchRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                MatchData match = task.getResult().getValue(MatchData.class);
                if (match != null) {
                    boolean targetTeamA = match.isSidesSwapped() ? !isLeftSide : isLeftSide;
                    if (targetTeamA) {
                        if (add) {
                            match.addPointA();
                        } else {
                            match.removePointA();
                        }
                    } else {
                        if (add) {
                            match.addPointB();
                        } else {
                            match.removePointB();
                        }
                    }
                    mMatchRef.setValue(match);
                }
            }
        });
    }

    private void toggleSwapSides() {
        mMatchRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                MatchData match = task.getResult().getValue(MatchData.class);
                if (match != null) {
                    match.setSidesSwapped(!match.isSidesSwapped());
                    mMatchRef.setValue(match);
                }
            }
        });
    }

    private void toggleTimer() {
        mMatchRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                MatchData match = task.getResult().getValue(MatchData.class);
                if (match != null) {
                    if (!match.isTimerIsPaused()) {
                        long currentTime = System.currentTimeMillis();
                        long sessionTimeSeconds = (currentTime - match.getTimerLastStartedAt()) / 1000;
                        match.setTimerAccumulatedSeconds(match.getTimerAccumulatedSeconds() + sessionTimeSeconds);
                        match.setTimerIsPaused(true);
                    } else {
                        match.setTimerLastStartedAt(System.currentTimeMillis());
                        match.setTimerIsPaused(false);
                    }
                    mMatchRef.setValue(match);
                }
            }
        });
    }

    private void updateTimerDisplay() {
        long currentDisplayTime = elapsedTime;
        if (isTimerRunning) {
            currentDisplayTime += (System.currentTimeMillis() - lastUpdateTime);
        }

        long seconds = (currentDisplayTime / 1000) % 60;
        long minutes = (currentDisplayTime / (1000 * 60)) % 60;
        long hours = currentDisplayTime / (1000 * 60 * 60);

        String timeString;
        if (hours > 0) {
            timeString = String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            timeString = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }

        timerView.setText(timeString);
    }

    private void updateDigitalClock() {
        String clockText = String.format(Locale.getDefault(), "%tH:%tM:%tS",
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            System.currentTimeMillis()
        );
        digitalClockView.setText(clockText);
    }

    private void updateTimerButton() {
        if (isTimerRunning) {
            btnToggleTimer.setText("STOP");
            btnToggleTimer.setBackgroundResource(R.drawable.button_stop_flat);
        } else {
            btnToggleTimer.setText("START");
            btnToggleTimer.setBackgroundResource(R.drawable.button_start_flat);
        }
    }

    private void resetMatch() {
        mMatchRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                MatchData match = task.getResult().getValue(MatchData.class);
                if (match != null) {
                    match.setScoreA(0);
                    match.setScoreB(0);
                    match.setSetsWonA(0);
                    match.setSetsWonB(0);
                    match.setCurrentSet(1);
                    match.setTimerAccumulatedSeconds(0);
                    match.setSidesSwapped(false);
                    match.setHistory(new java.util.ArrayList<>());
                    match.setTimerIsPaused(true);
                    match.setTimerLastStartedAt(System.currentTimeMillis());
                    mMatchRef.setValue(match);
                }
            }
        });
    }

    private void generateTeams() {
        if (rosterPlayers == null || rosterPlayers.size() < 2) {
            Toast.makeText(this, "Attiva almeno 2 persone", Toast.LENGTH_SHORT).show();
            return;
        }

        List<PlayerData> pool = new ArrayList<>();
        for (PlayerData p : rosterPlayers) {
            if (p.getActive()) {
                pool.add(p);
            }
        }

        if (pool.size() < 2) {
            Toast.makeText(this, "Attiva almeno 2 persone", Toast.LENGTH_SHORT).show();
            return;
        }

        int halfSize = (int) Math.ceil(pool.size() / 2.0);
        int targetBSize = pool.size() / 2;

        double bestDiff = Double.MAX_VALUE;
        List<PlayerData> bestA = new ArrayList<>();
        List<PlayerData> bestB = new ArrayList<>();

        java.util.Random random = new java.util.Random();

        for (int i = 0; i < 100; i++) {
            List<PlayerData> tempA = new ArrayList<>();
            List<PlayerData> tempB = new ArrayList<>();
            double twA = 0;
            double twB = 0;

            List<PlayerData> shuffled = new ArrayList<>(pool);
            java.util.Collections.shuffle(shuffled, random);

            for (PlayerData p : shuffled) {
                double w = p.getWeight();
                if (tempA.size() >= halfSize) {
                    tempB.add(p);
                    twB += w;
                } else if (tempB.size() >= targetBSize) {
                    tempA.add(p);
                    twA += w;
                } else if (twA <= twB) {
                    tempA.add(p);
                    twA += w;
                } else {
                    tempB.add(p);
                    twB += w;
                }
            }

            double avgA = tempA.isEmpty() ? 0 : twA / tempA.size();
            double avgB = tempB.isEmpty() ? 0 : twB / tempB.size();
            double diff = Math.abs(avgA - avgB);

            if (diff < bestDiff) {
                bestDiff = diff;
                bestA = new ArrayList<>(tempA);
                bestB = new ArrayList<>(tempB);
            }
            if (diff == 0) break;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("scoreA", 0);
        data.put("scoreB", 0);
        data.put("benchA", "Team A");
        data.put("benchB", "Team B");

        List<Map<String, String>> tokens = new ArrayList<>();
        for (PlayerData p : bestA) {
            Map<String, String> token = new HashMap<>();
            token.put("name", p.getName());
            token.put("team", "team-a");
            token.put("role", "Libero".equals(p.getRole()) ? "Libero" : "Universal");
            token.put("gender", "male");
            tokens.add(token);
        }
        for (PlayerData p : bestB) {
            Map<String, String> token = new HashMap<>();
            token.put("name", p.getName());
            token.put("team", "team-b");
            token.put("role", "Libero".equals(p.getRole()) ? "Libero" : "Universal");
            token.put("gender", "male");
            tokens.add(token);
        }

        data.put("tokens", tokens);
        mFormationRef.setValue(data);
        Toast.makeText(this, "Squadre bilanciate generate!", Toast.LENGTH_SHORT).show();
    }

    private void logout() {
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.removeCallbacks(clockRunnable);
        }
        if (mMatchListener != null) {
            mMatchRef.removeEventListener(mMatchListener);
        }
        if (mPlayersListener != null) {
            mPlayersRef.removeEventListener(mPlayersListener);
        }
        if (mFormationListener != null) {
            mFormationRef.removeEventListener(mFormationListener);
        }
        mAuth.signOut();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.removeCallbacks(clockRunnable);
        }
        if (mMatchListener != null) {
            mMatchRef.removeEventListener(mMatchListener);
        }
        if (mPlayersListener != null) {
            mPlayersRef.removeEventListener(mPlayersListener);
        }
        if (mFormationListener != null) {
            mFormationRef.removeEventListener(mFormationListener);
        }
    }

    private static class FormationToken {
        private final String name;
        private final String team;
        private final String role;

        public FormationToken(String name, String team, String role) {
            this.name = name;
            this.team = team;
            this.role = role;
        }
    }
}
