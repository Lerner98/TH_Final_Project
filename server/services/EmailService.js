// ===== EMAIL SERVICE =====
// services/EmailService.js
const nodemailer = require('nodemailer');
const path = require('path');
const fs = require('fs');

class EmailService {
  constructor() {
    this.transporter = null;
    this.initialize();
  }

  initialize() {
    // Configure email transporter based on environment
    const emailConfig = {
      // Gmail configuration (most common)
      gmail: {
        service: 'gmail',
        auth: {
          user: process.env.GMAIL_USER,
          pass: process.env.GMAIL_APP_PASSWORD, // Use App Password, not regular password
        },
      },
      // Generic SMTP configuration
      smtp: {
        host: process.env.SMTP_HOST,
        port: parseInt(process.env.SMTP_PORT) || 587,
        secure: process.env.SMTP_SECURE === 'true', // true for 465, false for other ports
        auth: {
          user: process.env.SMTP_USER,
          pass: process.env.SMTP_PASS,
        },
      },
      // Development mode (use Ethereal for testing)
      development: {
        host: 'smtp.ethereal.email',
        port: 587,
        secure: false,
        auth: {
          user: process.env.ETHEREAL_USER,
          pass: process.env.ETHEREAL_PASS,
        },
      },
    };

    const provider = process.env.EMAIL_PROVIDER || 'gmail';
    const config = emailConfig[provider];

    if (!config) {
      console.error('Invalid email provider configuration');
      return;
    }

    try {
      this.transporter = nodemailer.createTransport(config);
      console.log(`Email service initialized with ${provider} provider`);
    } catch (error) {
      console.error('Failed to initialize email service:', error);
    }
  }

  async verifyConnection() {
    if (!this.transporter) {
      throw new Error('Email service not initialized');
    }

    try {
      await this.transporter.verify();
      console.log('Email service connection verified');
      return true;
    } catch (error) {
      console.error('Email service verification failed:', error);
      throw error;
    }
  }

  async sendPasswordResetEmail(email, resetToken) {
    if (!this.transporter) {
      throw new Error('Email service not configured');
    }

    const resetUrl = `${process.env.FRONTEND_URL || 'https://your-redirect-domain.com/reset'}?token=${resetToken}`;
    
    const htmlTemplate = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Password Reset - TranslationHub</title>
        <style>
          body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            line-height: 1.6;
            color: #333;
            max-width: 600px;
            margin: 0 auto;
            padding: 20px;
          }
          .header {
            background: linear-gradient(135deg, #007AFF, #5856D6);
            color: white;
            padding: 30px;
            text-align: center;
            border-radius: 10px 10px 0 0;
          }
          .content {
            background: #f8f9fa;
            padding: 30px;
            border-radius: 0 0 10px 10px;
          }
          .reset-button {
            display: inline-block;
            background: #007AFF;
            color: white !important;
            padding: 15px 30px;
            text-decoration: none;
            border-radius: 8px;
            font-weight: 600;
            margin: 20px 0;
          }
          .reset-button:hover {
            background: #0056CC;
          }
          .footer {
            text-align: center;
            margin-top: 30px;
            padding-top: 20px;
            border-top: 1px solid #e9ecef;
            color: #6c757d;
            font-size: 14px;
          }
          .warning {
            background: #fff3cd;
            color: #856404;
            padding: 15px;
            border-radius: 5px;
            margin: 20px 0;
            border-left: 4px solid #ffc107;
          }
        </style>
      </head>
      <body>
        <div class="header">
          <h1>🌐 TranslationHub</h1>
          <p>Password Reset Request</p>
        </div>
        
        <div class="content">
          <h2>Reset Your Password</h2>
          <p>Hello,</p>
          <p>You recently requested to reset your password for your TranslationHub account.</p>
          
          <div style="text-align: center;">
            <a href="${resetUrl}" class="reset-button">Reset Password</a>
          </div>
          
          <div style="text-align: center; margin: 20px 0;">
            <p><strong>Or scan this QR code:</strong></p>
            <img src="https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(resetUrl)}" alt="QR Code" style="border: 1px solid #ddd; padding: 10px;">
          </div>
          
          <div class="warning">
            <strong>⚠️ Important:</strong> This link will expire in 1 hour for security reasons.
          </div>
          
          <p>If you're using the mobile app, the link will open directly in the app. If you're having trouble with the button, copy and paste this link into your browser:</p>
          <p style="word-break: break-all; background: #e9ecef; padding: 10px; border-radius: 5px; font-family: monospace;">
            ${resetUrl}
          </p>
          
          <p><strong>If you didn't request this password reset, please ignore this email.</strong> Your password will remain unchanged.</p>
        </div>
        
        <div class="footer">
          <p>© 2025 TranslationHub. All rights reserved.</p>
          <p>This is an automated message, please do not reply to this email.</p>
        </div>
      </body>
      </html>
    `;

    const textVersion = `
      TranslationHub - Password Reset Request
      
      Hello,
      
      You recently requested to reset your password for your TranslationHub account.
      
      Click or copy this link to reset your password:
      ${resetUrl}
      
      This link will expire in 1 hour for security reasons.
      
      If you didn't request this password reset, please ignore this email.
      
      © 2025 TranslationHub. All rights reserved.
    `;

    try {
      const info = await this.transporter.sendMail({
        from: `"TranslationHub" <${process.env.GMAIL_USER}>`,
        to: email,
        subject: 'Reset Your TranslationHub Password',
        text: textVersion,
        html: htmlTemplate,
      });

      console.log('Password reset email sent:', info.messageId);
      return info;
    } catch (error) {
      console.error('Failed to send password reset email:', error);
      throw error;
    }
  }

  async sendWelcomeEmail(email, name) {
    if (!this.transporter) {
      throw new Error('Email service not configured');
    }

    const htmlTemplate = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Welcome to TranslationHub</title>
        <style>
          body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            line-height: 1.6;
            color: #333;
            max-width: 600px;
            margin: 0 auto;
            padding: 20px;
          }
          .header {
            background: linear-gradient(135deg, #007AFF, #5856D6);
            color: white;
            padding: 30px;
            text-align: center;
            border-radius: 10px 10px 0 0;
          }
          .content {
            background: #f8f9fa;
            padding: 30px;
            border-radius: 0 0 10px 10px;
          }
          .feature {
            background: white;
            padding: 20px;
            margin: 15px 0;
            border-radius: 8px;
            border-left: 4px solid #007AFF;
          }
          .footer {
            text-align: center;
            margin-top: 30px;
            padding-top: 20px;
            border-top: 1px solid #e9ecef;
            color: #6c757d;
            font-size: 14px;
          }
        </style>
      </head>
      <body>
        <div class="header">
          <h1>🌐 Welcome to TranslationHub!</h1>
          <p>Your journey to seamless translation begins here</p>
        </div>
        
        <div class="content">
          <h2>Hello${name ? ` ${name}` : ''}! 👋</h2>
          <p>Thank you for joining TranslationHub! You now have access to powerful translation features:</p>
          
          <div class="feature">
            <h3>📝 Text & Voice Translation</h3>
            <p>Translate text or speak directly for instant voice translation</p>
          </div>
          
          <div class="feature">
            <h3>📁 File Translation</h3>
            <p>Upload and translate entire documents (PDF, DOCX, TXT)</p>
          </div>
          
          <div class="feature">
            <h3>📷 Camera Translation</h3>
            <p>Point your camera at text for instant visual translation</p>
          </div>
          
          <div class="feature">
            <h3>🤟 ASL Recognition</h3>
            <p>American Sign Language gesture recognition and translation</p>
          </div>
          
          <p>Get started by downloading our mobile app or logging into your account!</p>
        </div>
        
        <div class="footer">
          <p>© 2025 TranslationHub. All rights reserved.</p>
          <p>Need help? Contact our support team anytime.</p>
        </div>
      </body>
      </html>
    `;

    try {
      const info = await this.transporter.sendMail({
        from: `"TranslationHub" <${process.env.GMAIL_USER}>`,
        to: email,
        subject: 'Welcome to TranslationHub! 🌐',
        html: htmlTemplate,
      });

      console.log('Welcome email sent:', info.messageId);
      return info;
    } catch (error) {
      console.error('Failed to send welcome email:', error);
      throw error;
    }
  }

  async sendTestEmail(email) {
    if (!this.transporter) {
      throw new Error('Email service not configured');
    }

    try {
      const info = await this.transporter.sendMail({
        from: `"TranslationHub" <${process.env.GMAIL_USER}>`,
        to: email,
        subject: 'TranslationHub Email Service Test',
        text: 'This is a test email from TranslationHub email service.',
        html: '<p>This is a test email from <strong>TranslationHub</strong> email service.</p>',
      });

      console.log('Test email sent:', info.messageId);
      return info;
    } catch (error) {
      console.error('Failed to send test email:', error);
      throw error;
    }
  }
}

module.exports = new EmailService(); 