import React, { createContext, useContext, useReducer, useEffect, useMemo, useRef, useCallback } from 'react';
import PropTypes from 'prop-types';
import AsyncStorageUtils from './AsyncStorage';
import ApiService from '../services/ApiService';
import TokenManager from './TokenManager';
import GuestManager from './GuestManager';
import Helpers from './Helpers';
import { router } from 'expo-router';
import Constants from './Constants';
import * as Localization from 'expo-localization';
import GlobalErrorCapture from './GlobalErrorCapture';

// Auth state machine
const AUTH_STATES = {
  LOADING: 'loading',
  AUTHENTICATED: 'authenticated',
  GUEST: 'guest',
  ERROR: 'error',
};

// Action types
const AUTH_ACTIONS = {
  SET_LOADING: 'SET_LOADING',
  SET_AUTHENTICATED: 'SET_AUTHENTICATED',
  SET_GUEST: 'SET_GUEST',
  SET_ERROR: 'SET_ERROR',
  UPDATE_PREFERENCES: 'UPDATE_PREFERENCES',
  CLEAR_ERROR: 'CLEAR_ERROR',
};

// Initial state
const initialState = {
  status: AUTH_STATES.LOADING,
  user: null,
  preferences: null,
  error: null,
  isLoading: false,
  isAuthLoading: false,
};

// Auth reducer
const authReducer = (state, action) => {
  switch (action.type) {
    case AUTH_ACTIONS.SET_LOADING:
      return {
        ...state,
        status: AUTH_STATES.LOADING,
        isLoading: true,
        isAuthLoading: action.payload?.isAuth || false,
        error: null,
      };

    case AUTH_ACTIONS.SET_AUTHENTICATED:
      return {
        ...state,
        status: AUTH_STATES.AUTHENTICATED,
        user: action.payload.user,
        preferences: action.payload.preferences,
        isLoading: false,
        isAuthLoading: false,
        error: null,
      };

    case AUTH_ACTIONS.SET_GUEST:
      return {
        ...state,
        status: AUTH_STATES.GUEST,
        user: null,
        preferences: action.payload.preferences,
        isLoading: false,
        isAuthLoading: false,
        error: null,
      };

    case AUTH_ACTIONS.SET_ERROR:
      return {
        ...state,
        status: AUTH_STATES.ERROR,
        error: action.payload.error,
        isLoading: false,
        isAuthLoading: false,
      };

    case AUTH_ACTIONS.UPDATE_PREFERENCES:
      return {
        ...state,
        preferences: action.payload.preferences,
      };

    case AUTH_ACTIONS.CLEAR_ERROR:
      return {
        ...state,
        error: null,
      };

    default:
      return state;
  }
};

// Default preferences helper
const getDefaultPreferences = () => {
  const deviceLocale = Localization.locale.split('-')[0];
  return {
    defaultFromLang: deviceLocale || Constants.DEFAULT_PREFERENCES.DEFAULT_FROM_LANG || 'en',
    defaultToLang: deviceLocale === 'he' ? 'en' : Constants.DEFAULT_PREFERENCES.DEFAULT_TO_LANG || 'he',
  };
};

// Create context
const EnhancedSessionContext = createContext({
  ...initialState,
  signIn: async () => {},
  signOut: async () => {},
  register: async () => {},
  setPreferences: async () => {},
  clearError: () => {},
  isAuthenticated: false,
  isGuest: false,
  guestManager: GuestManager,
});

// Enhanced Session Provider
export const EnhancedSessionProvider = ({ children }) => {
  const [state, dispatch] = useReducer(authReducer, initialState);
  
  // Add refs to prevent double execution and track navigation
  const isSigningOutRef = useRef(false);
  const isSigningInRef = useRef(false);
  const navigationTimeoutRef = useRef(null);

  // Initialize authentication on app start
  useEffect(() => {
    initializeAuth();
  }, []);

  // Cleanup timeouts on unmount
  useEffect(() => {
    return () => {
      if (navigationTimeoutRef.current) {
        clearTimeout(navigationTimeoutRef.current);
      }
    };
  }, []);

  // Safe navigation with better timing and checks
  const safeNavigate = useCallback((path, retryCount = 0) => {
    const maxRetries = 3;
    const baseDelay = 200; // Increased base delay
    const retryDelay = baseDelay * (retryCount + 1);

    console.log(`[Auth] Attempting navigation to ${path} (attempt ${retryCount + 1})`);
    
    if (navigationTimeoutRef.current) {
      clearTimeout(navigationTimeoutRef.current);
    }

    navigationTimeoutRef.current = setTimeout(() => {
      try {
        router.replace(path);
        console.log(`[Auth] Navigation to ${path} completed successfully`);
      } catch (navError) {
        console.error(`[Auth] Navigation attempt ${retryCount + 1} failed:`, navError);
        
        if (retryCount < maxRetries) {
          console.log(`[Auth] Retrying navigation in ${retryDelay * 2}ms...`);
          safeNavigate(path, retryCount + 1);
        } else {
          console.error(`[Auth] Navigation failed after ${maxRetries + 1} attempts`);
        }
      }
    }, retryDelay);
  }, []);

  const initializeAuth = useCallback(async () => {
    try {
      console.log('[Auth] Initializing authentication...');
      dispatch({ type: AUTH_ACTIONS.SET_LOADING });

      // Load stored data
      const [userData, token, preferences] = await Promise.all([
        AsyncStorageUtils.getItem('user'),
        AsyncStorageUtils.getItem('signed_session_id'),
        AsyncStorageUtils.getItem('preferences'),
      ]);

      console.log('[Auth] Stored data check:', {
        hasUser: !!userData,
        hasToken: !!token,
        hasPreferences: !!preferences,
      });

      // Try to restore authenticated session
      if (userData?.id && token) {
        console.log('[Auth] Attempting to restore session...');
        
        // Validate and potentially refresh token
        const tokenResult = await TokenManager.validateAndRefreshToken(token);
        
        if (tokenResult.valid) {
          // Validate session with server
          const response = await ApiService.get('/validate-session', tokenResult.token);
          
          if (response.success) {
            console.log('[Auth] Session restored successfully');
              GlobalErrorCapture.setUserId(userData.id);
            dispatch({
              type: AUTH_ACTIONS.SET_AUTHENTICATED,
              payload: {
                user: { ...userData, signed_session_id: tokenResult.token },
                preferences: preferences || getDefaultPreferences(),
              },
            });
            return;
          } else {
            console.log('[Auth] Session validation failed:', response.error);
          }
        } else {
          console.log('[Auth] Token validation failed:', tokenResult.error);
        }
        
        // Clear invalid session data
        await TokenManager.clearTokens();
      }

      // Default to guest mode
      console.log('[Auth] Defaulting to guest mode');
      GlobalErrorCapture.setUserId('guest');
      dispatch({
        type: AUTH_ACTIONS.SET_GUEST,
        payload: {
          preferences: preferences || getDefaultPreferences(),
        },
      });

    } catch (error) {
      console.error('[Auth] Initialization error:', error);
      dispatch({
        type: AUTH_ACTIONS.SET_ERROR,
        payload: { error: Helpers.handleError(error) },
      });
    }
  }, []);

  const signIn = useCallback(async (email, password, options = {}) => {
    // Prevent double execution
    if (isSigningInRef.current) {
      console.log('[Auth] Sign in already in progress, ignoring duplicate call');
      return;
    }

    try {
        isSigningInRef.current = true;
        console.log('[Auth] Signing in user:', email);
        dispatch({ type: AUTH_ACTIONS.SET_LOADING, payload: { isAuth: true } });

        let response;
        
        // Handle Google authentication
        if (options.isGoogleAuth && options.token && options.user) {
        console.log('[Auth] Processing Google authentication');
        
        // For Google auth, we already have the token and user data
        response = {
            success: true,
            data: {
            token: options.token,
            user: options.user,
            }
        };
        } else {
        // Regular email/password authentication
        response = await ApiService.post('/login', { email, password });
        }

        if (!response.success || !response.data?.token || !response.data?.user) {
        throw new Error(response?.error || 'Login failed');
        }

        const { user, token } = response.data;

        // Store session data
        await Promise.all([
        AsyncStorageUtils.setItem('user', user),
        AsyncStorageUtils.setItem('signed_session_id', token),
        ]);

        // Set user preferences
        const userPreferences = {
        defaultFromLang: user.defaultFromLang || getDefaultPreferences().defaultFromLang,
        defaultToLang: user.defaultToLang || getDefaultPreferences().defaultToLang,
        };
        
        await AsyncStorageUtils.setItem('preferences', userPreferences);

        console.log('[Auth] Sign in successful');
        GlobalErrorCapture.setUserId(user.id);
        dispatch({
        type: AUTH_ACTIONS.SET_AUTHENTICATED,
        payload: {
            user: { ...user, signed_session_id: token },
            preferences: userPreferences,
        },
        });

        // DON'T navigate here - let the layout handle it based on isAuthenticated state

    } catch (error) {
        console.error('[Auth] Sign in failed:', error);
        const errorMessage = Helpers.handleError(error);
        dispatch({
        type: AUTH_ACTIONS.SET_ERROR,
        payload: { error: errorMessage },
        });
        throw new Error(errorMessage);
    } finally {
      isSigningInRef.current = false;
    }
  }, []);

  const signOut = useCallback(async () => {
    // Prevent double execution
    if (isSigningOutRef.current) {
      console.log('[Auth] Sign out already in progress, ignoring duplicate call');
      return;
    }

    try {
      isSigningOutRef.current = true;
      console.log('[Auth] Signing out user');
      dispatch({ type: AUTH_ACTIONS.SET_LOADING, payload: { isAuth: true } });

      const token = await AsyncStorageUtils.getItem('signed_session_id');
      
      // Attempt server logout
      if (token) {
        try {
          await ApiService.post('/logout', {}, token);
          console.log('[Auth] Server logout successful');
        } catch (logoutError) {
          console.warn('[Auth] Server logout failed, continuing with local cleanup');
        }
      }

      // Clear all stored data
      await Promise.all([
        TokenManager.clearTokens(),
        AsyncStorageUtils.removeItem('preferences'),
      ]);

      console.log('[Auth] Sign out successful');
      GlobalErrorCapture.setUserId('guest');
      dispatch({
        type: AUTH_ACTIONS.SET_GUEST,
        payload: {
          preferences: getDefaultPreferences(),
        },
      });

      // DON'T navigate here - let the layout handle it based on isAuthenticated state

    } catch (error) {
      console.error('[Auth] Sign out error:', error);
      // Force local cleanup even if server logout fails
      await TokenManager.clearTokens();
      dispatch({
        type: AUTH_ACTIONS.SET_GUEST,
        payload: {
          preferences: getDefaultPreferences(),
        },
      });
    } finally {
      setTimeout(() => {
        isSigningOutRef.current = false;
      }, 1000);
    }
  }, []);

  const register = useCallback(async (email, password) => {
    try {
      console.log('[Auth] Registering user:', email);
      dispatch({ type: AUTH_ACTIONS.SET_LOADING, payload: { isAuth: true } });

      const response = await ApiService.post('/register', { email, password });
      
      if (!response.success) {
        throw new Error(response.error || 'Registration failed');
      }

      // ✅ FIXED: DO NOT set user as authenticated after registration
      // Just set default preferences and stay in guest mode
      const defaultPrefs = getDefaultPreferences();
      await AsyncStorageUtils.setItem('preferences', defaultPrefs);
      
      // Stay in guest mode - user needs to log in separately
      dispatch({
        type: AUTH_ACTIONS.SET_GUEST,
        payload: { preferences: defaultPrefs },
      });

      console.log('[Auth] Registration successful - user remains in guest mode');

    } catch (error) {
      console.error('[Auth] Registration failed:', error);
      const errorMessage = Helpers.handleError(error);
      dispatch({
        type: AUTH_ACTIONS.SET_ERROR,
        payload: { error: errorMessage },
      });
      throw new Error(errorMessage);
    }
  }, []);

  const setPreferences = useCallback(async (prefs) => {
    try {
      console.log('[Auth] Updating preferences:', prefs);
      // ✅ REMOVED: dispatch({ type: AUTH_ACTIONS.SET_LOADING, payload: { isAuth: true } });

      // If user is authenticated, save to server
      if (state.status === AUTH_STATES.AUTHENTICATED && state.user?.signed_session_id) {
        const response = await ApiService.post('/preferences', prefs, state.user.signed_session_id);
        if (!response.success) {
          throw new Error(response.error || 'Failed to update preferences');
        }
      }

      // Always save locally
      await AsyncStorageUtils.setItem('preferences', prefs);
      
      dispatch({
        type: AUTH_ACTIONS.UPDATE_PREFERENCES,
        payload: { preferences: prefs },
      });

      console.log('[Auth] Preferences updated successfully');

    } catch (error) {
      console.error('[Auth] Set preferences failed:', error);
      const errorMessage = Helpers.handleError(error);
      dispatch({
        type: AUTH_ACTIONS.SET_ERROR,
        payload: { error: errorMessage },
      });
      throw new Error(errorMessage);
    }
  }, [state.status, state.user?.signed_session_id]);

  const clearError = useCallback(() => {
    dispatch({ type: AUTH_ACTIONS.CLEAR_ERROR });
  }, []);

  // Computed values
  const contextValue = useMemo(() => ({
    // State
    ...state,
    isAuthenticated: state.status === AUTH_STATES.AUTHENTICATED,
    isGuest: state.status === AUTH_STATES.GUEST,
    session: state.status === AUTH_STATES.AUTHENTICATED ? state.user : null,
    
    // Actions - now stable references
    signIn,
    signOut,
    register,
    setPreferences,
    clearError,
    
    // Utilities
    guestManager: GuestManager,
  }), [state, signIn, signOut, register, setPreferences, clearError]);

  return (
    <EnhancedSessionContext.Provider value={contextValue}>
      {children}
    </EnhancedSessionContext.Provider>
  );
};

EnhancedSessionProvider.propTypes = {
  children: PropTypes.node.isRequired,
};

// Custom hook
export const useEnhancedSession = () => {
  const context = useContext(EnhancedSessionContext);
  if (!context) {
    throw new Error('useEnhancedSession must be used within an EnhancedSessionProvider');
  }
  return context;
};

export default EnhancedSessionContext;