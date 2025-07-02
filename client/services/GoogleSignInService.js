// services/GoogleSignInService.js
import { GoogleSignin, statusCodes } from '@react-native-google-signin/google-signin';
import ApiService from './ApiService';

class GoogleSignInService {
  constructor() {
    this.isConfigured = false;
    this.initializeGoogleSignIn();
  }

  /**
   * Initialize Google Sign-In with your configuration
   */
  async initializeGoogleSignIn() {
    try {
      await GoogleSignin.configure({
        webClientId: '323209414339-nsunbrkh7m0894dqj8i4c68gmfelci9b.apps.googleusercontent.com',
        scopes: ['profile', 'email'],
        offlineAccess: false,
      });
      
      this.isConfigured = true;
      console.log('Google Sign-In configured successfully');
    } catch (error) {
      console.error('Google Sign-In configuration error:', error);
      this.isConfigured = false;
    }
  }

  /**
   * Check if user is already signed in (with error handling)
   */
  async isSignedIn() {
    try {
      // Handle potential API compatibility issues
      if (typeof GoogleSignin.isSignedIn === 'function') {
        return await GoogleSignin.isSignedIn();
      } else {
        console.warn('GoogleSignin.isSignedIn not available, assuming not signed in');
        return false;
      }
    } catch (error) {
      console.error('Error checking Google sign-in status:', error);
      return false;
    }
  }

  /**
   * Sign in with Google - ALWAYS force account selection
   */
  async signIn() {
    try {
      console.log('🔵 Starting Google Sign-In process...');
      
      if (!this.isConfigured) {
        console.log('🔵 Google Sign-In not configured, initializing...');
        await this.initializeGoogleSignIn();
      }

      console.log('🔵 Checking Google Play Services...');
      await GoogleSignin.hasPlayServices();
      
      // ALWAYS force account selection by signing out first
      console.log('🔵 Forcing account selection by clearing any existing session...');
      try {
        // Try multiple methods to ensure clean slate
        if (typeof GoogleSignin.revokeAccess === 'function') {
          await GoogleSignin.revokeAccess();
          console.log('🔵 Access revoked successfully');
        }
        if (typeof GoogleSignin.signOut === 'function') {
          await GoogleSignin.signOut();
          console.log('🔵 Signed out successfully');
        }
      } catch (cleanupError) {
        console.log('🟡 Cleanup failed, but continuing with fresh sign-in:', cleanupError.message);
      }
      
      console.log('🔵 Initiating Google Sign-In with forced account selection...');
      const signInResult = await GoogleSignin.signIn();
      
      console.log('🟢 Google Sign-In successful!');
      console.log('🔵 Sign-In Result:', JSON.stringify(signInResult, null, 2));
      
      // Handle the response structure correctly
      let userInfo;
      if (signInResult.type === 'success' && signInResult.data) {
        // New Google Sign-In library structure
        userInfo = signInResult.data;
        console.log('🔵 Using new structure - userInfo:', JSON.stringify(userInfo, null, 2));
      } else if (signInResult.user) {
        // Old structure or different response format
        userInfo = signInResult;
        console.log('🔵 Using old structure - userInfo:', JSON.stringify(userInfo, null, 2));
      } else {
        throw new Error('Unexpected Google Sign-In response structure');
      }
      
      // Extract user data safely
      const userData = userInfo.user || userInfo.data?.user;
      if (!userData || !userData.email) {
        console.log('🔴 User data structure:', JSON.stringify(userInfo, null, 2));
        throw new Error('Invalid user data structure from Google Sign-In');
      }
      
      console.log('🔵 Extracted User Data:', JSON.stringify(userData, null, 2));
      console.log('🔵 User Email:', userData.email);
      console.log('🔵 User Name:', userData.name);
      console.log('🔵 User ID:', userData.id);
      
      // Get the access token
      console.log('🔵 Getting Google access tokens...');
      const tokens = await GoogleSignin.getTokens();
      console.log('🔵 Tokens received:', {
        accessToken: tokens.accessToken ? 'Present' : 'Missing',
        idToken: tokens.idToken ? 'Present' : 'Missing'
      });
      
      // Send to backend
      console.log('🔵 Sending user data to backend...');
      const backendResponse = await ApiService.post('/auth/google', {
        accessToken: tokens.accessToken,
        userInfo: userData, // Use the correctly extracted userData
      });
      
      console.log('🔵 Backend response:', JSON.stringify(backendResponse, null, 2));
      
      if (!backendResponse.success) {
        console.log('🔴 Backend authentication failed:', backendResponse.error);
        throw new Error(backendResponse.error || 'Google sign-in failed');
      }
      
      console.log('🟢 Google Sign-In completed successfully!');
      console.log('🔵 Final user object:', JSON.stringify(backendResponse.data.user, null, 2));
      
      return {
        success: true,
        user: backendResponse.data.user,
        token: backendResponse.data.token,
        authMethod: 'google',
      };
      
    } catch (error) {
      console.log('🔴 Google Sign-In Error:', error);
      console.log('🔴 Error details:', JSON.stringify(error, null, 2));
      
      if (error.code === statusCodes.SIGN_IN_CANCELLED) {
        console.log('🟡 User cancelled Google sign-in');
        return {
          success: false,
          cancelled: true,
          error: 'User cancelled the sign-in',
          authMethod: 'google',
        };
      } else if (error.code === statusCodes.IN_PROGRESS) {
        console.log('🟡 Google sign-in already in progress');
        return {
          success: false,
          error: 'Sign-in is in progress',
          authMethod: 'google',
        };
      } else if (error.code === statusCodes.PLAY_SERVICES_NOT_AVAILABLE) {
        console.log('🔴 Google Play Services not available');
        return {
          success: false,
          error: 'Google Play Services not available',
          authMethod: 'google',
        };
      } else {
        console.log('🔴 Unknown Google sign-in error');
        return {
          success: false,
          error: error.message || 'Google sign-in failed',
          authMethod: 'google',
        };
      }
    }
  }

  /**
   * Sign out from Google (with error handling)
   */
  async signOut() {
    try {
      if (typeof GoogleSignin.signOut === 'function') {
        await GoogleSignin.signOut();
        console.log('Google Sign-Out successful');
      } else {
        console.warn('GoogleSignin.signOut not available');
      }
      return { success: true };
    } catch (error) {
      console.error('Google Sign-Out Error:', error);
      return {
        success: false,
        error: error.message || 'Google sign-out failed',
      };
    }
  }

  /**
   * Revoke access (with error handling)
   */
  async revokeAccess() {
    try {
      if (typeof GoogleSignin.revokeAccess === 'function') {
        await GoogleSignin.revokeAccess();
        console.log('Google access revoked');
      } else {
        console.warn('GoogleSignin.revokeAccess not available');
      }
      return { success: true };
    } catch (error) {
      console.error('Google revoke access error:', error);
      return {
        success: false,
        error: error.message || 'Failed to revoke Google access',
      };
    }
  }

  /**
   * Get current user info (with error handling)
   */
  async getCurrentUser() {
    try {
      if (typeof GoogleSignin.signInSilently === 'function') {
        const userInfo = await GoogleSignin.signInSilently();
        return userInfo;
      } else {
        console.warn('GoogleSignin.signInSilently not available');
        return null;
      }
    } catch (error) {
      console.error('Error getting current Google user:', error);
      return null;
    }
  }

  /**
   * Clear cached credentials and force account selection
   */
  async clearCachedCredentials() {
    try {
      const isSignedIn = await this.isSignedIn();
      if (isSignedIn) {
        await this.revokeAccess();
      }
      return { success: true };
    } catch (error) {
      console.error('Error clearing cached credentials:', error);
      return {
        success: false,
        error: error.message || 'Failed to clear credentials',
      };
    }
  }
}

export default new GoogleSignInService();