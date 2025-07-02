// ===== FIXED: services/PasswordResetService.js =====
import ApiService from './ApiService';
import Constants from '../utils/Constants';

class PasswordResetService {
  /**
   * Request a password reset email
   * @param {string} email - User's email address
   * @returns {Promise<Object>} API response
   */
  async requestPasswordReset(email) {
    try {
      if (!email || !email.trim()) {
        throw new Error('Email is required');
      }

      if (!/\S+@\S+\.\S+/.test(email)) {
        throw new Error('Please enter a valid email address');
      }

      const response = await ApiService.post('/auth/forgot-password', { email });
      
      // FIXED: Handle both success and Google user cases properly
      if (response.success) {
        return {
          success: true,
          message: response.message || 'Password reset email sent successfully',
        };
      } else {
        // Return the error without throwing - let the UI handle it
        return {
          success: false,
          error: response.error || 'Failed to send password reset email',
        };
      }

    } catch (error) {
      console.error('Password reset request error:', error);
      return {
        success: false,
        error: error.message || 'Failed to request password reset',
      };
    }
  }

  /**
   * Reset password using reset token
   * @param {string} token - Password reset token
   * @param {string} newPassword - New password
   * @returns {Promise<Object>} API response
   */
  async resetPassword(token, newPassword) {
    try {
      if (!token || !token.trim()) {
        throw new Error('Reset token is required');
      }

      if (!newPassword || newPassword.length < 6) {
        throw new Error('Password must be at least 6 characters long');
      }

      const response = await ApiService.post('/auth/reset-password', {
        token,
        newPassword,
      });

      if (!response.success) {
        throw new Error(response.error || 'Failed to reset password');
      }

      return {
        success: true,
        message: response.data?.message || 'Password reset successfully',
        email: response.data?.email,
      };

    } catch (error) {
      console.error('Password reset error:', error);
      return {
        success: false,
        error: error.message || 'Failed to reset password',
      };
    }
  }

  /**
   * Validate password reset token (optional - for checking token validity)
   * @param {string} token - Password reset token
   * @returns {Promise<Object>} Validation result
   */
  async validateResetToken(token) {
    try {
      if (!token || !token.trim()) {
        return {
          valid: false,
          error: 'Reset token is required',
        };
      }

      // You could add a server endpoint to validate tokens without resetting
      // For now, we'll assume the token is valid until reset is attempted
      return {
        valid: true,
      };

    } catch (error) {
      console.error('Token validation error:', error);
      return {
        valid: false,
        error: error.message || 'Failed to validate reset token',
      };
    }
  }
}

export default new PasswordResetService();