// routes/TranslationRoutes.js
const express = require('express');
const router = express.Router();
const multer = require('multer');
const TranslationController = require('../controllers/TranslationController');
const { optionalAuthMiddleware, authMiddleware } = require('../middleware/AuthMiddleware');

// Configure multer for file uploads - EXACT same as original
const upload = multer({ dest: 'uploads/' });

/**
 * Translate text using OpenAI
 */
router.post('/translate', optionalAuthMiddleware, TranslationController.translate.bind(TranslationController));

/**
 * Extract text from an image using OpenAI Vision
 */
router.post('/recognize-text', optionalAuthMiddleware, TranslationController.recognizeText.bind(TranslationController));

/**
 * Convert audio to text using OpenAI Whisper API
 */
router.post('/speech-to-text', upload.single('audio'), authMiddleware, TranslationController.speechToText.bind(TranslationController));

/**
 * Convert text to audio using OpenAI TTS
 */
router.post('/text-to-speech', authMiddleware, TranslationController.textToSpeech.bind(TranslationController));

/**
 * Recognize ASL gestures from an image using OpenAI Vision
 */
router.post('/recognize-asl', authMiddleware, TranslationController.recognizeAsl.bind(TranslationController));

/**
 * Extract text from a file (PDF, DOCX, TXT)
 */
router.post('/extract-text', authMiddleware, TranslationController.extractText.bind(TranslationController));

/**
 * Generate a Word document from text
 */
router.post('/generate-docx', optionalAuthMiddleware, TranslationController.generateDocx.bind(TranslationController));

/**
 * Search supported languages by query
 */
router.get('/languages', TranslationController.getLanguages.bind(TranslationController));

module.exports = router;