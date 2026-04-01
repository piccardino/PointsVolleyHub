package com.volleyhub.pro;

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
    private TextView teamANameView;
    private TextView teamBNameView;
    private TextView timerView;
    private TextView setsView;
    private Button btnAddPointA;
    private Button btnRemovePointA;
    private Button btnAddPointB;
    private Button btnRemovePointB;
    private Button btnToggleTimer;
    private Button btnResetMatch;
    private Button btnLogout;
    
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
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "";
        mMatchRef = FirebaseDatabase.getInstance().getReference().child(userId).child("match");
        
        // Initialize views
        scoreAView = findViewById(R.id.scoreAView);
        scoreBView = findViewById(R.id.scoreBView);
        teamANameView = findViewById(R.id.teamANameView);
        teamBNameView = findViewById(R.id.teamBNameView);
        timerView = findViewById(R.id.timerView);
        setsView = findViewById(R.id.setsView);
        btnAddPointA = findViewById(R.id.btnAddPointA);
        btnRemovePointA = findViewById(R.id.btnRemovePointA);
        btnAddPointB = findViewById(R.id.btnAddPointB);
        btnRemovePointB = findViewById(R.id.btnRemovePointB);
        btnToggleTimer = findViewById(R.id.btnToggleTimer);
        btnResetMatch = findViewById(R.id.btnResetMatch);
        btnLogout = findViewById(R.id.btnLogout);
        
        // Setup button listeners
        btnAddPointA.setOnClickListener(v -> updateScore(true, true));
        btnRemovePointA.setOnClickListener(v -> updateScore(false, true));
        btnAddPointB.setOnClickListener(v -> updateScore(true, false));
        btnRemovePointB.setOnClickListener(v -> updateScore(false, false));
        
        btnToggleTimer.setOnClickListener(v -> toggleTimer());
        btnResetMatch.setOnClickListener(v -> resetMatch());
        btnLogout.setOnClickListener(v -> logout());
        
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
        
        // Listen for Firebase updates
        setupFirebaseListener();
    }
    
    private void setupFirebaseListener() {
        mMatchListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                MatchData match = dataSnapshot.getValue(MatchData.class);
                if (match != null) {
                    updateUI(match);
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "Failed to read value", databaseError.toException());
                Toast.makeText(MainActivity.this, "Errore database", Toast.LENGTH_SHORT).show();
            }
        };
        mMatchRef.addValueEventListener(mMatchListener);
    }
    
    private void updateUI(MatchData match) {
        scoreAView.setText(String.valueOf(match.getScoreA()));
        scoreBView.setText(String.valueOf(match.getScoreB()));
        teamANameView.setText(match.getTeamAName());
        teamBNameView.setText(match.getTeamBName());
        setsView.setText(String.format(Locale.getDefault(), "Set: %d - %d", 
            match.getSetsWonA(), match.getSetsWonB()));
        
        elapsedTime = match.getElapsedTime();
        isTimerRunning = match.isTimerRunning();
        lastUpdateTime = match.getLastUpdateTime();
        
        updateTimerButton();
        
        if (isTimerRunning && timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);
        } else {
            timerHandler.removeCallbacks(timerRunnable);
        }
        
        updateTimerDisplay();
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
                    if (isTimerRunning) {
                        // Stop timer - add elapsed time
                        long currentTime = System.currentTimeMillis();
                        match.setElapsedTime(match.getElapsedTime() + (currentTime - match.getLastUpdateTime()));
                        match.setTimerRunning(false);
                    } else {
                        // Start timer
                        match.setLastUpdateTime(System.currentTimeMillis());
                        match.setTimerRunning(true);
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
            btnToggleTimer.setBackgroundColor(ContextCompat.getColor(this, R.color.color_stop));
        } else {
            btnToggleTimer.setText("START");
            btnToggleTimer.setBackgroundColor(ContextCompat.getColor(this, R.color.color_start));
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
                    match.setElapsedTime(0);
                    match.setTimerRunning(false);
                    match.setLastUpdateTime(System.currentTimeMillis());
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
