/**
 * translation.js - Translation Microservice
 * Express server handling translation operations on port 3002.
 * Provides endpoints for text translation, OCR, speech processing,
 * ASL recognition, and document handling using OpenAI APIs.
 */

require('dotenv').config();
const express = require('express');
const cors = require('cors');
const translationRoutes = require('../routes/TranslationRoutes');
const db = require('../utils/database');

const path = require('path');
const dotenv = require('dotenv');

dotenv.config({ path: path.resolve(__dirname, '../.env') });
console.log('OPENAI_API_KEY loaded:', !!process.env.OPENAI_API_KEY);

const app = express();
const port = 3002;

console.log('[Translation Service] Starting on port 3002...');

// Configure CORS to allow all origins and standard HTTP methods
app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'DELETE', 'PUT', 'PATCH'],
  allowedHeaders: ['Content-Type', 'Authorization'],
}));
app.use(express.json({ limit: '50mb' }));

// Handle HTTP method override for clients that don't support all methods
app.use((req, res, next) => {
  if (req.method === 'POST' && req.query._method) {
    req.method = req.query._method.toUpperCase();
  }
  next();
});

// Debug middleware to log all incoming requests and authentication status
app.use((req, res, next) => {
  console.log(`[Translation Service] ${req.method} ${req.url} - DEBUG`);
  console.log(`[Translation Service] Headers:`, req.headers.authorization ? 'TOKEN_PRESENT' : 'NO_TOKEN');
  console.log(`[Translation Service] Body:`, req.body);
  next();
});

// Global error handler for catching and logging server errors
app.use((err, req, res, next) => {
  console.error('[Translation Service] ERROR:', err);
  res.status(500).json({ error: 'Internal server error' });
});

app.use('/', translationRoutes);

// Health check endpoint for service monitoring
app.get('/health', (req, res) => {
  res.json({ 
    status: 'OK', 
    timestamp: new Date().toISOString(),
    service: 'Translation Service'
  });
});

app.listen(port, '0.0.0.0', () => {
  console.log(`[Translation Service] Server running on port ${port}`);
});