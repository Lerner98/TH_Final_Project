// routes/UserDataRoutes.js
const express = require('express');
const router = express.Router();

const UserDataController = require('../controllers/UserDataController');
const { authMiddleware } = require('../middleware/AuthMiddleware');

// 🔒 Preferences
router.post('/preferences', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] POST /preferences hit');
  next();
}, UserDataController.updatePreferences.bind(UserDataController));

// 🔒 Get preferences  
router.get('/preferences', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] GET /preferences hit');
  next();
}, UserDataController.getPreferences.bind(UserDataController));

// 🔒 Text translations
router.post('/translations/text', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] POST /translations/text hit');
  next();
}, UserDataController.saveTextTranslation.bind(UserDataController));

router.get('/translations/text', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] GET /translations/text hit');
  next();
}, UserDataController.getTextTranslations.bind(UserDataController));

// 🔒 Voice translations
router.post('/translations/voice', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] POST /translations/voice hit');
  next();
}, UserDataController.saveVoiceTranslation.bind(UserDataController));

router.get('/translations/voice', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] GET /translations/voice hit');
  next();
}, UserDataController.getVoiceTranslations.bind(UserDataController));

// 🔒 Delete one translation
router.delete('/translations/delete/:id', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] DELETE /translations/delete/:id hit', req.params.id);
  next();
}, UserDataController.deleteTranslation.bind(UserDataController));

// 🔒 Alternative POST delete (if app uses it)
router.post('/translations/delete/:id', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] POST /translations/delete/:id hit', req.params.id);
  next();
}, UserDataController.deleteTranslationPost.bind(UserDataController));

// 🔒 Clear all translations
router.delete('/translations', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] DELETE /translations hit');
  next();
}, UserDataController.clearTranslations.bind(UserDataController));

// 🔒 Stats & audit logs
router.get('/statistics', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] GET /statistics hit');
  next();
}, UserDataController.getStatistics.bind(UserDataController));

router.get('/audit-logs', authMiddleware, (req, res, next) => {
  console.log('[UDS][ROUTE] GET /audit-logs hit');
  next();
}, UserDataController.getAuditLogs.bind(UserDataController));

module.exports = router;
