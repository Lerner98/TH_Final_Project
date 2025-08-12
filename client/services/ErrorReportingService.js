// services/ErrorReportingService.js
import { Platform } from 'react-native';
import * as Device from 'expo-device';
import Constants from '../utils/Constants';

class ErrorReportingService {
  static isEnabled = true;
  static apiUrl = `${Constants.MDB_SERVER_URL}/api/reports`;

  /**
   * Report an error to the MDB server
   * @param {Error} error - The error object
   * @param {Object} context - Additional context (screen, user info, etc.)
   */
  static async reportError(error, context = {}) {
    if (!this.isEnabled) {
      console.log('[ErrorReporting] Service disabled, skipping report');
      return;
    }

    try {
      const deviceInfo = await this.getDeviceInfo();
      
      const reportData = {
        userId: context.userId || 'guest-mobile-user',
        type: context.type || 'error',
        message: error?.message || context.message || 'Unknown error',
        errorStack: error?.stack || context.errorStack || 'No stack trace available',
        screen: context.screen || context.currentScreen || 'Unknown Screen',
        platform: Platform.OS,
        appVersion: Constants.APP_VERSION || '1.0.0',
        deviceInfo,
        extra: {
          timestamp: new Date().toISOString(),
          errorType: context.errorType || 'component_error',
          isFatal: context.isFatal || false,
          ...context.extra
        }
      };

      console.log('[ErrorReporting] Sending report:', {
        type: reportData.type,
        message: reportData.message,
        screen: reportData.screen
      });

      const response = await fetch(this.apiUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-api-key': Constants.MDB_API_KEY
        },
        body: JSON.stringify(reportData),
        timeout: 5000
      });

      if (response.ok) {
        console.log('[ErrorReporting] Error reported successfully');
        return { success: true };
      } else {
        const errorText = await response.text();
        console.log('[ErrorReporting] Server error:', response.status, errorText);
        return { success: false, error: `Server responded with ${response.status}` };
      }

    } catch (err) {
      console.log('[ErrorReporting] Failed to send report:', err.message);
      return { success: false, error: err.message };
    }
  }

  /**
   * Get device information
   */
  static async getDeviceInfo() {
    try {
      return {
        model: Device.modelName || 'Unknown',
        brand: Device.brand || 'Unknown',
        os: `${Platform.OS} ${Platform.Version}`,
        osVersion: Device.osVersion || 'Unknown',
        platform: Platform.OS,
        isDevice: Device.isDevice || false
      };
    } catch (error) {
      return {
        model: 'Unknown',
        brand: 'Unknown',
        os: `${Platform.OS} ${Platform.Version}`,
        osVersion: 'Unknown',
        platform: Platform.OS,
        isDevice: false
      };
    }
  }

  /**
   * Enable/disable error reporting
   */
  static setEnabled(enabled) {
    this.isEnabled = enabled;
    console.log(`[ErrorReporting] Service ${enabled ? 'enabled' : 'disabled'}`);
  }

  /**
   * Test the connection to MDB server
   */
  static async testConnection() {
    try {
      const response = await fetch(this.apiUrl, {
        method: 'OPTIONS',
        headers: {
          'x-api-key': Constants.MDB_API_KEY
        }
      });
      return response.ok;
    } catch (error) {
      console.log('[ErrorReporting] Connection test failed:', error.message);
      return false;
    }
  }
}

export default ErrorReportingService;