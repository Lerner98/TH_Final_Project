/**
 * TranslationRoutes.js - Translation Routes Configuration
 * Defines all translation-related endpoints for the translation service.
 * Maps HTTP routes to TranslationController methods with appropriate
 * authentication middleware for text, voice, image, and document processing.
 */

const express = require('express');
const router = express.Router();
const multer = require('multer');
const TranslationController = require('../controllers/TranslationController');
const { optionalAuthMiddleware, authMiddleware } = require('../middleware/AuthMiddleware');

// Configure multer for file uploads (not used atm)
const upload = multer({ dest: 'uploads/' });

/**
 * // Translates text between languages using OpenAI (optional auth)
 */
router.post('/translate', optionalAuthMiddleware, TranslationController.translate.bind(TranslationController));

/**
 * Extracts text from images using OCR (optional auth)
 */
router.post('/recognize-text', optionalAuthMiddleware, TranslationController.recognizeText.bind(TranslationController));

/**
 * Converts audio files to text using Whisper API (requires auth)
 */
router.post('/speech-to-text', upload.single('audio'), authMiddleware, TranslationController.speechToText.bind(TranslationController));

/**
 * Converts text to speech audio using TTS API (requires auth)
 */
router.post('/text-to-speech', authMiddleware, TranslationController.textToSpeech.bind(TranslationController));

/**
 * Recognizes ASL gestures from images (requires auth)
 */
router.post('/recognize-asl', authMiddleware, TranslationController.recognizeAsl.bind(TranslationController));

/**
 * Extracts text from document files (PDF, DOCX, TXT) (requires auth)
 */
router.post('/extract-text', authMiddleware, TranslationController.extractText.bind(TranslationController));

/**
 * Generates Word document from text content (optional auth)
 */
router.post('/generate-docx', optionalAuthMiddleware, TranslationController.generateDocx.bind(TranslationController));

/**
 * Returns filtered list of supported languages (no auth required)
 */
router.get('/languages', TranslationController.getLanguages.bind(TranslationController));

module.exports = router;