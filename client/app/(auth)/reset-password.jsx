// ===== FIXED RESET PASSWORD SCREEN =====
// app/(auth)/reset-password.jsx
import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, TextInput, StyleSheet, ScrollView, Pressable, Platform, Alert, KeyboardAvoidingView } from 'react-native';
import { ActivityIndicator } from 'react-native-paper';
import PasswordResetService from '../../services/PasswordResetService';
import Toast from '../../components/Toast';
import { useRouter, useLocalSearchParams } from 'expo-router';
import Constants from '../../utils/Constants';
import { useTheme } from '../../utils/ThemeContext';
import { FontAwesome } from '@expo/vector-icons';

const { FORM, SCREEN, ICON, COLORS } = Constants;

const ResetPasswordScreen = () => {
  const { isDarkMode } = useTheme();
  const { token } = useLocalSearchParams(); // Get token from URL parameters
  console.log('=== RESET PASSWORD SCREEN LOADED ===');
  console.log('Token from params:', token);
  console.log('All params:', useLocalSearchParams());
  
  // States for both modes
  const [email, setEmail] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [toastVisible, setToastVisible] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isResetting, setIsResetting] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [isValidToken, setIsValidToken] = useState(true);
  const [isMounted, setIsMounted] = useState(false);
  const router = useRouter();

  // Check if we're in forgot password mode (no token) or reset mode (with token)
  const isForgotMode = !token;

  useEffect(() => {
    setIsMounted(true);
    return () => setIsMounted(false);
  }, []);

  useEffect(() => {
    // Only validate if component is mounted and we have a token that's empty
    if (isMounted && token !== undefined && token !== null && !token.trim()) {
      // Use setTimeout to avoid state update during render
      const timer = setTimeout(() => {
        if (isMounted) {
          setIsValidToken(false);
          setError('Invalid or missing reset token');
          setToastVisible(true);
        }
      }, 0);
      
      return () => clearTimeout(timer);
    }
  }, [token, isMounted]);

  // Handle forgot password (send email)
  const handleForgotPassword = useCallback(async () => {
    if (!isMounted) return;
    
    setError('');
    
    if (!email || !email.trim()) {
      setError('Email is required');
      setToastVisible(true);
      return;
    }

    if (!/\S+@\S+\.\S+/.test(email)) {
      setError('Please enter a valid email address');
      setToastVisible(true);
      return;
    }

    setIsSending(true);
    try {
      const result = await PasswordResetService.requestPasswordReset(email);
      
      if (result.success) {
        Alert.alert(
          'Password Reset',
          result.message || 'If an account with this email exists, a password reset link will be sent to your email.',
          [
            {
              text: 'OK',
              onPress: () => router.replace('/(auth)/login')
            }
          ]
        );
      } else {
        if (isMounted) {
          setError(result.error || 'Failed to send reset email');
          setToastVisible(true);
        }
      }
    } catch (err) {
      if (isMounted) {
        setError('Failed to send reset email');
        setToastVisible(true);
      }
    } finally {
      if (isMounted) {
        setIsSending(false);
      }
    }
  }, [email, router, isMounted]);

  // Handle reset password (with token)
  const handleResetPassword = useCallback(async () => {
    if (!isMounted) return;
    
    setError('');

    // Validation
    if (!newPassword || !confirmPassword) {
      setError('Both password fields are required');
      setToastVisible(true);
      return;
    }

    if (newPassword.length < 6) {
      setError('Password must be at least 6 characters long');
      setToastVisible(true);
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match');
      setToastVisible(true);
      return;
    }

    if (!token) {
      setError('Invalid reset token');
      setToastVisible(true);
      return;
    }

    setIsResetting(true);
    try {
      const result = await PasswordResetService.resetPassword(token, newPassword);
      
      if (result.success) {
        Alert.alert(
          'Password Reset Successful',
          'Your password has been reset successfully. You can now log in with your new password.',
          [
            {
              text: 'Go to Login',
              onPress: () => router.replace('/(auth)/login')
            }
          ]
        );
      } else {
        if (isMounted) {
          setError(result.error || 'Failed to reset password');
          setToastVisible(true);
        }
      }
    } catch (err) {
      if (isMounted) {
        setError(err.message || 'Failed to reset password');
        setToastVisible(true);
      }
    } finally {
      if (isMounted) {
        setIsResetting(false);
      }
    }
  }, [newPassword, confirmPassword, token, router, isMounted]);

  const goToLogin = useCallback(() => {
    router.replace('/(auth)/login');
  }, [router]);

  // Show invalid token screen only for reset mode
  if (!isForgotMode && !isValidToken) {
    return (
      <View style={[styles.container, { 
        backgroundColor: isDarkMode ? SCREEN.BACKGROUND_COLOR_DARK : SCREEN.BACKGROUND_COLOR_LIGHT 
      }]}>
        <View style={styles.centerContent}>
          <FontAwesome name="exclamation-triangle" size={60} color={COLORS.DESTRUCTIVE} />
          <Text style={[styles.errorTitle, { color: COLORS.DESTRUCTIVE }]}>
            Invalid Reset Link
          </Text>
          <Text style={[styles.errorMessage, { 
            color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
          }]}>
            This password reset link is invalid or has expired. Please request a new one.
          </Text>
          <Pressable
            onPress={goToLogin}
            style={[styles.button, { backgroundColor: COLORS.PRIMARY }]}
            accessibilityLabel="Go to login"
            accessibilityRole="button"
          >
            <Text style={styles.buttonLabel}>Go to Login</Text>
          </Pressable>
        </View>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      style={{ flex: 1 }}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
    >
      <View style={[styles.container, { 
        backgroundColor: isDarkMode ? SCREEN.BACKGROUND_COLOR_DARK : SCREEN.BACKGROUND_COLOR_LIGHT 
      }]}>
        <View style={styles.headerContainer}>
          <Pressable
            onPress={goToLogin}
            style={({ pressed }) => [styles.backButton, { opacity: pressed ? 0.7 : 1 }]}
            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
            accessibilityLabel="Go to login"
            accessibilityRole="button"
          >
            <FontAwesome 
              name="arrow-left" 
              size={24} 
              color={isDarkMode ? ICON.COLOR_DARK : ICON.COLOR_LIGHT} 
            />
          </Pressable>
        </View>

        <ScrollView contentContainerStyle={styles.scrollContent}>
          <View style={[styles.formContainer, { 
            backgroundColor: isDarkMode ? FORM.BACKGROUND_COLOR_DARK : FORM.BACKGROUND_COLOR_LIGHT 
          }]}>
            <FontAwesome 
              name={isForgotMode ? "envelope" : "lock"} 
              size={40} 
              color={COLORS.PRIMARY} 
              style={styles.lockIcon} 
            />
            
            <Text style={[styles.title, { 
              color: isDarkMode ? FORM.TITLE_COLOR_DARK : FORM.TITLE_COLOR_LIGHT 
            }]}>
              {isForgotMode ? 'Reset Password' : 'Set New Password'}
            </Text>

            <Text style={[styles.description, { 
              color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
            }]}>
              {isForgotMode 
                ? 'Enter your email address and we\'ll send you a link to reset your password.'
                : 'Please enter your new password below.'
              }
            </Text>

            {/* Google Sign-In Note - ONLY show in forgot password mode */}
            {isForgotMode && (
              <View style={[styles.noteContainer, {
                backgroundColor: isDarkMode ? 'rgba(0, 122, 255, 0.2)' : 'rgba(0, 122, 255, 0.1)',
                borderLeftColor: COLORS.PRIMARY,
              }]}>
                <FontAwesome name="info-circle" size={16} color={COLORS.PRIMARY} />
                <Text style={[styles.noteText, { 
                  color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
                }]}>
                  Note: If you signed up with Google, please use the "Sign in with Google" button instead.
                </Text>
              </View>
            )}

            {/* FORGOT PASSWORD MODE */}
            {isForgotMode && (
              <>
                {isSending && (
                  <ActivityIndicator
                    size="large"
                    color={isDarkMode ? '#FFF' : COLORS.PRIMARY}
                    style={styles.loading}
                    accessibilityLabel="Sending reset email"
                  />
                )}

                <Text style={[styles.label, { 
                  color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
                }]}>
                  Email
                </Text>
                
                <TextInput
                  style={[styles.input, {
                    backgroundColor: isDarkMode ? FORM.INPUT_BACKGROUND_COLOR_DARK : FORM.INPUT_BACKGROUND_COLOR_LIGHT,
                    color: isDarkMode ? FORM.INPUT_TEXT_COLOR_DARK : FORM.INPUT_TEXT_COLOR_LIGHT,
                    borderColor: FORM.INPUT_BORDER_COLOR,
                    borderWidth: FORM.INPUT_BORDER_WIDTH,
                    borderRadius: FORM.INPUT_BORDER_RADIUS,
                  }]}
                  placeholder="Enter your email"
                  placeholderTextColor={isDarkMode ? FORM.INPUT_PLACEHOLDER_COLOR_DARK : FORM.INPUT_PLACEHOLDER_COLOR_LIGHT}
                  value={email}
                  onChangeText={(text) => {
                    setEmail(text);
                    setError('');
                  }}
                  keyboardType="email-address"
                  autoCapitalize="none"
                  editable={!isSending}
                  accessibilityLabel="Email address input"
                />

                <Pressable
                  onPress={handleForgotPassword}
                  disabled={isSending}
                  style={({ pressed }) => [styles.button, { opacity: pressed ? 0.8 : 1 }]}
                  accessibilityLabel="Send reset link"
                  accessibilityRole="button"
                >
                  <Text style={styles.buttonLabel}>Send Reset Link</Text>
                </Pressable>
              </>
            )}

            {/* RESET PASSWORD MODE */}
            {!isForgotMode && (
              <>
                {isResetting && (
                  <ActivityIndicator
                    size="large"
                    color={isDarkMode ? '#FFF' : COLORS.PRIMARY}
                    style={styles.loading}
                    accessibilityLabel="Resetting password"
                  />
                )}

                <Text style={[styles.label, { 
                  color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
                }]}>
                  New Password
                </Text>
                <View style={styles.inputContainer}>
                  <TextInput
                    style={[styles.input, {
                      backgroundColor: isDarkMode ? FORM.INPUT_BACKGROUND_COLOR_DARK : FORM.INPUT_BACKGROUND_COLOR_LIGHT,
                      color: isDarkMode ? FORM.INPUT_TEXT_COLOR_DARK : FORM.INPUT_TEXT_COLOR_LIGHT,
                      borderColor: FORM.INPUT_BORDER_COLOR,
                      borderWidth: FORM.INPUT_BORDER_WIDTH,
                      borderRadius: FORM.INPUT_BORDER_RADIUS,
                    }]}
                    placeholder="Enter new password"
                    placeholderTextColor={isDarkMode ? FORM.INPUT_PLACEHOLDER_COLOR_DARK : FORM.INPUT_PLACEHOLDER_COLOR_LIGHT}
                    value={newPassword}
                    onChangeText={(text) => {
                      setNewPassword(text);
                      setError('');
                    }}
                    secureTextEntry={!showNewPassword}
                    editable={!isResetting}
                    accessibilityLabel="New password input"
                  />
                  <Pressable
                    onPress={() => setShowNewPassword(!showNewPassword)}
                    style={styles.toggleButton}
                    accessibilityLabel={showNewPassword ? "Hide password" : "Show password"}
                    accessibilityRole="button"
                  >
                    <FontAwesome
                      name={showNewPassword ? "eye-slash" : "eye"}
                      size={20}
                      color={isDarkMode ? ICON.COLOR_DARK : ICON.COLOR_LIGHT}
                    />
                  </Pressable>
                </View>

                <Text style={[styles.label, { 
                  color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
                }]}>
                  Confirm Password
                </Text>
                <View style={styles.inputContainer}>
                  <TextInput
                    style={[styles.input, {
                      backgroundColor: isDarkMode ? FORM.INPUT_BACKGROUND_COLOR_DARK : FORM.INPUT_BACKGROUND_COLOR_LIGHT,
                      color: isDarkMode ? FORM.INPUT_TEXT_COLOR_DARK : FORM.INPUT_TEXT_COLOR_LIGHT,
                      borderColor: FORM.INPUT_BORDER_COLOR,
                      borderWidth: FORM.INPUT_BORDER_WIDTH,
                      borderRadius: FORM.INPUT_BORDER_RADIUS,
                    }]}
                    placeholder="Confirm new password"
                    placeholderTextColor={isDarkMode ? FORM.INPUT_PLACEHOLDER_COLOR_DARK : FORM.INPUT_PLACEHOLDER_COLOR_LIGHT}
                    value={confirmPassword}
                    onChangeText={(text) => {
                      setConfirmPassword(text);
                      setError('');
                    }}
                    secureTextEntry={!showConfirmPassword}
                    editable={!isResetting}
                    accessibilityLabel="Confirm password input"
                  />
                  <Pressable
                    onPress={() => setShowConfirmPassword(!showConfirmPassword)}
                    style={styles.toggleButton}
                    accessibilityLabel={showConfirmPassword ? "Hide password" : "Show password"}
                    accessibilityRole="button"
                  >
                    <FontAwesome
                      name={showConfirmPassword ? "eye-slash" : "eye"}
                      size={20}
                      color={isDarkMode ? ICON.COLOR_DARK : ICON.COLOR_LIGHT}
                    />
                  </Pressable>
                </View>

                <View style={styles.passwordHints}>
                  <Text style={[styles.hintText, { 
                    color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
                  }]}>
                    Password must be at least 6 characters long
                  </Text>
                </View>

                <Pressable
                  onPress={handleResetPassword}
                  disabled={isResetting}
                  style={({ pressed }) => [styles.button, { opacity: pressed ? 0.8 : 1 }]}
                  accessibilityLabel="Reset password"
                  accessibilityRole="button"
                >
                  <Text style={styles.buttonLabel}>Reset Password</Text>
                </Pressable>
              </>
            )}

            <Pressable
              onPress={goToLogin}
              disabled={isResetting || isSending}
              style={({ pressed }) => [styles.cancelButton, { opacity: pressed ? 0.8 : 1 }]}
              accessibilityLabel="Back to login"
              accessibilityRole="button"
            >
              <Text style={styles.cancelText}>Back to Login</Text>
            </Pressable>
          </View>
        </ScrollView>

        {error && (
          <Toast
            message={error}
            visible={toastVisible}
            onHide={() => {
              setToastVisible(false);
              setError('');
            }}
            isDarkMode={isDarkMode}
          />
        )}
      </View>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  centerContent: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  headerContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 15,
    marginTop: Platform.OS === 'android' ? 20 : 0,
  },
  backButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(0,0,0,0.2)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: 20,
    paddingBottom: 40,
  },
  formContainer: {
    borderRadius: 20,
    padding: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.1,
    shadowRadius: 10,
    elevation: 5,
    alignItems: 'center',
  },
  lockIcon: {
    marginBottom: 20,
  },
  title: {
    fontSize: 26,
    fontWeight: '700',
    marginBottom: 10,
    textAlign: 'center',
  },
  description: {
    fontSize: 14,
    marginBottom: 20,
    textAlign: 'center',
    lineHeight: 20,
  },
  noteContainer: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    padding: 12,
    borderRadius: 8,
    marginBottom: 20,
    borderLeftWidth: 3,
    width: '100%',
  },
  noteText: {
    fontSize: 14,
    marginLeft: 8,
    flex: 1,
    lineHeight: 20,
    fontStyle: 'italic',
  },
  errorTitle: {
    fontSize: 24,
    fontWeight: '700',
    marginTop: 20,
    marginBottom: 10,
    textAlign: 'center',
  },
  errorMessage: {
    fontSize: 16,
    marginBottom: 30,
    textAlign: 'center',
    lineHeight: 22,
  },
  label: {
    fontSize: 16,
    marginBottom: 8,
    fontWeight: '500',
    alignSelf: 'flex-start',
    width: '100%',
  },
  input: {
    width: '100%',
    padding: 12,
    marginBottom: 16,
  },
  inputContainer: {
    position: 'relative',
    width: '100%',
  },
  toggleButton: {
    position: 'absolute',
    right: 12,
    top: 14,
  },
  passwordHints: {
    width: '100%',
    marginBottom: 20,
  },
  hintText: {
    fontSize: 12,
    fontStyle: 'italic',
  },
  loading: {
    marginBottom: 20,
  },
  button: {
    width: '100%',
    paddingVertical: 14,
    borderRadius: 25,
    backgroundColor: COLORS.PRIMARY,
    alignItems: 'center',
    marginBottom: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
    elevation: 3,
  },
  buttonLabel: {
    fontSize: 18,
    fontWeight: '600',
    color: '#FFF',
  },
  cancelButton: {
    width: '100%',
    paddingVertical: 12,
    borderRadius: 25,
    backgroundColor: COLORS.DESTRUCTIVE,
    alignItems: 'center',
    marginTop: 10,
  },
  cancelText: {
    fontSize: 16,
    fontWeight: '500',
    color: '#FFF',
  },
});

export default ResetPasswordScreen;