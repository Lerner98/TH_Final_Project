/**
 * GoogleAuthService.js - Google OAuth Authentication Service
 * Handles Google Sign-In token verification using Google Auth Library.
 * Verifies ID tokens, extracts user information, and provides token
 * revocation functionality for secure Google OAuth integration.
 */

const { OAuth2Client } = require('google-auth-library');

class GoogleAuthService {
  constructor() {
    this.client = null;
    this.initialize();
  }
  // Initializes Google OAuth2 client with environment configuration
  initialize() {
    if (!process.env.GOOGLE_CLIENT_ID) {
      console.warn('Google Client ID not configured - Google Sign-In will be disabled');
      return;
    }

    try {
      this.client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);
      console.log('Google Auth service initialized');
    } catch (error) {
      console.error('Failed to initialize Google Auth service:', error);
    }
  }

  // Verifies Google ID token and extracts user information
  async verifyIdToken(token) {
    if (!this.client) {
      throw new Error('Google Auth service not configured');
    }

    try {
      const ticket = await this.client.verifyIdToken({
        idToken: token,
        audience: process.env.GOOGLE_CLIENT_ID,
      });

      const payload = ticket.getPayload();
      
      // Validate required fields
      if (!payload.sub || !payload.email) {
        throw new Error('Invalid Google token payload');
      }

      return {
        googleId: payload.sub,
        email: payload.email,
        name: payload.name || payload.email.split('@')[0],
        picture: payload.picture || null,
        emailVerified: payload.email_verified || false,
        locale: payload.locale || 'en',
      };
    } catch (error) {
      console.error('Google token verification failed:', error);
      throw new Error('Invalid Google token');
    }
  }

  // Revokes Google access token for logout functionality
  async revokeToken(token) {
    if (!this.client) {
      throw new Error('Google Auth service not configured');
    }

    try {
      await this.client.revokeToken(token);
      console.log('Google token revoked successfully');
    } catch (error) {
      console.error('Failed to revoke Google token:', error);
      throw error;
    }
  }

  // Checks if Google Auth service is properly configured
  isConfigured() {
    return this.client !== null;
  }
}

module.exports = new GoogleAuthService();