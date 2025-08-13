/**
 * AuthRoutes.js - Authentication Routes Configuration
 * Defines all authentication-related endpoints for the auth service.
 * Maps HTTP routes to AuthController methods for user registration,
 * login, session management, Google OAuth, and password reset operations.
 */

const express = require('express');
const router = express.Router();
const AuthController = require('../controllers/AuthController');

/**
 * Register a new user account with email and password
 */
router.post('/register', AuthController.register.bind(AuthController));

/**
 * Authenticates user credentials and returns JWT token
 */
router.post('/login', AuthController.login.bind(AuthController));

/**
 * Validates current user session using JWT token
 */
router.get('/validate-session', AuthController.validateSession.bind(AuthController));

/**
 * Invalidates user session and logs out user 
 */
router.post('/logout', AuthController.logout.bind(AuthController));

/**
 * Google Sign-In authentication endpoint (handles Google Oauth auth flow)
 */
router.post('/auth/google', AuthController.googleAuth.bind(AuthController));

/**
 * Request password reset endpoint (initiates password reset process via email)
 */
router.post('/auth/forgot-password', AuthController.forgotPassword.bind(AuthController));

/**
 * Reset password endpoint (Completes password reset using reset token/QR)
 */
router.post('/auth/reset-password', AuthController.resetPassword.bind(AuthController));

/**
 * Refresh token endpoint (Generates new JWT token for authenticated user)
 */
router.post('/auth/refresh-token', AuthController.refreshToken.bind(AuthController));

module.exports = router;