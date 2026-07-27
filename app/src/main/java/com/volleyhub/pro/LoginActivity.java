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

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;
    
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private GoogleSignInClient mGoogleSignInClient;
    
    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private Button googleLoginButton;
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
        
        // Configure Google Sign In using web client ID from google-services.json
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("614392919865-ofota1pn71noh36cu18t24o97okbqf9p.apps.googleusercontent.com")
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        
        // Initialize views
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        googleLoginButton = findViewById(R.id.googleLoginButton);
        statusText = findViewById(R.id.statusText);

        loginButton.setOnClickListener(v -> attemptLogin());
        googleLoginButton.setOnClickListener(v -> signInWithGoogle());
        
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
        googleLoginButton.setEnabled(false);
        
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                loginButton.setEnabled(true);
                googleLoginButton.setEnabled(true);
                
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

    private void signInWithGoogle() {
        statusText.setText("Avvio Google Sign-In...");
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account.getIdToken());
                }
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                statusText.setText("Errore Google: " + e.getStatusCode());
                Toast.makeText(this, "Sign-In Google fallito: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        statusText.setText("Autenticazione Firebase...");
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Google sign in successful");
                    initializeMatchData();
                    navigateToMain();
                } else {
                    Log.w(TAG, "Firebase auth failed", task.getException());
                    statusText.setText("Errore: " + (task.getException() != null ? 
                        task.getException().getMessage() : "Auth fallita"));
                }
            });
    }
    
    private void initializeMatchData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Use same path structure as live-match.html: users/{uid}/matchData/liveMatch
            DatabaseReference matchRef = FirebaseDatabase.getInstance().getReference()
                .child("users").child(user.getUid()).child("matchData").child("liveMatchProgress_index");

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
