// ===== FIXED TEXT-VOICE SCREEN FOR MICROSERVICES =====
// app/(drawer)/(tabs)/text-voice.jsx
import React, { useState, useEffect, useCallback, useMemo, forwardRef, useRef } from 'react';
import { View, Text, TextInput, StyleSheet, ScrollView, TouchableOpacity, Alert, FlatList, Platform } from 'react-native';
import { IconButton, ActivityIndicator } from 'react-native-paper';
import { FontAwesome } from '@expo/vector-icons';
import { Audio } from 'expo-av';
import { useTranslation } from '../../../utils/TranslationContext';
import { useEnhancedSession } from '../../../utils/EnhancedSessionContext';
import useTranslationStore from '../../../stores/TranslationStore';
import TranslationService from '../../../services/TranslationService';
import LanguageSearch from '../../../components/LanguageSearch';
import Toast from '../../../components/Toast';
import Constants from '../../../utils/Constants';
import Helpers from '../../../utils/Helpers';
import useThemeStore from '../../../stores/ThemeStore';
import { useRouter } from 'expo-router';
import * as FileSystem from 'expo-file-system';
import { Buffer } from 'buffer';
import { PermissionsAndroid } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import GuestManager from '../../../utils/GuestManager';

const { ERROR_MESSAGES } = Constants;

// Memoized TextInput component
const MemoizedTextInput = forwardRef(({ value, onChangeText, style, placeholder, placeholderTextColor, multiline, numberOfLines, keyboardType, autoCapitalize, autoCorrect, textAlign, accessibilityLabel }, ref) => (
  <TextInput
    ref={ref}
    style={style}
    placeholder={placeholder}
    placeholderTextColor={placeholderTextColor}
    value={value}
    onChangeText={onChangeText}
    multiline={multiline}
    numberOfLines={numberOfLines}
    keyboardType={keyboardType}
    autoCapitalize={autoCapitalize}
    autoCorrect={autoCorrect}
    textAlign={textAlign}
    accessibilityLabel={accessibilityLabel}
  />
));

// Enhanced Text/Voice Input Component
const TextVoiceInput = React.memo(({ t, isDarkMode, session, sourceLang, setSourceLang, targetLang, setTargetLang, onAddTextTranslation, onAddVoiceTranslation, guestManager }) => {
  const [inputText, setInputText] = useState('');
  const [translatedText, setTranslatedText] = useState('');
  const [translatedOriginalText, setTranslatedOriginalText] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [recording, setRecording] = useState(null);
  const [hasPermission, setHasPermission] = useState(null);
  const [translationSaved, setTranslationSaved] = useState(false);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [soundObject, setSoundObject] = useState(null);
  const [toastVisible, setToastVisible] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [translationData, setTranslationData] = useState(null);
  const textInputRef = useRef(null);
  const router = useRouter();
  const { signOut } = useEnhancedSession();

  // ✅ FIXED: Use correct microservice URLs
  const TRANSLATION_API_URL = Constants.TRANSLATION_API_URL; // Port 3002
  const USER_DATA_API_URL = Constants.USER_DATA_API_URL;     // Port 3003

  useEffect(() => {
    setToastVisible(false);
  }, [error]);

  // ✅ FIXED: Audio permission setup with enhanced cleanup
  useEffect(() => {
    const checkAndRequestAudioPermission = async () => {
      try {
        if (Platform.OS === 'android') {
          const alreadyGranted = await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO);
          if (alreadyGranted) {
            setHasPermission(true);
            return;
          }

          const granted = await PermissionsAndroid.request(
            PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
            {
              title: 'Microphone Permission',
              message: 'TranslationHub needs access to your microphone to record audio.',
              buttonNeutral: 'Ask Me Later',
              buttonNegative: 'Cancel',
              buttonPositive: 'OK',
            }
          );
          setHasPermission(granted === PermissionsAndroid.RESULTS.GRANTED);
        } else {
          const { status } = await Audio.getPermissionsAsync();
          if (status === 'granted') {
            setHasPermission(true);
            return;
          }

          const { status: newStatus } = await Audio.requestPermissionsAsync();
          setHasPermission(newStatus === 'granted');
        }
      } catch (err) {
        setError(t('error') + ': ' + Constants.ERROR_MESSAGES.TRANSLATION_PERMISSION_FAILED(err.message));
        setToastVisible(true);
        setHasPermission(false);
      }
    };
    checkAndRequestAudioPermission();

    // ✅ FIXED: Enhanced cleanup function
    return () => {
      const cleanup = async () => {
        try {
          // Stop and clean up sound object
          if (soundObject) {
            await soundObject.stopAsync().catch(() => {});
            await soundObject.unloadAsync().catch(() => {});
            setSoundObject(null);
          }
          // Stop and clean up recording
          if (recording) {
            await recording.stopAndUnloadAsync().catch(() => {});
            setRecording(null);
          }
          // Reset speaking state
          setIsSpeaking(false);
        } catch (error) {
          console.warn('Audio cleanup error:', error);
        }
      };
      cleanup();
    };
  }, [t, soundObject, recording]); // ✅ FIXED: Include soundObject and recording in dependencies

  // ✅ FIXED: Session cleanup effect with proper logic
  useEffect(() => {
    if (!session) {
      // User signed out - clean up audio immediately
      const cleanupOnSignOut = async () => {
        try {
          if (soundObject) {
            await soundObject.stopAsync().catch(() => {});
            await soundObject.unloadAsync().catch(() => {});
            setSoundObject(null);
          }
          if (recording) {
            await recording.stopAndUnloadAsync().catch(() => {});
            setRecording(null);
          }
          setIsSpeaking(false);
        } catch (error) {
          console.warn('Error cleaning up audio on sign out:', error);
        }
      };
      cleanupOnSignOut();
    }
  }, [session, soundObject, recording]); // ✅ FIXED: Proper dependencies and immediate execution

  const handleTextChange = useCallback((text) => {
    setInputText(text);
    setError('');
  }, []);

  const checkGuestLimits = async (type = 'text') => {
    if (session) return true;

    const limitCheck = await guestManager.checkTranslationLimit(type);
    if (!limitCheck.allowed) {
      const shouldUpgrade = await guestManager.promptUpgrade(type);
      if (shouldUpgrade) {
        router.push('/(auth)/register');
      }
      return false;
    }
    return true;
  };

  // ✅ FIXED: Updated translate function to use Translation microservice (port 3002)
  const handleTranslate = async (textToTranslate = inputText, isVoice = false) => {
    setError('');
    setIsLoading(true);
    setTranslationSaved(false);
    setTranslationData(null);

    if (!textToTranslate.trim()) {
      setError(t('error') + ': ' + Constants.ERROR_MESSAGES.TRANSLATION_TEXT_REQUIRED);
      setIsLoading(false);
      return;
    }
    if (!sourceLang || !targetLang) {
      setError(t('error') + ': ' + Constants.ERROR_MESSAGES.TRANSLATION_LANGUAGES_REQUIRED);
      setIsLoading(false);
      return;
    }

    const translationType = isVoice ? 'voice' : 'text';
    const canTranslate = await checkGuestLimits(translationType);
    if (!canTranslate) {
      setIsLoading(false);
      return;
    }

    try {
      const token = session?.signed_session_id || '';
      
      // ✅ FIXED: Use existing TranslationService
      const result = await TranslationService.translateText(
        textToTranslate,
        targetLang,
        sourceLang,
        token
      );

      const { translatedText: translatedResult, detectedLang } = result;

      if (!translatedResult) {
        throw new Error('Translation failed');
      }

      setTranslatedText(translatedResult);
      setTranslatedOriginalText(textToTranslate);

      const translation = {
        id: Date.now().toString(),
        fromLang: detectedLang,
        toLang: targetLang,
        original_text: textToTranslate,
        translated_text: translatedResult,
        created_at: new Date().toISOString(),
        type: translationType,
      };
      setTranslationData(translation);

      if (!session) {
        await guestManager.incrementCount(translationType);
      }
    } catch (err) {
      const errorMessage = Helpers.handleError(err);
      if (errorMessage.includes('Invalid or expired session') && session) {
        await signOut();
        setError(t('error') + ': Your session has expired. Please log in again.');
        setToastVisible(true);
      } else {
        setError(t('error') + ': ' + errorMessage);
      }
    } finally {
      setIsLoading(false);
    }
  };

  // ✅ FIXED: Updated handleHear with proper audio cleanup
  const handleHear = async () => {
    if (translatedText) {
      if (!session || !session.signed_session_id) {
        Alert.alert(
          t('error'),
          'Text-to-speech requires login. Would you like to log in?',
          [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Log In', onPress: () => router.push('/(auth)/login') },
          ]
        );
        return;
      }

      // ✅ FIXED: Clean up any existing sound before creating new one
      if (soundObject) {
        try {
          await soundObject.stopAsync();
          await soundObject.unloadAsync();
          setSoundObject(null);
        } catch (error) {
          console.warn('Error cleaning up previous sound:', error);
        }
      }

      setIsSpeaking(true);
      
      try {
        // ✅ FIXED: Use existing TranslationService
        const audioData = await TranslationService.textToSpeech(
          translatedText,
          targetLang,
          session?.signed_session_id
        );
        const audioUri = `${FileSystem.documentDirectory}speech-${Date.now()}.mp3`;
        await FileSystem.writeAsStringAsync(audioUri, Buffer.from(audioData).toString('base64'), {
          encoding: FileSystem.EncodingType.Base64,
        });

        const { sound } = await Audio.Sound.createAsync({ uri: audioUri });
        setSoundObject(sound);
        
        // ✅ FIXED: Add proper status update handler with cleanup
        sound.setOnPlaybackStatusUpdate((status) => {
          if (status.didJustFinish || status.error) {
            setIsSpeaking(false);
            FileSystem.deleteAsync(audioUri).catch(console.error);
            // ✅ FIXED: Clean up sound object properly
            sound.unloadAsync().catch(console.error);
            setSoundObject(null);
          }
        });
        
        await sound.playAsync();
      } catch (err) {
        const errorMessage = Helpers.handleError(err);
        if (errorMessage.includes('Invalid or expired session') && session) {
          // ✅ FIXED: Clean up sound before signing out
          if (soundObject) {
            try {
              await soundObject.stopAsync();
              await soundObject.unloadAsync();
              setSoundObject(null);
            } catch (cleanupError) {
              console.warn('Error cleaning up sound during signout:', cleanupError);
            }
          }
          await signOut();
          setError(t('error') + ': Your session has expired. Please log in again.');
          setToastVisible(true);
        } else {
          setError(t('error') + ': ' + errorMessage);
        }
        setIsSpeaking(false);
      }
    } else {
      Alert.alert(t('error'), t('error') + ': ' + Constants.ERROR_MESSAGES.TRANSLATION_NO_TEXT_TO_HEAR);
    }
  };

  // ✅ FIXED: Updated save function to use User Data microservice (port 3003)
  const handleSave = async () => {
    if (!translatedText || !translationData) {
      Alert.alert(t('error'), t('error') + ': ' + Constants.ERROR_MESSAGES.TRANSLATION_NO_TEXT_TO_SAVE);
      return;
    }
    if (isSaving || translationSaved) return;

    setIsSaving(true);
    try {
      if (session && session.signed_session_id) {
        const endpoint = translationData.type === 'voice'
          ? '/translations/voice'
          : '/translations/text';

        // ✅ FIXED: Use User Data Service for saving
        const payload = {
          fromLang: translationData.fromLang,
          toLang: translationData.toLang,
          original_text: translationData.original_text,
          translated_text: translationData.translated_text,
          type: translationData.type,
        };

        const response = await fetch(`${USER_DATA_API_URL}${endpoint}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${session.signed_session_id}`,
          },
          body: JSON.stringify(payload),
        });

        const text = await response.text();
        let result;
        try { result = JSON.parse(text); } catch { result = {}; }

        if (!response.ok) {
          throw new Error(result.error || `Save failed (${response.status})`);
        }

        const serverId = result.id || translationData.id || Date.now().toString();
        const saved = { ...translationData, id: serverId };

        // Update store
        if (saved.type === 'voice') {
          const { recentVoiceTranslations } = useTranslationStore.getState();
          useTranslationStore.setState({
            recentVoiceTranslations: [saved, ...recentVoiceTranslations].slice(0, 5),
          });
        } else {
          const { recentTextTranslations } = useTranslationStore.getState();
          useTranslationStore.setState({
            recentTextTranslations: [saved, ...recentTextTranslations].slice(0, 5),
          });
        }
      } else {
        // Guest path unchanged
        useTranslationStore.setState((state) => {
          const isDup = state.guestTranslations.some(
            (it) =>
              it.original_text === translationData.original_text &&
              it.translated_text === translationData.translated_text &&
              it.type === translationData.type
          );
          if (isDup) return state;
          const updated = [translationData, ...state.guestTranslations];
          AsyncStorage.setItem('guestTranslations', JSON.stringify(updated));
          return { guestTranslations: updated };
        });
      }

      setTranslationSaved(true);
      Alert.alert(t('success'), t('saveSuccess'));
    } catch (err) {
      const msg = Helpers.handleError(err);
      if (msg.includes('Invalid or expired session') && session) {
        await signOut();
        setError(t('error') + ': Your session has expired. Please log in again.');
      } else {
        setError(t('error') + ': ' + msg);
      }
      setToastVisible(true);
    } finally {
      setIsSaving(false);
    }
  };

  // ✅ FIXED: Updated speech-to-text to use Translation microservice (port 3002)
  const startRecording = async () => {
    setError('');
    setTranslationSaved(false);

    if (!session || !session.signed_session_id) {
      Alert.alert(
        t('error'),
        'Voice translation requires login. Would you like to log in?',
        [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Log In', onPress: () => router.push('/(auth)/login') },
        ]
      );
      return;
    }

    if (!hasPermission) {
      Alert.alert(
        t('error'),
        t('error') + ': ' + Constants.ERROR_MESSAGES.TRANSLATION_PERMISSION_NOT_GRANTED,
        [{ text: 'OK' }]
      );
      return;
    }

    if (!sourceLang || !targetLang) {
      setError(t('error') + ': ' + Constants.ERROR_MESSAGES.TRANSLATION_LANGUAGES_REQUIRED);
      setToastVisible(true);
      return;
    }

    const canRecord = await checkGuestLimits('voice');
    if (!canRecord) return;

    if (recording) {
      try {
        await recording.stopAndUnloadAsync();
      } catch (err) {
        console.error('Failed to clean up existing recording:', err);
      }
      setRecording(null);
    }

    try {
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: true,
        playsInSilentModeIOS: true,
        allowsRecordingAndroid: true,
        staysActiveInBackground: false,
        shouldDuckAndroid: true,
      });

      const recordingOptions = {
        android: {
          extension: '.m4a',
          outputFormat: 2,
          audioEncoder: 3,
          sampleRate: 44100,
          numberOfChannels: 1,
          bitRate: 128000,
        },
        ios: {
          extension: '.m4a',
          audioQuality: 'high',
          outputFormat: 'aac',
          sampleRate: 44100,
          numberOfChannels: 1,
          bitRate: 128000,
          linearPCMBitDepth: 16,
          linearPCMIsBigEndian: false,
          linearPCMIsFloat: false,
        },
      };

      const { recording: newRecording } = await Audio.Recording.createAsync(recordingOptions);
      setRecording(newRecording);
      setIsRecording(true);
    } catch (err) {
      setError(t('error') + ': ' + Constants.ERROR_MESSAGES.TRANSLATION_RECORDING_FAILED(err.message));
      setToastVisible(true);
      if (recording) {
        try {
          await recording.stopAndUnloadAsync();
        } catch (cleanupErr) {
          console.error('Failed to clean up recording after error:', cleanupErr);
        }
      }
      setRecording(null);
      setIsRecording(false);
    }
  };

  const stopRecording = async () => {
  if (!recording) {
    setIsRecording(false);
    setIsLoading(false);
    return;
  }

  setIsRecording(false);
  setIsLoading(true);
  setError('');

  try {
    await recording.stopAndUnloadAsync();
    const status = await recording.getStatusAsync();

    if (!status || status.durationMillis < 1000) {
      setError(t('error') + ': ' + Constants.ERROR_MESSAGES.TRANSLATION_RECORDING_TOO_SHORT);
      setToastVisible(true);
      setRecording(null);
      setIsLoading(false);
      return;
    }

    const uri = recording.getURI();
    if (!uri || !uri.startsWith('file://')) {
      setError(t('error') + ': ' + Constants.ERROR_MESSAGES.TRANSLATION_INVALID_AUDIO_PATH);
      setToastVisible(true);
      setRecording(null);
      setIsLoading(false);
      return;
    }

    // Pass the URI directly - NO base64 conversion
    const transcribedText = await TranslationService.speechToText(
      uri,
      sourceLang,
      session?.signed_session_id
    );
    
    if (!transcribedText) {
      throw new Error('Speech-to-text failed');
    }

    setInputText(transcribedText);
    await handleTranslate(transcribedText, true);
  } catch (err) {
    const errorMessage = Helpers.handleError(err);
    if (errorMessage.includes('Invalid or expired session') && session) {
      await signOut();
      setError(t('error') + ': Your session has expired. Please log in again.');
      setToastVisible(true);
    } else {
      setError(t('error') + ': ' + errorMessage);
    }
  } finally {
    setRecording(null);
    setIsLoading(false);
  }
};

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent} pointerEvents="box-none">
        <View style={styles.content}>
          {/* Language Selection */}
          <View style={styles.languageContainer}>
            <View style={styles.languageSection}>
              <Text style={[styles.label, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.TEXT }]}>{t('sourceLang')}</Text>
              <LanguageSearch
                onSelectLanguage={(lang) => {
                  setSourceLang(lang);
                  setError('');
                }}
                selectedLanguage={sourceLang}
              />
            </View>
            <View style={styles.languageSection}>
              <Text style={[styles.label, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.TEXT }]}>{t('targetLang')}</Text>
              <LanguageSearch
                onSelectLanguage={(lang) => {
                  setTargetLang(lang);
                  setError('');
                }}
                selectedLanguage={targetLang}
              />
            </View>
          </View>

          {/* Text Input with Microphone */}
          <View style={styles.inputContainer}>
            <Text style={[styles.label, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.TEXT }]}>{t('original')}</Text>
            <View style={[styles.inputWrapper, { backgroundColor: isDarkMode ? Constants.INPUT.BACKGROUND_COLOR_DARK : Constants.COLORS.CARD }]}>
              <MemoizedTextInput
                ref={textInputRef}
                value={inputText}
                onChangeText={handleTextChange}
                style={[styles.input, { backgroundColor: isDarkMode ? Constants.INPUT.BACKGROUND_COLOR_DARK : Constants.COLORS.CARD, color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.TEXT }]}
                placeholder={t('original')}
                placeholderTextColor={isDarkMode ? Constants.INPUT.PLACEHOLDER_COLOR_DARK : Constants.INPUT.PLACEHOLDER_COLOR_LIGHT}
                multiline
                numberOfLines={4}
                keyboardType="default"
                autoCapitalize="sentences"
                autoCorrect={false}
                textAlign="auto"
                accessibilityLabel="Enter text to translate"
              />
              <IconButton
                icon={isRecording ? 'microphone-off' : 'microphone'}
                size={24}
                onPress={isRecording ? stopRecording : startRecording}
                style={styles.microphoneButton}
                iconColor={isRecording ? Constants.COLORS.DESTRUCTIVE : (isDarkMode ? Constants.INPUT.MICROPHONE_COLOR_DARK : Constants.COLORS.PRIMARY)}
                disabled={isLoading}
                accessibilityLabel={isRecording ? "Stop recording" : "Start recording"}
                hitSlop={{ top: 20, bottom: 20, left: 20, right: 20 }}
              />
            </View>
          </View>

          {/* Translate Button */}
          <TouchableOpacity
            onPress={() => {
              setError('');
              handleTranslate(inputText, false);
            }}
            disabled={isLoading || isRecording}
            style={[styles.translateButton, { backgroundColor: isDarkMode ? Constants.BUTTON.BACKGROUND_COLOR_DARK : Constants.COLORS.PRIMARY }]}
          >
            <Text style={styles.translateButtonLabel}>{t('translate')}</Text>
          </TouchableOpacity>

          {/* Error Messages */}
          {error ? (
            <Text style={[styles.error, { color: Constants.COLORS.DESTRUCTIVE }]}>{error}</Text>
          ) : null}

          {/* Loading Indicator */}
          {isLoading ? (
            <ActivityIndicator size="large" color={isDarkMode ? '#fff' : Constants.COLORS.PRIMARY} style={styles.loading} />
          ) : null}

          {/* Translation Results */}
          {translatedText ? (
            <View style={[styles.resultContainer, { backgroundColor: isDarkMode ? Constants.INPUT.BACKGROUND_COLOR_DARK : Constants.COLORS.CARD }]}>
              <Text style={[styles.resultLabel, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.TEXT }]}>{t('original')}</Text>
              <Text style={[styles.original, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.SECONDARY_TEXT }]}>{translatedOriginalText}</Text>
              <Text style={[styles.resultLabel, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.TEXT }]}>{t('translated')}</Text>
              <Text style={[styles.translated, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.SECONDARY_TEXT }]}>{translatedText}</Text>
              
              {/* Action Buttons */}
              <View style={styles.actionButtons}>
                <TouchableOpacity
                  onPress={() => {
                    setError('');
                    handleHear();
                  }}
                  disabled={isLoading || isSpeaking}
                  style={[
                    styles.actionButton, 
                    { backgroundColor: isDarkMode ? Constants.BUTTON.BACKGROUND_COLOR_DARK : Constants.COLORS.PRIMARY },
                    isLoading || isSpeaking ? styles.disabledButton : null
                  ]}
                  hitSlop={{ top: 15, bottom: 15, left: 15, right: 15 }}
                  activeOpacity={0.7}
                  accessibilityLabel="Play translated text as speech"
                  accessibilityRole="button"
                >
                  <FontAwesome 
                    name="volume-up" 
                    size={20} 
                    color={Constants.COLORS.CARD} 
                    style={styles.actionIcon} 
                  />
                  <Text style={styles.actionButtonText}>{t('hear')}</Text>
                </TouchableOpacity>
                
                <TouchableOpacity
                  onPress={() => {
                    setError('');
                    handleSave();
                  }}
                  disabled={translationSaved || isLoading || isSaving}
                  style={[
                    styles.actionButton, 
                    { backgroundColor: isDarkMode ? Constants.BUTTON.BACKGROUND_COLOR_DARK : Constants.COLORS.PRIMARY },
                    (translationSaved || isLoading || isSaving) ? styles.disabledButton : null
                  ]}
                  hitSlop={{ top: 15, bottom: 15, left: 15, right: 15 }}
                  activeOpacity={0.7}
                  accessibilityLabel="Save translation"
                  accessibilityRole="button"
                >
                  <FontAwesome 
                    name="save" 
                    size={20} 
                    color={Constants.COLORS.CARD} 
                    style={styles.actionIcon} 
                  />
                  <Text style={styles.actionButtonText}>{t('save')}</Text>
                </TouchableOpacity>
              </View>
              
              {/* Success Message */}
              {translationSaved ? (
                <Text style={[styles.savedMessage, { color: Constants.COLORS.SUCCESS }]}>{t('saveSuccess')}</Text>
              ) : null}
            </View>
          ) : null}

          {/* Toast for Errors */}
          <Toast
            message={error}
            visible={toastVisible}
            onHide={() => {
              setToastVisible(false);
              setError('');
            }}
            style={styles.toast}
          />
        </View>
      </ScrollView>
    </View>
  );
});

// Main TextVoiceTranslationScreen component
const TextVoiceTranslationScreen = () => {
  const { t, locale } = useTranslation();
  const { session, preferences, guestManager } = useEnhancedSession();
  const { recentTextTranslations, recentVoiceTranslations, guestTranslations, addTextTranslation, addVoiceTranslation } = useTranslationStore();
  const { isDarkMode } = useThemeStore();
  const [sourceLang, setSourceLang] = useState(preferences?.defaultFromLang || '');
  const [targetLang, setTargetLang] = useState(preferences?.defaultToLang || '');

  useEffect(() => {
    if (preferences) {
      setSourceLang(preferences.defaultFromLang || '');
      setTargetLang(preferences.defaultToLang || '');
    }
  }, [preferences]);

  const recentTranslations = useMemo(() => {
  if (session) {
    const textOnly = recentTextTranslations.filter(item => item.type === 'text');
    const voiceOnly = recentVoiceTranslations.filter(item => item.type === 'voice');
    return [...textOnly, ...voiceOnly].slice(-5);
  } else {
    const guestRecent = guestTranslations.filter(item => 
      item.type === 'text' || item.type === 'voice'
    );
    return guestRecent.slice(-5);
  }
}, [session, recentTextTranslations, recentVoiceTranslations, guestTranslations]);

  return (
    <View style={[styles.container, { backgroundColor: isDarkMode ? Constants.SCREEN.BACKGROUND_COLOR_DARK : Constants.COLORS.BACKGROUND }]}>
      <FlatList
        data={recentTranslations}
        keyExtractor={(item) => item.id}
        ListHeaderComponent={
          <>
            <TextVoiceInput
              t={t}
              isDarkMode={isDarkMode}
              session={session}
              sourceLang={sourceLang}
              setSourceLang={setSourceLang}
              targetLang={targetLang}
              setTargetLang={setTargetLang}
              onAddTextTranslation={addTextTranslation}
              onAddVoiceTranslation={addVoiceTranslation}
              guestManager={guestManager}
            />
            {(recentTranslations.length > 0) && (
              <View style={styles.historyContainer}>
                <Text style={[styles.historyTitle, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.TEXT }]}>
                  {t('recentHistory')}
                </Text>
              </View>
            )}
          </>
        }
        renderItem={({ item }) => (
          <View style={[styles.translationItem, { backgroundColor: isDarkMode ? Constants.INPUT.BACKGROUND_COLOR_DARK : Constants.COLORS.CARD }]}>
            <Text style={[styles.translationText, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.SECONDARY_TEXT }]}>
              {t('translated')}: {item.translated_text}
            </Text>
            <Text style={[styles.translationText, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.SECONDARY_TEXT }]}>
              {t('createdAt')}: {Helpers.formatDate(item.created_at, locale)}
            </Text>
          </View>
        )}
        contentContainerStyle={styles.scrollContent}
        extraData={isDarkMode}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    paddingBottom: Constants.SPACING.SECTION * 2,
  },
  content: {
    padding: Constants.SPACING.SECTION,
  },
  languageContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: Constants.SPACING.SECTION,
  },
  languageSection: {
    flex: 1,
    marginHorizontal: Constants.SPACING.SMALL,
  },
  label: {
    fontSize: Constants.FONT_SIZES.BODY,
    fontWeight: 'bold',
    marginBottom: Constants.SPACING.MEDIUM,
    letterSpacing: 0.5,
  },
  inputContainer: {
    marginBottom: Constants.SPACING.SECTION,
  },
  inputWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    shadowColor: Constants.COLORS.SHADOW,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  input: {
    flex: 1,
    padding: Constants.SPACING.LARGE,
    borderRadius: 12,
    minHeight: 100,
    textAlignVertical: 'top',
    fontSize: Constants.FONT_SIZES.BODY,
  },
  microphoneButton: {
    marginRight: Constants.SPACING.MEDIUM,
    padding: 10,
  },
  translateButton: {
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 10,
    marginBottom: Constants.SPACING.SECTION,
    alignItems: 'center',
  },
  translateButtonLabel: {
    fontSize: Constants.FONT_SIZES.BODY,
    fontWeight: 'bold',
    color: Constants.COLORS.CARD,
  },
  error: {
    color: Constants.COLORS.DESTRUCTIVE,
    fontSize: Constants.FONT_SIZES.SECONDARY,
    marginBottom: Constants.SPACING.LARGE,
    textAlign: 'center',
  },
  loading: {
    marginVertical: Constants.SPACING.SECTION,
  },
  resultContainer: {
    marginTop: Constants.SPACING.SECTION,
    padding: Constants.SPACING.SECTION,
    borderRadius: 12,
    shadowColor: Constants.COLORS.SHADOW,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
    width: '100%',
  },
  resultLabel: {
    fontSize: Constants.FONT_SIZES.BODY,
    fontWeight: 'bold',
    marginBottom: Constants.SPACING.MEDIUM,
    letterSpacing: 0.5,
  },
  original: {
    fontSize: Constants.FONT_SIZES.BODY,
    marginBottom: Constants.SPACING.LARGE,
    lineHeight: 24,
  },
  translated: {
    fontSize: Constants.FONT_SIZES.BODY,
    marginBottom: Constants.SPACING.LARGE,
    lineHeight: 24,
  },
  actionButtons: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginTop: Constants.SPACING.MEDIUM,
  },
  actionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14, 
    paddingHorizontal: 28, 
    borderRadius: 12,
    minWidth: 140,
    elevation: 3,
  },
  actionIcon: {
    marginRight: Constants.SPACING.MEDIUM,
  },
  disabledButton: {
    opacity: 0.6,
  },
  actionButtonText: {
    color: Constants.COLORS.CARD,
    fontSize: Constants.FONT_SIZES.SECONDARY,
    fontWeight: '600',
  },
  savedMessage: {
    fontSize: Constants.FONT_SIZES.SECONDARY,
    color: Constants.COLORS.SUCCESS,
    marginTop: Constants.SPACING.MEDIUM,
    textAlign: 'center',
  },
  historyContainer: {
    marginTop: Constants.SPACING.SECTION,
  },
  historyTitle: {
    fontSize: Constants.FONT_SIZES.SUBTITLE,
    fontWeight: 'bold',
    marginBottom: Constants.SPACING.MEDIUM,
    letterSpacing: 0.5,
  },
  translationItem: {
    padding: Constants.SPACING.LARGE,
    borderRadius: 12,
    marginBottom: Constants.SPACING.MEDIUM,
    shadowColor: Constants.COLORS.SHADOW,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  translationText: {
    fontSize: Constants.FONT_SIZES.SECONDARY,
    marginBottom: Constants.SPACING.SMALL,
    lineHeight: 20,
  },
});

export default TextVoiceTranslationScreen;