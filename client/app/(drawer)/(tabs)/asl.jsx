// ===== OPTIMIZED ASL SCREEN - IMPROVED UX WITH AUTO CAMERA MANAGEMENT =====
// app/(drawer)/(tabs)/asl.jsx
import React, { useState, useRef, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, Pressable, Dimensions, Alert, Linking, Platform, AppState } from 'react-native';
import { Camera, useCameraDevice, useCameraPermission } from 'react-native-vision-camera';
import * as FileSystem from 'expo-file-system';
import { useTranslation } from '../../../utils/TranslationContext';
import { useEnhancedSession } from '../../../utils/EnhancedSessionContext'; 
import useTranslationStore from '../../../stores/TranslationStore';
import Toast from '../../../components/Toast';
import Constants from '../../../utils/Constants';
import useThemeStore from '../../../stores/ThemeStore';
import { useRouter, useFocusEffect } from 'expo-router';
import SendIntentAndroid from 'react-native-send-intent';
import { ScrollView } from 'react-native';

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

// BALANCED OPTIMIZATION FOR FAST RESPONSE + SERVER EFFICIENCY
const RECONNECT_INTERVAL = 2000; 
const SNAPSHOT_INTERVAL = 500; // 2 FPS - Balanced for speed vs server load
const GESTURE_CONFIDENCE_THRESHOLD = 0.6; // Higher threshold for accuracy
const GESTURE_STABILITY_COUNT = 1; // INSTANT response - no waiting for confirmation
const DEBOUNCE_TIME = 800; // Prevent same gesture spam
const FRAME_TIMEOUT = 10000; // 10 seconds timeout for frame data
const CONNECTION_CHECK_INTERVAL = 3000; // Check connection every 3 seconds

const getSafeMessage = (msg) => {
  if (typeof msg === 'string') return msg;
  if (msg instanceof Error && msg.message) return msg.message;
  if (React.isValidElement(msg)) return '[Invalid React Element]';
  try {
    return JSON.stringify(msg);
  } catch {
    return '[Unrenderable error object]';
  }
};

const { WEBSOCKET_URL, COLORS, FONT_SIZES, SPACING } = Constants;

const ASLTranslationScreen = () => {
  const { t } = useTranslation();
  const { session, isAuthenticated, guestManager } = useEnhancedSession(); 
  const [remainingTranslations, setRemainingTranslations] = useState(null);
  const { addTextTranslation } = useTranslationStore();
  const { isDarkMode } = useThemeStore();
  const [translatedText, setTranslatedText] = useState('');
  const [confidence, setConfidence] = useState(0);
  const [error, setError] = useState('');
  const [toastVisible, setToastVisible] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);
  const [wsConnected, setWsConnected] = useState(false);
  const [processingTime, setProcessingTime] = useState(0); // For performance monitoring
  const [lastFrameTime, setLastFrameTime] = useState(null); // Track frame reception
  const [isScreenFocused, setIsScreenFocused] = useState(true);
  const [appState, setAppState] = useState(AppState.currentState);
  
  const wsRef = useRef(null);
  const lastGestureRef = useRef(null);
  const gestureCountRef = useRef({});
  const gestureTimeoutRef = useRef(null);
  const snapshotIntervalRef = useRef(null);
  const cameraRef = useRef(null);
  const lastSnapshotTime = useRef(0);
  const frameTimeoutRef = useRef(null);
  const connectionCheckRef = useRef(null);
  const wasStreamingRef = useRef(false); // Track if was streaming before unfocus
  const router = useRouter();

  // Camera permissions and device
  const { hasPermission, requestPermission } = useCameraPermission();
  const device = useCameraDevice('front');

  // Handle app state changes
  useEffect(() => {
    const handleAppStateChange = (nextAppState) => {
      console.log('📱 App state changed:', appState, '->', nextAppState);
      
      if (appState.match(/inactive|background/) && nextAppState === 'active') {
        console.log('📱 App became active');
        // App became active - restore streaming if it was active before
        if (wasStreamingRef.current && isScreenFocused) {
          console.log('🔄 Restoring streaming after app became active');
          setTimeout(() => {
            startStreaming();
          }, 1000);
        }
      } else if (appState === 'active' && nextAppState.match(/inactive|background/)) {
        console.log('📱 App going to background');
        // App going to background - stop streaming but remember state
        if (isStreaming) {
          wasStreamingRef.current = true;
          console.log('🛑 Stopping streaming due to app background');
          stopStreaming();
        }
      }
      
      setAppState(nextAppState);
    };

    const subscription = AppState.addEventListener('change', handleAppStateChange);
    return () => subscription?.remove();
  }, [appState, isStreaming, isScreenFocused]);

  // Handle screen focus/unfocus with useFocusEffect
  useFocusEffect(
    useCallback(() => {
      console.log('🎯 Screen focused');
      setIsScreenFocused(true);
      
      // Only restore streaming if user manually started it and app is active
      if (appState === 'active' && wasStreamingRef.current && !isStreaming) {
        console.log('🔄 Restoring streaming after screen focus');
        setTimeout(() => {
          startStreaming();
        }, 500);
      }

      return () => {
        console.log('🎯 Screen unfocused');
        setIsScreenFocused(false);
        
        // Stop streaming when screen loses focus, but remember the state
        if (isStreaming) {
          wasStreamingRef.current = true;
          console.log('🛑 Stopping streaming due to screen unfocus');
          stopStreaming();
        }
      };
    }, [isStreaming, appState])
  );

  // Enhanced WebSocket with better error handling and reconnection
  const initializeWebSocket = useCallback(() => {
    if (!isScreenFocused || appState !== 'active') {
      console.log('⚠️ Not initializing WebSocket - screen not focused or app not active');
      return;
    }

    try {
      // Clean up existing connection
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }

      console.log('🔌 Initializing WebSocket connection...');
      wsRef.current = new WebSocket(WEBSOCKET_URL);
      
      wsRef.current.onopen = () => {
        console.log('✅ WebSocket connected - Connection Status: Connected to server');
        setWsConnected(true);
        setError('');
        
        // Send initial configuration for faster processing
        if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
          wsRef.current.send(JSON.stringify({
            type: 'config',
            fast_mode: true,
            confidence_threshold: GESTURE_CONFIDENCE_THRESHOLD
          }));
        }
      };
      
      wsRef.current.onmessage = (event) => {
        const receiveTime = Date.now();
        setLastFrameTime(receiveTime);
        
        // Clear any existing frame timeout
        if (frameTimeoutRef.current) {
          clearTimeout(frameTimeoutRef.current);
          frameTimeoutRef.current = null;
        }
        
        // Set new frame timeout
        frameTimeoutRef.current = setTimeout(() => {
          console.warn('⏱️ Connection Status: No frame data received for 10 seconds - server may be unresponsive');
          if (wsRef.current && isStreaming) {
            setError('No response from server for 10 seconds. Check your Python server.');
            setToastVisible(true);
          }
        }, FRAME_TIMEOUT);
        
        try {
          const data = JSON.parse(event.data);
          
          if (data.error) {
            console.error('Server error:', data.error);
            // Filter out common frame-related errors that don't need user notification
            const frameErrors = [
              'No frame data received',
              'No frame data',
              'frame data',
              'Invalid frame',
              'Empty frame'
            ];
            
            const shouldShowError = !frameErrors.some(errorText => 
              data.error.toLowerCase().includes(errorText.toLowerCase())
            );
            
            if (shouldShowError) {
              setError(`Server error: ${data.error}`);
              setToastVisible(true);
            }
            return;
          }
          
          // Calculate processing time for performance monitoring
          if (data.timestamp) {
            const processTime = receiveTime - (data.timestamp * 1000);
            setProcessingTime(processTime);
          }
          
          if (data.hand_detected && data.gesture && data.gesture !== 'None' && data.gesture !== 'Unknown') {
            const newGesture = data.gesture;
            const newConfidence = data.confidence;
            
            console.log('📥 Fast gesture:', newGesture, 'confidence:', newConfidence.toFixed(2));
            
            // INSTANT RESPONSE with debouncing to prevent spam
            if (newConfidence > GESTURE_CONFIDENCE_THRESHOLD) {
              const now = Date.now();
              
              // Check if enough time has passed since last gesture of same type
              if (lastGestureRef.current !== newGesture || 
                  !lastGestureRef.gestureTime || 
                  (now - lastGestureRef.gestureTime) > DEBOUNCE_TIME) {
                
                setTranslatedText(newGesture);
                setConfidence(newConfidence);
                lastGestureRef.current = newGesture;
                lastGestureRef.gestureTime = now;

                // Immediately save translation which is not wanted
                //saveTranslation(newGesture);
                
                console.log(`⚡ INSTANT: ${newGesture} (${newConfidence.toFixed(2)})`);
              }
            }
          }
          
        } catch (err) {
          console.error('Error parsing WebSocket message:', err);
        }
      };
      
      wsRef.current.onclose = (event) => {
        console.log('❌ WebSocket disconnected - Connection Status: Disconnected from server. Code:', event.code, 'Reason:', event.reason);
        setWsConnected(false);
        setLastFrameTime(null);
        
        // Clear frame timeout
        if (frameTimeoutRef.current) {
          clearTimeout(frameTimeoutRef.current);
          frameTimeoutRef.current = null;
        }
        
        // Only attempt reconnection if streaming and screen is focused
        if (isStreaming && isScreenFocused && appState === 'active') {
          console.log('🔄 Connection Status: Attempting to reconnect in', RECONNECT_INTERVAL, 'ms');
          setTimeout(() => {
            console.log('🔄 Attempting to reconnect...');
            initializeWebSocket();
          }, RECONNECT_INTERVAL);
        }
      };
      
      wsRef.current.onerror = (error) => {
        console.error('WebSocket error - Connection Status: Error occurred:', error);
        setWsConnected(false);
        setLastFrameTime(null);
        
        // Only show connection errors if screen is focused
        if (isScreenFocused && appState === 'active') {
          setError('Connection to server lost. Please check if your Python server is running.');
          setToastVisible(true);
        }
      };
      
    } catch (err) {
      console.error('Failed to initialize WebSocket:', err);
      if (isScreenFocused && appState === 'active') {
        setError('Failed to initialize connection');
        setToastVisible(true);
      }
    }
  }, [isStreaming, isScreenFocused, appState]);

  // Save translation to store
  const saveTranslation = useCallback(async (gestureText) => {
    try {
      await addTextTranslation({
        id: Date.now().toString(),
        fromLang: 'asl',
        toLang: 'en',
        original_text: 'ASL Gesture',
        translated_text: gestureText,
        created_at: new Date().toISOString(),
        type: 'asl',
      }, !isAuthenticated, session?.signed_session_id);

      if (!isAuthenticated) await guestManager.incrementCount('asl');
    } catch (err) {
      console.error('Error saving translation:', err);
    }
  }, [addTextTranslation, isAuthenticated, session, guestManager]);

  // Enhanced snapshot capture with better error handling
  const captureSnapshot = useCallback(async () => {
    // Check if all conditions are met for capturing
    if (!cameraRef.current || 
        !wsRef.current || 
        wsRef.current.readyState !== WebSocket.OPEN ||
        !isScreenFocused ||
        appState !== 'active') {
      return;
    }

    const now = Date.now();
    
    // Prevent too frequent snapshots (respect the interval)
    if (now - lastSnapshotTime.current < SNAPSHOT_INTERVAL) {
      return;
    }
    
    lastSnapshotTime.current = now;

    try {
      const startTime = Date.now();
      
      // Take snapshot with OPTIMIZED settings for speed vs quality balance
      const snapshot = await cameraRef.current.takeSnapshot({
        quality: 40, // Balanced quality for speed
        skipMetadata: true,
      });

      // Ensure we still have a valid WebSocket connection
      if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
        console.warn('⚠️ WebSocket closed during snapshot processing');
        return;
      }

      const filePath = snapshot.path.startsWith('file://') ? snapshot.path : `file://${snapshot.path}`;
      
      // Read file as base64
      const base64 = await FileSystem.readAsStringAsync(filePath, {
        encoding: FileSystem.EncodingType.Base64,
      });

      // Double-check WebSocket is still open before sending
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        const frameData = {
          frame: `data:image/jpeg;base64,${base64}`,
          timestamp: Date.now() / 1000,
          client_timestamp: startTime
        };
        
        wsRef.current.send(JSON.stringify(frameData));
        
        const captureTime = Date.now() - startTime;
        if (captureTime > 200) {
          console.warn(`⚠️ Slow capture: ${captureTime}ms`);
        }
      }
      
    } catch (err) {
      console.error('Error capturing snapshot:', err);
      // Don't show user errors for snapshot failures unless it's persistent
    }
  }, [isScreenFocused, appState]);

  // Function to check server connectivity
  const checkServerConnection = useCallback(async () => {
    try {
      console.log('🔍 Checking server connection...');
      
      const testWs = new WebSocket(WEBSOCKET_URL);
      
      testWs.onopen = () => {
        console.log('✅ Server is reachable');
        testWs.close();
        Alert.alert(
          "Server Status ✅",
          "Your Python server is running and reachable!\n\nASLearn should work properly now.",
          [{ text: "Great!" }]
        );
      };
      
      testWs.onerror = (error) => {
        console.log('❌ Server not reachable:', error);
        testWs.close();
        Alert.alert(
          "Server Status ❌",
          "Cannot connect to Python server.\n\nPlease:\n1. Start your Python server\n2. Check the server address\n3. Ensure both devices are on same network",
          [
            { text: "OK" },
            {
              text: "Help",
              onPress: () => {
                Alert.alert(
                  "Server Troubleshooting",
                  `Current server URL: ${WEBSOCKET_URL}\n\nCommon fixes:\n• Restart Python server\n• Check IP address\n• Verify port is open\n• Check firewall settings\n• Ensure WiFi connection`,
                  [{ text: "Thanks!" }]
                );
              }
            }
          ]
        );
      };
      
      setTimeout(() => {
        if (testWs.readyState === WebSocket.CONNECTING) {
          testWs.close();
          Alert.alert(
            "Server Timeout ⏱️",
            "Server connection timed out.\n\nPlease check if your Python server is running and accessible.",
            [{ text: "OK" }]
          );
        }
      }, 5000);
      
    } catch (error) {
      console.error('Server check error:', error);
      setError(`Server check failed: ${error.message}`);
      setToastVisible(true);
    }
  }, []);

  // Enhanced function to open ASLearn app
  const openASLearnApp = useCallback(async () => {
    const packageName = "com.example.asltranslator";
    
    try {
      console.log('🚀 Opening ASLearn app...');
      
      try {
        console.log('⚙️ Trying to open ASLearn via app settings...');
        
        const settingsUrl = `android-app://${packageName}`;
        const canOpen = await Linking.canOpenURL(settingsUrl);
        
        if (canOpen) {
          await Linking.openURL(settingsUrl);
          console.log('✅ ASLearn opened via app settings');
          
          Alert.alert(
            "ASLearn Opened! 🎉",
            "ASLearn has been launched successfully!\n\n⚠️ Remember: Make sure your Python server is running for full functionality.",
            [
              { text: "Got it!" },
              {
                text: "Check Server",
                onPress: checkServerConnection
              }
            ]
          );
          return;
        }
      } catch (settingsError) {
        console.log('❌ App settings method failed:', settingsError.message);
      }
      
      try {
        console.log('⚙️ Trying alternative app settings method...');
        
        const appSettingsUrl = `package:${packageName}`;
        const canOpenSettings = await Linking.canOpenURL(appSettingsUrl);
        
        if (canOpenSettings) {
          await Linking.openURL(appSettingsUrl);
          console.log('✅ ASLearn opened via alternative app settings');
          
          Alert.alert(
            "ASLearn Opened! 🎉",
            "ASLearn has been launched!\n\nDon't forget to start your Python server for full functionality.",
            [
              { text: "Thanks!" },
              {
                text: "Server Status",
                onPress: checkServerConnection
              }
            ]
          );
          return;
        }
      } catch (altSettingsError) {
        console.log('❌ Alternative app settings failed:', altSettingsError.message);
      }
      
      if (SendIntentAndroid && SendIntentAndroid.openApp) {
        try {
          console.log('📱 Trying SendIntentAndroid as fallback...');
          const wasOpened = await SendIntentAndroid.openApp(packageName);
          
          if (wasOpened) {
            console.log('✅ ASLearn opened with SendIntentAndroid');
            Alert.alert(
              "ASLearn Launched! 🎉",
              "Remember to keep your Python server running!",
              [
                { text: "Got it!" },
                {
                  text: "Check Server",
                  onPress: checkServerConnection
                }
              ]
            );
            return;
          }
        } catch (sendIntentError) {
          console.log('❌ SendIntentAndroid fallback failed:', sendIntentError.message);
        }
      }
      
      const customSchemes = [
        'aslearn://open',
        'asltranslator://open'
      ];
      
      for (const scheme of customSchemes) {
        try {
          console.log(`🔗 Trying scheme: ${scheme}`);
          const canOpen = await Linking.canOpenURL(scheme);
          if (canOpen) {
            await Linking.openURL(scheme);
            console.log(`✅ ASLearn opened with scheme: ${scheme}`);
            Alert.alert(
              "ASLearn Launched! 🎉",
              "Remember to keep your Python server running!",
              [{ text: "Got it!" }]
            );
            return;
          }
        } catch (schemeError) {
          console.log(`❌ Scheme ${scheme} failed:`, schemeError.message);
        }
      }
      
      console.log('❌ All automated methods failed');
      Alert.alert(
        "Manual Launch Required",
        "Automatic launch failed, but ASLearn may be installed.\n\nPlease:\n\n1. Manually open ASLearn from your home screen or app drawer\n2. Ensure your Python server is running\n3. Both apps should work together once connected",
        [
          { text: "OK" },
          {
            text: "Check Server", 
            onPress: checkServerConnection
          }
        ]
      );
      
    } catch (error) {
      console.error('Error launching ASLearn:', error);
      setError(`ASLearn launch error: ${error.message}`);
      setToastVisible(true);
    }
  }, [checkServerConnection]);

  // Guest limit checking with GuestManager
  const checkGuestLimits = useCallback(async () => {
    if (isAuthenticated) return true;

    const limitCheck = await guestManager.checkTranslationLimit('asl');
    if (!limitCheck.allowed) {
      const shouldUpgrade = await guestManager.promptUpgrade('asl');
      if (shouldUpgrade) {
        router.push('/(auth)/register');
      }
      return false;
    }
    return true;
  }, [isAuthenticated, guestManager, router]);

  // Enhanced start streaming with better state management
  const startStreaming = useCallback(async () => {
    // Don't start if screen is not focused or app is not active
    if (!isScreenFocused || appState !== 'active') {
      console.log('⚠️ Not starting streaming - screen not focused or app not active');
      return;
    }

    setError('');
    
    if (!hasPermission) {
      const granted = await requestPermission();
      if (!granted) {
        setError(t('error') + ': Camera permission not granted.');
        setToastVisible(true);
        return;
      }
    }

    const canTranslate = await checkGuestLimits();
    if (!canTranslate) return;

    console.log('🚀 Starting OPTIMIZED ASL streaming...');
    setIsStreaming(true);
    setTranslatedText('');
    setConfidence(0);
    setProcessingTime(0);
    setLastFrameTime(null);
    gestureCountRef.current = {};
    lastGestureRef.current = null;
    lastSnapshotTime.current = 0;
    
    // Initialize WebSocket connection
    if (!wsConnected || !wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      initializeWebSocket();
    }
    
    // Wait a bit for WebSocket to connect before starting snapshots
    setTimeout(() => {
      if (isScreenFocused && appState === 'active') {
        snapshotIntervalRef.current = setInterval(captureSnapshot, SNAPSHOT_INTERVAL);
      }
    }, 1000);
    
  }, [hasPermission, requestPermission, checkGuestLimits, wsConnected, initializeWebSocket, captureSnapshot, t, isScreenFocused, appState]);

  // Enhanced stop streaming with complete cleanup
  const stopStreaming = useCallback(() => {
    console.log('🛑 Stopping ASL streaming...');
    setIsStreaming(false);
    wasStreamingRef.current = false; // Clear the streaming memory when manually stopped
    
    // Clear all intervals and timeouts
    if (snapshotIntervalRef.current) {
      clearInterval(snapshotIntervalRef.current);
      snapshotIntervalRef.current = null;
    }
    
    if (gestureTimeoutRef.current) {
      clearTimeout(gestureTimeoutRef.current);
      gestureTimeoutRef.current = null;
    }
    
    if (frameTimeoutRef.current) {
      clearTimeout(frameTimeoutRef.current);
      frameTimeoutRef.current = null;
    }
    
    if (connectionCheckRef.current) {
      clearInterval(connectionCheckRef.current);
      connectionCheckRef.current = null;
    }
    
    // Close WebSocket connection
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    
    setWsConnected(false);
    setProcessingTime(0);
    setLastFrameTime(null);
    
    console.log('📊 Connection Status: WebSocket closed, streaming stopped manually');
  }, []);

  // Monitor guest usage for the ASL translations
  useEffect(() => {
    if (!isAuthenticated && isStreaming) {
      const checkUsage = async () => {
        const stats = await guestManager.getUsageStats();
        const aslStats = stats.asl;
        if (aslStats) {
          setRemainingTranslations(aslStats.remaining);
          
          if (aslStats.remaining <= 2 && aslStats.remaining > 0) {
            setError(`Only ${aslStats.remaining} ASL translations remaining!`);
            setToastVisible(true);
          }
        }
      };
      
      checkUsage();
      const interval = setInterval(checkUsage, 5000);
      return () => clearInterval(interval);
    }
  }, [isAuthenticated, isStreaming, guestManager]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      console.log('🧹 Component unmounting - cleaning up...');
      stopStreaming();
      wasStreamingRef.current = false;
    };
  }, [stopStreaming]);

  const handleDeleteTranslation = () => {
    setTranslatedText('');
    setConfidence(0);
    lastGestureRef.current = null;
    lastGestureRef.gestureTime = null;
  };

  // Reset state on auth changes
  useEffect(() => {
    console.log('Auth status changed - resetting ASL state');
    
    if (isStreaming) {
      stopStreaming();
    }
    
    setTranslatedText('');
    setConfidence(0);
    setError('');
    setToastVisible(false);
    setRemainingTranslations(null);
    setProcessingTime(0);
    setLastFrameTime(null);
    
    lastGestureRef.current = null;
    gestureCountRef.current = {};
    wasStreamingRef.current = false;
    
  }, [isAuthenticated, stopStreaming]);

  // Permission loading state
  if (hasPermission === null) {
    return (
      <View style={[styles.container, { backgroundColor: isDarkMode ? '#222' : Constants.COLORS.BACKGROUND }]}>
        <View style={styles.centerContent}>
          <ActivityIndicator size="large" color={isDarkMode ? '#fff' : Constants.COLORS.PRIMARY} />
          <Text style={[styles.loadingText, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.SECONDARY_TEXT }]}>
            {t('loading')} Camera Permissions...
          </Text>
        </View>
      </View>
    );
  }

  // Permission denied state
  if (hasPermission === false) {
    return (
      <ScrollView 
        style={[styles.container, { backgroundColor: isDarkMode ? '#222' : Constants.COLORS.BACKGROUND }]}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.centerContent}>
          <Text style={[styles.error, { color: Constants.COLORS.DESTRUCTIVE }]}>
            {t('error')}: Camera permission denied
          </Text>
          <Pressable
            onPress={requestPermission}
            style={({ pressed }) => [
              styles.retryButton,
              { backgroundColor: isDarkMode ? '#555' : Constants.COLORS.PRIMARY, opacity: pressed ? 0.7 : 1 },
            ]}
            accessibilityLabel="Request camera permission"
          >
            <Text style={styles.retryButtonLabel}>Grant Permission</Text>
          </Pressable>
        </View>
      </ScrollView>
    );
  }

  // Device unavailable state
  if (device == null) {
    return (
      <ScrollView 
        style={[styles.container, { backgroundColor: isDarkMode ? '#222' : Constants.COLORS.BACKGROUND }]}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.centerContent}>
          <Text style={[styles.error, { color: Constants.COLORS.DESTRUCTIVE }]}>
            {t('error')}: Front camera not available
          </Text>
        </View>
      </ScrollView>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: isDarkMode ? '#222' : Constants.COLORS.BACKGROUND }]}>
      <ScrollView 
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
        bounces={true}
        alwaysBounceVertical={true}
      >
        {/* Camera View */}
        <View style={styles.cameraContainer}>
          <Camera
            ref={cameraRef}
            style={styles.camera}
            device={device}
            isActive={isStreaming && isScreenFocused && appState === 'active'}
            photo={true}
            video={false}
            enableHighQualityPhotos={false}
            enablePortraitEffectsMatteDelivery={false}
            enableDepthData={false}
          />
          
          {/* Connection Status Indicator */}
          <View style={[styles.statusIndicator, { backgroundColor: wsConnected ? '#4CAF50' : '#F44336' }]}>
            <Text style={styles.statusText}>
              {wsConnected ? '🟢 Connected' : '🔴 Disconnected'}
            </Text>
          </View>

          {/* Streaming Indicator */}
          {isStreaming && isScreenFocused && appState === 'active' && (
            <View style={[styles.streamingIndicator, { backgroundColor: '#FF9800' }]}>
              <Text style={styles.statusText}>
                🎥 Fast Mode
              </Text>
            </View>
          )}

          {/* Screen Focus Indicator */}
          {(!isScreenFocused || appState !== 'active') && (
            <View style={[styles.pausedIndicator, { backgroundColor: 'rgba(255,0,0,0.8)' }]}>
              <Text style={styles.statusText}>
                ⏸️ Paused
              </Text>
            </View>
          )}
          
          {/* Remaining Translations Indicator */}
          {!isAuthenticated && remainingTranslations !== null && isStreaming && (
            <View style={[styles.remainingIndicator, { backgroundColor: 'rgba(0,0,0,0.7)' }]}>
              <Text style={styles.statusText}>
                {remainingTranslations} left
              </Text>
            </View>
          )}
        </View>

        {/* Control Buttons Container */}
        <View style={styles.buttonsContainer}>
          {/* Main Control Button */}
          <Pressable
            onPress={isStreaming ? stopStreaming : startStreaming}
            disabled={!isScreenFocused || appState !== 'active'}
            style={({ pressed }) => [
              styles.mainButton,
              { 
                backgroundColor: isStreaming ? Constants.COLORS.DESTRUCTIVE : (isDarkMode ? '#0066CC' : Constants.COLORS.PRIMARY), 
                opacity: (!isScreenFocused || appState !== 'active') ? 0.5 : (pressed ? 0.8 : 1)
              },
            ]}
            accessibilityLabel={isStreaming ? 'Stop ASL recognition' : 'Start ASL recognition'}
          >
            <Text style={styles.mainButtonLabel}>
              {isStreaming ? (
                <>🛑 {t('stopCamera')}</>
              ) : (
                <>⚡ {t('startCamera')}</>
              )}
            </Text>
          </Pressable>

          {/* Launch ASLearn Button */}
          <Pressable
            onPress={openASLearnApp}
            style={({ pressed }) => [
              styles.launchButton,
              { 
                backgroundColor: isDarkMode ? '#2D5016' : '#4CAF50', 
                opacity: pressed ? 0.8 : 1,
                borderWidth: 2,
                borderColor: isDarkMode ? '#4CAF50' : '#45A049'
              },
            ]}
            accessibilityLabel="Open ASLearn App"
          >
            <Text style={styles.launchButtonLabel}>
              🚀 Open ASLearn App
            </Text>
          </Pressable>
        </View>

        {/* Error Display */}
        {error ? (
          <View style={styles.errorContainer}>
            <Text style={[styles.error, { color: Constants.COLORS.DESTRUCTIVE }]}>{error}</Text>
          </View>
        ) : null}

        {/* Translation Results with Performance Info */}
        {translatedText ? (
          <View style={[styles.resultContainer, { backgroundColor: isDarkMode ? '#333' : Constants.COLORS.CARD }]}>
            <Text style={[styles.resultLabel, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.TEXT }]}>
              {t('translated')}
            </Text>
            <Text style={[styles.translated, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.SECONDARY_TEXT }]}>
              {translatedText}
            </Text>
            <Text style={[styles.confidence, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.SECONDARY_TEXT }]}>
              Confidence: {(confidence * 100).toFixed(1)}%
              {processingTime > 0 }
            </Text>
            <View style={styles.buttonRow}>
              <Pressable
                onPress={handleDeleteTranslation}
                style={({ pressed }) => [
                  styles.deleteButton,
                  { backgroundColor: Constants.COLORS.DESTRUCTIVE, opacity: pressed ? 0.7 : 1 },
                ]}
                accessibilityLabel="Delete translation"
              >
                <Text style={styles.deleteButtonLabel}>{t('deleteTranslation')}</Text>
              </Pressable>
              
              <Pressable
                onPress={() => saveTranslation(translatedText)}
                style={({ pressed }) => [
                  styles.saveButton,
                  { backgroundColor: isDarkMode ? '#0066CC' : Constants.COLORS.PRIMARY, opacity: pressed ? 0.7 : 1 },
                ]}
                accessibilityLabel="Save translation"
              >
                <Text style={styles.saveButtonLabel}>💾 Save Translation</Text>
              </Pressable>
            </View>
          </View>
        ) : null}

        {/* Connection Status Information - Removed from UI, now only in logs */}
        {/* Status information is now logged to console for debugging */}

        {/* Tips Section */}
        <View style={[styles.tipsContainer, { backgroundColor: isDarkMode ? '#2A2A2A' : '#F8F9FA' }]}>
          <Text style={[styles.tipsTitle, { color: isDarkMode ? Constants.COLORS.CARD : Constants.COLORS.TEXT }]}>
            💡 Tips for Best Results
          </Text>
          <Text style={[styles.tipText, { color: isDarkMode ? '#B0B0B0' : Constants.COLORS.SECONDARY_TEXT }]}>
            • Ensure good lighting conditions
          </Text>
          <Text style={[styles.tipText, { color: isDarkMode ? '#B0B0B0' : Constants.COLORS.SECONDARY_TEXT }]}>
            • Keep your hand clearly visible in the camera frame
          </Text>
          <Text style={[styles.tipText, { color: isDarkMode ? '#B0B0B0' : Constants.COLORS.SECONDARY_TEXT }]}>
            • Make sure your Python server is running before starting
          </Text>
          <Text style={[styles.tipText, { color: isDarkMode ? '#B0B0B0' : Constants.COLORS.SECONDARY_TEXT }]}>
            • Hold gestures steady for better recognition
          </Text>
          <Text style={[styles.tipText, { color: isDarkMode ? '#B0B0B0' : Constants.COLORS.SECONDARY_TEXT }]}>
            • Use a plain background for improved accuracy
          </Text>
          <Text style={[styles.tipText, { color: isDarkMode ? '#B0B0B0' : Constants.COLORS.SECONDARY_TEXT }]}>
            • Camera automatically pauses when you leave this screen
          </Text>
          <Text style={[styles.tipText, { color: isDarkMode ? '#B0B0B0' : Constants.COLORS.SECONDARY_TEXT }]}>
            • Use the stop button to manually stop the camera
          </Text>
        </View>

        {/* Spacer for better scrolling experience */}
        <View style={styles.bottomSpacer} />
      </ScrollView>
      
      <Toast
        message={getSafeMessage(error)}
        visible={toastVisible}
        onHide={() => setToastVisible(false)}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: { 
    flex: 1 
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    padding: Constants.SPACING.SECTION,
    paddingBottom: Constants.SPACING.LARGE,
  },
  centerContent: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    minHeight: screenHeight * 0.7,
  },
  cameraContainer: {
    position: 'relative',
    width: '100%',
    height: Math.min(350, screenHeight * 0.4),
    marginBottom: Constants.SPACING.SECTION,
    borderRadius: 15,
    overflow: 'hidden',
  },
  camera: {
    width: '100%',
    height: '100%',
  },
  statusIndicator: {
    position: 'absolute',
    top: 10,
    right: 10,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  streamingIndicator: {
    position: 'absolute',
    top: 10,
    left: 10,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  pausedIndicator: {
    position: 'absolute',
    top: 50,
    left: 10,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  statusText: {
    color: 'white',
    fontSize: 12,
    fontWeight: 'bold',
  },
  remainingIndicator: {
    position: 'absolute',
    bottom: 10,
    right: 10,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  buttonsContainer: {
    width: '100%',
    gap: Constants.SPACING.MEDIUM,
    marginBottom: Constants.SPACING.SECTION,
  },
  mainButton: {
    paddingVertical: 18,
    paddingHorizontal: 35,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    elevation: 6,
  },
  mainButtonLabel: {
    fontSize: Constants.FONT_SIZES.SUBTITLE,
    fontWeight: 'bold',
    color: Constants.COLORS.CARD,
    textAlign: 'center',
    letterSpacing: 0.5,
  },
  launchButton: {
    paddingVertical: 16,
    paddingHorizontal: 30,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.25,
    shadowRadius: 5,
    elevation: 5,
  },
  launchButtonLabel: {
    fontSize: Constants.FONT_SIZES.BODY,
    fontWeight: 'bold',
    color: Constants.COLORS.CARD,
    textAlign: 'center',
    letterSpacing: 0.3,
  },
  statusButton: {
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 3,
    elevation: 3,
  },
  statusButtonLabel: {
    fontSize: Constants.FONT_SIZES.BODY,
    fontWeight: '600',
    color: Constants.COLORS.CARD,
    textAlign: 'center',
  },
  errorContainer: {
    marginBottom: Constants.SPACING.SECTION,
    padding: Constants.SPACING.MEDIUM,
    borderRadius: 8,
    backgroundColor: 'rgba(244, 67, 54, 0.1)',
    borderWidth: 1,
    borderColor: 'rgba(244, 67, 54, 0.2)',
  },
  error: {
    fontSize: Constants.FONT_SIZES.SECONDARY,
    textAlign: 'center',
    lineHeight: 20,
  },
  loadingText: {
    marginTop: Constants.SPACING.MEDIUM,
    fontSize: Constants.FONT_SIZES.BODY,
    textAlign: 'center',
  },
  resultContainer: {
    marginBottom: Constants.SPACING.SECTION,
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
  translated: {
    fontSize: Constants.FONT_SIZES.SUBTITLE,
    marginBottom: Constants.SPACING.MEDIUM,
    lineHeight: 24,
    fontWeight: 'bold',
  },
  confidence: {
    fontSize: Constants.FONT_SIZES.SECONDARY,
    marginBottom: Constants.SPACING.LARGE,
    fontStyle: 'italic',
  },
  buttonRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: Constants.SPACING.MEDIUM,
  },
  deleteButton: {
    flex: 1,
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
  },
  deleteButtonLabel: {
    fontSize: Constants.FONT_SIZES.SECONDARY,
    fontWeight: 'bold',
    color: Constants.COLORS.CARD,
    textAlign: 'center',
  },
  saveButton: {
    flex: 1,
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
  },
  saveButtonLabel: {
    fontSize: Constants.FONT_SIZES.SECONDARY,
    fontWeight: 'bold',
    color: Constants.COLORS.CARD,
    textAlign: 'center',
  },
  retryButton: {
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 10,
    marginTop: Constants.SPACING.MEDIUM,
  },
  retryButtonLabel: {
    fontSize: Constants.FONT_SIZES.BODY,
    fontWeight: 'bold',
    color: Constants.COLORS.CARD,
  },
  statusInfoContainer: {
    marginBottom: Constants.SPACING.SECTION,
    padding: Constants.SPACING.MEDIUM,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.1)',
  },
  statusInfoTitle: {
    fontSize: Constants.FONT_SIZES.BODY,
    fontWeight: 'bold',
    marginBottom: Constants.SPACING.SMALL,
  },
  statusInfoText: {
    fontSize: Constants.FONT_SIZES.SECONDARY,
    marginBottom: 4,
    lineHeight: 18,
  },
  tipsContainer: {
    marginBottom: Constants.SPACING.SECTION,
    padding: Constants.SPACING.MEDIUM,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.1)',
  },
  tipsTitle: {
    fontSize: Constants.FONT_SIZES.BODY,
    fontWeight: 'bold',
    marginBottom: Constants.SPACING.SMALL,
  },
  tipText: {
    fontSize: Constants.FONT_SIZES.SECONDARY,
    marginBottom: 6,
    lineHeight: 18,
  },
  bottomSpacer: {
    height: Constants.SPACING.LARGE,
  },
});

export default ASLTranslationScreen;