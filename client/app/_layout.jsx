// ===== UPDATED _layout.jsx (Root Layout) =====
// app/_layout.jsx
import React, { useState, useEffect } from 'react';
import { Stack } from 'expo-router';
import { PaperProvider, DefaultTheme, DarkTheme } from 'react-native-paper';
import { TranslationProvider, useTranslation } from '../utils/TranslationContext';
import { EnhancedSessionProvider, useEnhancedSession } from '../utils/EnhancedSessionContext';
import { ThemeProvider, useTheme } from '../utils/ThemeContext';
import Toast from '../components/Toast';
import ErrorBoundaryWrapper from '../components/ErrorBoundary';
import * as SplashScreen from 'expo-splash-screen';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import Constants from '../utils/Constants';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';

SplashScreen.preventAutoHideAsync();

const RootLayoutInner = () => {
  const { isDarkMode = false } = useTheme();
  const { status, error, clearError, isLoading } = useEnhancedSession();
  const { error: translationError } = useTranslation();
  const [appIsReady, setAppIsReady] = useState(false);
  const [toastVisible, setToastVisible] = useState(false);
  

  useEffect(() => {
    const initializeApp = async () => {
      try {
        // App-specific initialization can go here
        console.log('[App] Initializing application...');
      } catch (err) {
        console.error('[App] Initialization error:', err);
      } finally {
        setAppIsReady(true);
      }
    };
    initializeApp();
  }, []);

  // Hide splash screen when ready
  useEffect(() => {
    if (appIsReady && status !== 'loading') {
      SplashScreen.hideAsync();
    }
  }, [appIsReady, status]);

  // Show toast for errors
  useEffect(() => {
    if (error || translationError) {
      setToastVisible(true);
    }
  }, [error, translationError]);

  const theme = isDarkMode ? DarkTheme : DefaultTheme;

  // Show loading screen while app initializes or auth loads
  if (!appIsReady || status === 'loading') {
    return (
      <SafeAreaView style={[styles.loadingContainer, { 
        backgroundColor: isDarkMode ? '#222' : Constants.COLORS.BACKGROUND 
      }]}>
        <ActivityIndicator 
          size="large" 
          color={isDarkMode ? '#fff' : Constants.COLORS.PRIMARY} 
        />
      </SafeAreaView>
    );
  }

  return (
    <PaperProvider theme={theme}>
      <SafeAreaView style={{ flex: 1 }} edges={['top', 'left', 'right']}>
        <StatusBar
          backgroundColor={isDarkMode ? '#000' : Constants.COLORS.BACKGROUND}
          style={isDarkMode ? 'light' : 'dark'}
        />
        <ErrorBoundaryWrapper>
          <Stack screenOptions={{ headerShown: false }}>
            <Stack.Screen name="welcome" />
            <Stack.Screen name="(auth)" />
            <Stack.Screen name="(drawer)" />
          </Stack>
          
          {(error || translationError) && (
            <Toast
              message={String(error || translationError || 'Unknown error')}
              visible={toastVisible}
              onHide={() => {
                setToastVisible(false);
                clearError();
              }}
              isDarkMode={isDarkMode}
            />
          )}
        </ErrorBoundaryWrapper>
      </SafeAreaView>
    </PaperProvider>
  );
};

export default function RootLayout() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <ThemeProvider>
          <EnhancedSessionProvider>
            <TranslationProvider>
              <RootLayoutInner />
            </TranslationProvider>
          </EnhancedSessionProvider>
        </ThemeProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});