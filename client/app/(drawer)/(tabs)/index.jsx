// ===== UPDATED HOME SCREEN =====
// app/(drawer)/(tabs)/index.jsx
import React, { useRef, useEffect, useCallback, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, Pressable } from 'react-native';
import { useTranslation } from '../../../utils/TranslationContext';
import { useEnhancedSession } from '../../../utils/EnhancedSessionContext';
import useThemeStore from '../../../stores/ThemeStore';
import { useRouter } from 'expo-router';
import Constants from '../../../utils/Constants';
import { FontAwesome } from '@expo/vector-icons';

const HOME = Constants.HOME;

const HomeScreen = () => {
  const { t } = useTranslation();
  const { session, isAuthenticated, guestManager } = useEnhancedSession();
  const themeStore = useThemeStore();
  const { isDarkMode = false } = themeStore;
  const router = useRouter();
  const isMounted = useRef(true);
  const [guestStats, setGuestStats] = useState({});

  useEffect(() => {
    isMounted.current = true;
    return () => {
      isMounted.current = false;
    };
  }, []);

  // Load guest statistics if not authenticated
  useEffect(() => {
    const loadGuestStats = async () => {
      if (!isAuthenticated) {
        try {
          const stats = await guestManager.getUsageStats();
          setGuestStats(stats);
        } catch (error) {
          console.error('Failed to load guest stats:', error);
        }
      }
    };
    loadGuestStats();
  }, [isAuthenticated, guestManager]);

  const safeTranslate = useCallback((key, fallback = '') => {
    const value = t(key);
    return typeof value === 'string' ? value : fallback;
  }, [t]);

  const welcomeMessage = session
    ? `Hello, ${session.email.split('@')[0]}!`
    : safeTranslate('welcomeGuest', 'Welcome, Guest!');

  const checkGuestLimitAndNavigate = useCallback(async (route, translationType) => {
    if (isAuthenticated) {
      router.navigate(route);
      return;
    }

    const limitCheck = await guestManager.checkTranslationLimit(translationType);
    if (limitCheck.allowed) {
      router.navigate(route);
    } else {
      const shouldUpgrade = await guestManager.promptUpgrade(translationType);
      if (shouldUpgrade) {
        router.push('/(auth)/register');
      }
    }
  }, [isAuthenticated, guestManager, router]);

  const handleTextVoicePress = useCallback(() => {
    if (isMounted.current) checkGuestLimitAndNavigate('/text-voice', 'text');
  }, [checkGuestLimitAndNavigate]);

  const handleFilePress = useCallback(() => {
    if (isMounted.current) {
      if (!isAuthenticated) {
        router.navigate('/file'); // File screen will handle auth requirement
      } else {
        checkGuestLimitAndNavigate('/file', 'file');
      }
    }
  }, [checkGuestLimitAndNavigate, isAuthenticated, router]);

  const handleASLPress = useCallback(() => {
    if (isMounted.current) checkGuestLimitAndNavigate('/asl', 'asl');
  }, [checkGuestLimitAndNavigate]);

  const handleCameraPress = useCallback(() => {
    if (isMounted.current) checkGuestLimitAndNavigate('/camera', 'camera');
  }, [checkGuestLimitAndNavigate]);

  return (
    <View style={[styles.container, { backgroundColor: isDarkMode ? HOME.BACKGROUND_COLOR_DARK : HOME.BACKGROUND_COLOR_LIGHT }]}>
      <ScrollView contentContainerStyle={styles.contentContainer}>
        <View style={styles.heroSection}>
          <Text style={[styles.welcomeText, { color: isDarkMode ? HOME.WELCOME_TEXT_COLOR_DARK : HOME.WELCOME_TEXT_COLOR_LIGHT }]}>
            {welcomeMessage}
          </Text>
          <Text style={[styles.descriptionText, { color: isDarkMode ? HOME.DESCRIPTION_TEXT_COLOR_DARK : HOME.DESCRIPTION_TEXT_COLOR_LIGHT }]}>
            {safeTranslate('welcomeMessage', 'Welcome to the app')}
          </Text>
          
          {/* Guest Usage Summary */}
          {!isAuthenticated && Object.keys(guestStats).length > 0 && (
            <View style={styles.guestStatsContainer}>
              <Text style={[styles.guestStatsTitle, { color: isDarkMode ? HOME.DESCRIPTION_TEXT_COLOR_DARK : HOME.DESCRIPTION_TEXT_COLOR_LIGHT }]}>
                Your Usage Today
              </Text>
              <Text style={[styles.guestStatsText, { color: isDarkMode ? HOME.DESCRIPTION_TEXT_COLOR_DARK : HOME.DESCRIPTION_TEXT_COLOR_LIGHT }]}>
                {guestStats.total?.current || 0}/{guestStats.total?.limit || 10} translations used
              </Text>
              {(guestStats.total?.current || 0) >= (guestStats.total?.limit || 10) && (
                <Pressable
                  style={[styles.upgradePrompt, { backgroundColor: Constants.COLORS.PRIMARY }]}
                  onPress={() => router.push('/(auth)/register')}
                >
                  <Text style={styles.upgradePromptText}>
                    Upgrade for Unlimited Access
                  </Text>
                </Pressable>
              )}
            </View>
          )}
        </View>

        <View style={styles.buttonGrid}>
          <Pressable
            style={({ pressed }) => [
              styles.gridButton,
              { backgroundColor: isDarkMode ? HOME.BUTTON_COLOR_DARK : HOME.BUTTON_COLOR_LIGHT, opacity: pressed ? 0.8 : 1 },
            ]}
            onPress={handleTextVoicePress}
            accessibilityLabel="Navigate to text and voice translation"
            accessibilityRole="button"
          >
            <FontAwesome name="microphone" size={24} color="#FFF" style={styles.gridButtonIcon} />
            <Text style={styles.gridButtonText}>
              {safeTranslate('textVoiceTranslation', 'Text/Voice')}
            </Text>
            {!isAuthenticated && guestStats.text && (
              <Text style={styles.usageIndicator}>
                {guestStats.text.remaining || 0} left
              </Text>
            )}
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.gridButton,
              { backgroundColor: isDarkMode ? HOME.BUTTON_COLOR_DARK : HOME.BUTTON_COLOR_LIGHT, opacity: pressed ? 0.8 : 1 },
            ]}
            onPress={handleFilePress}
            accessibilityLabel="Navigate to file translation"
            accessibilityRole="button"
          >
            <FontAwesome name="file-text" size={24} color="#FFF" style={styles.gridButtonIcon} />
            <Text style={styles.gridButtonText}>
              {safeTranslate('fileTranslation', 'File')}
            </Text>
            {!isAuthenticated && (
              <Text style={styles.authRequired}>Login Required</Text>
            )}
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.gridButton,
              { backgroundColor: isDarkMode ? HOME.BUTTON_COLOR_DARK : HOME.BUTTON_COLOR_LIGHT, opacity: pressed ? 0.8 : 1 },
            ]}
            onPress={handleASLPress}
            accessibilityLabel="Navigate to ASL translation"
            accessibilityRole="button"
          >
            <FontAwesome name="sign-language" size={24} color="#FFF" style={styles.gridButtonIcon} />
            <Text style={styles.gridButtonText}>
              {safeTranslate('aslTranslation', 'ASL')}
            </Text>
            {!isAuthenticated && guestStats.asl && (
              <Text style={styles.usageIndicator}>
                {guestStats.asl.remaining || 0} left
              </Text>
            )}
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.gridButton,
              { backgroundColor: isDarkMode ? HOME.BUTTON_COLOR_DARK : HOME.BUTTON_COLOR_LIGHT, opacity: pressed ? 0.8 : 1 },
            ]}
            onPress={handleCameraPress}
            accessibilityLabel="Navigate to camera translation"
            accessibilityRole="button"
          >
            <FontAwesome name="camera" size={24} color="#FFF" style={styles.gridButtonIcon} />
            <Text style={styles.gridButtonText}>
              {safeTranslate('cameraTranslation', 'Camera')}
            </Text>
            {!isAuthenticated && guestStats.camera && (
              <Text style={styles.usageIndicator}>
                {guestStats.camera.remaining || 0} left
              </Text>
            )}
          </Pressable>
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  contentContainer: {
    padding: Constants.SPACING.SECTION,
    paddingBottom: 80,
  },
  heroSection: {
    marginBottom: Constants.SPACING.SECTION,
    padding: 20,
    borderRadius: 15,
    backgroundColor: HOME.HERO_BACKGROUND_COLOR,
    alignItems: 'center',
  },
  welcomeText: {
    fontSize: 28,
    fontWeight: '700',
    marginBottom: Constants.SPACING.MEDIUM,
    textAlign: 'center',
  },
  descriptionText: {
    fontSize: 18,
    textAlign: 'center',
    lineHeight: 26,
    marginLeft: 10,
  },
  guestStatsContainer: {
    marginTop: Constants.SPACING.MEDIUM,
    padding: Constants.SPACING.MEDIUM,
    borderRadius: 10,
    backgroundColor: 'rgba(0,0,0,0.1)',
    alignItems: 'center',
  },
  guestStatsTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: Constants.SPACING.SMALL,
  },
  guestStatsText: {
    fontSize: 14,
    marginBottom: Constants.SPACING.SMALL,
  },
  upgradePrompt: {
    paddingVertical: Constants.SPACING.SMALL,
    paddingHorizontal: Constants.SPACING.MEDIUM,
    borderRadius: 8,
    marginTop: Constants.SPACING.SMALL,
  },
  upgradePromptText: {
    color: '#FFF',
    fontSize: 14,
    fontWeight: '600',
  },
  buttonGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  gridButton: {
    width: '48%',
    paddingVertical: Constants.SPACING.MEDIUM,
    borderRadius: 12,
    marginBottom: Constants.SPACING.MEDIUM,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 3,
    minHeight: 120,
  },
  gridButtonIcon: {
    marginBottom: Constants.SPACING.SMALL,
  },
  gridButtonText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600',
    textAlign: 'center',
  },
  usageIndicator: {
    color: 'rgba(255,255,255,0.8)',
    fontSize: 12,
    marginTop: Constants.SPACING.SMALL,
    fontStyle: 'italic',
  },
  authRequired: {
    color: 'rgba(255,255,255,0.8)',
    fontSize: 12,
    marginTop: Constants.SPACING.SMALL,
    fontStyle: 'italic',
  },
});

export default HomeScreen;