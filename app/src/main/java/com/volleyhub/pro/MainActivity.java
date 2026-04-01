package com.volleyhub.pro;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final long UPDATE_INTERVAL = 1000; // 1 second
    
    private FirebaseAuth mAuth;
    private DatabaseReference mMatchRef;
    private ValueEventListener mMatchListener;
    
    private TextView scoreAView;
    private TextView scoreBView;
    private TextView timerView;
    private TextView setsView;
    private Button btnAddPointA;
    private Button btnRemovePointA;
    private Button btnAddPointB;
    private Button btnRemovePointB;
    private Button btnToggleTimer;
    private Button btnResetMatch;
    private Button btnExit;
    
    private Handler timerHandler;
    private Runnable timerRunnable;
    private boolean isTimerRunning = false;
    private long elapsedTime = 0;
    private long lastUpdateTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on for Wear OS
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            Log.e(TAG, "No user logged in, finishing MainActivity");
            finish();
            return;
        }
        String userId = mAuth.getCurrentUser().getUid();
        // Use same path structure as live-match.html: users/{uid}/matchData/liveMatchProgress_index
        mMatchRef = FirebaseDatabase.getInstance().getReference()
            .child("users").child(userId).child("matchData").child("liveMatchProgress_index");

        // Initialize views
        scoreAView = findViewById(R.id.scoreAView);
        scoreBView = findViewById(R.id.scoreBView);
        timerView = findViewById(R.id.timerView);
        setsView = findViewById(R.id.setsView);
        btnAddPointA = findViewById(R.id.btnAddPointA);
        btnRemovePointA = findViewById(R.id.btnRemovePointA);
        btnAddPointB = findViewById(R.id.btnAddPointB);
        btnRemovePointB = findViewById(R.id.btnRemovePointB);
        btnToggleTimer = findViewById(R.id.btnToggleTimer);
        btnResetMatch = findViewById(R.id.btnResetMatch);
        btnExit = findViewById(R.id.btnExit);

        // Setup button listeners
        btnAddPointA.setOnClickListener(v -> updateScore(true, true));
        btnRemovePointA.setOnClickListener(v -> updateScore(false, true));
        btnAddPointB.setOnClickListener(v -> updateScore(true, false));
        btnRemovePointB.setOnClickListener(v -> updateScore(false, false));

        btnToggleTimer.setOnClickListener(v -> toggleTimer());
        btnResetMatch.setOnClickListener(v -> resetMatch());
        btnExit.setOnClickListener(v -> logout());

        // Force dark gray tint for control buttons to prevent theme override
        ColorStateList darkGray = ColorStateList.valueOf(Color.parseColor("#1c1c1c"));
        btnToggleTimer.setBackgroundTintList(darkGray);
        btnResetMatch.setBackgroundTintList(darkGray);
        btnExit.setBackgroundTintList(darkGray);

        // Timer handler
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

        // Initialize match data if not exists, then listen for updates
        initializeMatchDataIfNeeded();
        setupFirebaseListener();
    }

    private void initializeMatchDataIfNeeded() {
        mMatchRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (!task.getResult().exists()) {
                    // Initialize with default values including colors
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
                    // Data doesn't exist yet - will be initialized by initializeMatchDataIfNeeded()
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
    
    private void updateUI(MatchData match) {
        scoreAView.setText(String.valueOf(match.getScoreA()));
        scoreBView.setText(String.valueOf(match.getScoreB()));
        
        String setInfo = String.format(Locale.getDefault(), "Set %d: %d - %d",
            match.getCurrentSet(), match.getSetsWonA(), match.getSetsWonB());
        setsView.setText(setInfo);

        // Get team colors with fallback to defaults
        String teamAColorStr = match.getTeamAColor();
        String teamBColorStr = match.getTeamBColor();
        
        try {
            int teamAColor = Color.parseColor(teamAColorStr);
            int teamBColor = Color.parseColor(teamBColorStr);
            
            scoreAView.setTextColor(teamAColor);
            scoreBView.setTextColor(teamBColor);
            
            // Update button backgrounds with team colors
            updateButtonBackgrounds(teamAColor, teamBColor);
        } catch (IllegalArgumentException e) {
            // Fallback to default colors if parsing fails
            Log.w(TAG, "Invalid color format, using defaults", e);
            int defaultTeamAColor = Color.parseColor("#00fbff");
            int defaultTeamBColor = Color.parseColor("#ff0055");
            
            scoreAView.setTextColor(defaultTeamAColor);
            scoreBView.setTextColor(defaultTeamBColor);
            
            updateButtonBackgrounds(defaultTeamAColor, defaultTeamBColor);
        }

        elapsedTime = match.getTimerAccumulatedSeconds() * 1000;
        isTimerRunning = !match.isTimerIsPaused();
        lastUpdateTime = match.getTimerLastStartedAt();

        updateTimerButton();

        if (isTimerRunning && timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);
        } else {
            timerHandler.removeCallbacks(timerRunnable);
        }

        updateTimerDisplay();
    }

    private void updateButtonBackgrounds(int teamAColor, int teamBColor) {
        ColorStateList cslA = ColorStateList.valueOf(teamAColor);
        ColorStateList cslB = ColorStateList.valueOf(teamBColor);
        
        if (btnAddPointA != null) btnAddPointA.setBackgroundTintList(cslA);
        if (btnRemovePointA != null) btnRemovePointA.setBackgroundTintList(cslA);
        if (btnAddPointB != null) btnAddPointB.setBackgroundTintList(cslB);
        if (btnRemovePointB != null) btnRemovePointB.setBackgroundTintList(cslB);
    }
    
    private void updateScore(boolean add, boolean teamA) {
        mMatchRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                MatchData match = task.getResult().getValue(MatchData.class);
                if (match != null) {
                    if (teamA) {
                        if (add) match.addPointA();
                        else match.removePointA();
                    } else {
                        if (add) match.addPointB();
                        else match.removePointB();
                    }
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
                        // Stop timer - add elapsed time
                        long currentTime = System.currentTimeMillis();
                        long sessionTimeSeconds = (currentTime - match.getTimerLastStartedAt()) / 1000;
                        match.setTimerAccumulatedSeconds(match.getTimerAccumulatedSeconds() + sessionTimeSeconds);
                        match.setTimerIsPaused(true);
                    } else {
                        // Start timer
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
                    match.setTimerIsPaused(true);
                    match.setTimerLastStartedAt(System.currentTimeMillis());
                    mMatchRef.setValue(match);
                }
            }
        });
    }
    
    private void logout() {
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        mMatchRef.removeEventListener(mMatchListener);
        mAuth.signOut();
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        if (mMatchListener != null) {
            mMatchRef.removeEventListener(mMatchListener);
        }
    }
}
