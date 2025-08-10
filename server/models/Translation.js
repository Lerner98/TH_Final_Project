// models/Translation.js
class Translation {
  constructor(data) {
    this.id = data.id;
    this.userId = data.userId;
    this.fromLang = data.fromLang;
    this.toLang = data.toLang;
    this.originalText = data.originalText;
    this.translatedText = data.translatedText;
    this.type = data.type; // 'text', 'voice', 'image', 'file'
    this.createdAt = data.createdAt;
  }

  /**
   * Validate translation request data
   */
  static validateTranslationRequest(data) {
    const errors = [];
    
    if (!data.text || data.text.trim().length === 0) {
      errors.push('Text is required');
    }
    
    if (!data.targetLang) {
      errors.push('Target language is required');
    }
    
    if (data.text && data.text.length > 5000) {
      errors.push('Text must be less than 5000 characters');
    }
    
    return {
      isValid: errors.length === 0,
      errors
    };
  }

  /**
   * Validate file extraction request
   */
  static validateFileExtractionRequest(data) {
    const errors = [];
    
    if (!data.uri) {
      errors.push('File URI is required');
    }
    
    return {
      isValid: errors.length === 0,
      errors
    };
  }

  /**
   * Validate image recognition request
   */
  static validateImageRequest(data) {
    const errors = [];
    
    if (!data.imageBase64) {
      errors.push('Image data is required');
    }
    
    return {
      isValid: errors.length === 0,
      errors
    };
  }

  /**
   * Validate TTS request
   */
  static validateTTSRequest(data) {
    const errors = [];
    
    if (!data.text || data.text.trim().length === 0) {
      errors.push('Text is required');
    }
    
    if (!data.language) {
      errors.push('Language is required');
    }
    
    return {
      isValid: errors.length === 0,
      errors
    };
  }

  /**
   * Format translation response
   */
  toResponse() {
    return {
      id: this.id,
      fromLang: this.fromLang,
      toLang: this.toLang,
      originalText: this.originalText,
      translatedText: this.translatedText,
      type: this.type,
      createdAt: this.createdAt
    };
  }
}

module.exports = Translation;