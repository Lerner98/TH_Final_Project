/**
 * UserDataRoutes.js - User Data Routes Configuration
 * Defines all user data management endpoints for the user data service.
 * Maps HTTP routes to UserDataController methods with authentication
 * middleware and detailed logging for preferences, translations, and statistics.
 */

const express = require('express');
const router = express.Router();

const UserDataController = require('../controllers/UserDataController');
const { authMiddleware } = require('../middleware/AuthMiddleware');

// 🔒 Preferences - Updates user's default language prefs
router.post('/preferences', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] POST /preferences hit');
  next();
}, UserDataController.updatePreferences.bind(UserDataController));

// 🔒 Get preferences - Retrieves user's language preferences with defaults
router.get('/preferences', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] GET /preferences hit');
  next();
}, UserDataController.getPreferences.bind(UserDataController));

// 🔒 Text translations - Saves a text translation to user's history
router.post('/translations/text', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] POST /translations/text hit');
  next();
}, UserDataController.saveTextTranslation.bind(UserDataController));

// Retrieves all text translations for authenticated user
router.get('/translations/text', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] GET /translations/text hit');
  next();
}, UserDataController.getTextTranslations.bind(UserDataController));

// 🔒 Voice translations - Saves a voice translation to user's history
router.post('/translations/voice', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] POST /translations/voice hit');
  next();
}, UserDataController.saveVoiceTranslation.bind(UserDataController));

// Retrieves all voice translations for authenticated user
router.get('/translations/voice', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] GET /translations/voice hit');
  next();
}, UserDataController.getVoiceTranslations.bind(UserDataController));

// 🔒 Delete one translation - Deletes specific translation by ID using DELETE method
router.delete('/translations/delete/:id', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] DELETE /translations/delete/:id hit', req.params.id);
  next();
}, UserDataController.deleteTranslation.bind(UserDataController));

// 🔒 Alternative POST method for deleting translation (client compatibility)
router.post('/translations/delete/:id', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] POST /translations/delete/:id hit', req.params.id);
  next();
}, UserDataController.deleteTranslationPost.bind(UserDataController));

// 🔒 Clears all translations for authenticated user
router.delete('/translations', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] DELETE /translations hit');
  next();
}, UserDataController.clearTranslations.bind(UserDataController));

// 🔒 Stats & audit logs - Retrieves user's translation statistics and language usage data
router.get('/statistics', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] GET /statistics hit');
  next();
}, UserDataController.getStatistics.bind(UserDataController));

// Retrieves audit logs showing user's activity history
router.get('/audit-logs', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] GET /audit-logs hit');
  next();
}, UserDataController.getAuditLogs.bind(UserDataController));

module.exports = router;
