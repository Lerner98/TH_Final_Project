// utils/GlobalErrorCapture.js
import { ErrorUtils } from 'react-native';
import ErrorReportingService from '../services/ErrorReportingService';

class GlobalErrorCapture {
  static isInitialized = false;
  static currentScreen = 'Unknown';
  static userId = null;

  static init() {
    if (this.isInitialized) {
      console.log('[GlobalErrorCapture] Already initialized, skipping');
      return;
    }

    console.log('[GlobalErrorCapture] Initializing...');
    this.isInitialized = true;

    try {
      // Check if ErrorUtils is available (React Native only, not Expo managed workflow)
      if (ErrorUtils && typeof ErrorUtils.setGlobalHandler === 'function') {
        ErrorUtils.setGlobalHandler((error, isFatal) => {
          console.log('[GlobalErrorCapture] Caught global error:', error.message);
          
          ErrorReportingService.reportError(error, {
            errorType: 'global_js_error',
            isFatal,
            screen: this.currentScreen,
            userId: this.userId
          });
        });
      } else {
        console.log('[GlobalErrorCapture] ErrorUtils not available (Expo managed workflow)');
      }

      // Capture unhandled promise rejections (this works in Expo)
      const originalHandler = global.onunhandledrejection;
      global.onunhandledrejection = (event) => {
        console.log('[GlobalErrorCapture] Caught unhandled promise rejection:', event.reason);
        
        const error = event.reason instanceof Error ? event.reason : new Error(String(event.reason));
        
        ErrorReportingService.reportError(error, {
          errorType: 'unhandled_promise',
          screen: this.currentScreen,
          userId: this.userId
        });
        
        if (originalHandler) originalHandler(event);
      };

      // Override console.error (this works in Expo)
      const originalConsoleError = console.error;
      console.error = (...args) => {
        if (args.length > 0 && (args[0] instanceof Error || typeof args[0] === 'string')) {
          const message = args[0] instanceof Error ? args[0].message : String(args[0]);
          
          // Skip React Native warnings and our own error reporting logs
          if (!message.includes('Warning:') && 
              !message.includes('VirtualizedList') &&
              !message.includes('[ErrorReporting]') &&
              !message.includes('[GlobalErrorCapture]')) {
            
            ErrorReportingService.reportError(new Error(message), {
              errorType: 'console_error',
              screen: this.currentScreen,
              userId: this.userId,
              extra: { consoleArgs: args.slice(1) }
            });
          }
        }
        
        originalConsoleError.apply(console, args);
      };

      console.log('[GlobalErrorCapture] Initialized successfully (Expo-compatible mode)');
    } catch (initError) {
      console.error('[GlobalErrorCapture] Initialization failed:', initError);
    }
  }

  static setCurrentScreen(screenName) {
    this.currentScreen = screenName || 'Unknown';
  }

  static setUserId(userId) {
    this.userId = userId;
  }

  static reportError(error, context = {}) {
    ErrorReportingService.reportError(error, {
      ...context,
      screen: context.screen || this.currentScreen,
      userId: context.userId || this.userId
    });
  }
}

export default GlobalErrorCapture;