/**
 * AuthMiddleware.js - Authentication Middleware
 * Provides JWT token validation and session verification for protected routes.
 * Includes both required and optional authentication middleware functions
 * for different endpoint security requirements across all services.
 */

const jwt = require('jsonwebtoken');
const db = require('../utils/database');
const { ERROR_MESSAGES } = require('../utils/constants');

const JWT_SECRET = process.env.SESSION_SECRET || 'K9mP2qL8j5vX4rY7n6zB3wT';

// Validates JWT token and session for authenticated requests
const authenticateToken = async (req) => {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    console.log('[UDS][AUTH] missing/invalid Authorization header');
    return { error: ERROR_MESSAGES.TOKEN_REQUIRED, status: 401 };
  }

  const token = authHeader.split(' ')[1];
  try {
    console.log('[UDS][AUTH] token length:', token.length);
    const decoded = jwt.verify(token, JWT_SECRET);

    // Optional: log claims briefly (avoid PII)
    console.log('[UDS][AUTH] decoded user id:', decoded?.id);

    const session = await db.validateSession(token);
    if (!session) {
      console.log('[UDS][AUTH] session not found/expired for token');
      return { error: ERROR_MESSAGES.INVALID_SESSION, status: 403 };
    }
    return { user: decoded };
  } catch (err) {
    console.log('[UDS][AUTH] verify failed:', err.message);
    return { error: ERROR_MESSAGES.INVALID_TOKEN, status: 403 };
  }
};

// Validates JWT token optionally, allowing unauthenticated requests
const optionalAuthenticateToken = async (req) => {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return { user: null };
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    const session = await db.validateSession(token);
    if (!session) {
      console.log('[UDS][AUTH][OPT] invalid session');
      return { error: ERROR_MESSAGES.INVALID_SESSION, status: 403 };
    }
    return { user: decoded };
  } catch (err) {
    console.log('[UDS][AUTH][OPT] verify failed:', err.message);
    return { error: ERROR_MESSAGES.INVALID_TOKEN, status: 403 };
  }
};

// Express middleware requiring valid authentication to proceed
const authMiddleware = async (req, res, next) => {
  const authResult = await authenticateToken(req);
  if (authResult.error) {
    return res.status(authResult.status).json({ error: authResult.error });
  }
  req.user = authResult.user;
  next();
};

// Express middleware allowing both authenticated and anonymous requests
const optionalAuthMiddleware = async (req, res, next) => {
  const authResult = await optionalAuthenticateToken(req);
  if (authResult.error) {
    return res.status(authResult.status).json({ error: authResult.error });
  }
  req.user = authResult.user;
  next();
};

module.exports = {
  authenticateToken,
  optionalAuthenticateToken,
  authMiddleware,
  optionalAuthMiddleware
};
