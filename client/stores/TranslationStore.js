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

// Create stable function references to prevent Metro rebuild issues
const createStableFunctions = (set, get) => {
  // Cache the functions so they have stable references
  const stableFunctions = {};

  stableFunctions.initializeGuestTranslations = async () => {
    try {
      const guestTranslations = await AsyncStorageUtils.getItem(STORAGE_KEYS.GUEST_TRANSLATIONS);
      set({ guestTranslations: guestTranslations || [] });
    } catch (error) {
      console.error('Failed to initialize guest translations:', error);
      set({ guestTranslations: [] });
    }
  };

  stableFunctions.fetchTranslations = async (user) => {
    const token = user?.signed_session_id || '';
    const base = Constants.USER_DATA_API_URL;
    const textUrl = `${base}${API_ENDPOINTS.TRANSLATIONS_TEXT}`;
    const voiceUrl = `${base}${API_ENDPOINTS.TRANSLATIONS_VOICE}`;

    console.log('[Store] fetchTranslations: start', {
      hasToken: !!token,
      textUrl,
      voiceUrl,
    });

    try {
      set({ isLoading: true, error: null });

      // quick reachability
      try {
        const hc = await fetch(`${base}/health`);
        console.log('[Store] UDS /health status =', hc.status);
      } catch (e) {
        console.log('[Store] UDS /health failed:', e?.message || e);
      }

      // First try: native fetch (cleanest signal)
      const [textResp, voiceResp] = await Promise.all([
        fetch(textUrl, {
          method: 'GET',
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        }),
        fetch(voiceUrl, {
          method: 'GET',
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        }),
      ]);

      console.log('[Store] fetchTranslations: fetch statuses', {
        textStatus: textResp.status,
        voiceStatus: voiceResp.status,
      });

      if (!textResp.ok || !voiceResp.ok) {
        // read bodies (if any) for better error reporting
        let tBody = '';
        let vBody = '';
        try { tBody = await textResp.text(); } catch {}
        try { vBody = await voiceResp.text(); } catch {}
        throw new Error(
          `UDS GET failed: text(${textResp.status} ${tBody?.slice(0, 200)}) voice(${voiceResp.status} ${vBody?.slice(0, 200)})`
        );
      }

      const [textData, voiceData] = await Promise.all([textResp.json(), voiceResp.json()]);

      console.log('[Store] fetchTranslations: fetch ok', {
        textCount: Array.isArray(textData) ? textData.length : 'n/a',
        voiceCount: Array.isArray(voiceData) ? voiceData.length : 'n/a',
      });

      set({
        savedTextTranslations: textData || [],
        savedVoiceTranslations: voiceData || [],
        recentTextTranslations: Array.isArray(textData) ? textData.slice(-5) : [],
        recentVoiceTranslations: Array.isArray(voiceData) ? voiceData.slice(-5) : [],
        isLoading: false,
        error: null,
      });
    } catch (fetchErr) {
      console.log('[Store] fetchTranslations: fetch path ERROR -> trying ApiService.get fallback', fetchErr?.message || fetchErr);

      try {
        const [textRes, voiceRes] = await Promise.all([
          ApiService.get(API_ENDPOINTS.TRANSLATIONS_TEXT, token, {
            headers: token ? { Authorization: `Bearer ${token}` } : {},
          }),
          ApiService.get(API_ENDPOINTS.TRANSLATIONS_VOICE, token, {
            headers: token ? { Authorization: `Bearer ${token}` } : {},
          }),
        ]);

        console.log('[Store] fetchTranslations: ApiService fallback results', {
          textOk: textRes?.success,
          voiceOk: voiceRes?.success,
          textCount: Array.isArray(textRes?.data) ? textRes.data.length : 'n/a',
          voiceCount: Array.isArray(voiceRes?.data) ? voiceRes.data.length : 'n/a',
        });

        if (!textRes.success || !voiceRes.success) {
          throw new Error(textRes.error || voiceRes.error || 'Unknown fetch error');
        }

        set({
          savedTextTranslations: textRes.data || [],
          savedVoiceTranslations: voiceRes.data || [],
          recentTextTranslations: Array.isArray(textRes.data) ? textRes.data.slice(-5) : [],
          recentVoiceTranslations: Array.isArray(voiceRes.data) ? voiceRes.data.slice(-5) : [],
          isLoading: false,
          error: null,
        });
      } catch (axiosErr) {
        console.log('[Store] fetchTranslations: ApiService fallback ERROR', axiosErr?.message || axiosErr);
        const msg = Helpers.handleError(axiosErr);
        set({ error: msg, isLoading: false });
        throw new Error(msg);
      }
    }
  };

  stableFunctions.clearTranslations = async (user) => {
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
  };

  stableFunctions.clearGuestTranslations = async () => {
    try {
      set({ isLoading: true, error: null });
      await GuestManager.clearAllData();
      set({ guestTranslations: [], isLoading: false, error: null });
    } catch (err) {
      const msg = Helpers.handleError(err);
      set({ error: msg, isLoading: false });
      throw new Error(msg);
    }
  };

  stableFunctions.addTextTranslation = async (translation, isGuest, sessionId) => {
    if (isGuest) {
      try {
        const limitCheck = await GuestManager.checkTranslationLimit('text');
        if (!limitCheck.allowed) {
          throw new Error('Guest translation limit reached');
        }
        const updated = [...get().guestTranslations, { ...translation, type: 'text' }];
        set({ guestTranslations: updated });
        await saveGuestTranslations(updated);
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
  };

  stableFunctions.addVoiceTranslation = async (translation, isGuest, sessionId) => {
    if (isGuest) {
      try {
        const limitCheck = await GuestManager.checkTranslationLimit('voice');
        if (!limitCheck.allowed) {
          throw new Error('Guest translation limit reached');
        }
        const updated = [...get().guestTranslations, { ...translation, type: 'voice' }];
        set({ guestTranslations: updated });
        await saveGuestTranslations(updated);
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
  };

  stableFunctions.incrementGuestTranslationCount = async (type) => {
    try {
      await GuestManager.incrementCount(type);
    } catch (err) {
      const msg = `Failed to increment ${type} count: ${err.message}`;
      set({ error: msg });
    }
  };

  stableFunctions.getGuestTranslationCount = async (type) => {
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
  };

  stableFunctions.removeTranslation = async (translationId, type, isGuest, sessionId) => {
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
  };

  stableFunctions.getGuestUsageStats = async () => {
    try {
      return await GuestManager.getUsageStats();
    } catch (error) {
      console.error('Failed to get guest usage stats:', error);
      return {};
    }
  };

  stableFunctions.checkGuestLimit = async (type) => {
    try {
      return await GuestManager.checkTranslationLimit(type);
    } catch (error) {
      console.error('Failed to check guest limit:', error);
      return { allowed: false, remaining: 0, limit: 0 };
    }
  };

  stableFunctions.resetStore = () => {
    set({
      recentTextTranslations: [],
      recentVoiceTranslations: [],
      savedTextTranslations: [],
      savedVoiceTranslations: [],
      guestTranslations: [],
      isLoading: false,
      error: null,
    });
  };

  return stableFunctions;
};

const useTranslationStore = create((set, get) => {
  const stableFunctions = createStableFunctions(set, get);

  return {
    recentTextTranslations: [],
    recentVoiceTranslations: [],
    savedTextTranslations: [],
    savedVoiceTranslations: [],
    guestTranslations: [],
    isLoading: false,
    error: null,

    // All functions now have stable references
    ...stableFunctions,
  };
});

export default useTranslationStore;