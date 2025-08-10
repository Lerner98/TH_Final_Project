// routes/AuthRoutes.js
const express = require('express');
const router = express.Router();
const AuthController = require('../controllers/AuthController');

/**
 * Register a new user
 */
router.post('/register', AuthController.register.bind(AuthController));

/**
 * Authenticate a user and return a JWT token
 */
router.post('/login', AuthController.login.bind(AuthController));

/**
 * Validate a user session
 */
router.get('/validate-session', AuthController.validateSession.bind(AuthController));

/**
 * Log out a user by invalidating their session
 */
router.post('/logout', AuthController.logout.bind(AuthController));

/**
 * Google Sign-In authentication endpoint
 */
router.post('/auth/google', AuthController.googleAuth.bind(AuthController));

/**
 * Request password reset endpoint
 */
router.post('/auth/forgot-password', AuthController.forgotPassword.bind(AuthController));

/**
 * Reset password endpoint
 */
router.post('/auth/reset-password', AuthController.resetPassword.bind(AuthController));

/**
 * Refresh token endpoint
 */
router.post('/auth/refresh-token', AuthController.refreshToken.bind(AuthController));

module.exports = router;