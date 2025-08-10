// models/User.js
class User {
  constructor(data) {
    this.id = data.UserId || data.id;
    this.email = data.email;
    this.defaultFromLang = data.default_from_lang;
    this.defaultToLang = data.default_to_lang;
    this.authProvider = data.auth_provider;
    this.emailVerified = data.email_verified;
    this.profilePictureUrl = data.profile_picture_url;
    this.createdAt = data.created_at;
  }

  /**
   * Validate user registration data
   */
  static validateRegistration(data) {
    const errors = [];
    
    if (!data.email || !data.email.includes('@')) {
      errors.push('Valid email is required');
    }
    
    if (!data.password || data.password.length < 6) {
      errors.push('Password must be at least 6 characters long');
    }
    
    return {
      isValid: errors.length === 0,
      errors
    };
  }

  /**
   * Validate user login data
   */
  static validateLogin(data) {
    const errors = [];
    
    if (!data.email || !data.email.includes('@')) {
      errors.push('Valid email is required');
    }
    
    if (!data.password) {
      errors.push('Password is required');
    }
    
    return {
      isValid: errors.length === 0,
      errors
    };
  }

  /**
   * Format user response data
   */
  toResponse() {
    return {
      id: this.id,
      email: this.email,
      defaultFromLang: this.defaultFromLang,
      defaultToLang: this.defaultToLang,
      authProvider: this.authProvider,
      emailVerified: this.emailVerified,
      profilePictureUrl: this.profilePictureUrl
    };
  }
}

module.exports = User;