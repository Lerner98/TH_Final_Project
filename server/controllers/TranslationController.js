// controllers/TranslationController.js - WORKING VERSION FROM MONOLITH
const { Document, Packer, Paragraph, TextRun } = require('docx');
const { v4: uuidv4 } = require('uuid');
const fs = require('fs');
const path = require('path');
const pdfParse = require('pdf-parse');
const mammoth = require('mammoth');
const OpenAI = require('openai');
const { ERROR_MESSAGES } = require('../utils/constants');

// Initialize OpenAI client
const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY,
});

class TranslationController {
  /**
   * Search supported languages based on a query.
   */
  async searchLanguages(query) {
    const supportedLanguages = [
      { code: 'af', name: 'Afrikaans' },
      { code: 'ar', name: 'Arabic' },
      { code: 'hy', name: 'Armenian' },
      { code: 'az', name: 'Azerbaijani' },
      { code: 'be', name: 'Belarusian' },
      { code: 'bs', name: 'Bosnian' },
      { code: 'bg', name: 'Bulgarian' },
      { code: 'ca', name: 'Catalan' },
      { code: 'zh', name: 'Chinese' },
      { code: 'hr', name: 'Croatian' },
      { code: 'cs', name: 'Czech' },
      { code: 'da', name: 'Danish' },
      { code: 'nl', name: 'Dutch' },
      { code: 'en', name: 'English' },
      { code: 'et', name: 'Estonian' },
      { code: 'fi', name: 'Finnish' },
      { code: 'fr', name: 'French' },
      { code: 'gl', name: 'Galician' },
      { code: 'de', name: 'German' },
      { code: 'el', name: 'Greek' },
      { code: 'he', name: 'Hebrew' },
      { code: 'hi', name: 'Hindi' },
      { code: 'hu', name: 'Hungarian' },
      { code: 'is', name: 'Icelandic' },
      { code: 'id', name: 'Indonesian' },
      { code: 'it', name: 'Italian' },
      { code: 'ja', name: 'Japanese' },
      { code: 'kn', name: 'Kannada' },
      { code: 'kk', name: 'Kazakh' },
      { code: 'ko', name: 'Korean' },
      { code: 'lv', name: 'Latvian' },
      { code: 'lt', name: 'Lithuanian' },
      { code: 'mk', name: 'Macedonian' },
      { code: 'ms', name: 'Malay' },
      { code: 'mr', name: 'Marathi' },
      { code: 'mi', name: 'Maori' },
      { code: 'ne', name: 'Nepali' },
      { code: 'no', name: 'Norwegian' },
      { code: 'fa', name: 'Persian' },
      { code: 'pl', name: 'Polish' },
      { code: 'pt', name: 'Portuguese' },
      { code: 'ro', name: 'Romanian' },
      { code: 'ru', name: 'Russian' },
      { code: 'sr', name: 'Serbian' },
      { code: 'sk', name: 'Slovak' },
      { code: 'sl', name: 'Slovenian' },
      { code: 'es', name: 'Spanish' },
      { code: 'sw', name: 'Swahili' },
      { code: 'sv', name: 'Swedish' },
      { code: 'tl', name: 'Tagalog' },
      { code: 'ta', name: 'Tamil' },
      { code: 'th', name: 'Thai' },
      { code: 'tr', name: 'Turkish' },
      { code: 'uk', name: 'Ukrainian' },
      { code: 'ur', name: 'Urdu' },
      { code: 'vi', name: 'Vietnamese' },
      { code: 'cy', name: 'Welsh' },
    ];

    const filteredLanguages = supportedLanguages.filter((lang) =>
      lang.name.toLowerCase().includes(query.toLowerCase()) ||
      lang.code.toLowerCase().includes(query.toLowerCase())
    );
    return filteredLanguages;
  }

  /**
   * Detect file type from Base64 prefix.
   */
  detectFileExtensionFromBase64(base64String) {
    const prefix = base64String.slice(0, 50);
    if (prefix.includes('application/pdf')) return 'pdf';
    if (prefix.includes('application/vnd.openxmlformats-officedocument.wordprocessingml.document')) return 'docx';
    if (prefix.includes('application/msword')) return 'docx';
    if (prefix.includes('text/plain')) return 'txt';
    if (prefix.includes('UEsDB')) return 'docx';
    if (prefix.startsWith('%PDF')) return 'pdf';
    return '';
  }

  /**
   * Translate text using OpenAI - WORKING VERSION FROM MONOLITH
   */
  async translate(req, res) {
    console.log('[Translation Service] POST /translate - Controller called');
    
    const { text, targetLang, sourceLang = 'auto' } = req.body;
    if (!text || !targetLang) {
      return res.status(400).json({ error: ERROR_MESSAGES.TEXT_TARGETLANG_REQUIRED });
    }

    try {
      let detectedLang = sourceLang;

      // Step 1: Detect the language of the input text
      if (sourceLang === 'auto') {
        const detectResponse = await openai.chat.completions.create({
          model: 'gpt-4o',
          messages: [
            {
              role: 'system',
              content: `You are a language detection expert. Detect the primary language of the following text and return only the language code (e.g., "en" for English, "he" for Hebrew). If the text contains multiple languages, focus on the most prominent language. If the text is a proper noun (e.g., a brand name like "Lenovo"), ambiguous, or empty, return "unknown" instead of guessing. Do not provide any explanations.`,
            },
            {
              role: 'user',
              content: text,
            },
          ],
        });

        detectedLang = detectResponse.choices[0].message.content.trim();

        if (detectedLang === 'unknown') {
          detectedLang = 'en';
        }
      } else {
        detectedLang = sourceLang;
      }

      // Step 2: If the detected language is the same as the target language, return the original text
      if (detectedLang === targetLang) {
        return res.json({ translatedText: text, detectedLang });
      }

      // Step 3: Attempt to translate the text
      const translationResponse = await openai.chat.completions.create({
        model: 'gpt-4o',
        messages: [
          {
            role: 'system',
            content: `You are a professional translator. Translate the user's message from ${detectedLang} to ${targetLang}. If the text is a proper noun (e.g., a brand name like "Lenovo") or cannot be translated into a meaningful word in the target language, transliterate the text into the script of ${targetLang} without translating the meaning (e.g., "Lenovo" in English to "לנובו" in Hebrew). Respond only with the translated or transliterated text, without any explanation or context.`,
          },
          {
            role: 'user',
            content: text,
          },
        ],
      });

      const translatedText = translationResponse.choices[0].message.content.trim();
      res.json({ translatedText, detectedLang });
    } catch (err) {
      console.error('[Translation Service] Error:', err);
      res.status(500).json({ error: ERROR_MESSAGES.FAILED_TO_TRANSLATE });
    }
  }

  /**
   * Extract text from an image using OpenAI Vision - WORKING VERSION
   */
  async recognizeText(req, res) {
    console.log('[Translation Service] POST /recognize-text');

    const { imageBase64 } = req.body;
    if (!imageBase64) {
      return res.status(400).json({ error: ERROR_MESSAGES.IMAGE_DATA_REQUIRED });
    }

    try {
      const response = await openai.chat.completions.create({
        model: 'gpt-4o',
        messages: [
          {
            role: 'system',
            content: 'Extract only the text from this image, ignoring any non-text elements such as logos, icons, or graphics. Return only the exact text found in the image, without any additional descriptions, explanations, or context. If no text is found, return an empty string (""). Do not include phrases like "The text in the image is".',
          },
          {
            role: 'user',
            content: [
              { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${imageBase64}` } },
            ],
          },
        ],
      });

      let extractedText = response.choices[0].message.content.trim();

      // Post-process to remove any unwanted descriptive text
      const match = extractedText.match(/(?:The text[^']*')(.+?)'/);
      if (match && match[1]) {
        extractedText = match[1];
      }

      res.json({ text: extractedText });
    } catch (err) {
      res.status(500).json({ error: ERROR_MESSAGES.FAILED_TO_RECOGNIZE_TEXT });
    }
  }

  /**
   * Convert audio to text using OpenAI Whisper API - WORKING VERSION
   */
  async speechToText(req, res) {
    console.log('[Translation Service] POST /speech-to-text');

    const { sourceLang } = req.body;
    if (!req.file) {
      return res.status(400).json({ error: ERROR_MESSAGES.AUDIO_FILE_REQUIRED });
    }

    if (!req.file.path || !fs.existsSync(req.file.path)) {
      return res.status(400).json({ error: ERROR_MESSAGES.INVALID_FILE_PATH });
    }

    if (!req.file.mimetype || !req.file.mimetype.startsWith('audio/')) {
      return res.status(400).json({ error: ERROR_MESSAGES.INVALID_AUDIO_FORMAT });
    }

    try {
      const originalPath = req.file.path;
      const ext = path.extname(req.file.originalname) || '.m4a';
      const tempPath = `${originalPath}${ext}`;

      fs.renameSync(originalPath, tempPath);

      const response = await openai.audio.transcriptions.create({
        file: fs.createReadStream(tempPath),
        model: 'whisper-1',
        language: sourceLang,
      });

      if (fs.existsSync(tempPath)) {
        fs.unlinkSync(tempPath);
      }

      const transcription = response.text;
      res.json({ text: transcription || '' });
    } catch (err) {
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_TRANSCRIBE });
    }
  }

  /**
   * Convert text to audio using OpenAI TTS - WORKING VERSION
   */
  async textToSpeech(req, res) {
    console.log('[Translation Service] POST /text-to-speech');

    const { text, language } = req.body;
    if (!text || !language) {
      return res.status(400).json({ error: ERROR_MESSAGES.TEXT_LANGUAGE_REQUIRED });
    }

    try {
      const speechFilePath = path.join(__dirname, `speech-${uuidv4()}.mp3`);
      const response = await openai.audio.speech.create({
        model: 'tts-1',
        voice: 'alloy',
        input: text,
        response_format: 'mp3',
      });

      const buffer = Buffer.from(await response.arrayBuffer());
      fs.writeFileSync(speechFilePath, buffer);

      res.setHeader('Content-Type', 'audio/mpeg');
      res.setHeader('Content-Disposition', 'attachment; filename=speech.mp3');
      res.sendFile(speechFilePath, (err) => {
        if (err) {
          // Log removed
        }
        fs.unlinkSync(speechFilePath);
      });
    } catch (err) {
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_GENERATE_SPEECH });
    }
  }

  /**
   * Recognize ASL gestures from an image using OpenAI Vision - WORKING VERSION
   */
  async recognizeAsl(req, res) {
    console.log('[Translation Service] POST /recognize-asl');

    const { imageBase64 } = req.body;
    if (!imageBase64) {
      return res.status(400).json({ error: ERROR_MESSAGES.IMAGE_DATA_REQUIRED });
    }

    try {
      const response = await openai.chat.completions.create({
        model: 'gpt-4o',
        messages: [
          {
            role: 'user',
            content: [
              { type: 'text', text: 'Interpret the American Sign Language (ASL) gesture in this image and provide the corresponding English word or phrase.' },
              { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${imageBase64}` } },
            ],
          },
        ],
      });

      const recognizedText = response.choices[0].message.content;
      res.json({ text: recognizedText });
    } catch (err) {
      res.status(500).json({ error: ERROR_MESSAGES.FAILED_TO_RECOGNIZE_ASL });
    }
  }

  /**
   * Extract text from a file (PDF, DOCX, TXT) - WORKING VERSION
   */
  async extractText(req, res) {
    console.log('[Translation Service] POST /extract-text');

    const { uri } = req.body;
    if (!uri) {
      return res.status(400).json({ error: ERROR_MESSAGES.FILE_URI_REQUIRED });
    }

    try {
      const buffer = Buffer.from(uri, 'base64');
      const extension = this.detectFileExtensionFromBase64(uri);

      if (extension === 'pdf') {
        const textData = await pdfParse(buffer);
        return res.json({ text: textData.text });
      }

      if (extension === 'docx') {
        const result = await mammoth.extractRawText({ buffer });
        return res.json({ text: result.value });
      }

      if (extension === 'txt') {
        return res.json({ text: buffer.toString('utf-8') });
      }

      return res.status(400).json({ error: ERROR_MESSAGES.UNSUPPORTED_FILE_TYPE });
    } catch (err) {
      return res.status(500).json({ error: ERROR_MESSAGES.FAILED_TO_EXTRACT_TEXT });
    }
  }

  /**
   * Generate a Word document from text - WORKING VERSION
   */
  async generateDocx(req, res) {
    console.log('[Translation Service] POST /generate-docx');

    const { text } = req.body;
    if (!text) {
      return res.status(400).json({ error: ERROR_MESSAGES.TEXT_REQUIRED });
    }

    try {
      const doc = new Document({
        sections: [
          {
            properties: {},
            children: [
              new Paragraph({
                children: [
                  new TextRun({
                    text: text,
                    size: 24,
                  }),
                ],
              }),
            ],
          },
        ],
      });

      const buffer = await Packer.toBuffer(doc);

      res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
      res.setHeader('Content-Disposition', 'attachment; filename=translated.docx');
      res.send(buffer);
    } catch (err) {
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_GENERATE_DOC });
    }
  }

  /**
   * Search supported languages by query - WORKING VERSION
   */
  async getLanguages(req, res) {
    console.log('[Translation Service] GET /languages');
    const { query } = req.query;
    if (query === undefined) {
      return res.status(400).json({ error: ERROR_MESSAGES.QUERY_PARAM_REQUIRED });
    }

    try {
      const result = await this.searchLanguages(query);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_SEARCH_LANGUAGES });
    }
  }
}

module.exports = new TranslationController();