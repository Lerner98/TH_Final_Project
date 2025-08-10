// controllers/UserDataController.js
const db = require('../utils/database');
const { ERROR_MESSAGES } = require('../utils/constants');

class UserDataController {
  async updatePreferences(req, res) {
    console.log('[User Data Service] POST /preferences');
    const { defaultFromLang, defaultToLang } = req.body;
    const userId = req.user.id;

    try {
      await db.updateUserPreferences(userId, defaultFromLang, defaultToLang);
      res.json({ success: true });
    } catch (err) {
      console.error('[UDS][CTRL] updatePreferences ERROR:', err.message);
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_UPDATE_PREFERENCES });
    }
  }

  async saveTextTranslation(req, res) {
    console.log('[User Data Service] POST /translations/text START');
    console.log('[User Data Service] User ID:', req.user?.id);
    console.log('[User Data Service] Auth header:', req.headers.authorization ? 'PRESENT' : 'MISSING');
    console.log('[User Data Service] Body:', req.body);

    const { fromLang, toLang, original_text, translated_text, type } = req.body;
    const userId = req.user.id;

    try {
      console.log('[User Data Service] Calling db.saveTextTranslation...');
      await db.saveTextTranslation(userId, fromLang, toLang, original_text, translated_text, type);
      console.log('[User Data Service] db.saveTextTranslation DONE');
      res.json({ success: true });
    } catch (err) {
      console.error('[User Data Service] db.saveTextTranslation ERROR:', err);
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_SAVE_TEXT_TRANSLATION });
    }
  }

  async getTextTranslations(req, res) {
    console.log('[User Data Service] GET /translations/text START user:', req.user?.id);
    try {
      const translations = await db.getTextTranslations(req.user.id);
      console.log('[User Data Service] GET /translations/text OK count:', Array.isArray(translations) ? translations.length : 0);
      res.json(translations);
    } catch (err) {
      console.error('[UDS][CTRL] getTextTranslations ERROR:', err.message);
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_FETCH_TEXT_TRANSLATIONS });
    }
  }

  async saveVoiceTranslation(req, res) {
    console.log('[User Data Service] POST /translations/voice START user:', req.user?.id);
    const { fromLang, toLang, original_text, translated_text, type } = req.body;

    try {
      await db.saveVoiceTranslation(req.user.id, fromLang, toLang, original_text, translated_text, type);
      console.log('[User Data Service] POST /translations/voice DONE');
      res.json({ success: true });
    } catch (err) {
      console.error('[UDS][CTRL] saveVoiceTranslation ERROR:', err.message);
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_SAVE_VOICE_TRANSLATION });
    }
  }

  async getVoiceTranslations(req, res) {
    console.log('[User Data Service] GET /translations/voice START user:', req.user?.id);
    try {
      const translations = await db.getVoiceTranslations(req.user.id);
      console.log('[User Data Service] GET /translations/voice OK count:', Array.isArray(translations) ? translations.length : 0);
      res.json(translations);
    } catch (err) {
      console.error('[UDS][CTRL] getVoiceTranslations ERROR:', err.message);
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_FETCH_VOICE_TRANSLATIONS });
    }
  }

  async deleteTranslation(req, res) {
    console.log('[User Data Service] DELETE /translations/delete/:id START user:', req.user?.id, 'id:', req.params.id);
    try {
      await db.deleteTranslation(req.user.id, req.params.id);
      console.log('[User Data Service] DELETE /translations/delete/:id DONE');
      res.json({ success: true });
    } catch (err) {
      console.error('[UDS][CTRL] deleteTranslation ERROR:', err.message);
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_DELETE_TRANSLATION });
    }
  }

  async deleteTranslationPost(req, res) {
    console.log('[User Data Service] POST /translations/delete/:id START user:', req.user?.id, 'id:', req.params.id);
    try {
      await db.deleteTranslation(req.user.id, req.params.id);
      console.log('[User Data Service] POST /translations/delete/:id DONE');
      res.json({ success: true });
    } catch (err) {
      console.error('[UDS][CTRL] deleteTranslationPost ERROR:', err.message);
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_DELETE_TRANSLATION });
    }
  }

  async clearTranslations(req, res) {
    console.log('[User Data Service] DELETE /translations START user:', req.user?.id);
    try {
      await db.clearTranslations(req.user.id);
      console.log('[User Data Service] DELETE /translations DONE');
      res.json({ success: true });
    } catch (err) {
      console.error('[UDS][CTRL] clearTranslations ERROR:', err.message);
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_CLEAR_TRANSLATIONS });
    }
  }

  async getStatistics(req, res) {
    console.log('[User Data Service] GET /statistics START user:', req.user?.id);
    try {
      const stats = await db.getLanguageStatistics(req.user.id);
      console.log('[User Data Service] GET /statistics OK');
      res.json(stats);
    } catch (err) {
      console.error('[UDS][CTRL] getStatistics ERROR:', err.message);
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_FETCH_STATISTICS });
    }
  }

  async getPreferences(req, res) {
  console.log('[User Data Service] GET /preferences START user:', req.user?.id);
  try {
    const preferences = await db.getUserPreferences(req.user.id);
    console.log('[User Data Service] GET /preferences OK');
    res.json(preferences || { 
      defaultFromLang: 'en', 
      defaultToLang: 'he' 
    });
  } catch (err) {
    console.error('[UDS][CTRL] getPreferences ERROR:', err.message);
    res.status(500).json({ error: err.message || 'Failed to fetch preferences' });
  }
}

  async getAuditLogs(req, res) {
    console.log('[User Data Service] GET /audit-logs START user:', req.user?.id);
    try {
      const logs = await db.getAuditLogs(req.user.id);
      console.log('[User Data Service] GET /audit-logs OK count:', Array.isArray(logs) ? logs.length : 0);
      res.json(logs);
    } catch (err) {
      console.error('[UDS][CTRL] getAuditLogs ERROR:', err.message);
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_FETCH_AUDIT_LOGS });
    }
  }
}


module.exports = new UserDataController();
