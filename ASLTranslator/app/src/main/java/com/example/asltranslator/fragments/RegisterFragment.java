package com.example.asltranslator.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.asltranslator.R;
import com.example.asltranslator.activities.MainActivity;
import com.example.asltranslator.models.User;
import com.example.asltranslator.network.CryptoManager;
import com.example.asltranslator.utils.Constants;
import com.example.asltranslator.utils.MerkleTreeManager;
import com.example.asltranslator.utils.PreferenceManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.regex.Pattern;

/**
 * Handles user registration with Firebase Authentication and database storage.
 */
public class RegisterFragment extends Fragment {

    private TextInputEditText etName, etEmail, etPassword;
    private MaterialButton btnRegister;
    private MaterialCheckBox cbTerms;
    private ProgressBar progressBar;
    private LinearProgressIndicator passwordStrengthIndicator;
    private TextView tvPasswordStrength;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private PreferenceManager prefManager;
    private CryptoManager cryptoManager;

    // Password strength patterns
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+=\\-\\[\\]{};':\"\\\\|,.<>/?].*");

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        initFirebase();
        setupListeners();
    }

    private void initViews(View view) {
        etName = view.findViewById(R.id.et_name);
        etEmail = view.findViewById(R.id.et_email);
        etPassword = view.findViewById(R.id.et_password);
        btnRegister = view.findViewById(R.id.btn_register);
        cbTerms = view.findViewById(R.id.cb_terms);
        progressBar = view.findViewById(R.id.progress_bar);
        passwordStrengthIndicator = view.findViewById(R.id.password_strength_indicator);
        tvPasswordStrength = view.findViewById(R.id.tv_password_strength);
    }

    private void initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        prefManager = new PreferenceManager(requireContext());
        cryptoManager = CryptoManager.getInstance();
    }

    private void setupListeners() {
        // Password strength checker
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePasswordStrength(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Terms checkbox listener
        cbTerms.setOnCheckedChangeListener((buttonView, isChecked) -> {
            validateForm();
        });

        // Text change listeners for form validation
        etName.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateForm();
            }
        });

        etEmail.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateForm();
            }
        });

        // Register button click
        btnRegister.setOnClickListener(v -> register());
    }

    private void updatePasswordStrength(String password) {
        int strength = calculatePasswordStrength(password);

        // Update progress indicator
        passwordStrengthIndicator.setProgress(strength);

        // Update color and text based on strength
        String strengthText;
        int color;

        if (strength < 25) {
            strengthText = "Weak";
            color = getResources().getColor(R.color.error, null);
        } else if (strength < 50) {
            strengthText = "Fair";
            color = getResources().getColor(R.color.warning, null);
        } else if (strength < 75) {
            strengthText = "Good";
            color = getResources().getColor(android.R.color.holo_blue_light, null);
        } else {
            strengthText = "Strong";
            color = getResources().getColor(R.color.success, null);
        }

        tvPasswordStrength.setText("Password Strength: " + strengthText);
        tvPasswordStrength.setTextColor(color);
        passwordStrengthIndicator.setIndicatorColor(color);

        validateForm();
    }

    private int calculatePasswordStrength(String password) {
        int strength = 0;

        // Length score (max 40 points)
        if (password.length() >= 8) strength += 10;
        if (password.length() >= 10) strength += 10;
        if (password.length() >= 12) strength += 10;
        if (password.length() >= 14) strength += 10;

        // Character variety (max 60 points)
        if (LOWERCASE_PATTERN.matcher(password).matches()) strength += 15;
        if (UPPERCASE_PATTERN.matcher(password).matches()) strength += 15;
        if (DIGIT_PATTERN.matcher(password).matches()) strength += 15;
        if (SPECIAL_CHAR_PATTERN.matcher(password).matches()) strength += 15;

        return Math.min(strength, 100);
    }

    private void validateForm() {
        boolean isValid = !TextUtils.isEmpty(etName.getText()) &&
                !TextUtils.isEmpty(etEmail.getText()) &&
                etPassword.getText().length() >= 8 &&
                cbTerms.isChecked() &&
                calculatePasswordStrength(etPassword.getText().toString()) >= 50;

        btnRegister.setEnabled(isValid);
    }

    /**
     * Registers a new user with Firebase Authentication and saves profile to database.
     */
    private void register() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (!isValidEmail(email)) {
            etEmail.setError("Invalid email format");
            return;
        }

        showLoading(true);

        // Create user with Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // Update display name
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();

                            firebaseUser.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        if (profileTask.isSuccessful()) {
                                            // Create user in database
                                            createUserInDatabase(firebaseUser, name);
                                        }
                                    });

                            // Send verification email
                            firebaseUser.sendEmailVerification()
                                    .addOnCompleteListener(emailTask -> {
                                        if (emailTask.isSuccessful()) {
                                            Toast.makeText(getContext(),
                                                    "Verification email sent to " + email,
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }
                    } else {
                        showLoading(false);
                        Toast.makeText(getContext(),
                                "Registration failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void createUserInDatabase(FirebaseUser firebaseUser, String name) {
        // Create user object
        User user = new User(firebaseUser.getUid(), firebaseUser.getEmail(), name);

        // Generate initial Merkle root for empty progress
        String initialMerkleRoot = MerkleTreeManager.getInstance()
                .computeMerkleRoot(user.getProgress().values());
        user.setMerkleRoot(initialMerkleRoot);

        // Save to database
        mDatabase.child(Constants.USERS_NODE)
                .child(firebaseUser.getUid())
                .setValue(user)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        // Save user ID to preferences
                        prefManager.setUserId(firebaseUser.getUid());
                        prefManager.setFirstTime(true);

                        // Navigate to main activity
                        navigateToMain();
                    } else {
                        Toast.makeText(getContext(),
                                "Failed to create user profile",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void navigateToMain() {
        Intent intent = new Intent(getActivity(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        getActivity().finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!show);
    }

    // Simple TextWatcher implementation
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void afterTextChanged(Editable s) {}
    }
}