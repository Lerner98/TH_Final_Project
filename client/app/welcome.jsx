// ===== UPDATED WELCOME SCREEN =====
// app/welcome.jsx
import React, { useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Alert } from 'react-native';
import { useRouter } from 'expo-router';
import useThemeStore from '../stores/ThemeStore';
import Constants from '../utils/Constants';
import { useEnhancedSession } from '../utils/EnhancedSessionContext';
import { useTranslation } from '../utils/TranslationContext';
import { FontAwesome } from '@expo/vector-icons';

const { WELCOME } = Constants;

const WelcomeScreen = () => {
  const router = useRouter();
  const { t } = useTranslation();
  const { status, isAuthenticated } = useEnhancedSession();
  const { isDarkMode } = useThemeStore();

  const safeTranslate = useCallback((key, fallback = '') => {
    try {
      const val = t(key);
      return typeof val === 'string' ? val : fallback;
    } catch {
      return fallback;
    }
  }, [t]);

  // Redirect if already authenticated with delay
  useEffect(() => {
    if (isAuthenticated) {
      setTimeout(() => {
        router.replace('/(drawer)/(tabs)');
      }, 100);
    }
  }, [isAuthenticated, router]);

  const continueAsGuest = useCallback(async () => {
    router.replace('/(drawer)/(tabs)');
  }, [router]);

  const showGuestLimitInfo = useCallback(() => {
    Alert.alert(
      'Guest Mode',
      'As a guest, you can try up to 10 translations. Sign up for unlimited access and to save your translation history!',
      [
        { text: 'Continue as Guest', onPress: continueAsGuest },
        { text: 'Sign Up', onPress: () => router.push('/(auth)/register') }
      ]
    );
  }, [continueAsGuest, router]);

  // Don't render if still loading auth state
  if (status === 'loading') {
    return null;
  }

  return (
    <View style={[styles.container, { 
      backgroundColor: isDarkMode ? WELCOME.BACKGROUND_COLOR_DARK : WELCOME.BACKGROUND_COLOR_LIGHT 
    }]}>
      <FontAwesome
        name="language"
        size={60}
        color={isDarkMode ? WELCOME.TITLE_COLOR_DARK : WELCOME.TITLE_COLOR_LIGHT}
        style={styles.icon}
      />
      <Text style={[styles.title, { 
        color: isDarkMode ? WELCOME.TITLE_COLOR_DARK : WELCOME.TITLE_COLOR_LIGHT 
      }]}>
        {safeTranslate('welcome', 'TranslationHub')}
      </Text>

      <TouchableOpacity
        style={[styles.button, { 
          backgroundColor: isDarkMode ? WELCOME.GUEST_BUTTON_COLOR_DARK : WELCOME.GUEST_BUTTON_COLOR_LIGHT 
        }]}
        onPress={showGuestLimitInfo}
        accessibilityLabel="Continue as guest"
        accessibilityRole="button"
      >
        <Text style={styles.buttonText}>
          {safeTranslate('continueGuest', 'Continue as Guest')}
        </Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.button, { 
          backgroundColor: isDarkMode ? WELCOME.REGISTER_BUTTON_COLOR_DARK : WELCOME.REGISTER_BUTTON_COLOR_LIGHT 
        }]}
        onPress={() => router.push('/(auth)/register')}
        accessibilityLabel="Register"
        accessibilityRole="button"
      >
        <Text style={styles.buttonText}>
          {safeTranslate('register', 'Sign Up')}
        </Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.button, { 
          backgroundColor: isDarkMode ? WELCOME.LOGIN_BUTTON_COLOR_DARK : WELCOME.LOGIN_BUTTON_COLOR_LIGHT 
        }]}
        onPress={() => router.push('/(auth)/login')}
        accessibilityLabel="Login"
        accessibilityRole="button"
      >
        <Text style={styles.buttonText}>
          {safeTranslate('login', 'Login')}
        </Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: Constants.SPACING.SECTION,
  },
  icon: {
    marginBottom: Constants.SPACING.MEDIUM,
  },
  title: {
    fontSize: Constants.FONT_SIZES.TITLE,
    fontWeight: 'bold',
    marginBottom: Constants.SPACING.SECTION * 2,
    textAlign: 'center',
  },
  button: {
    width: '80%',
    paddingVertical: Constants.SPACING.MEDIUM,
    borderRadius: 12,
    alignItems: 'center',
    marginBottom: Constants.SPACING.MEDIUM,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 3,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: Constants.FONT_SIZES.BODY,
    fontWeight: '600',
  },
});

export default WelcomeScreen;