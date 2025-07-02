// app/(auth)/_layout.jsx
import React, { useEffect, useState } from 'react';
import { Stack, useRouter } from 'expo-router';
import { ActivityIndicator, View, StyleSheet } from 'react-native';
import { useEnhancedSession } from '../../utils/EnhancedSessionContext';
import { useTheme } from '../../utils/ThemeContext';
import Constants from '../../utils/Constants';
import Toast from '../../components/Toast';

export default function AuthLayout() {
  const { status, error, clearError, isAuthenticated } = useEnhancedSession();
  const { isDarkMode } = useTheme();
  const [toastVisible, setToastVisible] = useState(false);
  const [isMounted, setIsMounted] = useState(false);
  const router = useRouter();

  // Track when component is mounted
  useEffect(() => {
    setIsMounted(true);
    return () => setIsMounted(false);
  }, []);

  useEffect(() => {
    if (error) {
      setToastVisible(true);
    }
  }, [error]);

  // ✅ FIXED: Only navigate after component is mounted and with proper delay
  useEffect(() => {
    if (isAuthenticated && isMounted) {
      // Use longer delay to ensure Stack is fully rendered
      const timer = setTimeout(() => {
        try {
          router.replace('/(drawer)/(tabs)');
        } catch (navError) {
          console.warn('[Auth Layout] Navigation failed:', navError);
          // Retry navigation after a longer delay
          setTimeout(() => {
            try {
              router.replace('/(drawer)/(tabs)');
            } catch (retryError) {
              console.error('[Auth Layout] Navigation retry failed:', retryError);
            }
          }, 500);
        }
      }, 300); // Increased delay

      return () => clearTimeout(timer);
    }
  }, [isAuthenticated, isMounted, router]);

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