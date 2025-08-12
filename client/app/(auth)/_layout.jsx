// app/(auth)/_layout.jsx
import React, { useEffect, useState } from 'react';
import { Stack, useRouter } from 'expo-router';
import { ActivityIndicator, View, StyleSheet } from 'react-native';
import { useEnhancedSession } from '../../utils/EnhancedSessionContext';
import { useTheme } from '../../utils/ThemeContext';
import Constants from '../../utils/Constants';
import Toast from '../../components/Toast';
import GlobalErrorCapture from '../../utils/GlobalErrorCapture'; // ✅ ADD ERROR CAPTURE

export default function AuthLayout() {
  const { status, error, clearError, isAuthenticated } = useEnhancedSession();
  const { isDarkMode } = useTheme();
  const [toastVisible, setToastVisible] = useState(false);
  const [isMounted, setIsMounted] = useState(false);
  const router = useRouter();

  // ✅ INITIALIZE ERROR CAPTURE FOR AUTH SECTION
  useEffect(() => {
    console.log('[AuthLayout] Initializing error capture for auth section...');
    GlobalErrorCapture.init(); // Ensure error capture is active in auth section
    GlobalErrorCapture.setCurrentScreen('AuthLayout');
  }, []);

  // Track when component is mounted
  useEffect(() => {
    setIsMounted(true);
    return () => setIsMounted(false);
  }, []);

  useEffect(() => {
    if (error) {
      setToastVisible(true);
      
      // ✅ REPORT AUTH ERRORS TO MDB SERVER
      GlobalErrorCapture.reportError(new Error(`Auth Error: ${error}`), {
        screen: 'AuthLayout',
        errorType: 'auth_error',
        extra: { 
          authStatus: status,
          isAuthenticated,
          timestamp: new Date().toISOString()
        }
      });
    }
  }, [error, status, isAuthenticated]);

  // ✅ FIXED: Only navigate after component is mounted and with proper delay
  useEffect(() => {
    if (isAuthenticated && isMounted) {
      // Use longer delay to ensure Stack is fully rendered
      const timer = setTimeout(() => {
        try {
          console.log('[AuthLayout] Navigating to main app...');
          router.replace('/(drawer)/(tabs)');
        } catch (navError) {
          console.warn('[Auth Layout] Navigation failed:', navError);
          
          // ✅ REPORT NAVIGATION ERRORS
          GlobalErrorCapture.reportError(navError, {
            screen: 'AuthLayout',
            errorType: 'navigation_error',
            extra: { 
              attemptType: 'initial_navigation',
              isAuthenticated,
              timestamp: new Date().toISOString()
            }
          });
          
          // Retry navigation after a longer delay
          setTimeout(() => {
            try {
              console.log('[AuthLayout] Retrying navigation...');
              router.replace('/(drawer)/(tabs)');
            } catch (retryError) {
              console.error('[Auth Layout] Navigation retry failed:', retryError);
              
              // ✅ REPORT RETRY NAVIGATION ERRORS
              GlobalErrorCapture.reportError(retryError, {
                screen: 'AuthLayout',
                errorType: 'navigation_retry_error',
                extra: { 
                  attemptType: 'retry_navigation',
                  isAuthenticated,
                  timestamp: new Date().toISOString()
                }
              });
            }
          }, 500);
        }
      }, 300); // Increased delay

      return () => clearTimeout(timer);
    }
  }, [isAuthenticated, isMounted, router]);

  // ✅ UPDATE SCREEN CONTEXT WHEN STATUS CHANGES
  useEffect(() => {
    GlobalErrorCapture.setCurrentScreen(`AuthLayout-${status}`);
  }, [status]);

  // Show loading for auth operations
  if (status === 'loading') {
    return (
      <View style={[styles.loadingContainer, { 
        backgroundColor: isDarkMode ? '#222' : Constants.COLORS.BACKGROUND 
      }]}>
        <ActivityIndicator 
          size="large" 
          color={isDarkMode ? '#fff' : Constants.COLORS.PRIMARY} 
        />
      </View>
    );
  }

  return (
    <View style={{ flex: 1 }}>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="login" />
        <Stack.Screen name="register" />
      </Stack>
      
      {error && (
        <Toast
          message={error}
          visible={toastVisible}
          onHide={() => {
            setToastVisible(false);
            clearError();
          }}
          isDarkMode={isDarkMode}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});