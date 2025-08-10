// controllers/AuthController.js - WORKING VERSION FROM MONOLITH
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const db = require('../utils/database');
const { ERROR_MESSAGES } = require('../utils/constants');
const GoogleAuthService = require('../services/GoogleAuthService');
const EmailService = require('../services/EmailService');

const JWT_SECRET = process.env.SESSION_SECRET || 'K9mP2qL8j5vX4rY7n6zB3wT';
const SESSION_EXPIRATION = 24 * 60 * 60; // 24 hours in seconds

class AuthController {
  /**
   * Register a new user - WORKING VERSION FROM MONOLITH
   */
  async register(req, res) {
    console.log('[Auth Service] POST /register');
    const { email, password } = req.body;
    if (!email || !password) {
      return res.status(400).json({ error: ERROR_MESSAGES.EMAIL_PASSWORD_REQUIRED });
    }

    try {
      const hashedPassword = await bcrypt.hash(password, 10);
      await db.createUser(email, hashedPassword);
      res.status(201).json({ success: true });
    } catch (err) {
      res.status(400).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_REGISTER });
    }
  }

  /**
   * Authenticate a user and return a JWT token - WORKING VERSION FROM MONOLITH
   */
  async login(req, res) {
    console.log('[Auth Service] POST /login');
    const { email, password } = req.body;
    if (!email || !password) {
      return res.status(400).json({ error: ERROR_MESSAGES.EMAIL_PASSWORD_REQUIRED });
    }

    try {
      const user = await db.findUserByEmail(email);
      if (!user) {
        return res.status(400).json({ error: ERROR_MESSAGES.INVALID_CREDENTIALS });
      }

      const match = await bcrypt.compare(password, user.password_hash);
      if (!match) {
        return res.status(400).json({ error: ERROR_MESSAGES.INVALID_CREDENTIALS });
      }

      const token = jwt.sign({ id: user.UserId, email: user.email }, JWT_SECRET, { expiresIn: SESSION_EXPIRATION });
      const sessionId = uuidv4();
      const expiresAt = new Date(Date.now() + SESSION_EXPIRATION * 1000);
      await db.createSession(user.UserId, sessionId, expiresAt, token);

      res.json({
        success: true,
        user: {
          id: user.UserId,
          email: user.email,
          defaultFromLang: user.default_from_lang,
          defaultToLang: user.default_to_lang,
          signed_session_id: token,
        },
        token,
      });
    } catch (err) {
      res.status(400).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_LOGIN });
    }
  }

  /**
   * Validate a user session - WORKING VERSION FROM MONOLITH
   */
  async validateSession(req, res) {
    console.log('[Auth Service] GET /validate-session');
    const authResult = await this.authenticateToken(req);
    if (authResult.error) {
      return res.status(authResult.status).json({ error: authResult.error });
    }
    res.json({ success: true, user: authResult.user });
  }

  /**
   * Log out a user by invalidating their session - WORKING VERSION FROM MONOLITH
   */
  async logout(req, res) {
    console.log('[Auth Service] POST /logout');
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];
    if (!token) {
      return res.status(401).json({ error: ERROR_MESSAGES.TOKEN_REQUIRED });
    }

    try {
      const session = await db.validateSession(token);
      if (!session) {
        return res.json({ success: true });
      }

      await db.logout(token);
      res.json({ success: true });
    } catch (err) {
      res.status(500).json({ error: err.message || ERROR_MESSAGES.FAILED_TO_LOGOUT });
    }
  }

  /**
   * Google Sign-In authentication endpoint - WORKING VERSION FROM MONOLITH
   */
  async googleAuth(req, res) {
    console.log('[Auth Service] POST /auth/google');
    const { accessToken, userInfo } = req.body;
    
    if (!accessToken || !userInfo) {
      return res.status(400).json({ error: 'Google access token and user info are required' });
    }

    try {
      // Verify the access token with Google
      const googleUserResponse = await fetch(
        `https://www.googleapis.com/oauth2/v2/userinfo?access_token=${accessToken}`
      );
      
      if (!googleUserResponse.ok) {
        return res.status(400).json({ error: 'Invalid Google access token' });
      }
      
      const googleUserData = await googleUserResponse.json();
      
      // Verify the user info matches
      if (googleUserData.id !== userInfo.id || googleUserData.email !== userInfo.email) {
        return res.status(400).json({ error: 'Google user info mismatch' });
      }

      // Create or update user in database using stored procedure
      const user = await db.googleSignIn(
        googleUserData.id,
        googleUserData.email,
        googleUserData.name || googleUserData.email.split('@')[0],
        googleUserData.picture || null
      );
      
      // Create JWT token
      const token = jwt.sign(
        { 
          id: user.UserId, 
          email: user.email,
          auth_provider: 'google'
        }, 
        JWT_SECRET, 
        { expiresIn: SESSION_EXPIRATION }
      );
      
      // Create session
      const sessionId = uuidv4();
      const expiresAt = new Date(Date.now() + SESSION_EXPIRATION * 1000);
      await db.createSession(user.UserId, sessionId, expiresAt, token);

      // Send welcome email for new users
      if (user.created_at && new Date(user.created_at).getTime() > Date.now() - 60000) {
        try {
          await EmailService.sendWelcomeEmail(user.email, user.email.split('@')[0]);
        } catch (emailError) {
          console.error('Failed to send welcome email:', emailError);
        }
      }

      res.json({
        success: true,
        user: {
          id: user.UserId,
          email: user.email,
          auth_provider: user.auth_provider,
          email_verified: user.email_verified,
          profile_picture_url: user.profile_picture_url,
          defaultFromLang: user.default_from_lang,
          defaultToLang: user.default_to_lang,
          signed_session_id: token,
        },
        token,
      });

    } catch (error) {
      console.error('Google Sign-In error:', error);
      res.status(500).json({ error: 'Google Sign-In failed' });
    }
  }

  /**
   * Request password reset endpoint - WORKING VERSION FROM MONOLITH
   */
  async forgotPassword(req, res) {
    console.log('[Auth Service] POST /auth/forgot-password');
    const { email } = req.body;
    
    if (!email) {
      return res.status(400).json({ error: 'Email is required' });
    }

    try {
      // Check if email service is properly initialized
      try {
        await EmailService.verifyConnection();
      } catch (verifyError) {
        console.error('❌ Email service verification failed:', verifyError);
        return res.status(500).json({ error: 'Email service is not configured properly' });
      }

      // Use stored procedure to handle password reset request
      const resetData = await db.requestPasswordReset(email);
      
      if (resetData.Status === 'found' && resetData.ResetToken) {
        // Send password reset email
        try {
          const emailResult = await EmailService.sendPasswordResetEmail(email, resetData.ResetToken);
          
          res.json({ 
            success: true, 
            message: 'Password reset email sent successfully' 
          });
        } catch (emailError) {
          console.error('❌ Failed to send password reset email:', emailError);
          res.status(500).json({ error: 'Failed to send password reset email' });
        }
      } else if (resetData.Status === 'google_user') {
        res.json({ 
          success: true,
          message: 'This email is associated with a Google account. Please use Google Sign-In instead.' 
        });
      } else {
        // For security, always return success even if email not found
        res.json({ 
          success: true, 
          message: 'If an account with this email exists, a password reset link has been sent.\n\nNote: If you signed up with Google, please use Google Sign-In instead.' 
        });
      }

    } catch (error) {
      console.error('❌ Password reset request error:', error);
      res.status(500).json({ error: 'Failed to process password reset request' });
    }
  }

  /**
   * Reset password endpoint - WORKING VERSION FROM MONOLITH
   */
  async resetPassword(req, res) {
    console.log('[Auth Service] POST /auth/reset-password');
    const { token, newPassword } = req.body;
    
    if (!token || !newPassword) {
      return res.status(400).json({ error: 'Reset token and new password are required' });
    }

    if (newPassword.length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters long' });
    }

    try {
      // Hash the new password
      const hashedPassword = await bcrypt.hash(newPassword, 10);
      
      // Use stored procedure to reset password
      const resetResult = await db.resetPassword(token, hashedPassword);
      
      res.json({
        success: true,
        message: resetResult.Message,
        email: resetResult.Email
      });

    } catch (error) {
      console.error('Password reset error:', error);
      if (error.message.includes('Invalid or expired reset token')) {
        res.status(400).json({ error: 'Invalid or expired reset token' });
      } else {
        res.status(500).json({ error: 'Failed to reset password' });
      }
    }
  }

  /**
   * Refresh token endpoint - WORKING VERSION FROM MONOLITH
   */
  async refreshToken(req, res) {
    console.log('[Auth Service] POST /auth/refresh-token');
    const authResult = await this.authenticateToken(req);
    if (authResult.error) {
      return res.status(authResult.status).json({ error: authResult.error });
    }

    try {
      // Generate new token
      const newToken = jwt.sign(
        { 
          id: authResult.user.id, 
          email: authResult.user.email 
        }, 
        JWT_SECRET, 
        { expiresIn: SESSION_EXPIRATION }
      );
      
      // Update session with new token
      const sessionId = uuidv4();
      const expiresAt = new Date(Date.now() + SESSION_EXPIRATION * 1000);
      await db.createSession(authResult.user.id, sessionId, expiresAt, newToken);

      res.json({
        success: true,
        token: newToken
      });

    } catch (error) {
      console.error('Token refresh error:', error);
      res.status(500).json({ error: 'Failed to refresh token' });
    }
  }

  /**
   * WORKING middleware to verify JWT token for protected routes.
   */
  async authenticateToken(req) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];
    if (!token) {
      return { error: ERROR_MESSAGES.TOKEN_REQUIRED, status: 401 };
    }

    try {
      const decoded = jwt.verify(token, JWT_SECRET);
      const session = await db.validateSession(token);
      if (!session) {
        return { error: ERROR_MESSAGES.INVALID_SESSION, status: 403 };
      }
      return { user: decoded };
    } catch (err) {
      return { error: ERROR_MESSAGES.INVALID_TOKEN, status: 403 };
    }
  }
}

module.exports = new AuthController();