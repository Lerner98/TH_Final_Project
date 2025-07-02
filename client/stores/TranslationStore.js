// ===== UPDATED TRANSLATION STORE =====
// stores/TranslationStore.js
import { create } from 'zustand';
import { debounce } from 'lodash';
import AsyncStorageUtils from '../utils/AsyncStorage';
import ApiService from '../services/ApiService';
import Constants from '../utils/Constants';
import Helpers from '../utils/Helpers';
import GuestManager from '../utils/GuestManager';

const { STORAGE_KEYS, API_ENDPOINTS } = Constants;

const saveGuestTranslations = debounce(async (translations) => {
  await AsyncStorageUtils.setItem(STORAGE_KEYS.GUEST_TRANSLATIONS, translations);
}, 300);

const useTranslationStore = create((set, get) => ({
  recentTextTranslations: [],
  recentVoiceTranslations: [],
  savedTextTranslations: [],
  savedVoiceTranslations: [],
  guestTranslations: [],
  isLoading: false,
  error: null,

  /**
   * Initialize guest translations from storage
   */
  initializeGuestTranslations: async () => {
    try {
      const guestTranslations = await AsyncStorageUtils.getItem(STORAGE_KEYS.GUEST_TRANSLATIONS);
      set({ guestTranslations: guestTranslations || [] });
    } catch (error) {
      console.error('Failed to initialize guest translations:', error);
      set({ guestTranslations: [] });
    }
  },

  /**
   * Fetch translations for a logged-in user.
   * @param {Object} user - The user object with signed_session_id.
   * @returns {Promise<void>} A promise that resolves when translations are fetched.
   * @throws {Error} If fetching fails.
   */
  fetchTranslations: async (user) => {
    try {
      set({ isLoading: true, error: null });

      const [textRes, voiceRes] = await Promise.all([
        ApiService.get(API_ENDPOINTS.TRANSLATIONS_TEXT, user.signed_session_id),
        ApiService.get(API_ENDPOINTS.TRANSLATIONS_VOICE, user.signed_session_id),
      ]);

      if (!textRes.success || !voiceRes.success) {
        throw new Error(textRes.error || voiceRes.error);
      }

      set({
        savedTextTranslations: textRes.data,
        savedVoiceTranslations: voiceRes.data,
        recentTextTranslations: textRes.data.slice(-5),
        recentVoiceTranslations: voiceRes.data.slice(-5),
        isLoading: false,
        error: null,
      });
    } catch (err) {
      const msg = Helpers.handleError(err);
      set({ error: msg, isLoading: false });
      throw new Error(msg);
    }
  },

  /**
   * Clear all translations for a logged-in user.
   * @param {Object} user - The user object with signed_session_id.
   * @returns {Promise<void>} A promise that resolves when translations are cleared.
   * @throws {Error} If clearing fails.
   */
  clearTranslations: async (user) => {
    try {
      set({ isLoading: true, error: null });
      const response = await ApiService.delete('/translations', user.signed_session_id);
      if (!response.success) throw new Error(response.error);

      set({
        savedTextTranslations: [],
        savedVoiceTranslations: [],
        recentTextTranslations: [],
        recentVoiceTranslations: [],
        isLoading: false,
        error: null,
      });
    } catch (err) {
      const msg = Helpers.handleError(err);
      set({ error: msg, isLoading: false });
      throw new Error(msg);
    }
  },

  /**
   * Clear all guest translations.
   * @returns {Promise<void>} A promise that resolves when guest translations are cleared.
   * @throws {Error} If clearing fails.
   */
  clearGuestTranslations: async () => {
    try {
      set({ isLoading: true, error: null });
      await GuestManager.clearAllData();
      set({ guestTranslations: [], isLoading: false, error: null });
    } catch (err) {
      const msg = Helpers.handleError(err);
      set({ error: msg, isLoading: false });
      throw new Error(msg);
    }
  },

  /**
   * Add a text translation for a logged-in user or guest.
   * @param {Object} translation - The translation object to add.
   * @param {boolean} isGuest - Whether the user is a guest.
   * @param {string} sessionId - The session ID for logged-in users.
   * @returns {Promise<void>} A promise that resolves when the translation is added.
   * @throws {Error} If adding fails.
   */
  addTextTranslation: async (translation, isGuest, sessionId) => {
    if (isGuest) {
      try {
        // Check guest limits before adding
        const limitCheck = await GuestManager.checkTranslationLimit('text');
        if (!limitCheck.allowed) {
          throw new Error('Guest translation limit reached');
        }

        const updated = [...get().guestTranslations, { ...translation, type: 'text' }];
        set({ guestTranslations: updated });
        await saveGuestTranslations(updated);
        
        // Increment guest count
        await GuestManager.incrementCount('text');
      } catch (err) {
        throw new Error(Helpers.handleError(err));
      }
    } else {
      try {
        set({ isLoading: true, error: null });
        const res = await ApiService.post(API_ENDPOINTS.TRANSLATIONS_TEXT, translation, sessionId);
        if (!res.success) throw new Error(res.error);

        const fetch = await ApiService.get(API_ENDPOINTS.TRANSLATIONS_TEXT, sessionId);
        if (!fetch.success) throw new Error(fetch.error);

        set({
          recentTextTranslations: fetch.data.slice(-5),
          savedTextTranslations: fetch.data,
          isLoading: false,
          error: null,
        });
      } catch (err) {
        const msg = Helpers.handleError(err);
        set({ error: msg, isLoading: false });
        throw new Error(msg);
      }
    }
  },

  /**
   * Add a voice translation for a logged-in user or guest.
   * @param {Object} translation - The translation object to add.
   * @param {boolean} isGuest - Whether the user is a guest.
   * @param {string} sessionId - The session ID for logged-in users.
   * @returns {Promise<void>} A promise that resolves when the translation is added.
   * @throws {Error} If adding fails.
   */
  addVoiceTranslation: async (translation, isGuest, sessionId) => {
    if (isGuest) {
      try {
        // Check guest limits before adding
        const limitCheck = await GuestManager.checkTranslationLimit('voice');
        if (!limitCheck.allowed) {
          throw new Error('Guest translation limit reached');
        }

        const updated = [...get().guestTranslations, { ...translation, type: 'voice' }];
        set({ guestTranslations: updated });
        await saveGuestTranslations(updated);
        
        // Increment guest count
        await GuestManager.incrementCount('voice');
      } catch (err) {
        throw new Error(Helpers.handleError(err));
      }
    } else {
      try {
        set({ isLoading: true, error: null });
        const res = await ApiService.post(API_ENDPOINTS.TRANSLATIONS_VOICE, translation, sessionId);
        if (!res.success) throw new Error(res.error);

        const fetch = await ApiService.get(API_ENDPOINTS.TRANSLATIONS_VOICE, sessionId);
        if (!fetch.success) throw new Error(fetch.error);

        set({
          recentVoiceTranslations: fetch.data.slice(-5),
          savedVoiceTranslations: fetch.data,
          isLoading: false,
          error: null,
        });
      } catch (err) {
        const msg = Helpers.handleError(err);
        set({ error: msg, isLoading: false });
        throw new Error(msg);
      }
    }
  },

  /**
   * Increment the guest translation count for a specific type.
   * @param {string} type - The type of translation ('text' or 'voice').
   * @returns {Promise<void>} A promise that resolves when the count is incremented.
   */
  incrementGuestTranslationCount: async (type) => {
    try {
      await GuestManager.incrementCount(type);
    } catch (err) {
      const msg = `Failed to increment ${type} count: ${err.message}`;
      set({ error: msg });
    }
  },

  /**
   * Get the guest translation count, either total or by type.
   * @param {string} type - The type of count ('total', 'text', or 'voice').
   * @returns {Promise<number>} The number of translations.
   */
  getGuestTranslationCount: async (type) => {
    try {
      if (type === 'total') {
        const guest = await AsyncStorageUtils.getItem(STORAGE_KEYS.GUEST_TRANSLATIONS);
        return guest?.length || 0;
      }

      const counts = await GuestManager.getAllCounts();
      return counts[type] || 0;
    } catch {
      return 0;
    }
  },

  /**
   * Remove a single translation for a logged-in user or guest.
   * @param {string} translationId - The ID of the translation to remove.
   * @param {string} type - The type of translation ('text' or 'voice').
   * @param {boolean} isGuest - Whether the user is a guest.
   * @param {string} [sessionId] - The session ID for logged-in users.
   * @returns {Promise<void>} A promise that resolves when the translation is removed.
   * @throws {Error} If removal fails.
   */
  removeTranslation: async (translationId, type, isGuest, sessionId) => {
    try {
      set({ isLoading: true, error: null });

      if (isGuest) {
        const updated = get().guestTranslations.filter(
          (t) => !(t.id === translationId && t.type === type)
        );
        set({ guestTranslations: updated });
        await saveGuestTranslations(updated);
      } else {
        const endpoint = type === 'text' ? API_ENDPOINTS.TRANSLATIONS_TEXT : API_ENDPOINTS.TRANSLATIONS_VOICE;
        const res = await ApiService.delete(`${endpoint}/${translationId}`, sessionId);
        if (!res.success) throw new Error(res.error);

        const fetch = await ApiService.get(endpoint, sessionId);
        if (!fetch.success) throw new Error(fetch.error);

        if (type === 'text') {
          set({
            recentTextTranslations: fetch.data.slice(-5),
            savedTextTranslations: fetch.data,
            isLoading: false,
            error: null,
          });
        } else {
          set({
            recentVoiceTranslations: fetch.data.slice(-5),
            savedVoiceTranslations: fetch.data,
            isLoading: false,
            error: null,
          });
        }
      }
    } catch (err) {
      const msg = Helpers.handleError(err);
      set({ error: msg, isLoading: false });
      throw new Error(msg);
    }
  },

  /**
   * Get guest usage statistics
   * @returns {Promise<Object>} Usage statistics
   */
  getGuestUsageStats: async () => {
    try {
      return await GuestManager.getUsageStats();
    } catch (error) {
      console.error('Failed to get guest usage stats:', error);
      return {};
    }
  },

  /**
   * Check if guest user can perform translation
   * @param {string} type - Translation type
   * @returns {Promise<Object>} Limit check result
   */
  checkGuestLimit: async (type) => {
    try {
      return await GuestManager.checkTranslationLimit(type);
    } catch (error) {
      console.error('Failed to check guest limit:', error);
      return { allowed: false, remaining: 0, limit: 0 };
    }
  },

  /**
   * Reset all store state (useful for logout)
   */
  resetStore: () => {
    set({
      recentTextTranslations: [],
      recentVoiceTranslations: [],
      savedTextTranslations: [],
      savedVoiceTranslations: [],
      guestTranslations: [],
      isLoading: false,
      error: null,
    });
  },
}));

export default useTranslationStore;