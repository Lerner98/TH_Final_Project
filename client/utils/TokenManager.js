import AsyncStorageUtils from './AsyncStorage';
import ApiService from '../services/ApiService';

class TokenManager {
  static TOKEN_REFRESH_THRESHOLD = 5 * 60 * 1000; // 5 minutes in milliseconds

  /**
   * Validate token and refresh if needed
   * @param {string} token - JWT token to validate
   * @returns {Promise<{valid: boolean, token?: string, error?: string}>}
   */
  static async validateAndRefreshToken(token) {
    try {
      if (!token) {
        return { valid: false, error: 'No token provided' };
      }

      // Basic JWT structure check
      const parts = token.split('.');
      if (parts.length !== 3) {
        return { valid: false, error: 'Invalid token format' };
      }

      // Decode payload to check expiration
      try {
        const payload = JSON.parse(atob(parts[1]));
        const expiresAt = payload.exp * 1000;
        const timeUntilExpiry = expiresAt - Date.now();

        if (timeUntilExpiry <= 0) {
          return { valid: false, error: 'Token expired' };
        }

        // If token expires soon, try to refresh
        if (timeUntilExpiry < this.TOKEN_REFRESH_THRESHOLD) {
          console.log('Token expires soon, attempting refresh...');
          return await this.refreshToken(token);
        }

        return { valid: true, token };
      } catch (decodeError) {
        return { valid: false, error: 'Invalid token payload' };
      }
    } catch (error) {
      console.error('Token validation error:', error);
      return { valid: false, error: error.message };
    }
  }

  /**
   * Refresh expired or expiring token
   * @param {string} token - Current token
   * @returns {Promise<{valid: boolean, token?: string, error?: string}>}
   */
  static async refreshToken(token) {
    try {
      const response = await ApiService.post('/auth/refresh-token', {}, token);
      
      if (response.success && response.data?.token) {
        // Save new token
        await AsyncStorageUtils.setItem('signed_session_id', response.data.token);
        console.log('Token refreshed successfully');
        return { valid: true, token: response.data.token };
      }
      
      throw new Error(response.error || 'Token refresh failed');
    } catch (error) {
      console.error('Token refresh error:', error);
      return { valid: false, error: error.message };
    }
  }

  /**
   * Clear all stored tokens
   * @returns {Promise<void>}
   */
  static async clearTokens() {
    try {
      await Promise.all([
        AsyncStorageUtils.removeItem('signed_session_id'),
        AsyncStorageUtils.removeItem('user'),
      ]);
    } catch (error) {
      console.error('Error clearing tokens:', error);
    }
  }
}

export default TokenManager;