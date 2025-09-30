package com.example.asltranslator.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.core.content.FileProvider;

import com.example.asltranslator.R;
import com.example.asltranslator.network.CryptoManager;
import com.example.asltranslator.utils.Constants;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import android.graphics.BitmapFactory;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int GALLERY_REQUEST_CODE = 101;
    private static final int CAMERA_PERMISSION_CODE = 102;

    private Uri photoUri;

    // Views
    private Toolbar toolbar;
    private ImageView ivProfilePicture;
    private TextView tvUserName, tvUserEmail, tvMemberSince, tvAccountType;
    private TextInputEditText etDisplayName, etCurrentPassword, etNewPassword, etConfirmPassword;
    private TextInputLayout tilCurrentPassword, tilNewPassword, tilConfirmPassword;
    private MaterialButton btnUpdateProfile, btnChangePassword, btnChangeEmail, btnChangeProfilePicture, btnDeleteAccount;
    private MaterialCardView cardProfileInfo, cardSecurity, cardDangerZone;
    private ProgressBar progressBar;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference mDatabase;
    private StorageReference mStorage;
    private CryptoManager cryptoManager;

    // State
    private boolean isUpdatingPassword = false;
    private boolean isGoogleUser = false;
    private Uri selectedImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        initFirebase();
        setupToolbar();
        setupClickListeners();
        loadUserData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        ivProfilePicture = findViewById(R.id.iv_profile_picture);
        tvUserName = findViewById(R.id.tv_user_name);
        tvUserEmail = findViewById(R.id.tv_user_email);
        tvMemberSince = findViewById(R.id.tv_member_since);
        tvAccountType = findViewById(R.id.tv_account_type);
        etDisplayName = findViewById(R.id.et_display_name);
        etCurrentPassword = findViewById(R.id.et_current_password);
        etNewPassword = findViewById(R.id.et_new_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        tilCurrentPassword = findViewById(R.id.til_current_password);
        tilNewPassword = findViewById(R.id.til_new_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);
        btnUpdateProfile = findViewById(R.id.btn_update_profile);
        btnChangePassword = findViewById(R.id.btn_change_password);
        btnChangeEmail = findViewById(R.id.btn_change_email);
        btnChangeProfilePicture = findViewById(R.id.btn_change_profile_picture);
        btnDeleteAccount = findViewById(R.id.btn_delete_account);
        cardProfileInfo = findViewById(R.id.card_profile_info);
        cardSecurity = findViewById(R.id.card_security);
        cardDangerZone = findViewById(R.id.card_danger_zone);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mStorage = FirebaseStorage.getInstance().getReference();
        cryptoManager = CryptoManager.getInstance();

        if (currentUser == null) {
            Intent intent = new Intent(this, AuthActivity.class);
            startActivity(intent);
            finish();
            return;
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Profile Settings");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupClickListeners() {
        btnUpdateProfile.setOnClickListener(v -> updateDisplayName());
        btnChangePassword.setOnClickListener(v -> {
            if (isUpdatingPassword) {
                updatePassword();
            } else {
                togglePasswordChange();
            }
        });
        btnChangeEmail.setOnClickListener(v -> {
            if (isGoogleUser) {
                showGoogleEmailChangeInfo();
            } else {
                showChangeEmailDialog();
            }
        });
        btnChangeProfilePicture.setOnClickListener(v -> showImagePickerDialog());
        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void loadUserData() {
        if (currentUser != null) {
            // Check if user signed in with Google
            checkUserProvider();

            // Basic user info
            tvUserName.setText(currentUser.getDisplayName() != null ?
                    currentUser.getDisplayName() : "User");
            tvUserEmail.setText(currentUser.getEmail());
            etDisplayName.setText(currentUser.getDisplayName());

            // Account type
            tvAccountType.setText(isGoogleUser ? "Google Account" : "Email Account");
            tvAccountType.setVisibility(View.VISIBLE);

            // Member since
            long creationTime = currentUser.getMetadata() != null ?
                    currentUser.getMetadata().getCreationTimestamp() : System.currentTimeMillis();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy",
                    java.util.Locale.getDefault());
            tvMemberSince.setText("Member since " + sdf.format(new java.util.Date(creationTime)));

            // Configure UI based on user type
            configureUIForUserType();

            // Load local profile picture if exists
            loadProfilePictureFromLocal();
        }
    }

    private void checkUserProvider() {
        isGoogleUser = false;
        if (currentUser.getProviderData() != null) {
            for (UserInfo profile : currentUser.getProviderData()) {
                if (GoogleAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                    isGoogleUser = true;
                    break;
                }
            }
        }
    }

    private void configureUIForUserType() {
        if (isGoogleUser) {
            // Hide password functionality for Google users
            btnChangePassword.setVisibility(View.GONE);
            tilCurrentPassword.setVisibility(View.GONE);
            tilNewPassword.setVisibility(View.GONE);
            tilConfirmPassword.setVisibility(View.GONE);

            // Change email button text for Google users
            btnChangeEmail.setText("Email Settings");
            btnChangeEmail.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_google, 0, 0, 0);
        } else {
            // Show all functionality for email users
            btnChangePassword.setVisibility(View.VISIBLE);
            btnChangeEmail.setText("Change Email Address");
        }
    }

    private void loadProfilePicture(Uri photoUri) {
        // This method is not used anymore - we load from local storage instead
    }

    private void loadProfilePictureFromLocal() {
        String filename = "profile_" + currentUser.getUid() + ".jpg";
        try {
            FileInputStream fis = openFileInput(filename);
            Bitmap bitmap = BitmapFactory.decodeStream(fis);
            fis.close();
            if (bitmap != null) {
                ivProfilePicture.setImageBitmap(bitmap);
            }
        } catch (IOException e) {
            Log.d(TAG, "No local profile picture found");
            // Keep default placeholder
        }
    }

    private void updateDisplayName() {
        String newName = etDisplayName.getText().toString().trim();

        if (TextUtils.isEmpty(newName)) {
            etDisplayName.setError("Display name cannot be empty");
            return;
        }

        showLoading(true);

        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build();

        currentUser.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        mDatabase.child(Constants.USERS_NODE)
                                .child(currentUser.getUid())
                                .child("displayName")
                                .setValue(newName)
                                .addOnCompleteListener(dbTask -> {
                                    showLoading(false);
                                    if (dbTask.isSuccessful()) {
                                        tvUserName.setText(newName);
                                        Toast.makeText(this, "Profile updated successfully",
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(this, "Failed to update database",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        showLoading(false);
                        Toast.makeText(this, "Failed to update profile: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void togglePasswordChange() {
        isUpdatingPassword = !isUpdatingPassword;

        if (isUpdatingPassword) {
            tilCurrentPassword.setVisibility(View.VISIBLE);
            tilNewPassword.setVisibility(View.VISIBLE);
            tilConfirmPassword.setVisibility(View.VISIBLE);
            btnChangePassword.setText("Update Password");
        } else {
            tilCurrentPassword.setVisibility(View.GONE);
            tilNewPassword.setVisibility(View.GONE);
            tilConfirmPassword.setVisibility(View.GONE);
            etCurrentPassword.setText("");
            etNewPassword.setText("");
            etConfirmPassword.setText("");
            btnChangePassword.setText("Change Password");
        }
    }

    private void updatePassword() {
        String currentPassword = etCurrentPassword.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(currentPassword)) {
            etCurrentPassword.setError("Current password is required");
            return;
        }

        if (TextUtils.isEmpty(newPassword)) {
            etNewPassword.setError("New password is required");
            return;
        }

        if (newPassword.length() < 6) {
            etNewPassword.setError("Password must be at least 6 characters");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        showLoading(true);

        AuthCredential credential = EmailAuthProvider.getCredential(
                currentUser.getEmail(), currentPassword);

        currentUser.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        currentUser.updatePassword(newPassword)
                                .addOnCompleteListener(updateTask -> {
                                    showLoading(false);
                                    if (updateTask.isSuccessful()) {
                                        Toast.makeText(this, "Password updated successfully",
                                                Toast.LENGTH_SHORT).show();
                                        togglePasswordChange();
                                    } else {
                                        Toast.makeText(this, "Failed to update password: " +
                                                        updateTask.getException().getMessage(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                    } else {
                        showLoading(false);
                        Toast.makeText(this, "Current password is incorrect",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showGoogleEmailChangeInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Google Account Email")
                .setMessage("To change your email address, please visit your Google Account settings.\n\n" +
                        "Your email is managed by Google and cannot be changed from this app.")
                .setPositiveButton("Open Google Account", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/personal-info"));
                        intent.addCategory(Intent.CATEGORY_BROWSABLE);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "Unable to open browser. Please check your device settings.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("OK", null)
                .setIcon(R.drawable.ic_google)
                .show();
    }


    private void showChangeEmailDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_email, null);

        TextInputEditText etNewEmail = dialogView.findViewById(R.id.et_new_email);
        TextInputEditText etPasswordForEmail = dialogView.findViewById(R.id.et_password_for_email);

        etNewEmail.setText(currentUser.getEmail());

        builder.setView(dialogView)
                .setTitle("Change Email Address")
                .setPositiveButton("Update Email", null)
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newEmail = etNewEmail.getText().toString().trim();
            String password = etPasswordForEmail.getText().toString().trim();

            if (TextUtils.isEmpty(newEmail) || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                etNewEmail.setError("Valid email is required");
                return;
            }

            if (TextUtils.isEmpty(password)) {
                etPasswordForEmail.setError("Password is required");
                return;
            }

            updateEmail(newEmail, password, dialog);
        });
    }

    private void updateEmail(String newEmail, String password, AlertDialog dialog) {
        showLoading(true);

        AuthCredential credential = EmailAuthProvider.getCredential(
                currentUser.getEmail(), password);

        currentUser.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        currentUser.verifyBeforeUpdateEmail(newEmail)
                                .addOnCompleteListener(updateTask -> {
                                    showLoading(false);
                                    if (updateTask.isSuccessful()) {
                                        dialog.dismiss();
                                        new AlertDialog.Builder(this)
                                                .setTitle("Email Verification Sent")
                                                .setMessage("A verification email has been sent to " + newEmail +
                                                        ". Please check your email and follow the instructions to complete the email change.")
                                                .setPositiveButton("OK", null)
                                                .show();
                                    } else {
                                        Toast.makeText(this, "Failed to update email: " +
                                                        updateTask.getException().getMessage(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                    } else {
                        showLoading(false);
                        Toast.makeText(this, "Password is incorrect",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.\n\n" +
                        "All your progress and data will be permanently lost.")
                .setPositiveButton("Delete Account", (dialog, which) -> {
                    if (isGoogleUser) {
                        deleteGoogleAccount();
                    } else {
                        showPasswordConfirmationForDeletion();
                    }
                })
                .setNegativeButton("Cancel", null)
                .setIcon(R.drawable.ic_delete)
                .show();
    }

    private void deleteGoogleAccount() {
        showLoading(true);
        currentUser.delete()
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show();
                        redirectToAuth();
                    } else {
                        Toast.makeText(this, "Failed to delete account: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showPasswordConfirmationForDeletion() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);

        // Reuse the forgot password dialog layout but change the hint
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_reset_email);
        etPassword.setHint("Enter your password to confirm deletion");

        builder.setView(dialogView)
                .setTitle("Confirm Account Deletion")
                .setMessage("Enter your password to confirm account deletion:")
                .setPositiveButton("Delete Account", null)
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Password is required");
                return;
            }

            deleteEmailAccount(password, dialog);
        });
    }

    private void deleteEmailAccount(String password, AlertDialog dialog) {
        showLoading(true);

        AuthCredential credential = EmailAuthProvider.getCredential(
                currentUser.getEmail(), password);

        currentUser.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        currentUser.delete()
                                .addOnCompleteListener(deleteTask -> {
                                    showLoading(false);
                                    if (deleteTask.isSuccessful()) {
                                        dialog.dismiss();
                                        Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show();
                                        redirectToAuth();
                                    } else {
                                        Toast.makeText(this, "Failed to delete account: " +
                                                deleteTask.getException().getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                    } else {
                        showLoading(false);
                        Toast.makeText(this, "Password is incorrect", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void redirectToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showImagePickerDialog() {
        String[] options = {"Camera", "Gallery"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Profile Picture")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openCamera();
                            break;
                        case 1:
                            openGallery();
                            break;
                    }
                });
        builder.create().show();
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            return;
        }

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() {
        try {
            String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                    java.util.Locale.getDefault()).format(new java.util.Date());
            String imageFileName = "JPEG_" + timeStamp + "_";

            // Use app's private external files directory
            File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);

            // Make sure directory exists
            if (storageDir != null && !storageDir.exists()) {
                boolean created = storageDir.mkdirs();
                Log.d(TAG, "Storage directory created: " + created);
            }

            File imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);
            Log.d(TAG, "Created image file at: " + imageFile.getAbsolutePath());

            return imageFile;
        } catch (IOException e) {
            Log.e(TAG, "Error creating image file", e);
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case CAMERA_REQUEST_CODE:
                    if (data != null && data.getExtras() != null) {
                        Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                        if (imageBitmap != null) {
                            ivProfilePicture.setImageBitmap(imageBitmap);
                            uploadProfilePicture(imageBitmap);
                        }
                    }
                    break;
                case GALLERY_REQUEST_CODE:
                    if (data != null && data.getData() != null) {
                        selectedImageUri = data.getData();
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                                    getContentResolver(), selectedImageUri);
                            if (bitmap != null) {
                                ivProfilePicture.setImageBitmap(bitmap);
                                uploadProfilePicture(bitmap);
                            }
                        } catch (IOException e) {
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
            }
        }
    }

    private void uploadProfilePicture(Bitmap bitmap) {
        showLoading(true);

        try {
            // Save image to internal storage
            String filename = "profile_" + currentUser.getUid() + ".jpg";
            FileOutputStream fos = openFileOutput(filename, MODE_PRIVATE);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();

            // Save the filename to Realtime Database
            mDatabase.child(Constants.USERS_NODE)
                    .child(currentUser.getUid())
                    .child("profileImagePath")
                    .setValue(filename)
                    .addOnCompleteListener(task -> {
                        showLoading(false);
                        if (task.isSuccessful()) {
                            // Keep the image in the UI
                            ivProfilePicture.setImageBitmap(bitmap);

                            // Notify MainActivity to refresh profile picture
                            setResult(RESULT_OK);

                            Toast.makeText(this, "Profile picture updated successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Failed to save profile picture", Toast.LENGTH_SHORT).show();
                        }
                    });

        } catch (IOException e) {
            showLoading(false);
            Log.e(TAG, "Error saving profile picture", e);
            Toast.makeText(this, "Error saving profile picture", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnUpdateProfile.setEnabled(!show);
        btnChangePassword.setEnabled(!show);
        btnChangeEmail.setEnabled(!show);
        btnChangeProfilePicture.setEnabled(!show);
        btnDeleteAccount.setEnabled(!show);
    }
}