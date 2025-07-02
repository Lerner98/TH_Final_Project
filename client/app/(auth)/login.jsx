// app/(auth)/login.jsx
import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, TextInput, StyleSheet, ScrollView, Pressable, Platform, Alert, KeyboardAvoidingView } from 'react-native';
import { ActivityIndicator } from 'react-native-paper';
import { useTranslation } from '../../utils/TranslationContext';
import { useEnhancedSession } from '../../utils/EnhancedSessionContext';
import GoogleSignInService from '../../services/GoogleSignInService';
import PasswordResetService from '../../services/PasswordResetService';
import Toast from '../../components/Toast';
import { useRouter } from 'expo-router';
import Constants from '../../utils/Constants';
import { useTheme } from '../../utils/ThemeContext';
import { FontAwesome } from '@expo/vector-icons';

const { FORM, SCREEN, ICON, ERROR_MESSAGES, COLORS } = Constants;

const LoginScreen = () => {
  const { t } = useTranslation();
  const { signIn, isAuthLoading, error: sessionError, clearError } = useEnhancedSession();
  const { isDarkMode } = useTheme();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [toastVisible, setToastVisible] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [isGoogleLoading, setIsGoogleLoading] = useState(false);
  const [showForgotPassword, setShowForgotPassword] = useState(false);
  const [resetEmail, setResetEmail] = useState('');
  const [isResettingPassword, setIsResettingPassword] = useState(false);
  const router = useRouter();

  useEffect(() => {
    if (sessionError) {
      setError(sessionError);
      setToastVisible(true);
    }
  }, [sessionError]);

  const handleLogin = useCallback(async () => {
    setError('');

    if (!email || !password) {
      setError(t('error') + ': ' + ERROR_MESSAGES.LOGIN_EMAIL_PASSWORD_REQUIRED);
      setToastVisible(true);
      return;
    }
    
    if (!/\S+@\S+\.\S+/.test(email)) {
      setError(t('error') + ': ' + ERROR_MESSAGES.LOGIN_INVALID_EMAIL);
      setToastVisible(true);
      return;
    }
    
    if (password.length < 6) {
      setError(t('error') + ': ' + ERROR_MESSAGES.LOGIN_PASSWORD_MIN_LENGTH);
      setToastVisible(true);
      return;
    }

    try {
      await signIn(email, password);
      setTimeout(() => {
        router.replace('/(drawer)/(tabs)');
      }, 100);
    } catch (err) {
      setError(t('error') + ': ' + err.message);
      setToastVisible(true);
    }
  }, [email, password, t, signIn, router]);

  const handleGoogleSignIn = useCallback(async () => {
    console.log('🔵 [LOGIN] Google Sign-In button pressed');
    setIsGoogleLoading(true);
    try {
      const result = await GoogleSignInService.signIn();
      
      console.log('🔵 [LOGIN] Google Sign-In result:', JSON.stringify(result, null, 2));
      
      if (result.success) {
        console.log('🟢 [LOGIN] Google Sign-In successful, proceeding with app sign-in...');
        
        const userEmail = result.user?.email;
        if (!userEmail) {
          console.log('🔴 [LOGIN] No email found in result:', JSON.stringify(result.user, null, 2));
          throw new Error('Email not found in Google Sign-In response');
        }
        console.log('🔵 [LOGIN] User email:', userEmail);
        
        await signIn(userEmail, null, {
          isGoogleAuth: true,
          token: result.token,
          user: result.user,
          authMethod: 'google',
        });
        
        console.log('🟢 [LOGIN] App sign-in completed successfully!');
        setTimeout(() => {
          router.replace('/(drawer)/(tabs)');
        }, 100);
      } else if (result.cancelled) {
        console.log('🟡 [LOGIN] User cancelled Google sign-in');
      } else {
        console.log('🔴 [LOGIN] Google Sign-In failed:', result.error);
        setError(t('error') + ': ' + result.error);
        setToastVisible(true);
      }
    } catch (err) {
      console.log('🔴 [LOGIN] Unexpected error during Google Sign-In:', err);
      setError(t('error') + ': ' + err.message);
      setToastVisible(true);
    } finally {
      setIsGoogleLoading(false);
      console.log('🔵 [LOGIN] Google Sign-In process completed');
    }
  }, [t, signIn, router]);

  const handleForgotPassword = useCallback(async () => {
    if (!resetEmail) {
      setError(t('error') + ': Email is required');
      setToastVisible(true);
      return;
    }

    if (!/\S+@\S+\.\S+/.test(resetEmail)) {
      setError(t('error') + ': Please enter a valid email address');
      setToastVisible(true);
      return;
    }

    setIsResettingPassword(true);
    try {
      const result = await PasswordResetService.requestPasswordReset(resetEmail);
      
      if (result.success) {
        Alert.alert(
          'Password Reset',
          'If an account with this email exists, a password reset link has been sent to your email.',
          [{ text: 'OK', onPress: () => setShowForgotPassword(false) }]
        );
        setResetEmail('');
      } else {
        setError(t('error') + ': ' + result.error);
        setToastVisible(true);
      }
    } catch (err) {
      setError(t('error') + ': ' + err.message);
      setToastVisible(true);
    } finally {
      setIsResettingPassword(false);
    }
  }, [resetEmail, t]);

  if (showForgotPassword) {
    return (
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
      >
        <View style={[styles.container, { 
          backgroundColor: isDarkMode ? SCREEN.BACKGROUND_COLOR_DARK : SCREEN.BACKGROUND_COLOR_LIGHT 
        }]}>
          <ScrollView contentContainerStyle={styles.scrollContentCentered}>
            <View style={[styles.formContainer, { 
              backgroundColor: isDarkMode ? FORM.BACKGROUND_COLOR_DARK : FORM.BACKGROUND_COLOR_LIGHT 
            }]}>
              <Text style={[styles.title, { 
                color: isDarkMode ? FORM.TITLE_COLOR_DARK : FORM.TITLE_COLOR_LIGHT 
              }]}>
                Reset Password
              </Text>

              <Text style={[styles.description, { 
                color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
              }]}>
                Enter your email address and we'll send you a link to reset your password.
              </Text>

              {isResettingPassword && (
                <ActivityIndicator
                  size="large"
                  color={isDarkMode ? '#FFF' : Constants.COLORS.PRIMARY}
                  style={styles.loading}
                  accessibilityLabel="Sending reset email"
                />
              )}

              <Text style={[styles.label, { 
                color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
              }]}>
                {t('email')}
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
                value={resetEmail}
                onChangeText={(text) => {
                  setResetEmail(text);
                  setError('');
                }}
                keyboardType="email-address"
                autoCapitalize="none"
                editable={!isResettingPassword}
                accessibilityLabel="Reset email input"
              />

              <Pressable
                onPress={handleForgotPassword}
                disabled={isResettingPassword}
                style={({ pressed }) => [styles.button, { opacity: pressed ? 0.8 : 1 }]}
                accessibilityLabel="Send reset email"
                accessibilityRole="button"
              >
                <Text style={styles.buttonLabel}>Send Reset Link</Text>
              </Pressable>

              <Pressable
                onPress={() => setShowForgotPassword(false)}
                disabled={isResettingPassword}
                style={({ pressed }) => [styles.switchButton, { 
                  opacity: pressed ? 0.8 : 1, 
                  backgroundColor: FORM.BUTTON_BACKGROUND_COLOR 
                }]}
                accessibilityLabel="Back to login"
                accessibilityRole="button"
              >
                <Text style={styles.switchText}>Back to Login</Text>
              </Pressable>
            </View>
          </ScrollView>
        </View>
      </KeyboardAvoidingView>
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
        <ScrollView contentContainerStyle={styles.scrollContentCentered}>
          <View style={[styles.formContainer, { 
            backgroundColor: isDarkMode ? FORM.BACKGROUND_COLOR_DARK : FORM.BACKGROUND_COLOR_LIGHT 
          }]}>
            <Text style={[styles.title, { 
              color: isDarkMode ? FORM.TITLE_COLOR_DARK : FORM.TITLE_COLOR_LIGHT 
            }]}>
              {t('login')}
            </Text>

            {(isAuthLoading || isGoogleLoading) && (
              <ActivityIndicator
                size="large"
                color={isDarkMode ? '#FFF' : Constants.COLORS.PRIMARY}
                style={styles.loading}
                accessibilityLabel="Logging in"
              />
            )}

            <Pressable
              onPress={handleGoogleSignIn}
              disabled={isAuthLoading || isGoogleLoading}
              style={({ pressed }) => [
                styles.googleButton, 
                { 
                  backgroundColor: '#4285F4',
                  opacity: pressed ? 0.8 : 1 
                }
              ]}
              accessibilityLabel="Sign in with Google"
              accessibilityRole="button"
            >
              <FontAwesome name="google" size={20} color="#FFF" style={styles.googleIcon} />
              <Text style={styles.googleButtonText}>Continue with Google</Text>
            </Pressable>

            <View style={styles.divider}>
              <View style={[styles.dividerLine, { 
                backgroundColor: isDarkMode ? FORM.INPUT_BORDER_COLOR : '#E0E0E0' 
              }]} />
              <Text style={[styles.dividerText, { 
                color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
              }]}>
                or
              </Text>
              <View style={[styles.dividerLine, { 
                backgroundColor: isDarkMode ? FORM.INPUT_BORDER_COLOR : '#E0E0E0' 
              }]} />
            </View>

            <Text style={[styles.label, { 
              color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
            }]}>
              {t('email')}
            </Text>
            <TextInput
              style={[styles.input, {
                backgroundColor: isDarkMode ? FORM.INPUT_BACKGROUND_COLOR_DARK : FORM.INPUT_BACKGROUND_COLOR_LIGHT,
                color: isDarkMode ? FORM.INPUT_TEXT_COLOR_DARK : FORM.INPUT_TEXT_COLOR_LIGHT,
                borderColor: FORM.INPUT_BORDER_COLOR,
                borderWidth: FORM.INPUT_BORDER_WIDTH,
                borderRadius: FORM.INPUT_BORDER_RADIUS,
              }]}
              placeholder={t('email')}
              placeholderTextColor={isDarkMode ? FORM.INPUT_PLACEHOLDER_COLOR_DARK : FORM.INPUT_PLACEHOLDER_COLOR_LIGHT}
              value={email}
              onChangeText={(text) => {
                setEmail(text);
                setError('');
              }}
              keyboardType="email-address"
              autoCapitalize="none"
              editable={!isAuthLoading && !isGoogleLoading}
              accessibilityLabel="Email input"
            />

            <Text style={[styles.label, { 
              color: isDarkMode ? FORM.LABEL_COLOR_DARK : FORM.LABEL_COLOR_LIGHT 
            }]}>
              {t('password')}
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
                placeholder={t('password')}
                placeholderTextColor={isDarkMode ? FORM.INPUT_PLACEHOLDER_COLOR_DARK : FORM.INPUT_PLACEHOLDER_COLOR_LIGHT}
                value={password}
                onChangeText={(text) => {
                  setPassword(text);
                  setError('');
                }}
                secureTextEntry={!showPassword}
                editable={!isAuthLoading && !isGoogleLoading}
                accessibilityLabel="Password input"
              />
              <Pressable
                onPress={() => setShowPassword(!showPassword)}
                style={styles.toggleButton}
                accessibilityLabel={showPassword ? "Hide password" : "Show password"}
                accessibilityRole="button"
              >
                <FontAwesome
                  name={showPassword ? "eye-slash" : "eye"}
                  size={20}
                  color={isDarkMode ? ICON.COLOR_DARK : ICON.COLOR_LIGHT}
                />
              </Pressable>
            </View>

            <Pressable
              onPress={() => setShowForgotPassword(true)}
              disabled={isAuthLoading || isGoogleLoading}
              style={styles.forgotPasswordLink}
              accessibilityLabel="Forgot password"
              accessibilityRole="button"
            >
              <Text style={[styles.forgotPasswordText, { 
                color: isDarkMode ? Constants.COLORS.PRIMARY : Constants.COLORS.PRIMARY 
              }]}>
                Forgot Password?
              </Text>
            </Pressable>

            <Pressable
              onPress={handleLogin}
              disabled={isAuthLoading || isGoogleLoading}
              style={({ pressed }) => [styles.button, { opacity: pressed ? 0.8 : 1 }]}
              accessibilityLabel="Login button"
              accessibilityRole="button"
            >
              <Text style={styles.buttonLabel}>{t('login')}</Text>
            </Pressable>

            <Pressable
              onPress={() => router.push('/(auth)/register')}
              disabled={isAuthLoading || isGoogleLoading}
              style={({ pressed }) => [styles.switchButton, { 
                opacity: pressed ? 0.8 : 1, 
                backgroundColor: FORM.BUTTON_BACKGROUND_COLOR 
              }]}
              accessibilityLabel="Go to register screen"
              accessibilityRole="button"
            >
              <Text style={styles.switchText}>{t('register')}</Text>
            </Pressable>

            <Pressable
              onPress={() => router.push('/(drawer)/(tabs)')}
              disabled={isAuthLoading || isGoogleLoading}
              style={({ pressed }) => [styles.cancelButton, { opacity: pressed ? 0.8 : 1 }]}
              accessibilityLabel="Go to home screen"
              accessibilityRole="button"
            >
              <Text style={styles.cancelText}>{t('goToHome')}</Text>
            </Pressable>
          </View>
        </ScrollView>

        {(error || sessionError) && (
          <Toast
            message={(error || sessionError)?.toString?.() || ''}
            visible={toastVisible}
            onHide={() => {
              setToastVisible(false);
              clearError();
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
  scrollContentCentered: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: 20,
    paddingVertical: 40,
  },
  formContainer: {
    borderRadius: 20,
    padding: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.1,
    shadowRadius: 10,
    elevation: 5,
  },
  title: {
    fontSize: 26,
    fontWeight: '700',
    marginBottom: 20,
    textAlign: 'center',
  },
  description: {
    fontSize: 14,
    marginBottom: 20,
    textAlign: 'center',
    lineHeight: 20,
  },
  googleButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 14,
    borderRadius: 25,
    marginBottom: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
    elevation: 3,
  },
  googleIcon: {
    marginRight: 12,
  },
  googleButtonText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600',
  },
  divider: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 20,
  },
  dividerLine: {
    flex: 1,
    height: 1,
  },
  dividerText: {
    marginHorizontal: 15,
    fontSize: 14,
    fontWeight: '500',
  },
  label: {
    fontSize: 16,
    marginBottom: 8,
    fontWeight: '500',
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
  forgotPasswordLink: {
    alignSelf: 'flex-end',
    marginBottom: 20,
  },
  forgotPasswordText: {
    fontSize: 14,
    fontWeight: '500',
  },
  loading: {
    marginBottom: 20,
  },
  button: {
    width: '100%',
    paddingVertical: 14,
    borderRadius: 25,
    backgroundColor: Constants.COLORS.PRIMARY,
    alignItems: 'center',
    marginVertical: 10,
    shadowColor: Constants.COLORS.SHADOW,
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
  switchButton: {
    width: '100%',
    paddingVertical: 12,
    borderRadius: 25,
    alignItems: 'center',
    marginVertical: 10,
  },
  switchText: {
    fontSize: 16,
    fontWeight: '500',
    color: Constants.COLORS.PRIMARY,
  },
  cancelButton: {
    width: '100%',
    paddingVertical: 12,
    borderRadius: 25,
    backgroundColor: Constants.COLORS.DESTRUCTIVE,
    alignItems: 'center',
    marginVertical: 10,
  },
  cancelText: {
    fontSize: 16,
    fontWeight: '500',
    color: '#FFF',
  },
});

export default LoginScreen;