package com.volleyhub.pro;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    
    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Keep screen on for Wear OS
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_login);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        
        // Initialize views
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        statusText = findViewById(R.id.statusText);

        loginButton.setOnClickListener(v -> attemptLogin());
        
        // Check if already logged in
        if (mAuth.getCurrentUser() != null) {
            navigateToMain();
        }
    }
    
    private void attemptLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        
        if (email.isEmpty() || password.isEmpty()) {
            statusText.setText("Inserisci email e password");
            return;
        }
        
        statusText.setText("Accesso in corso...");
        loginButton.setEnabled(false);
        
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                loginButton.setEnabled(true);
                
                if (task.isSuccessful()) {
                    Log.d(TAG, "Login successful");
                    initializeMatchData();
                    navigateToMain();
                } else {
                    Log.w(TAG, "Login failed", task.getException());
                    statusText.setText("Errore: " + (task.getException() != null ? 
                        task.getException().getMessage() : "Accesso fallito"));
                }
            });
    }
    
    private void initializeMatchData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Use same path structure as live-match.html: users/{uid}/matchData/liveMatch
            DatabaseReference matchRef = FirebaseDatabase.getInstance().getReference()
                .child("users").child(user.getUid()).child("matchData").child("liveMatch");

            // Check if match data exists
            matchRef.get().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || !task.getResult().exists()) {
                    // Initialize with default values
                    MatchData initialData = new MatchData("Team A", "Team B", "#00fbff", "#ff0055");
                    matchRef.setValue(initialData);
                }
            });
        }
    }
    
    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            navigateToMain();
        }
    }
}
