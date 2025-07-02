import AsyncStorageUtils from './AsyncStorage';
import { Alert } from 'react-native';

class GuestManager {
  static LIMITS = {
    TOTAL_TRANSLATIONS: 10,
    DAILY_TRANSLATIONS: 5,
    TEXT_TRANSLATIONS: 8,
    VOICE_TRANSLATIONS: 5,
    FILE_TRANSLATIONS: 2,
    CAMERA_TRANSLATIONS: 3,
    ASL_TRANSLATIONS: 3,
  };

  static STORAGE_KEYS = {
    GUEST_TRANSLATIONS: 'guestTranslations',
    DAILY_COUNT: 'guest_daily_count',
    TOTAL_COUNT: 'guest_total_count',
    TEXT_COUNT: 'guest_text_count',
    VOICE_COUNT: 'guest_voice_count',
    FILE_COUNT: 'guest_file_count',
    CAMERA_COUNT: 'guest_camera_count',
    ASL_COUNT: 'guest_asl_count',
  };

  /**
   * Check if guest user can perform a translation
   * @param {string} type - Translation type ('text', 'voice', 'file', 'camera', 'asl')
   * @returns {Promise<{allowed: boolean, remaining: number, limit: number}>}
   */
  static async checkTranslationLimit(type = 'total') {
    try {
      const counts = await this.getAllCounts();
      const typeKey = type.toUpperCase() + '_TRANSLATIONS';
      const limit = this.LIMITS[typeKey] || this.LIMITS.TOTAL_TRANSLATIONS;
      const current = counts[type] || 0;
      
      return {
        allowed: current < limit,
        remaining: Math.max(0, limit - current),
        limit,
        current,
      };
    } catch (error) {
      console.error('Error checking translation limit:', error);
      return { allowed: false, remaining: 0, limit: 0, current: 0 };
    }
  }

  /**
   * Get all translation counts for guest user
   * @returns {Promise<Object>} Object with all count types
   */
  static async getAllCounts() {
    try {
      const [guestTranslations, dailyCount, textCount, voiceCount, fileCount, cameraCount, aslCount] = await Promise.all([
        AsyncStorageUtils.getItem(this.STORAGE_KEYS.GUEST_TRANSLATIONS),
        AsyncStorageUtils.getItem(this.STORAGE_KEYS.DAILY_COUNT),
        AsyncStorageUtils.getItem(this.STORAGE_KEYS.TEXT_COUNT),
        AsyncStorageUtils.getItem(this.STORAGE_KEYS.VOICE_COUNT),
        AsyncStorageUtils.getItem(this.STORAGE_KEYS.FILE_COUNT),
        AsyncStorageUtils.getItem(this.STORAGE_KEYS.CAMERA_COUNT),
        AsyncStorageUtils.getItem(this.STORAGE_KEYS.ASL_COUNT),
      ]);

      const today = new Date().toDateString();
      
      return {
        total: guestTranslations?.length || 0,
        daily: (dailyCount?.date === today) ? (dailyCount?.count || 0) : 0,
        text: parseInt(textCount || '0', 10),
        voice: parseInt(voiceCount || '0', 10),
        file: parseInt(fileCount || '0', 10),
        camera: parseInt(cameraCount || '0', 10),
        asl: parseInt(aslCount || '0', 10),
      };
    } catch (error) {
      console.error('Error getting counts:', error);
      return {};
    }
  }

  /**
   * Increment translation count for a specific type
   * @param {string} type - Translation type
   * @returns {Promise<void>}
   */
  static async incrementCount(type) {
    try {
      const typeKey = `${type.toUpperCase()}_COUNT`;
      const storageKey = this.STORAGE_KEYS[typeKey];
      
      if (storageKey) {
        const current = parseInt(await AsyncStorageUtils.getItem(storageKey) || '0', 10);
        await AsyncStorageUtils.setItem(storageKey, (current + 1).toString());
      }
      
      // Also increment daily count
      await this.incrementDailyCount();
    } catch (error) {
      console.error('Error incrementing count:', error);
    }
  }

  /**
   * Increment daily translation count
   * @returns {Promise<void>}
   */
  static async incrementDailyCount() {
    try {
      const today = new Date().toDateString();
      const dailyData = await AsyncStorageUtils.getItem(this.STORAGE_KEYS.DAILY_COUNT);
      
      if (dailyData?.date === today) {
        await AsyncStorageUtils.setItem(this.STORAGE_KEYS.DAILY_COUNT, {
          date: today,
          count: (dailyData.count || 0) + 1,
        });
      } else {
        await AsyncStorageUtils.setItem(this.STORAGE_KEYS.DAILY_COUNT, {
          date: today,
          count: 1,
        });
      }
    } catch (error) {
      console.error('Error incrementing daily count:', error);
    }
  }

  /**
   * Show upgrade prompt when limit is reached
   * @param {string} type - Translation type that hit the limit
   * @returns {Promise<boolean>} Whether user chose to upgrade
   */
  static async promptUpgrade(type = 'translation') {
    return new Promise((resolve) => {
      Alert.alert(
        'Translation Limit Reached',
        `You've reached your ${type} limit as a guest user. Sign up for unlimited translations and save your history!`,
        [
          { 
            text: 'Maybe Later', 
            style: 'cancel',
            onPress: () => resolve(false) 
          },
          { 
            text: 'Sign Up', 
            onPress: () => resolve(true) 
          }
        ]
      );
    });
  }

  /**
   * Clear all guest data (useful for testing or when user signs up)
   * @returns {Promise<void>}
   */
  static async clearAllData() {
    try {
      await Promise.all([
        AsyncStorageUtils.removeItem(this.STORAGE_KEYS.GUEST_TRANSLATIONS),
        AsyncStorageUtils.removeItem(this.STORAGE_KEYS.DAILY_COUNT),
        AsyncStorageUtils.removeItem(this.STORAGE_KEYS.TOTAL_COUNT),
        AsyncStorageUtils.removeItem(this.STORAGE_KEYS.TEXT_COUNT),
        AsyncStorageUtils.removeItem(this.STORAGE_KEYS.VOICE_COUNT),
        AsyncStorageUtils.removeItem(this.STORAGE_KEYS.FILE_COUNT),
        AsyncStorageUtils.removeItem(this.STORAGE_KEYS.CAMERA_COUNT),
        AsyncStorageUtils.removeItem(this.STORAGE_KEYS.ASL_COUNT),
      ]);
    } catch (error) {
      console.error('Error clearing guest data:', error);
    }
  }

  /**
   * Get guest usage statistics for display
   * @returns {Promise<Object>} Usage statistics
   */
  static async getUsageStats() {
    try {
      const counts = await this.getAllCounts();
      const stats = {};
      
      Object.keys(this.LIMITS).forEach(limitKey => {
        const type = limitKey.replace('_TRANSLATIONS', '').toLowerCase();
        const limit = this.LIMITS[limitKey];
        const current = counts[type] || 0;
        
        stats[type] = {
          current,
          limit,
          remaining: Math.max(0, limit - current),
          percentage: (current / limit) * 100,
        };
      });
      
      return stats;
    } catch (error) {
      console.error('Error getting usage stats:', error);
      return {};
    }
  }
}

export default GuestManager;