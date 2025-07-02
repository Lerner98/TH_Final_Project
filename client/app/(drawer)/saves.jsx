import React, { useState, useCallback } from 'react';
import { View, Text, StyleSheet, FlatList, Alert, Pressable } from 'react-native';
import { ActivityIndicator } from 'react-native-paper';
import { useTranslation } from '../../utils/TranslationContext';
import { useEnhancedSession } from '../../utils/EnhancedSessionContext';
import useTranslationStore from '../../stores/TranslationStore';
import Toast from '../../components/Toast';
import { useRouter } from 'expo-router';
import { useFocusEffect } from '@react-navigation/native';
import Constants from '../../utils/Constants';
import Helpers from '../../utils/Helpers';
import { useTheme } from '../../utils/ThemeContext';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { FontAwesome } from '@expo/vector-icons';
import ApiService from '../../services/ApiService';

const { INPUT, SAVES, ERROR_MESSAGES } = Constants;

const TranslationItem = React.memo(({ item, isDarkMode, onDelete, t, locale }) => {
  const [isDeleting, setIsDeleting] = useState(false);

  const onDeletePress = () => {
    if (isDeleting) return;
    setIsDeleting(true);
    onDelete(item.id, () => setIsDeleting(false));
  };

  const getTypeIcon = () => {
    switch (item.type) {
      case 'text':
        return 'font';
      case 'voice':
        return 'microphone';
      case 'camera':
        return 'camera';
      case 'file':
        return 'file';
      case 'asl':
        return 'sign-language';
      default:
        return 'question';
    }
  };

  return (
    <View style={[styles.translationItem, { backgroundColor: isDarkMode ? INPUT.BACKGROUND_COLOR_DARK : INPUT.BACKGROUND_COLOR_LIGHT }]}>
      <View style={[styles.translationContent]}>
        <View style={styles.typeIconContainer}>
          <FontAwesome name={getTypeIcon()} size={20} color={isDarkMode ? INPUT.TEXT_COLOR_DARK : Constants.COLORS.SECONDARY_TEXT} />
        </View>
        <Text style={[styles.translationText, { color: isDarkMode ? INPUT.TEXT_COLOR_DARK : Constants.COLORS.SECONDARY_TEXT }]}> 
          {t('original', { defaultValue: 'Original Text' })}: {item.original_text}
        </Text>
        <Text style={[styles.translationText, { color: isDarkMode ? INPUT.TEXT_COLOR_DARK : Constants.COLORS.SECONDARY_TEXT }]}> 
          {t('translated', { defaultValue: 'Translated Text' })}: {item.translated_text}
        </Text>
        <Text style={[styles.translationText, { color: isDarkMode ? INPUT.TEXT_COLOR_DARK : Constants.COLORS.SECONDARY_TEXT }]}> 
          {t('createdAt', { defaultValue: 'Created At' })}: {Helpers.formatDate(item.created_at, locale)}
        </Text>
      </View>
      <Pressable
        onPress={onDeletePress}
        style={({ pressed }) => [styles.deleteButtonWrapper, { opacity: pressed ? 0.7 : 1 }]}
        hitSlop={{ top: 20, bottom: 20, left: 20, right: 20 }}
        accessibilityLabel="Delete translation"
        accessibilityRole="button"
      >
        <FontAwesome name="trash" size={24} color={Constants.COLORS.DESTRUCTIVE} />
      </Pressable>
    </View>
  );
});

const SavesScreen = () => {
  const { t, locale } = useTranslation();
  const { session } = useEnhancedSession();
  const { isDarkMode } = useTheme();
  const [toastVisible, setToastVisible] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [translations, setTranslations] = useState([]);
  const router = useRouter();

  useFocusEffect(
    useCallback(() => {
      let isMounted = true;

      const loadTranslations = async () => {
        if (!isMounted) return;

        if (!session?.signed_session_id) {
          // Load guest translations from AsyncStorage
          try {
            const guestData = await AsyncStorage.getItem('guestTranslations');
            if (isMounted) {
              const guestTranslations = guestData ? JSON.parse(guestData) : [];
              setTranslations(guestTranslations);
              console.log('📱 Guest translations loaded:', guestTranslations.length);
            }
          } catch (err) {
            console.error('Failed to load guest translations:', err);
            if (isMounted) setTranslations([]);
          }
          return;
        }

        // Load logged-in user translations
        if (isMounted) setIsLoading(true);

        try {
          console.log('📡 Fetching translations from server...');

          const [textResponse, voiceResponse] = await Promise.all([
            fetch(`${Constants.API_URL}/translations/text`, {
              method: 'GET',
              headers: {
                'Authorization': `Bearer ${session.signed_session_id}`,
                'Content-Type': 'application/json',
              },
            }),
            fetch(`${Constants.API_URL}/translations/voice`, {
              method: 'GET',
              headers: {
                'Authorization': `Bearer ${session.signed_session_id}`,
                'Content-Type': 'application/json',
              },
            }),
          ]);

          if (!textResponse.ok || !voiceResponse.ok) {
            throw new Error('Failed to fetch translations');
          }

          const textTranslations = await textResponse.json();
          const voiceTranslations = await voiceResponse.json();

          console.log('📥 Fetched translations:', {
            textCount: textTranslations.length,
            voiceCount: voiceTranslations.length,
            textTranslations,
            voiceTranslations,
          });

          // Deduplicate translations by ID and content
          const uniqueTranslations = [];
          const seenIds = new Set();
          const seenContent = new Set();

          [...textTranslations, ...voiceTranslations].forEach((translation) => {
            const contentKey = `${translation.original_text}|${translation.translated_text}|${translation.fromLang}|${translation.toLang}|${translation.type}`;

            if (!seenIds.has(translation.id) && !seenContent.has(contentKey)) {
              uniqueTranslations.push(translation);
              seenIds.add(translation.id);
              seenContent.add(contentKey);
            } else {
              console.warn('Duplicate translation detected:', {
                id: translation.id,
                contentKey,
                type: translation.type,
                source: seenIds.has(translation.id) ? 'ID' : 'Content',
              });
            }
          });

          if (isMounted) {
            setTranslations(uniqueTranslations);
            console.log('✅ Loaded translations:', uniqueTranslations.length);
          }
        } catch (err) {
          console.error('❌ Failed to load translations:', err);
          if (isMounted) {
            setError(t('error') + ': ' + Helpers.handleError(err));
            setToastVisible(true);
          }
        } finally {
          if (isMounted) setIsLoading(false);
        }
      };

      loadTranslations();

      return () => {
        isMounted = false;
      };
    }, [session?.signed_session_id, t])
  );

  const handleDeleteTranslation = useCallback(async (id, onComplete) => {
    console.log('handleDeleteTranslation called with id:', id);
    Alert.alert(
      t('deleteTranslation', { defaultValue: 'Delete Translation' }),
      t('areYouSure', { defaultValue: 'Are you sure?' }),
      [
        { 
          text: t('cancel', { defaultValue: 'Cancel' }), 
          style: 'cancel',
          onPress: onComplete
        },
        {
          text: t('delete', { defaultValue: 'Delete' }),
          style: 'destructive',
          onPress: async () => {
            try {
              if (session?.signed_session_id) {
                // Delete from server
                const response = await ApiService.delete(`/translations/delete/${id}`, session.signed_session_id, { timeout: 10000 });
                if (!response.success) {
                  throw new Error(response.error || ERROR_MESSAGES.SAVES_DELETE_SERVER_FAILED);
                }

                // Update local state
                setTranslations(prev => prev.filter(item => item.id !== id));
                console.log('✅ Translation deleted from server');
              } else {
                // Delete from guest storage
                const updatedTranslations = translations.filter(item => item.id !== id);
                setTranslations(updatedTranslations);
                await AsyncStorage.setItem('guestTranslations', JSON.stringify(updatedTranslations));
                console.log('✅ Guest translation deleted');
              }
            } catch (err) {
              console.error('❌ Delete failed:', err);
              setError(t('error') + ': ' + Helpers.handleError(err));
              setToastVisible(true);
            } finally {
              onComplete();
            }
          },
        },
      ]
    );
  }, [session, translations, t]);

  const handleClearTranslations = useCallback(async () => {
    Alert.alert(
      t('clearTranslations', { defaultValue: 'Clear Translations' }),
      t('areYouSure', { defaultValue: 'Are you sure?' }),
      [
        { text: t('cancel', { defaultValue: 'Cancel' }), style: 'cancel' },
        {
          text: t('clear', { defaultValue: 'Clear' }),
          style: 'destructive',
          onPress: async () => {
            try {
              if (session?.signed_session_id) {
                // Clear from server
                const response = await fetch(`${Constants.API_URL}/translations`, {
                  method: 'DELETE',
                  headers: {
                    'Authorization': `Bearer ${session.signed_session_id}`,
                    'Content-Type': 'application/json',
                  },
                });

                if (!response.ok) {
                  throw new Error('Failed to clear translations');
                }

                setTranslations([]);
                console.log('✅ All translations cleared from server');
              } else {
                // Clear guest storage
                setTranslations([]);
                await AsyncStorage.setItem('guestTranslations', JSON.stringify([]));
                console.log('✅ Guest translations cleared');
              }
            } catch (err) {
              console.error('❌ Clear failed:', err);
              setError(t('error') + ': ' + Helpers.handleError(err));
              setToastVisible(true);
            }
          },
        },
      ]
    );
  }, [session, t]);

  console.log('📋 Displaying translations:', {
    count: translations.length,
    isLoggedIn: !!session,
    hasTranslations: translations.length > 0,
  });

  return (
    <View style={[styles.container, { backgroundColor: isDarkMode ? SAVES.BACKGROUND_COLOR_DARK : Constants.COLORS.BACKGROUND }]}>
      {isLoading && (
        <ActivityIndicator size="large" color={isDarkMode ? '#fff' : Constants.COLORS.PRIMARY} style={styles.loading} accessibilityLabel="Loading translations" />
      )}
      <FlatList
        data={translations}
        renderItem={({ item }) => (
          <TranslationItem item={item} isDarkMode={isDarkMode} onDelete={handleDeleteTranslation} t={t} locale={locale} />
        )}
        keyExtractor={(item, index) => `${item.id || index}`}
        ListHeaderComponent={
          <View style={styles.header}>
            <View style={styles.headerRow}>
              <Pressable
                onPress={() => router.back()}
                style={({ pressed }) => [styles.backButton, { opacity: pressed ? 0.7 : 1 }]}
                hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                accessibilityLabel="Go back"
                accessibilityRole="button"
              >
                <FontAwesome name="chevron-left" size={24} color={isDarkMode ? INPUT.TEXT_COLOR_DARK : INPUT.TEXT_COLOR_LIGHT} />
              </Pressable>
              <Text style={[styles.title, { color: isDarkMode ? INPUT.TEXT_COLOR_DARK : INPUT.TEXT_COLOR_LIGHT }]}>
                {t('saves', { defaultValue: 'Saved Translations' })}
              </Text>
              <View style={styles.placeholder} />
            </View>
            {translations.length > 0 && (
              <Pressable
                onPress={handleClearTranslations}
                style={({ pressed }) => [
                  styles.clearButton,
                  { backgroundColor: Constants.COLORS.DESTRUCTIVE, opacity: pressed ? 0.7 : 1 },
                ]}
                hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                accessibilityLabel="Clear all translations"
                accessibilityRole="button"
              >
                <Text style={styles.clearButtonLabel}>{t('clearTranslations', { defaultValue: 'Clear Translations' })}</Text>
              </Pressable>
            )}
          </View>
        }
        ListEmptyComponent={
          !isLoading && (
            <Text style={[styles.noTranslations, { color: isDarkMode ? INPUT.TEXT_COLOR_DARK : Constants.COLORS.SECONDARY_TEXT }]}> 
              {t('noTranslations', { defaultValue: 'No saved translations found.' })}
            </Text>
          )
        }
        contentContainerStyle={styles.scrollContent}
      />
      {error && (
        <Toast message={error} visible={toastVisible} onHide={() => setToastVisible(false)} />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    padding: Constants.SPACING.SECTION,
    paddingBottom: Constants.SPACING.SECTION * 2,
  },
  header: {
    marginBottom: Constants.SPACING.SECTION,
    alignItems: 'center',
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    width: '100%',
    marginBottom: Constants.SPACING.LARGE,
  },
  backButton: {
    padding: Constants.SPACING.SMALL,
  },
  title: {
    fontSize: Constants.FONT_SIZES.TITLE,
    fontWeight: 'bold',
    letterSpacing: 0.5,
  },
  placeholder: {
    width: 24,
  },
  clearButton: {
    width: '80%',
    paddingVertical: Constants.SPACING.MEDIUM,
    paddingHorizontal: Constants.SPACING.LARGE,
    borderRadius: 12,
    alignItems: 'center',
    shadowColor: Constants.COLORS.SHADOW,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
    backgroundColor: Constants.COLORS.DESTRUCTIVE,
  },
  clearButtonLabel: {
    fontSize: Constants.FONT_SIZES.BODY,
    fontWeight: 'bold',
    color: Constants.COLORS.CARD,
  },
  translationItem: {
    flexDirection: 'row',
    padding: Constants.SPACING.LARGE,
    borderRadius: 12,
    marginBottom: Constants.SPACING.MEDIUM,
    shadowColor: Constants.COLORS.SHADOW,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
    alignItems: 'center',
  },
  translationContent: {
    flex: 1,
  },
  typeIconContainer: {
    marginBottom: Constants.SPACING.SMALL,
  },
  translationText: {
    fontSize: Constants.FONT_SIZES.SECONDARY,
    marginBottom: Constants.SPACING.SMALL,
    lineHeight: 20,
  },
  deleteButtonWrapper: {
    padding: Constants.SPACING.SMALL,
  },
  noTranslations: {
    fontSize: Constants.FONT_SIZES.BODY,
    textAlign: 'center',
    marginTop: Constants.SPACING.SECTION,
  },
  loading: {
    marginVertical: Constants.SPACING.SECTION,
  },
});

export default SavesScreen;