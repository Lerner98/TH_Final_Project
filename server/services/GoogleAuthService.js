// ===== GOOGLE AUTHENTICATION SERVICE =====
// services/GoogleAuthService.js
const { OAuth2Client } = require('google-auth-library');

class GoogleAuthService {
  constructor() {
    this.client = null;
    this.initialize();
  }

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

  isConfigured() {
    return this.client !== null;
  }
}

module.exports = new GoogleAuthService();