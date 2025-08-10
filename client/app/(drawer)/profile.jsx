// app/(drawer)/profile.jsx - CORRECTED VERSION
import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, FlatList, Switch, Alert, Pressable, KeyboardAvoidingView, Platform } from 'react-native';
import { ActivityIndicator } from 'react-native-paper';
import { useTranslation } from '../../utils/TranslationContext';
import { useEnhancedSession } from '../../utils/EnhancedSessionContext';
import { useRouter } from 'expo-router';
import LanguageSearch from '../../components/LanguageSearch';
import Toast from '../../components/Toast';
import AsyncStorageUtils from '../../utils/AsyncStorage';
import Constants from '../../utils/Constants';
import Helpers from '../../utils/Helpers';
import { FontAwesome } from '@expo/vector-icons';
import { useTheme } from '../../utils/ThemeContext';
import GuestManager from '../../utils/GuestManager'; // Added to allow hard reset of guest counts for all screens for debugging

const { PROFILE, ERROR_MESSAGES } = Constants;

const ProfileScreen = () => {
  const { t, locale, changeLocale } = useTranslation();
  const { session, preferences, setPreferences, isAuthenticated, guestManager } = useEnhancedSession();
  const { isDarkMode, toggleTheme } = useTheme();
  const router = useRouter();

  const [selectedLanguage, setSelectedLanguage] = useState(locale);
  const [isDarkModeLocal, setIsDarkModeLocal] = useState(isDarkMode ?? false);
  const [localPreferences, setLocalPreferences] = useState({
    defaultFromLang: preferences?.defaultFromLang || '',
    defaultToLang: preferences?.defaultToLang || '',
  });
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [toastVisible, setToastVisible] = useState(false);
  const [guestStats, setGuestStats] = useState({});

  useEffect(() => {
    setIsDarkModeLocal(isDarkMode ?? false);
  }, [isDarkMode]);

  useEffect(() => {
    setSelectedLanguage(locale);
  }, [locale]);

  useEffect(() => {
    const loadData = async () => {
      setIsLoading(true);
      setError('');
      try {
        if (session) {
          // For logged users: load from context (which loads from server)
          setLocalPreferences({
            defaultFromLang: preferences?.defaultFromLang || '',
            defaultToLang: preferences?.defaultToLang || '',
          });
        } else {
          // For guests: load both stats and preferences from local storage
          const [stats, guestPrefs] = await Promise.all([
            guestManager.getUsageStats(),
            AsyncStorageUtils.getItem('preferences')
          ]);
          setGuestStats(stats);
          if (guestPrefs) {
            setLocalPreferences({
              defaultFromLang: guestPrefs.defaultFromLang || '',
              defaultToLang: guestPrefs.defaultToLang || '',
            });
          }
        }
      } catch (err) {
        setError(t('error') + ': ' + Helpers.handleError(err));
        setToastVisible(true);
      } finally {
        setIsLoading(false);
      }
    };
    loadData();
  }, [session, t, preferences, guestManager]);

  // ✅ App Language Change - Available for ALL users, works instantly
  const handleAppLanguageChange = useCallback(async (language) => {
    try {
      setSelectedLanguage(language);
      await changeLocale(language);
      Alert.alert(t('success'), `App language changed to ${language === 'en' ? 'English' : 'Hebrew'}`);
    } catch (err) {
      setError(t('error') + ': ' + Helpers.handleError(err));
      setToastVisible(true);
    }
  }, [changeLocale, t]);

  // ✅ Save Translation Preferences - For ALL users (server for logged, local for guests)
  const handleSavePreferences = useCallback(async () => {
    setError('');
    setIsLoading(true);
    
    try {
      if (session) {
        // Logged users: save to server via context
        await setPreferences(localPreferences);
        Alert.alert(t('success'), 'Translation preferences saved successfully!');
      } else {
        // Guests: save to local storage
        await AsyncStorageUtils.setItem('preferences', localPreferences);
        Alert.alert(t('success'), 'Translation preferences saved locally!');
      }
    } catch (err) {
      setError(t('error') + ': ' + Helpers.handleError(err));
      setToastVisible(true);
    } finally {
      setIsLoading(false);
    }
  }, [session, localPreferences, setPreferences, t]);

  const handleToggleDarkMode = useCallback(async () => {
    setIsDarkModeLocal((prev) => !prev);
    await toggleTheme();
  }, [toggleTheme]);

  const handleClearTranslations = useCallback(async () => {
    Alert.alert(
      t('clearTranslations'),
      t('areYouSure'),
      [
        { text: t('cancel'), style: 'cancel' },
        {
          text: t('clear'),
          style: 'destructive',
          onPress: async () => {
            setIsLoading(true);
            try {
              if (session) {
                await AsyncStorageUtils.removeItem('textTranslations');
                await AsyncStorageUtils.removeItem('voiceTranslations');
              } else {
                await guestManager.clearAllData();
              }
              Alert.alert(t('success'), 'Translations cleared successfully!');
            } catch (err) {
              setError(t('error') + ': ' + Helpers.handleError(err));
              setToastVisible(true);
            } finally {
              setIsLoading(false);
            }
          },
        },
      ]
    );
  }, [t, session, guestManager]);

  const renderContent = () => (
    <View style={styles.content}>
      <View style={[styles.headerContainer, { backgroundColor: isDarkModeLocal ? PROFILE.BACKGROUND_COLOR_DARK : PROFILE.BACKGROUND_COLOR_LIGHT }]}>
        <Pressable
          onPress={() => router.back()}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          accessibilityLabel="Go back"
          accessibilityRole="button"
        >
          <FontAwesome name="arrow-left" size={24} color={isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT} />
        </Pressable>
        <Text style={[styles.headerText, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>
          {session ? t('profile', { defaultValue: 'Profile' }) : t('settings', { defaultValue: 'Settings' })}
        </Text>
        <View style={styles.placeholder} />
      </View>

      {/* User Profile Section - Only for logged users */}
      {session && (
        <View style={[styles.section, { backgroundColor: isDarkModeLocal ? PROFILE.SECTION_BACKGROUND_COLOR_DARK : PROFILE.SECTION_BACKGROUND_COLOR_LIGHT }]}>
          <Text style={[styles.sectionTitle, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>{t('profile')}</Text>
          <Text style={[styles.detailText, { color: isDarkModeLocal ? PROFILE.SECONDARY_TEXT_COLOR_DARK : PROFILE.SECONDARY_TEXT_COLOR_LIGHT }]}>{t('email')}</Text>
          <Text style={[styles.detailValue, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>{session.email}</Text>
        </View>
      )}

      {/* Guest Usage Statistics - Only for guests */}
      {!isAuthenticated && Object.keys(guestStats).length > 0 && (
        <View style={[styles.section, { backgroundColor: isDarkModeLocal ? PROFILE.SECTION_BACKGROUND_COLOR_DARK : PROFILE.SECTION_BACKGROUND_COLOR_LIGHT }]}>
          <Text style={[styles.sectionTitle, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>Guest Usage</Text>
          
          {Object.entries(guestStats)
            .filter(([type]) => !['file', 'daily', 'total', 'voice'].includes(type))
            .map(([type, stats]) => (
              <View key={type} style={styles.usageItem}>
                <Text style={[styles.usageLabel, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>
                  {type.charAt(0).toUpperCase() + type.slice(1)} Translations:
                </Text>
                <Text style={[styles.usageValue, { color: isDarkModeLocal ? PROFILE.SECONDARY_TEXT_COLOR_DARK : PROFILE.SECONDARY_TEXT_COLOR_LIGHT }]}>
                  {stats.current}/{stats.limit} ({stats.remaining} remaining)
                </Text>
              </View>
            ))}
          
          <Pressable
            style={[styles.upgradeButton, { backgroundColor: Constants.COLORS.PRIMARY }]}
            onPress={() => router.push('/(auth)/register')}
          >
            <Text style={styles.upgradeButtonText}>Upgrade to Unlimited</Text>
          </Pressable>
        </View>
      )}

      {/* Preferences Section - Available for ALL users */}
      <View style={[styles.section, { backgroundColor: isDarkModeLocal ? PROFILE.SECTION_BACKGROUND_COLOR_DARK : PROFILE.SECTION_BACKGROUND_COLOR_LIGHT }]}>
        <Text style={[styles.sectionTitle, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>{t('preferences')}</Text>

        {/* ✅ App Language - Available for ALL users */}
        <View style={styles.option}>
          <Text style={[styles.optionText, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>{t('appLanguage')}</Text>
          <View style={styles.languageOptions}>
            {['en', 'he'].map((lang) => (
              <Pressable
                key={lang}
                style={({ pressed }) => [
                  styles.languageButton,
                  selectedLanguage === lang && styles.selectedLanguageButton,
                  { opacity: pressed ? 0.7 : 1 },
                ]}
                onPress={() => handleAppLanguageChange(lang)}
                hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                accessibilityLabel={`Select ${lang === 'en' ? 'English' : 'Hebrew'} language`}
                accessibilityRole="button"
              >
                <Text style={[
                  styles.languageButtonText,
                  selectedLanguage === lang && styles.selectedLanguageText,
                  { color: isDarkModeLocal ? PROFILE.SECONDARY_TEXT_COLOR_DARK : PROFILE.SECONDARY_TEXT_COLOR_LIGHT },
                  selectedLanguage === lang && { color: PROFILE.SELECTED_TEXT_COLOR },
                ]}>
                  {lang === 'en' ? 'English' : 'Hebrew'}
                </Text>
              </Pressable>
            ))}
          </View>
        </View>

        {/* ✅ Dark Mode - Available for ALL users */}
        <View style={styles.option}>
          <Text style={[styles.optionText, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>{t('darkMode')}</Text>
          <Switch
            value={isDarkModeLocal}
            onValueChange={handleToggleDarkMode}
            trackColor={{ false: '#767577', true: '#81b0ff' }}
            thumbColor={isDarkModeLocal ? '#f5dd4b' : '#f4f3f4'}
            accessibilityLabel="Toggle dark mode"
          />
        </View>

        {/* ✅ Translation Preferences - Available for ALL users */}
        <Text style={[styles.subsectionTitle, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>
          Translation Preferences {!session && '(Local)'}
        </Text>
        
        <View style={styles.languagePreference}>
          <Text style={[styles.optionText, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>{t('sourceLang')}</Text>
          <View style={styles.languageSearchContainer}>
            <LanguageSearch
              onSelectLanguage={(lang) => setLocalPreferences({ ...localPreferences, defaultFromLang: lang })}
              selectedLanguage={localPreferences.defaultFromLang}
            />
          </View>
        </View>

        <View style={styles.languagePreference}>
          <Text style={[styles.optionText, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>{t('targetLang')}</Text>
          <View style={styles.languageSearchContainer}>
            <LanguageSearch
              onSelectLanguage={(lang) => setLocalPreferences({ ...localPreferences, defaultToLang: lang })}
              selectedLanguage={localPreferences.defaultToLang}
            />
          </View>
        </View>

        <Pressable
          style={({ pressed }) => [
            styles.saveButton,
            { backgroundColor: isDarkModeLocal ? PROFILE.SAVE_BUTTON_COLOR_DARK : PROFILE.SAVE_BUTTON_COLOR_LIGHT, opacity: pressed ? 0.7 : 1 },
          ]}
          onPress={handleSavePreferences}
          disabled={isLoading}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          accessibilityLabel="Save translation preferences"
          accessibilityRole="button"
        >
          <Text style={styles.saveButtonText}>
            {session ? t('savePreferences') : 'Save Preferences (Local)'}
          </Text>
        </Pressable>
      </View>

      {/* Clear Translations Section - Available for ALL users */}
      <View style={[styles.section, { backgroundColor: isDarkModeLocal ? PROFILE.SECTION_BACKGROUND_COLOR_DARK : PROFILE.SECTION_BACKGROUND_COLOR_LIGHT }]}>
        <Text style={[styles.sectionTitle, { color: isDarkModeLocal ? PROFILE.TEXT_COLOR_DARK : PROFILE.TEXT_COLOR_LIGHT }]}>{t('clearTranslations')}</Text>
        <Pressable
          style={({ pressed }) => [
            styles.clearButton,
            { backgroundColor: Constants.COLORS.DESTRUCTIVE, opacity: pressed ? 0.7 : 1 },
          ]}
          onPress={handleClearTranslations}
          disabled={isLoading}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          accessibilityLabel="Clear translations"
          accessibilityRole="button"
        >
          <Text style={styles.clearButtonText}>{t('clearTranslations')}</Text>
        </Pressable>
      </View>
    </View>
  );

  return (
    <KeyboardAvoidingView
      style={{ flex: 1 }}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
    >
      <View style={[styles.container, { backgroundColor: isDarkModeLocal ? PROFILE.BACKGROUND_COLOR_DARK : PROFILE.BACKGROUND_COLOR_LIGHT }]}>
        <FlatList
          data={[]}
          keyExtractor={() => 'profile'}
          ListHeaderComponent={renderContent}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
        />

        {isLoading && (
          <ActivityIndicator
            size="large"
            color={isDarkModeLocal ? '#fff' : Constants.COLORS.PRIMARY}
            style={styles.loading}
            accessibilityLabel="Loading profile settings"
          />
        )}
        <Toast message={error} visible={toastVisible} onHide={() => setToastVisible(false)} />
      </View>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    padding: Constants.SPACING.SECTION,
    paddingBottom: Constants.SPACING.SECTION * 2,
  },
  headerContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Constants.SPACING.MEDIUM,
    paddingVertical: Constants.SPACING.LARGE,
    marginHorizontal: -Constants.SPACING.SECTION,
    marginTop: -Constants.SPACING.SECTION,
    marginBottom: Constants.SPACING.MEDIUM,
  },
  headerText: {
    fontSize: 20,
    fontWeight: '600',
    flex: 1,
    textAlign: 'center',
  },
  placeholder: {
    width: 24,
  },
  scrollContent: {
    paddingBottom: Constants.SPACING.SECTION * 2,
  },
  section: {
    borderRadius: 12,
    padding: Constants.SPACING.MEDIUM,
    marginBottom: Constants.SPACING.MEDIUM,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: Constants.SPACING.MEDIUM,
  },
  subsectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginTop: Constants.SPACING.MEDIUM,
    marginBottom: Constants.SPACING.SMALL,
  },
  detailText: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: Constants.SPACING.SMALL,
  },
  detailValue: {
    fontSize: 16,
    marginBottom: Constants.SPACING.MEDIUM,
  },
  option: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Constants.SPACING.SMALL,
  },
  optionText: {
    fontSize: 16,
    fontWeight: '500',
  },
  usageItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Constants.SPACING.SMALL,
  },
  usageLabel: {
    fontSize: 14,
    fontWeight: '500',
  },
  usageValue: {
    fontSize: 14,
  },
  upgradeButton: {
    paddingVertical: Constants.SPACING.MEDIUM,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: Constants.SPACING.MEDIUM,
  },
  upgradeButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  languageOptions: {
    flexDirection: 'row',
  },
  languageButton: {
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 8,
    marginLeft: 8,
    borderWidth: 1,
    borderColor: PROFILE.BORDER_COLOR,
  },
  selectedLanguageButton: {
    borderColor: PROFILE.SELECTED_BUTTON_BORDER_COLOR,
    backgroundColor: PROFILE.SELECTED_BUTTON_COLOR,
  },
  languageButtonText: {
    fontSize: 14,
  },
  selectedLanguageText: {
    color: PROFILE.SELECTED_TEXT_COLOR,
    fontWeight: '600',
  },
  languagePreference: {
    marginBottom: Constants.SPACING.MEDIUM,
  },
  languageSearchContainer: {
    marginTop: Constants.SPACING.SMALL,
  },
  saveButton: {
    paddingVertical: Constants.SPACING.MEDIUM,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: Constants.SPACING.MEDIUM,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 3,
  },
  saveButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  clearButton: {
    paddingVertical: Constants.SPACING.MEDIUM,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: Constants.SPACING.MEDIUM,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 3,
  },
  clearButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  loading: {
    marginBottom: Constants.SPACING.LARGE,
  },
});

export default ProfileScreen;