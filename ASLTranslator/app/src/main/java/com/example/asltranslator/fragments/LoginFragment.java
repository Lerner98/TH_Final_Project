package com.example.asltranslator.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.asltranslator.R;
import com.example.asltranslator.activities.MainActivity;
import com.example.asltranslator.network.CryptoManager;
import com.example.asltranslator.utils.PreferenceManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Handles user login with Firebase Authentication, Google Sign-In, and biometric authentication.
 */
public class LoginFragment extends Fragment {

    private TextInputEditText etEmail, etPassword;
    private TextInputLayout tilEmail, tilPassword;
    private MaterialButton btnLogin, btnGoogleSignIn, btnBiometric;
    private TextView tvForgotPassword;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private PreferenceManager prefManager;
    private CryptoManager cryptoManager;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        initFirebase();
        setupBiometric();
        setupClickListeners();

        // Register Google Sign-In ActivityResult launcher
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            Log.d("LoginFragment", "Google Sign-In successful: " + account.getEmail());
                            if (account != null) {
                                firebaseAuthWithGoogle(account.getIdToken(), account);
                            }
                        } catch (ApiException e) {
                            Log.e("LoginFragment", "Google sign-in failed", e);
                            Toast.makeText(getContext(), "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.w("LoginFragment", "Google sign-in canceled or failed with code: " + result.getResultCode());
                    }
                }
        );
    }

    private void initViews(View view) {
        etEmail = view.findViewById(R.id.et_email);
        etPassword = view.findViewById(R.id.et_password);
        tilEmail = view.findViewById(R.id.til_email);
        tilPassword = view.findViewById(R.id.til_password);
        btnLogin = view.findViewById(R.id.btn_login);
        btnGoogleSignIn = view.findViewById(R.id.btn_google_signin);
        btnBiometric = view.findViewById(R.id.btn_biometric);
        tvForgotPassword = view.findViewById(R.id.tv_forgot_password);
        progressBar = view.findViewById(R.id.progress_bar);
    }

    private void initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        prefManager = new PreferenceManager(requireContext());
        cryptoManager = CryptoManager.getInstance();

        // Google Sign-In setup
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
    }

    private void setupBiometric() {
        Executor executor = ContextCompat.getMainExecutor(requireContext());

        biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Toast.makeText(getContext(), "Authentication error: " + errString,
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        String savedEmail = prefManager.getString("biometric_email", "");
                        String savedPasswordHash = prefManager.getString("biometric_password_hash", "");

                        if (!TextUtils.isEmpty(savedEmail) && !TextUtils.isEmpty(savedPasswordHash)) {
                            loginWithSavedCredentials(savedEmail, savedPasswordHash);
                        } else {
                            Toast.makeText(getContext(), "No saved credentials found",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(getContext(), "Authentication failed",
                                Toast.LENGTH_SHORT).show();
                    }
                });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Use password")
                .build();

        if (prefManager.getString("biometric_email", "").isEmpty()) {
            btnBiometric.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> validateAndLogin());
        btnBiometric.setOnClickListener(v -> biometricPrompt.authenticate(promptInfo));
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    /**
     * Validates and performs login with email and password using Firebase Authentication.
     */
    private void validateAndLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Clear previous errors
        tilEmail.setError(null);
        tilPassword.setError(null);

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            tilPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        showLoading(true);

        String salt = email;
        String hashedPassword = cryptoManager.hashPasswordSHA256(password, salt);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Save credentials for biometric login
                            prefManager.setString("biometric_email", email);
                            prefManager.setString("biometric_password_hash", hashedPassword);

                            if (TextUtils.isEmpty(user.getDisplayName())) {
                                String extractedName = email.contains("@") ?
                                        email.substring(0, email.indexOf("@")) :
                                        email;

                                UserProfileChangeRequest profileUpdates =
                                        new UserProfileChangeRequest.Builder()
                                                .setDisplayName(extractedName)
                                                .build();

                                user.updateProfile(profileUpdates)
                                        .addOnCompleteListener(updateTask -> {
                                            saveUserToDatabaseIfMissing(user, null);
                                            navigateToMain();
                                        });
                            } else {
                                saveUserToDatabaseIfMissing(user, null);
                                navigateToMain();
                            }
                        }
                    } else {
                        showLoading(false);
                        String errorMessage = "Authentication failed";
                        if (task.getException() != null) {
                            String exceptionMessage = task.getException().getMessage();
                            if (exceptionMessage != null) {
                                if (exceptionMessage.contains("no user record")) {
                                    errorMessage = "No account found with this email";
                                } else if (exceptionMessage.contains("password is invalid")) {
                                    errorMessage = "Incorrect password";
                                } else if (exceptionMessage.contains("badly formatted")) {
                                    errorMessage = "Invalid email format";
                                } else {
                                    errorMessage = exceptionMessage;
                                }
                            }
                        }
                        Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);

        TextInputEditText etResetEmail = dialogView.findViewById(R.id.et_reset_email);

        // Pre-fill with current email if available
        String currentEmail = etEmail.getText().toString().trim();
        if (!TextUtils.isEmpty(currentEmail)) {
            etResetEmail.setText(currentEmail);
        }

        builder.setView(dialogView)
                .setTitle("Reset Password")
                .setPositiveButton("Send Reset Email", null)
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Override positive button to prevent auto-dismiss
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String resetEmail = etResetEmail.getText().toString().trim();

            if (TextUtils.isEmpty(resetEmail)) {
                etResetEmail.setError("Email is required");
                return;
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(resetEmail).matches()) {
                etResetEmail.setError("Please enter a valid email address");
                return;
            }

            // Send password reset email
            mAuth.sendPasswordResetEmail(resetEmail)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            dialog.dismiss();
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Password Reset Email Sent")
                                    .setMessage("Check your email (" + resetEmail + ") for instructions to reset your password.")
                                    .setPositiveButton("OK", null)
                                    .setIcon(R.drawable.ic_email)
                                    .show();
                        } else {
                            String errorMessage = "Failed to send reset email";
                            if (task.getException() != null && task.getException().getMessage() != null) {
                                String exceptionMessage = task.getException().getMessage();
                                if (exceptionMessage.contains("no user record")) {
                                    errorMessage = "No account found with this email address";
                                } else {
                                    errorMessage = exceptionMessage;
                                }
                            }
                            Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void loginWithSavedCredentials(String email, String passwordHash) {
        showLoading(true);
        Toast.makeText(getContext(), "Biometric login successful", Toast.LENGTH_SHORT).show();
        navigateToMain();
    }

    private void signInWithGoogle() {
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void firebaseAuthWithGoogle(String idToken, GoogleSignInAccount account) {
        showLoading(true);

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Log.d("DEBUG", "Name: " + user.getDisplayName());
                            Log.d("DEBUG", "Email: " + user.getEmail());

                            if (user.getDisplayName() == null || user.getDisplayName().isEmpty()) {
                                String extractedName = user.getEmail().contains("@") ?
                                        user.getEmail().substring(0, user.getEmail().indexOf("@")) : "User";

                                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(extractedName)
                                        .build();

                                final GoogleSignInAccount finalAccount = account;
                                user.updateProfile(profileUpdates)
                                        .addOnCompleteListener(updateTask -> {
                                            saveUserToDatabaseIfMissing(user, finalAccount);
                                            navigateToMain();
                                        });
                            } else {
                                saveUserToDatabaseIfMissing(user, account);
                                navigateToMain();
                            }
                        }
                    } else {
                        showLoading(false);
                        Toast.makeText(getContext(), "Firebase auth failed: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigateToMain() {
        Intent intent = new Intent(getActivity(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        getActivity().finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
        btnGoogleSignIn.setEnabled(!show);
        btnBiometric.setEnabled(!show);
        tvForgotPassword.setEnabled(!show);
    }

    private void saveUserToDatabaseIfMissing(FirebaseUser user, @Nullable GoogleSignInAccount account) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance()
                .getReference("users").child(user.getUid());

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.d("DEBUG", "User not found in database, creating...");

                    // Determine display name with fallback logic
                    String displayName;
                    if (account != null && account.getDisplayName() != null && !account.getDisplayName().isEmpty()) {
                        displayName = account.getDisplayName();
                    } else if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                        displayName = user.getDisplayName();
                    } else if (user.getEmail() != null && user.getEmail().contains("@")) {
                        displayName = user.getEmail().substring(0, user.getEmail().indexOf("@"));
                    } else {
                        displayName = "User";
                    }

                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("uid", user.getUid());
                    userMap.put("email", user.getEmail());
                    userMap.put("displayName", displayName);
                    userMap.put("createdAt", System.currentTimeMillis() / 1000);
                    userMap.put("lastLoginAt", System.currentTimeMillis() / 1000);
                    userMap.put("totalTranslations", 0);
                    userMap.put("totalPracticeTime", 0);
                    userMap.put("merkleRoot", "default_merkle_root");

                    usersRef.setValue(userMap)
                            .addOnSuccessListener(aVoid ->
                                    Log.d("DEBUG", "User saved to database successfully."))
                            .addOnFailureListener(e ->
                                    Log.e("ERROR", "Failed to save user to database: " + e.getMessage()));
                } else {
                    Log.d("DEBUG", "User already exists in DB.");
                    usersRef.child("lastLoginAt").setValue(System.currentTimeMillis() / 1000);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ERROR", "Database read cancelled: " + error.getMessage());
            }
        });
    }
}