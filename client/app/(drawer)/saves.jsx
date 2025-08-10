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
import { FontAwesome } from '@expo/vector-icons';

const { INPUT, SAVES, ERROR_MESSAGES } = Constants;

const TranslationItem = React.memo(({ item, isDarkMode, onDelete, t, locale }) => {
  const [isDeleting, setIsDeleting] = useState(false);

  const onDeletePress = () => {
    if (isDeleting) return;
    setIsDeleting(true);
    onDelete(item.id, item.type, () => setIsDeleting(false));
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
  const router = useRouter();

  // Use the store for guest translations, but fetch server data directly for logged-in users
  const {
    guestTranslations,
    clearGuestTranslations,
    removeTranslation,
    initializeGuestTranslations
  } = useTranslationStore();

  // Local state for server translations (to handle all types)
  const [serverTranslations, setServerTranslations] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  // Combine all translations for display
  const isGuest = !session?.signed_session_id;
  const allTranslations = isGuest ? guestTranslations : serverTranslations;

  useFocusEffect(
    useCallback(() => {
      let isMounted = true;

      const loadTranslations = async () => {
        if (!isMounted) return;

        if (isGuest) {
          console.log('📱 Loading guest translations...');
          try {
            await initializeGuestTranslations();
          } catch (err) {
            console.error('❌ Failed to load guest translations:', err);
          }
          return;
        }

        // Load logged-in user translations (ALL TYPES)
        if (isMounted) setIsLoading(true);

        try {
          console.log('📡 Fetching translations from User Data Service...');
          console.log('📡 Using User Data API URL:', Constants.USER_DATA_API_URL || `${Constants.API_URL}:3003`);

          // Fetch from the actual User Data Service endpoints
          const userDataBaseUrl = Constants.USER_DATA_API_URL || 'http://192.168.1.26:3003';
          
          const [textResponse, voiceResponse] = await Promise.all([
            fetch(`${userDataBaseUrl}/translations/text`, {
              method: 'GET',
              headers: {
                'Authorization': `Bearer ${session.signed_session_id}`,
                'Content-Type': 'application/json',
              },
            }),
            fetch(`${userDataBaseUrl}/translations/voice`, {
              method: 'GET',
              headers: {
                'Authorization': `Bearer ${session.signed_session_id}`,
                'Content-Type': 'application/json',
              },
            }),
          ]);

          console.log('📡 User Data Service responses:', {
            textStatus: textResponse.status,
            voiceStatus: voiceResponse.status,
          });

          if (!textResponse.ok || !voiceResponse.ok) {
            throw new Error(`User Data Service failed: text(${textResponse.status}) voice(${voiceResponse.status})`);
          }

          const [textTranslations, voiceTranslations] = await Promise.all([
            textResponse.json(),
            voiceResponse.json(),
          ]);

          const allTranslations = [...(textTranslations || []), ...(voiceTranslations || [])];
          
          console.log('📥 Fetched from User Data Service:', {
            textCount: textTranslations?.length || 0,
            voiceCount: voiceTranslations?.length || 0,
            total: allTranslations.length,
          });

          console.log('📥 Total translations before deduplication:', allTranslations.length);

          // Deduplicate translations by ID and content
          const uniqueTranslations = [];
          const seenIds = new Set();
          const seenContent = new Set();

          allTranslations.forEach((translation, index) => {
            if (!translation) {
              console.warn('📥 Null translation at index:', index);
              return;
            }

            const contentKey = `${translation.original_text || ''}|${translation.translated_text || ''}|${translation.fromLang || ''}|${translation.toLang || ''}|${translation.type || 'unknown'}`;
            const translationId = translation.id || translation._id || `temp_${index}`;

            if (!seenIds.has(translationId) && !seenContent.has(contentKey)) {
              // Ensure translation has required properties
              const processedTranslation = {
                id: translationId,
                original_text: translation.original_text || translation.originalText || '',
                translated_text: translation.translated_text || translation.translatedText || '',
                type: translation.type || 'text',
                created_at: translation.created_at || translation.createdAt || translation.timestamp || new Date().toISOString(),
                fromLang: translation.fromLang || translation.from_lang || 'auto',
                toLang: translation.toLang || translation.to_lang || 'en',
                ...translation
              };
              
              uniqueTranslations.push(processedTranslation);
              seenIds.add(translationId);
              seenContent.add(contentKey);
            } else {
              console.warn('Duplicate translation detected:', {
                id: translationId,
                contentKey,
                type: translation.type || 'unknown',
                source: seenIds.has(translationId) ? 'ID' : 'Content',
              });
            }
          });

          if (isMounted) {
            setServerTranslations(uniqueTranslations);
            console.log('✅ Loaded ALL translations:', uniqueTranslations.length);
          }
        } catch (err) {
          console.error('❌ Failed to load translations:', err);
          if (isMounted) {
            setError(t('error') + ': ' + Helpers.handleError(err));
          }
        } finally {
          if (isMounted) setIsLoading(false);
        }
      };

      loadTranslations();

      return () => {
        isMounted = false;
      };
    }, [session?.signed_session_id, isGuest, initializeGuestTranslations, t])
  );

  const handleDeleteTranslation = useCallback(async (id, type, onComplete) => {
    console.log('handleDeleteTranslation called with id:', id, 'type:', type);
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
              if (isGuest) {
                // Use store method for guest translations
                await removeTranslation(id, type, true, null);
                console.log('✅ Guest translation deleted');
              } else {
                // Delete from User Data Service
                const userDataBaseUrl = Constants.USER_DATA_API_URL || 'http://192.168.1.26:3003';
                const response = await fetch(`${userDataBaseUrl}/translations/delete/${id}`, {
                  method: 'DELETE',
                  headers: {
                    'Authorization': `Bearer ${session.signed_session_id}`,
                    'Content-Type': 'application/json',
                  },
                });

                if (!response.ok) {
                  const errorText = await response.text();
                  throw new Error(`Delete failed: ${response.status} ${errorText}`);
                }

                // Update local server translations state
                setServerTranslations(prev => prev.filter(item => item.id !== id));
                console.log('✅ Server translation deleted');
              }
            } catch (err) {
              console.error('❌ Delete failed:', err);
              setError(t('error') + ': ' + Helpers.handleError(err));
            } finally {
              onComplete();
            }
          },
        },
      ]
    );
  }, [session?.signed_session_id, isGuest, removeTranslation, t]);

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
              if (isGuest) {
                await clearGuestTranslations();
                console.log('✅ Guest translations cleared');
              } else {
                // Clear ALL translations from User Data Service
                const userDataBaseUrl = Constants.USER_DATA_API_URL || 'http://192.168.1.26:3003';
                const response = await fetch(`${userDataBaseUrl}/translations`, {
                  method: 'DELETE',
                  headers: {
                    'Authorization': `Bearer ${session.signed_session_id}`,
                    'Content-Type': 'application/json',
                  },
                });

                if (!response.ok) {
                  const errorText = await response.text();
                  throw new Error(`Clear failed: ${response.status} ${errorText}`);
                }

                setServerTranslations([]);
                console.log('✅ All User Data Service translations cleared');
              }
            } catch (err) {
              console.error('❌ Clear failed:', err);
              setError(t('error') + ': ' + Helpers.handleError(err));
            }
          },
        },
      ]
    );
  }, [session?.signed_session_id, isGuest, clearGuestTranslations, t]);

  // Show error toast when error changes
  React.useEffect(() => {
    if (error) {
      setToastVisible(true);
    }
  }, [error]);

  console.log('📋 Displaying translations:', {
    count: allTranslations.length,
    isLoggedIn: !isGuest,
    hasTranslations: allTranslations.length > 0,
    guestCount: guestTranslations.length,
    serverCount: serverTranslations.length,
    breakdown: isGuest ? 'guest-only' : 'server-only',
  });

  return (
    <View style={[styles.container, { backgroundColor: isDarkMode ? SAVES.BACKGROUND_COLOR_DARK : Constants.COLORS.BACKGROUND }]}>
      {isLoading && (
        <ActivityIndicator size="large" color={isDarkMode ? '#fff' : Constants.COLORS.PRIMARY} style={styles.loading} accessibilityLabel="Loading translations" />
      )}
      <FlatList
        data={allTranslations}
        renderItem={({ item }) => (
          <TranslationItem item={item} isDarkMode={isDarkMode} onDelete={handleDeleteTranslation} t={t} locale={locale} />
        )}
        keyExtractor={(item, index) => `${item.id || index}-${item.type || 'unknown'}`}
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
            {allTranslations.length > 0 && (
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